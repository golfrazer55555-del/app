package com.memowallpaper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 백업 파일(zip) 만들기·가져오기. 다른 폰으로 옮기거나 앱을 다시 깔 때 써요.
 * 안에 든 것: data.json(메모·일정·장소·카운터·타이머·설정) + media/(사진·그림)
 */
object BackupManager {
    private const val FORMAT = 1
    private const val DATA = "data.json"
    private const val MEDIA = "media/"

    data class Summary(val memos: Int, val schedules: Int, val places: Int, val counters: Int, val timers: Int) {
        override fun toString() =
            "메모 ${memos}개 · 일정 ${schedules}개 · 장소 ${places}개 · 카운터 ${counters}개 · 타이머 ${timers}개"
    }

    fun export(context: Context, out: OutputStream): Summary {
        val store = MemoStore.get(context)
        val memos = store.memos.value
        val data = JSONObject()
            .put("format", FORMAT)
            .put("app", "깜빡 방지")
            .put("createdAt", System.currentTimeMillis())
            .put("memos", JSONArray(memos.map { it.toJson() }))
            .put("schedules", JSONArray(store.schedules.value.map { it.toJson() }))
            .put("places", JSONArray(store.places.value.map { it.toJson() }))
            .put("counters", JSONArray(store.counters.value.map { it.toJson() }))
            // 돌던 타이머는 처음 상태로 저장
            .put(
                "timers",
                JSONArray(
                    store.timers.value.map {
                        it.copy(state = TimerState.IDLE, endAt = 0, remainingMs = it.durationMs).toJson()
                    },
                ),
            )
            .put(
                "settings",
                JSONObject()
                    .put("target", store.target.value.name)
                    .put("placeSound", store.placeSound.name)
                    .put("timerSound", store.timerSound.name)
                    .put("soundInSilent", store.soundInSilentMode),
            )

        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(data.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            store.usedMediaFiles().forEach { name ->
                val f = ImageStore.file(context, name)
                if (!f.exists()) return@forEach
                zip.putNextEntry(ZipEntry(MEDIA + name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return Summary(memos.size, store.schedules.value.size, store.places.value.size, store.counters.value.size, store.timers.value.size)
    }

    /** 백업 파일의 내용으로 지금 데이터를 바꿔요. 잘못된 파일이면 예외 */
    fun import(context: Context, input: InputStream): Summary {
        var data: JSONObject? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.name == DATA -> data = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    e.name.startsWith(MEDIA) && !e.isDirectory -> {
                        val name = e.name.removePrefix(MEDIA)
                        // 경로 조작 방지: 파일 이름만 허용
                        if (name.isNotEmpty() && !name.contains('/') && !name.contains("..")) {
                            ImageStore.file(context, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val d = data ?: error("깜빡 방지 백업 파일이 아니에요")
        if (d.optInt("format", 0) !in 1..FORMAT) error("이 앱 버전에서 읽을 수 없는 백업이에요")

        fun <T> list(key: String, parse: (JSONObject) -> T): List<T> {
            val arr = d.optJSONArray(key) ?: return emptyList()
            return List(arr.length()) { parse(arr.getJSONObject(it)) }
        }
        val memos = list("memos") { Memo.fromJson(it) }
        val schedules = list("schedules") { Schedule.fromJson(it) }
        val places = list("places") { Place.fromJson(it) }
        val counters = list("counters") { Counter.fromJson(it) }
        val timers = list("timers") { TimerItem.fromJson(it) }

        val store = MemoStore.get(context)
        store.replaceAll(memos, schedules, places, counters, timers)
        d.optJSONObject("settings")?.let { s ->
            store.setTarget(enumOr(s.optString("target"), Target.LOCK))
            store.placeSound = enumOr(s.optString("placeSound"), SoundChoice.CHIME)
            store.timerSound = enumOr(s.optString("timerSound"), SoundChoice.BELL)
            store.soundInSilentMode = s.optBoolean("soundInSilent", false)
        }
        Shortcuts.update(context, memos)
        return Summary(memos.size, schedules.size, places.size, counters.size, timers.size)
    }
}
