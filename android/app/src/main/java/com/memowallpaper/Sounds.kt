package com.memowallpaper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

/** 일반 알림과 구별되는 앱 전용 소리 */
enum class SoundChoice(val label: String, val rawName: String?) {
    CHIME("맑은 차임", "snd_chime"),
    MARIMBA("통통 마림바", "snd_marimba"),
    BELL("종소리", "snd_bell"),
    BEEP("삐삐 경고음", "snd_beep"),
    SYSTEM("휴대폰 기본 소리", null),
    ;

    fun uri(context: Context): Uri = if (rawName == null) {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    } else {
        Uri.parse("android.resource://${context.packageName}/raw/$rawName")
    }

    fun alarmUri(context: Context): Uri = if (rawName == null) {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: uri(context)
    } else {
        uri(context)
    }
}

object Sounds {
    /** 일반 알림과 다른 진동: 길게-짧게-길게 */
    val VIBRATION = longArrayOf(0, 450, 150, 150, 150, 700)

    private const val PLACE_PREFIX = "place_"

    /**
     * 장소 알림 채널. 안드로이드는 채널을 만든 뒤에는 소리를 바꿀 수 없어서,
     * 소리·무음모드 설정마다 다른 채널을 만들고 예전 채널은 지워요.
     */
    fun placeChannelId(context: Context): String {
        val store = MemoStore.get(context)
        val sound = store.placeSound
        val loud = store.soundInSilentMode
        val id = "${PLACE_PREFIX}${sound.name.lowercase()}_${if (loud) "alarm" else "normal"}"
        val nm = context.getSystemService(NotificationManager::class.java) ?: return id
        if (nm.getNotificationChannel(id) == null) {
            nm.notificationChannels
                .filter { (it.id.startsWith(PLACE_PREFIX) || it.id == "place_alerts") && it.id != id }
                .forEach { nm.deleteNotificationChannel(it.id) }
            val attrs = AudioAttributes.Builder()
                .setUsage(if (loud) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            nm.createNotificationChannel(
                NotificationChannel(id, "장소 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "정해 둔 장소에 도착하거나 떠날 때 알려줘요"
                    setSound(sound.uri(context), attrs)
                    enableVibration(true)
                    vibrationPattern = VIBRATION
                    if (loud) setBypassDnd(true)
                },
            )
        }
        return id
    }

    private var preview: MediaPlayer? = null

    /** 미리 듣기(한 번) */
    fun play(context: Context, sound: SoundChoice, alarm: Boolean = false) {
        stopPreview()
        preview = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, if (alarm) sound.alarmUri(context) else sound.uri(context))
                setOnCompletionListener { it.release(); if (preview === it) preview = null }
                prepare()
                start()
            }
        }.getOrNull()
    }

    fun stopPreview() {
        preview?.runCatching { stop(); release() }
        preview = null
    }
}
