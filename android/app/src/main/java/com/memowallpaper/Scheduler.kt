package com.memowallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 시간대별 자동 변경 + 자정 갱신(날짜·D-day 문구).
 * 다음에 할 일 하나만 알람으로 걸어 두고, 알람이 울리면 [runDue]가 처리한 뒤 다음 알람을 겁니다.
 */
object Scheduler {
    private const val REQUEST_CODE = 4100

    fun latestOccurrence(s: Schedule, now: LocalDateTime): LocalDateTime? {
        for (d in 0L..7L) {
            val date = now.toLocalDate().minusDays(d)
            if (date.dayOfWeek.value !in s.days) continue
            val t = date.atTime(s.hour, s.minute)
            if (!t.isAfter(now)) return t
        }
        return null
    }

    fun nextOccurrence(s: Schedule, now: LocalDateTime): LocalDateTime? {
        for (d in 0L..7L) {
            val date = now.toLocalDate().plusDays(d)
            if (date.dayOfWeek.value !in s.days) continue
            val t = date.atTime(s.hour, s.minute)
            if (t.isAfter(now)) return t
        }
        return null
    }

    private fun activeSchedules(store: MemoStore): List<Schedule> =
        store.schedules.value.filter { it.enabled && it.days.isNotEmpty() && store.find(it.memoId) != null }

    /** 다음 자동 변경(시각, 일정) */
    fun nextChange(context: Context, now: LocalDateTime = LocalDateTime.now()): Pair<LocalDateTime, Schedule>? {
        val store = MemoStore.get(context)
        return activeSchedules(store)
            .mapNotNull { s -> nextOccurrence(s, now)?.let { it to s } }
            .minByOrNull { it.first }
    }

    /** 지금 해야 할 일을 처리: 지난 일정의 메모 적용, 또는 날짜가 바뀌었으면 현재 메모 다시 만들기 */
    suspend fun runDue(context: Context) {
        val app = context.applicationContext
        val store = MemoStore.get(app)
        val now = LocalDateTime.now()
        val last = LocalDateTime.ofInstant(Instant.ofEpochMilli(store.lastAppliedAt), ZoneId.systemDefault())

        val due = activeSchedules(store)
            .mapNotNull { s -> latestOccurrence(s, now)?.let { it to s } }
            .maxByOrNull { it.first }

        val target = store.target.value
        if (due != null && due.first.isAfter(last)) {
            store.find(due.second.memoId)?.let { WallpaperApplier.apply(app, it, target) }
        } else {
            val applied = store.applied()
            if (applied != null && applied.hasTokens() && last.toLocalDate() != now.toLocalDate()) {
                WallpaperApplier.apply(app, applied, target)
            }
        }
        WidgetUpdater.updateAll(app)
        reschedule(app)
    }

    /** 다음 알람 하나를 건다(없으면 취소) */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            app, REQUEST_CODE, Intent(app, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val store = MemoStore.get(app)
        val now = LocalDateTime.now()

        val candidates = mutableListOf<LocalDateTime>()
        nextChange(app, now)?.let { candidates += it.first }
        // 날짜·D-day 문구가 있으면 자정 직후에 다시 만들기
        if (store.applied()?.hasTokens() == true) {
            candidates += LocalDate.now().plusDays(1).atTime(0, 0, 30)
        }
        val at = candidates.minOrNull()
        if (at == null) {
            am.cancel(pi)
            return
        }
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching {
            if (canExact(app)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        }
    }

    /** 정확한 시각 알람을 쓸 수 있는지(Android 12+는 '알람 및 리마인더' 권한 필요) */
    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }
}

/** 예약 알람 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        AppScope.launch {
            try {
                Scheduler.runDue(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** 재부팅·시간 변경·앱 업데이트 후 알람과 장소 감지 다시 걸기(놓친 변경도 처리) */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        AppScope.launch {
            try {
                GeofenceManager.sync(context.applicationContext)
                Scheduler.runDue(context)
            } finally {
                pending.finish()
            }
        }
    }
}
