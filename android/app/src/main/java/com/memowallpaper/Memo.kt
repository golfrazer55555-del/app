package com.memowallpaper

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
) {
    /** 목록·타일·바로가기에 보여줄 이름 */
    fun displayName(): String =
        title.trim().ifEmpty { text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "빈 메모" }

    fun isBlank(): Boolean = title.isBlank() && text.isBlank()

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

    companion object {
        const val DEFAULT_BG = 0xFF1E3A8A.toInt()

        fun fromJson(o: JSONObject) = Memo(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            title = o.optString("title"),
            text = o.optString("text"),
            prefix = enumOr(o.optString("prefix"), Prefix.CHECK),
            bgColor = o.optInt("bg", DEFAULT_BG),
            sizePct = o.optDouble("size", 7.0).toFloat().coerceIn(3f, 14f),
            bold = o.optBoolean("bold", true),
            align = enumOr(o.optString("align"), Align.LEFT),
            posPct = o.optDouble("pos", 55.0).toFloat().coerceIn(5f, 95f),
        )
    }
}

inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

/** 배경색 프리셋 */
val PRESET_COLORS: List<Int> = listOf(
    0xFF000000, 0xFF0F172A, 0xFF1E3A8A, 0xFF1D4ED8,
    0xFF14532D, 0xFF0F766E, 0xFF7F1D1D, 0xFF4C1D95,
    0xFF374151, 0xFFF5F5F4, 0xFFFDE68A, 0xFFFED7AA,
    0xFFFECDD3, 0xFFE9D5FF, 0xFFBFDBFE, 0xFFBBF7D0,
).map { it.toInt() }
