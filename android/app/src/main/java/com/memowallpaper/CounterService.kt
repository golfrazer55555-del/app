package com.memowallpaper

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.VolumeProviderCompat

/** 진동 피드백(화면을 안 봐도 알 수 있게) */
object Haptics {
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    @SuppressLint("MissingPermission")
    fun play(context: Context, timings: LongArray, repeat: Int = -1) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(timings, repeat)
        // 알람 용도로 지정해야 화면이 꺼져 있어도 확실히 울려요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    fun cancel(context: Context) {
        vibrator(context)?.cancel()
    }

    /** +1: 짧게 한 번 */
    val PLUS = longArrayOf(0, 60)
    /** −1: 짧게 두 번 */
    val MINUS = longArrayOf(0, 50, 90, 50)
    /** 10번째마다: 조금 길게 */
    val TENTH = longArrayOf(0, 280)
    /** 목표 도달: 길게 두 번 */
    val GOAL = longArrayOf(0, 600, 200, 600)
}

/** 버튼 한 번 처리(서비스·주머니 모드·앱 화면 공용) */
object CounterLogic {
    /** 앱 화면에서 볼륨 버튼을 받을 카운터(카운터 화면이 열려 있고 '볼륨 버튼으로 세기'가 켜졌을 때) */
    @Volatile
    var foregroundTarget: String? = null

    fun press(context: Context, counterId: String, up: Boolean): Counter? {
        val store = MemoStore.get(context)
        val c = store.findCounter(counterId) ?: return null
        val delta = if (up || c.downAction == DownAction.PLUS) 1 else -1
        val updated = store.addToCounter(counterId, delta) ?: return null
        when {
            delta > 0 && updated.goal > 0 && updated.count == updated.goal -> {
                Haptics.play(context, Haptics.GOAL)
                Sounds.play(context, store.timerSound)
            }
            delta > 0 && updated.buzzEvery10 && updated.count % 10 == 0 -> Haptics.play(context, Haptics.TENTH)
            delta > 0 -> Haptics.play(context, Haptics.PLUS)
            else -> Haptics.play(context, Haptics.MINUS)
        }
        CounterService.refresh(context)
        return updated
    }
}

/**
 * 볼륨 버튼으로 횟수 세기.
 * 소리 없는 미디어 재생 세션을 켜 두면 화면이 꺼져 있어도 볼륨 버튼 입력이 이 앱으로 와요.
 * (이어폰 버튼도 +1)
 */
class CounterService : Service() {

    companion object {
        private const val ACTION_START = "counter.START"
        private const val ACTION_STOP = "counter.STOP"
        private const val ACTION_PLUS = "counter.PLUS"
        private const val ACTION_DOWN = "counter.DOWN"
        private const val ACTION_REFRESH = "counter.REFRESH"
        private const val EXTRA_ID = "counter_id"
        private const val CHANNEL = "counter_running"
        private const val NOTIFICATION_ID = 8100

        fun start(context: Context, counterId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CounterService::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, counterId),
            )
        }

        fun stop(context: Context) {
            if (!running) return
            context.startService(Intent(context, CounterService::class.java).setAction(ACTION_STOP))
        }

        /** 알림의 숫자 새로 고침(서비스가 돌고 있을 때만) */
        fun refresh(context: Context) {
            if (!running) return
            runCatching { context.startService(Intent(context, CounterService::class.java).setAction(ACTION_REFRESH)) }
        }

        @Volatile
        var running = false
            private set
    }

    private val store by lazy { MemoStore.get(this) }
    private var counterId: String? = null
    private var session: MediaSessionCompat? = null
    private var silence: AudioTrack? = null

    /** 볼륨 버튼 → 횟수. 테스트에서도 직접 부를 수 있게 공개 */
    val volumeProvider = object : VolumeProviderCompat(VOLUME_CONTROL_RELATIVE, 100, 50) {
        override fun onAdjustVolume(direction: Int) {
            when {
                direction > 0 -> press(up = true)
                direction < 0 -> press(up = false)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_ID)
                counterId = id
                running = true
                // startForegroundService로 시작했으면 먼저 포그라운드로 올려야 해요
                goForeground()
                if (id == null || store.findCounter(id) == null) {
                    shutdown()
                    return START_NOT_STICKY
                }
                store.setActiveCounter(id)
                setUpSession()
            }
            ACTION_PLUS -> press(up = true)
            ACTION_DOWN -> press(up = false)
            ACTION_STOP -> shutdown()
            ACTION_REFRESH -> updateNotification()
            else -> if (counterId == null) shutdown()
        }
        return START_NOT_STICKY
    }

    /** 버튼 한 번 처리 */
    fun press(up: Boolean) {
        val id = counterId ?: return
        CounterLogic.press(this, id, up)
    }

    private fun setUpSession() {
        if (session == null) {
            session = MediaSessionCompat(this, "kkamppak-counter").apply {
                setPlaybackToRemote(volumeProvider)
                setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
                        .build(),
                )
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                        @Suppress("DEPRECATION")
                        val key = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        if (key?.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) {
                            when (key.keyCode) {
                                KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
                                KeyEvent.KEYCODE_MEDIA_NEXT,
                                -> { press(up = true); return true }
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { press(up = false); return true }
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonEvent)
                    }
                })
                isActive = true
            }
        }
        startSilence()
        updateNotification()
    }

    /** 소리 없는 오디오를 계속 재생해서 이 앱이 '재생 중인 미디어'가 되게 함 */
    private fun startSilence() {
        if (silence != null) return
        silence = runCatching {
            val rate = 8000
            val frames = rate // 1초
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(frames * 2)
                .build()
                .apply {
                    write(ShortArray(frames), 0, frames)
                    setLoopPoints(0, frames, -1)
                    play()
                }
        }.getOrNull()
    }

    private fun goForeground() {
        val n = buildNotification()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }

    private fun action(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, CounterService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm?.getNotificationChannel(CHANNEL) == null) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, "횟수 세기", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "볼륨 버튼으로 세는 동안 표시돼요"
                    setShowBadge(false)
                },
            )
        }
        val c = store.findCounter(counterId)
        val downLabel = if (c?.downAction == DownAction.PLUS) "+1" else "−1"
        val goal = if (c != null && c.goal > 0) " / ${c.goal}" else ""
        val open = PendingIntent.getActivity(
            this, 8101,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_COUNTER, counterId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2)
        session?.let { style.setMediaSession(it.sessionToken) }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("${c?.displayName() ?: "카운터"}: ${c?.count ?: 0}$goal")
            .setContentText("볼륨 ↑ +1 · 볼륨 ↓ $downLabel · 이어폰 버튼 +1")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, downLabel, action(ACTION_DOWN, 1))
            .addAction(0, "+1", action(ACTION_PLUS, 2))
            .addAction(0, "그만 세기", action(ACTION_STOP, 3))
            .setStyle(style)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        if (counterId == null) return
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun shutdown() {
        silence?.runCatching { stop(); release() }
        silence = null
        session?.runCatching { isActive = false; release() }
        session = null
        counterId = null
        running = false
        store.setActiveCounter(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        silence?.runCatching { stop(); release() }
        session?.runCatching { release() }
        running = false
        store.setActiveCounter(null)
        super.onDestroy()
    }
}
