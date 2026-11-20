package com.memowallpaper

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** 현재 위치, 주소 검색 */
object LocationHelper {

    data class Found(val label: String, val lat: Double, val lon: Double)

    /** 지금 위치(권한이 없거나 못 찾으면 null) */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Pair<Double, Double>? {
        if (!GeofenceManager.hasForegroundLocation(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        val loc = withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                cont.invokeOnCancellation { cts.cancel() }
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } ?: runCatching {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                client.lastLocation.addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) }
            }
        }.getOrNull()
        return loc?.let { it.latitude to it.longitude }
    }

    private fun label(a: Address): String {
        val line = a.getAddressLine(0)
        val feature = a.featureName
        return when {
            line.isNullOrBlank() -> feature ?: "${a.latitude}, ${a.longitude}"
            !feature.isNullOrBlank() && !line.contains(feature) -> "$feature · $line"
            else -> line
        }
    }

    /** 주소·장소 이름으로 검색(휴대폰의 주소 검색 기능 사용) */
    suspend fun search(context: Context, query: String): List<Found> {
        if (!Geocoder.isPresent() || query.isBlank()) return emptyList()
        val geocoder = Geocoder(context, Locale.KOREA)
        val list: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine<List<Address>> { cont ->
                    geocoder.getFromLocationName(query, 6, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                        override fun onError(errorMessage: String?) = cont.resume(emptyList())
                    })
                }
            } ?: emptyList()
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocationName(query, 6) }.getOrNull().orEmpty()
            }
        }
        return list.filter { it.hasLatitude() && it.hasLongitude() }.map { Found(label(it), it.latitude, it.longitude) }
    }

    /** 좌표 → 주소 한 줄 */
    suspend fun addressOf(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.KOREA)
        val list: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<List<Address>> { cont ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                        override fun onError(errorMessage: String?) = cont.resume(emptyList())
                    })
                }
            } ?: emptyList()
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lon, 1) }.getOrNull().orEmpty()
            }
        }
        return list.firstOrNull()?.getAddressLine(0)
    }
}
