package com.memowallpaper

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

/**
 * 장소 알림 등록. 안드로이드 위치 감지(지오펜스)를 써서 앱이 꺼져 있어도
 * 장소에 도착·출발하면 [GeofenceReceiver]가 깨어나요.
 */
object GeofenceManager {
    private const val REQUEST_CODE = 5100

    fun hasForegroundLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** 장소 알림을 쓸 준비가 다 됐는지 */
    fun ready(context: Context): Boolean = hasForegroundLocation(context) && hasBackgroundLocation(context)

    private fun pendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, Intent(context, GeofenceReceiver::class.java), flags)
    }

    fun buildGeofence(p: Place): Geofence {
        val types = when (p.trigger) {
            Trigger.ENTER -> Geofence.GEOFENCE_TRANSITION_ENTER
            Trigger.EXIT -> Geofence.GEOFENCE_TRANSITION_EXIT
            Trigger.BOTH -> Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
        }
        return Geofence.Builder()
            .setRequestId(p.id)
            .setCircularRegion(p.lat, p.lon, p.radius.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(types)
            .setNotificationResponsiveness(30_000)
            .build()
    }

    /** 켜진 장소들을 다시 등록(전부 지우고 새로 등록) */
    @SuppressLint("MissingPermission")
    fun sync(context: Context) {
        val app = context.applicationContext
        val store = MemoStore.get(app)
        val places = store.places.value.filter { it.enabled }
        val client = runCatching { LocationServices.getGeofencingClient(app) }.getOrNull() ?: return
        val pi = pendingIntent(app)
        runCatching {
            client.removeGeofences(pi).addOnCompleteListener {
                if (places.isEmpty() || !ready(app)) {
                    store.geofenceError = null
                    return@addOnCompleteListener
                }
                // 등록하는 순간 이미 안에 있어도 바로 알리지는 않음(initialTrigger = 0)
                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(0)
                    .addGeofences(places.take(100).map(::buildGeofence))
                    .build()
                client.addGeofences(request, pi)
                    .addOnSuccessListener { store.geofenceError = null }
                    .addOnFailureListener { e -> store.geofenceError = describe(e) }
            }
        }.onFailure { store.geofenceError = it.message }
    }

    private fun describe(e: Exception): String {
        val code = (e as? ApiException)?.statusCode
        return when (code) {
            GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE ->
                "휴대폰 위치가 꺼져 있거나 'Google 위치 정확도'가 꺼져 있어요. 위치 설정에서 켜 주세요."
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> "장소가 너무 많아요(최대 100개)."
            else -> "위치 감지를 등록하지 못했어요: ${e.message ?: code}"
        }
    }
}

/** 장소 도착·출발 시 알림 보내기(+ 잠금화면 바꾸기) */
object PlaceAlerts {
    const val CHANNEL_ID = "place_alerts"
    /** 경계에서 들락날락해도 같은 알림은 이 시간 안에 다시 안 보냄 */
    private const val COOLDOWN_MS = 15 * 60 * 1000L

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "장소 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "정해 둔 장소에 도착하거나 떠날 때 알려줘요"
                enableVibration(true)
            },
        )
    }

    /** 알림 본문(문구가 비어 있으면 연결된 메모 내용) */
    fun body(place: Place, store: MemoStore): String {
        val msg = TextTokens.resolve(place.message).trim()
        if (msg.isNotEmpty()) return msg
        return store.find(place.memoId)?.let { WidgetUpdater.widgetText(it) } ?: "잊은 것 없는지 확인해요"
    }

    fun title(place: Place, entering: Boolean): String =
        if (entering) "📍 ${place.displayName()} 근처예요" else "📍 ${place.displayName()}에서 나왔어요"

    /**
     * 장소 이벤트 처리. 반환값: 알림을 보냈는지
     */
    suspend fun handle(context: Context, placeId: String, entering: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        val store = MemoStore.get(context)
        val place = store.findPlace(placeId) ?: return false
        if (!place.enabled) return false
        val wanted = when (place.trigger) {
            Trigger.ENTER -> entering
            Trigger.EXIT -> !entering
            Trigger.BOTH -> true
        }
        if (!wanted) return false
        if (now - store.lastPlaceAlertAt(placeId, entering) < COOLDOWN_MS) return false
        store.markPlaceAlert(placeId, entering)

        notify(context, place, entering, store)

        if (place.changeWallpaper) {
            place.wallpaperMemo(store)?.let { WallpaperApplier.apply(context, it, store.target.value) }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun notify(context: Context, place: Place, entering: Boolean, store: MemoStore) {
        if (!GeofenceManager.hasNotificationPermission(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, place.id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = body(place, store)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title(place, entering))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(place.id.hashCode() * 2 + if (entering) 1 else 0, n)
    }
}

/** 지오펜스 이벤트 수신 */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val entering = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> true
            Geofence.GEOFENCE_TRANSITION_EXIT -> false
            else -> return
        }
        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        if (ids.isEmpty()) return
        val app = context.applicationContext
        val pending = goAsync()
        AppScope.launch {
            try {
                ids.forEach { PlaceAlerts.handle(app, it, entering) }
            } finally {
                pending.finish()
            }
        }
    }
}
