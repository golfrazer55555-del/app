package com.memowallpaper

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** 타이머 시작·멈춤·울림 관리 */
object TimerManager {
    const val ACTION_FIRE = "timer.FIRE"
    const val ACTION_PAUSE = "timer.PAUSE"
    const val ACTION_RESUME = "timer.RESUME"
    const val ACTION_RESET = "timer.RESET"
    const val ACTION_ADD_MINUTE = "timer.ADD_MINUTE"
    const val ACTION_DISMISS = "timer.DISMISS"
    const val EXTRA_ID = "timer_id"

    private const val CHANNEL_RUNNING = "timer_running"
    const val CHANNEL_RING = "timer_ring"

    fun notificationId(id: String) = 9000 + (id.hashCode() and 0x3FFF)

    private fun store(context: Context) = MemoStore.get(context)

    /** 새 타이머 만들고 바로 시작 */
    fun createAndStart(context: Context, durationMs: Long, label: String = ""): TimerItem {
        val store = store(context)
        // 같은 길이의 이름 없는 쉬는 타이머가 있으면 재사용(목록이 쌓이지 않게)
        val reuse = store.timers.value.firstOrNull {
            it.state == TimerState.IDLE && it.durationMs == durationMs && it.label == label
        }
        val t = reuse ?: TimerItem(label = label, durationMs = durationMs).also { store.upsertTimer(it) }
        start(context, t.id)
        return store.findTimer(t.id) ?: t
    }

    fun start(context: Context, id: String, now: Long = System.currentTimeMillis()) {
        val store = store(context)
        val t = store.findTimer(id) ?: return
        val remain = if (t.state == TimerState.PAUSED) t.remainingMs else t.durationMs
        val running = t.copy(state = TimerState.RUNNING, endAt = now + remain, remainingMs = remain)
        store.upsertTimer(running)
        schedule(context, running)
        showRunning(context, running)
    }

    fun pause(context: Context, id: String, now: Long = System.currentTimeMillis()) {
        val store = store(context)
        val t = store.findTimer(id) ?: return
        if (t.state != TimerState.RUNNING) return
        val paused = t.copy(state = TimerState.PAUSED, remainingMs = (t.endAt - now).coerceAtLeast(1000))
        store.upsertTimer(paused)
        cancelAlarm(context, id)
        showRunning(context, paused)
    }

    fun reset(context: Context, id: String) {
        val store = store(context)
        val t = store.findTimer(id) ?: return
        store.upsertTimer(t.copy(state = TimerState.IDLE, endAt = 0, remainingMs = t.durationMs))
        cancelAlarm(context, id)
        NotificationManagerCompat.from(context).cancel(notificationId(id))
        RingService.stopRinging(context, id)
    }

    fun delete(context: Context, id: String) {
        reset(context, id)
        store(context).deleteTimer(id)
    }

    fun addMinute(context: Context, id: String, now: Long = System.currentTimeMillis()) {
        val store = store(context)
        val t = store.findTimer(id) ?: return
        when (t.state) {
            TimerState.RUNNING -> {
                val updated = t.copy(endAt = t.endAt + 60_000)
                store.upsertTimer(updated)
                schedule(context, updated)
                showRunning(context, updated)
            }
            TimerState.PAUSED -> {
                val updated = t.copy(remainingMs = t.remainingMs + 60_000)
                store.upsertTimer(updated)
                showRunning(context, updated)
            }
            TimerState.IDLE -> store.upsertTimer(t.copy(durationMs = t.durationMs + 60_000, remainingMs = t.durationMs + 60_000))
            TimerState.RINGING -> {
                RingService.stopRinging(context, id)
                val updated = t.copy(state = TimerState.RUNNING, endAt = now + 60_000)
                store.upsertTimer(updated)
                schedule(context, updated)
                showRunning(context, updated)
            }
        }
    }

    /** 시간이 다 됐을 때(알람에서 호출) */
    fun fire(context: Context, id: String) {
        val store = store(context)
        val t = store.findTimer(id) ?: return
        if (t.state != TimerState.RUNNING) return
        store.upsertTimer(t.copy(state = TimerState.RINGING))
        NotificationManagerCompat.from(context).cancel(notificationId(id))
        RingService.ring(context, id)
    }

    /** 울림 끄기 → 다시 쓸 수 있게 처음 상태로 */
    fun dismiss(context: Context, id: String) = reset(context, id)

    /** 재부팅·앱 시작 시: 지난 타이머는 바로 울리고, 남은 건 다시 예약 */
    fun restore(context: Context, now: Long = System.currentTimeMillis()) {
        store(context).timers.value.filter { it.state == TimerState.RUNNING }.forEach { t ->
            if (t.endAt <= now) fire(context, t.id) else {
                schedule(context, t)
                showRunning(context, t)
            }
        }
    }

    private fun firePendingIntent(context: Context, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, notificationId(id),
            Intent(context, TimerReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 9001,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, "TIMER")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun schedule(context: Context, t: TimerItem) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = firePendingIntent(context, t.id)
        runCatching {
            if (Scheduler.canExact(context)) {
                // 알람 시계 방식: 절전 모드에서도 정확한 시각에 울려요
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t.endAt, openApp(context)), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.endAt, pi)
            }
        }
    }

    private fun cancelAlarm(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java)?.cancel(firePendingIntent(context, id))
    }

    private fun action(context: Context, action: String, id: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, notificationId(id) * 10 + code,
            Intent(context, TimerReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun ensureRunningChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_RUNNING) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "타이머 진행", NotificationManager.IMPORTANCE_LOW).apply {
                description = "돌아가는 타이머의 남은 시간"
                setShowBadge(false)
            },
        )
    }

    /** 남은 시간이 줄어드는 알림(진행 중) 또는 멈춤 알림 */
    @SuppressLint("MissingPermission")
    private fun showRunning(context: Context, t: TimerItem) {
        if (!GeofenceManager.hasNotificationPermission(context)) return
        ensureRunningChannel(context)
        val b = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("⏱ ${t.displayName()}")
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (t.state == TimerState.RUNNING) {
            b.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(t.endAt)
                .setShowWhen(true)
                .setContentText("진행 중")
                .addAction(0, "일시정지", action(context, ACTION_PAUSE, t.id, 1))
        } else {
            b.setContentText("일시정지 · 남은 시간 ${formatDuration(t.remainingMs)}")
                .setShowWhen(false)
                .addAction(0, "계속", action(context, ACTION_RESUME, t.id, 2))
        }
        b.addAction(0, "+1분", action(context, ACTION_ADD_MINUTE, t.id, 3))
            .addAction(0, "취소", action(context, ACTION_RESET, t.id, 4))
        NotificationManagerCompat.from(context).notify(notificationId(t.id), b.build())
    }

    fun ringActionIntent(context: Context, action: String, id: String, code: Int) = action(context, action, id, code)
}

/** 타이머 알람·알림 버튼 */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(TimerManager.EXTRA_ID) ?: return
        when (intent.action) {
            TimerManager.ACTION_FIRE -> TimerManager.fire(context, id)
            TimerManager.ACTION_PAUSE -> TimerManager.pause(context, id)
            TimerManager.ACTION_RESUME -> TimerManager.start(context, id)
            TimerManager.ACTION_RESET, TimerManager.ACTION_DISMISS -> TimerManager.reset(context, id)
            TimerManager.ACTION_ADD_MINUTE -> TimerManager.addMinute(context, id)
        }
    }
}

/**
 * 타이머가 끝나면 고른 소리를 반복 재생 + 진동. 1분 동안 안 끄면 저절로 멈춰요.
 */
class RingService : Service() {

    companion object {
        private const val ACTION_RING = "ring.START"
        private const val ACTION_STOP = "ring.STOP"
        private const val NOTIFICATION_ID = 9500
        private const val AUTO_STOP_MS = 60_000L

        fun ring(context: Context, id: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RingService::class.java).setAction(ACTION_RING).putExtra(TimerManager.EXTRA_ID, id),
            )
        }

        fun stopRinging(context: Context, id: String) {
            if (!running) return
            context.startService(Intent(context, RingService::class.java).setAction(ACTION_STOP).putExtra(TimerManager.EXTRA_ID, id))
        }

        @Volatile
        var running = false
            private set
    }

    private val ringing = linkedSetOf<String>()
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable {
        // 아무도 안 끄면: 소리는 멈추고 '끝남' 알림만 남김
        ringing.toList().forEach { id -> missed(id) }
        ringing.clear()
        finish()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(TimerManager.EXTRA_ID)
        when (intent?.action) {
            ACTION_RING -> {
                running = true
                if (id != null) ringing += id
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
                ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
                startSound()
                Haptics.play(this, Sounds.VIBRATION, repeat = 0)
                handler.removeCallbacks(autoStop)
                handler.postDelayed(autoStop, AUTO_STOP_MS)
                // 잠금화면 위에 울림 화면 띄우기
                runCatching {
                    startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            ACTION_STOP -> {
                if (id != null) ringing -= id
                if (ringing.isEmpty()) finish() else updateNotification()
            }
            else -> if (ringing.isEmpty()) finish()
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(TimerManager.CHANNEL_RING) != null) return
        nm.createNotificationChannel(
            NotificationChannel(TimerManager.CHANNEL_RING, "타이머 끝", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "타이머가 끝나면 울려요(소리는 앱이 직접 재생)"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val store = MemoStore.get(this)
        val names = ringing.mapNotNull { store.findTimer(it)?.displayName() }
        val first = ringing.firstOrNull() ?: ""
        val full = PendingIntent.getActivity(
            this, 9501, Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, TimerManager.CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("⏰ 시간 끝!")
            .setContentText(names.joinToString(", ").ifEmpty { "타이머" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .addAction(0, "끄기", TimerManager.ringActionIntent(this, TimerManager.ACTION_DISMISS, first, 5))
            .addAction(0, "+1분", TimerManager.ringActionIntent(this, TimerManager.ACTION_ADD_MINUTE, first, 6))
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun startSound() {
        if (player != null) return
        val sound = MemoStore.get(this).timerSound
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@RingService, sound.alarmUri(this@RingService))
                isLooping = true
                setWakeMode(this@RingService, PowerManager.PARTIAL_WAKE_LOCK)
                prepare()
                start()
            }
        }.getOrNull()
    }

    /** 끄지 않고 지나간 타이머: 처음 상태로 돌리고 '끝남' 알림 남기기 */
    @SuppressLint("MissingPermission")
    private fun missed(id: String) {
        val store = MemoStore.get(this)
        val t = store.findTimer(id) ?: return
        store.upsertTimer(t.copy(state = TimerState.IDLE, endAt = 0, remainingMs = t.durationMs))
        if (!GeofenceManager.hasNotificationPermission(this)) return
        val n = NotificationCompat.Builder(this, TimerManager.CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("⏰ ${t.displayName()} 끝났어요")
            .setContentText("타이머가 끝났어요")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(TimerManager.notificationId(id), n)
    }

    private fun finish() {
        handler.removeCallbacks(autoStop)
        player?.runCatching { stop(); release() }
        player = null
        Haptics.cancel(this)
        running = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        player?.runCatching { release() }
        Haptics.cancel(this)
        running = false
        super.onDestroy()
    }
}
