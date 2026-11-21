package com.memowallpaper

import org.json.JSONObject
import java.util.UUID

/** 아래(볼륨 다운) 버튼이 할 일 */
enum class DownAction(val label: String) { MINUS("−1 (잘못 센 것 빼기)"), PLUS("+1 (둘 다 더하기)") }

/** 횟수 세기 */
data class Counter(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val count: Int = 0,
    /** 목표 횟수(도달하면 길게 진동 + 소리). 0이면 없음 */
    val goal: Int = 0,
    val downAction: DownAction = DownAction.MINUS,
    /** 10번마다 조금 길게 진동해서 주머니 속에서도 몇 번째인지 감 잡기 */
    val buzzEvery10: Boolean = true,
) {
    fun displayName(): String = name.trim().ifEmpty { "카운터" }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("count", count)
        .put("goal", goal)
        .put("downAction", downAction.name)
        .put("buzzEvery10", buzzEvery10)

    companion object {
        fun fromJson(o: JSONObject) = Counter(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = o.optString("name"),
            count = o.optInt("count", 0),
            goal = o.optInt("goal", 0).coerceAtLeast(0),
            downAction = enumOr(o.optString("downAction"), DownAction.MINUS),
            buzzEvery10 = o.optBoolean("buzzEvery10", true),
        )
    }
}

enum class TimerState { IDLE, RUNNING, PAUSED, RINGING }

/** 타이머(한 번 만든 타이머는 다시 쓸 수 있게 남겨 둬요) */
data class TimerItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val durationMs: Long = 5 * 60_000L,
    val state: TimerState = TimerState.IDLE,
    /** RUNNING일 때 끝나는 시각(epoch ms) */
    val endAt: Long = 0L,
    /** PAUSED일 때 남은 시간 */
    val remainingMs: Long = durationMs,
) {
    fun displayName(): String = label.trim().ifEmpty { formatDuration(durationMs) + " 타이머" }

    fun remaining(now: Long = System.currentTimeMillis()): Long = when (state) {
        TimerState.RUNNING -> (endAt - now).coerceAtLeast(0)
        TimerState.PAUSED -> remainingMs
        TimerState.IDLE -> durationMs
        TimerState.RINGING -> 0
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("durationMs", durationMs)
        .put("state", state.name)
        .put("endAt", endAt)
        .put("remainingMs", remainingMs)

    companion object {
        fun fromJson(o: JSONObject): TimerItem {
            val d = o.optLong("durationMs", 5 * 60_000L).coerceAtLeast(1000L)
            return TimerItem(
                id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                label = o.optString("label"),
                durationMs = d,
                state = enumOr(o.optString("state"), TimerState.IDLE),
                endAt = o.optLong("endAt", 0L),
                remainingMs = o.optLong("remainingMs", d),
            )
        }
    }
}

/** 1:05:09 / 5:00 형식 */
fun formatDuration(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
