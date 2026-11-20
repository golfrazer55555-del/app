package com.memowallpaper

import org.json.JSONObject
import java.util.UUID

/** 언제 알릴지 */
enum class Trigger(val label: String) {
    ENTER("도착할 때"),
    EXIT("떠날 때"),
    BOTH("둘 다"),
}

/** 장소 알림: 이 장소 근처에 도착하거나 떠나면 문구를 알려줘요 */
data class Place(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val lat: Double = DEFAULT_LAT,
    val lon: Double = DEFAULT_LON,
    /** 반경(m) */
    val radius: Int = 200,
    val trigger: Trigger = Trigger.ENTER,
    /** 알림 문구({오늘}, D-day 등 자동 문구도 쓸 수 있음) */
    val message: String = "",
    /** 알림과 함께 잠금화면도 바꿀지 */
    val changeWallpaper: Boolean = false,
    /** 잠금화면에 쓸 메모. null이면 알림 문구 그대로 */
    val memoId: String? = null,
    val enabled: Boolean = true,
) {
    fun displayName(): String = name.trim().ifEmpty { "이름 없는 장소" }

    fun summary(): String = "${trigger.label} · 반경 ${radius}m" + if (changeWallpaper) " · 잠금화면 변경" else ""

    /** 잠금화면을 바꿀 때 쓸 메모(연결된 메모가 없으면 알림 문구로 즉석에서 만듦) */
    fun wallpaperMemo(store: MemoStore): Memo? {
        store.find(memoId)?.let { return it }
        if (message.isBlank()) return null
        val multi = message.trim().lines().size > 1
        return Memo(
            id = "place:$id",
            title = displayName(),
            text = message,
            prefix = if (multi) Prefix.CHECK else Prefix.NONE,
            align = if (multi) Align.LEFT else Align.CENTER,
            sizePct = if (multi) 7f else 9f,
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("lat", lat)
        .put("lon", lon)
        .put("radius", radius)
        .put("trigger", trigger.name)
        .put("message", message)
        .put("changeWallpaper", changeWallpaper)
        .put("memoId", memoId ?: JSONObject.NULL)
        .put("enabled", enabled)

    companion object {
        /** 위치를 모를 때 지도 기본 위치(서울시청) */
        const val DEFAULT_LAT = 37.5665
        const val DEFAULT_LON = 126.9780
        const val MIN_RADIUS = 100
        const val MAX_RADIUS = 1000

        fun fromJson(o: JSONObject) = Place(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = o.optString("name"),
            lat = o.optDouble("lat", DEFAULT_LAT),
            lon = o.optDouble("lon", DEFAULT_LON),
            radius = o.optInt("radius", 200).coerceIn(MIN_RADIUS, MAX_RADIUS),
            trigger = enumOr(o.optString("trigger"), Trigger.ENTER),
            message = o.optString("message"),
            changeWallpaper = o.optBoolean("changeWallpaper", false),
            memoId = if (o.isNull("memoId")) null else o.optString("memoId").ifEmpty { null },
            enabled = o.optBoolean("enabled", true),
        )
    }
}
