package com.memowallpaper

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Prefix(val label: String) { NONE("없음"), DOT("점"), CHECK("체크"), NUM("번호") }

enum class Align(val label: String) { LEFT("왼쪽"), CENTER("가운데"), RIGHT("오른쪽") }

/** 어디에 적용할지 */
enum class Target(val label: String, val shortLabel: String) {
    LOCK("잠금화면", "잠금화면"),
    HOME("홈화면", "홈화면"),
    BOTH("잠금화면과 홈화면", "둘 다"),
}

data class Memo(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val text: String = "",
    val prefix: Prefix = Prefix.CHECK,
    val bgColor: Int = DEFAULT_BG,
    val sizePct: Float = 7f,
    val bold: Boolean = true,
    val align: Align = Align.LEFT,
    val posPct: Float = 55f,
    /** null이면 배경에 맞춰 자동 */
    val textColor: Int? = null,
    /** 글자 뒤 반투명 상자 */
    val textBox: Boolean = false,
    /** 배경 사진 파일 이름(앱 내부 저장소) */
    val image: String? = null,
    val imgScale: Float = 1f,
    val imgX: Float = 0f,
    val imgY: Float = 0f,
    val imgDim: Float = 0.25f,
    val imgGray: Boolean = false,
    /** 손그림 파일 이름(투명 PNG) */
    val drawing: String? = null,
) {
    /** 목록·타일·바로가기에 보여줄 이름 */
    fun displayName(): String =
        title.trim().ifEmpty {
            TextTokens.resolve(text).lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                ?: if (image != null || drawing != null) "그림 메모" else "빈 메모"
        }

    fun isBlank(): Boolean = title.isBlank() && text.isBlank() && image == null && drawing == null

    fun hasTokens(): Boolean = TextTokens.hasTokens(text)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("text", text)
        .put("prefix", prefix.name)
        .put("bg", bgColor)
        .put("size", sizePct.toDouble())
        .put("bold", bold)
        .put("align", align.name)
        .put("pos", posPct.toDouble())
        .put("textColor", textColor ?: JSONObject.NULL)
        .put("textBox", textBox)
        .put("image", image ?: JSONObject.NULL)
        .put("imgScale", imgScale.toDouble())
        .put("imgX", imgX.toDouble())
        .put("imgY", imgY.toDouble())
        .put("imgDim", imgDim.toDouble())
        .put("imgGray", imgGray)
        .put("drawing", drawing ?: JSONObject.NULL)

    companion object {
        const val DEFAULT_BG = 0xFF1E3A8A.toInt()
        const val MIN_SIZE = 3f
        const val MAX_SIZE = 30f

        fun fromJson(o: JSONObject) = Memo(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            title = o.optString("title"),
            text = o.optString("text"),
            prefix = enumOr(o.optString("prefix"), Prefix.CHECK),
            bgColor = o.optInt("bg", DEFAULT_BG),
            sizePct = o.optDouble("size", 7.0).toFloat().coerceIn(MIN_SIZE, MAX_SIZE),
            bold = o.optBoolean("bold", true),
            align = enumOr(o.optString("align"), Align.LEFT),
            posPct = o.optDouble("pos", 55.0).toFloat().coerceIn(5f, 95f),
            textColor = if (o.isNull("textColor")) null else o.optInt("textColor"),
            textBox = o.optBoolean("textBox", false),
            image = o.optStringOrNull("image"),
            imgScale = o.optDouble("imgScale", 1.0).toFloat().coerceIn(1f, 6f),
            imgX = o.optDouble("imgX", 0.0).toFloat(),
            imgY = o.optDouble("imgY", 0.0).toFloat(),
            imgDim = o.optDouble("imgDim", 0.25).toFloat().coerceIn(0f, 0.9f),
            imgGray = o.optBoolean("imgGray", false),
            drawing = o.optStringOrNull("drawing"),
        )
    }
}

/** 정해진 시각에 특정 메모로 자동 변경 */
data class Schedule(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int = 8,
    val minute: Int = 0,
    val memoId: String,
    /** 1=월 … 7=일 (java.time.DayOfWeek 값) */
    val days: Set<Int> = (1..7).toSet(),
    val enabled: Boolean = true,
) {
    fun timeLabel(): String {
        val ampm = if (hour < 12) "오전" else "오후"
        val h = (hour % 12).let { if (it == 0) 12 else it }
        return "$ampm $h:${minute.toString().padStart(2, '0')}"
    }

    fun daysLabel(): String = when {
        days.size == 7 -> "매일"
        days == setOf(1, 2, 3, 4, 5) -> "평일"
        days == setOf(6, 7) -> "주말"
        days.isEmpty() -> "요일 없음"
        else -> days.sorted().joinToString(" ") { DAY_NAMES[it - 1] }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("hour", hour)
        .put("minute", minute)
        .put("memoId", memoId)
        .put("days", JSONArray(days.sorted()))
        .put("enabled", enabled)

    companion object {
        val DAY_NAMES = listOf("월", "화", "수", "목", "금", "토", "일")

        fun fromJson(o: JSONObject): Schedule {
            val arr = o.optJSONArray("days")
            val days = if (arr == null) (1..7).toSet() else List(arr.length()) { arr.getInt(it) }.filter { it in 1..7 }.toSet()
            return Schedule(
                id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                hour = o.optInt("hour", 8).coerceIn(0, 23),
                minute = o.optInt("minute", 0).coerceIn(0, 59),
                memoId = o.optString("memoId"),
                days = days,
                enabled = o.optBoolean("enabled", true),
            )
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).ifEmpty { null }

inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

/** 배경색 프리셋 */
val PRESET_COLORS: List<Int> = listOf(
    0xFF000000, 0xFF0F172A, 0xFF1E3A8A, 0xFF1D4ED8,
    0xFF14532D, 0xFF0F766E, 0xFF7F1D1D, 0xFF4C1D95,
    0xFF374151, 0xFFF5F5F4, 0xFFFDE68A, 0xFFFED7AA,
    0xFFFECDD3, 0xFFE9D5FF, 0xFFBFDBFE, 0xFFBBF7D0,
).map { it.toInt() }

/** 글자색 선택지(null = 자동) */
val TEXT_COLORS: List<Pair<String, Int?>> = listOf(
    "자동" to null,
    "흰색" to 0xFFFFFFFF.toInt(),
    "검정" to 0xFF111111.toInt(),
    "노랑" to 0xFFFDE047.toInt(),
    "빨강" to 0xFFEF4444.toInt(),
    "하늘" to 0xFF7DD3FC.toInt(),
)

/** 그림 펜 색 */
val PEN_COLORS: List<Int> = listOf(
    0xFFFFFFFF, 0xFF111111, 0xFFEF4444, 0xFFF97316, 0xFFFDE047,
    0xFF22C55E, 0xFF3B82F6, 0xFFA855F7, 0xFFEC4899,
).map { it.toInt() }
