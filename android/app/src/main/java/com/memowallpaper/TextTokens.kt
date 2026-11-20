package com.memowallpaper

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 메모 안의 자동 문구. 배경화면과 위젯은 매일 자정 직후 자동으로 다시 만들어져요.
 * - `{오늘}`                → 11월 20일 (금)
 * - `{D-day:2026-12-25}`    → D-35 / D-Day / D+3   (남은 날 카운트다운)
 * - `{며칠째:2026-01-01}`   → 324일째              (시작일을 1일째로 세는 카운트업)
 */
object TextTokens {
    const val TODAY = "{오늘}"
    private val REGEX = Regex("""\{(오늘|D-day:(\d{4}-\d{2}-\d{2})|며칠째:(\d{4}-\d{2}-\d{2}))\}""")
    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

    fun dday(date: LocalDate): String = "{D-day:$date}"

    fun countUp(start: LocalDate): String = "{며칠째:$start}"

    fun hasTokens(text: String): Boolean = REGEX.containsMatchIn(text)

    fun ddayLabel(target: LocalDate, today: LocalDate = LocalDate.now()): String {
        val diff = ChronoUnit.DAYS.between(today, target)
        return when {
            diff > 0 -> "D-$diff"
            diff == 0L -> "D-Day"
            else -> "D+${-diff}"
        }
    }

    fun countUpLabel(start: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = ChronoUnit.DAYS.between(start, today) + 1
        return if (days >= 1) "${days}일째" else "${1 - days}일 뒤 시작"
    }

    fun resolve(text: String, today: LocalDate = LocalDate.now()): String =
        REGEX.replace(text) { m ->
            val ddayIso = m.groupValues[2]
            val countIso = m.groupValues[3]
            try {
                when {
                    ddayIso.isNotEmpty() -> ddayLabel(LocalDate.parse(ddayIso), today)
                    countIso.isNotEmpty() -> countUpLabel(LocalDate.parse(countIso), today)
                    else -> today.format(DATE_FMT)
                }
            } catch (_: DateTimeParseException) {
                m.value
            }
        }
}
