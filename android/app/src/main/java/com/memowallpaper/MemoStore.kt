package com.memowallpaper

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** 메모·자동 변경 일정·장소 알림·설정을 SharedPreferences에 저장합니다. 앱 전체에서 하나만 씁니다. */
class MemoStore private constructor(private val appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("memo_store", Context.MODE_PRIVATE)

    private val _memos = MutableStateFlow(loadMemos())
    val memos: StateFlow<List<Memo>> = _memos.asStateFlow()

    private val _schedules = MutableStateFlow(loadSchedules())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _places = MutableStateFlow(loadPlaces())
    val places: StateFlow<List<Place>> = _places.asStateFlow()

    private val _counters = MutableStateFlow(loadList(KEY_COUNTERS) { Counter.fromJson(it) })
    val counters: StateFlow<List<Counter>> = _counters.asStateFlow()

    /** 볼륨 버튼으로 세는 중인 카운터 */
    private val _activeCounterId = MutableStateFlow<String?>(null)
    val activeCounterId: StateFlow<String?> = _activeCounterId.asStateFlow()

    private val _timers = MutableStateFlow(loadList(KEY_TIMERS) { TimerItem.fromJson(it) })
    val timers: StateFlow<List<TimerItem>> = _timers.asStateFlow()

    private val _appliedId = MutableStateFlow(prefs.getString(KEY_APPLIED, null))
    val appliedId: StateFlow<String?> = _appliedId.asStateFlow()

    private val _target = MutableStateFlow(enumOr(prefs.getString(KEY_TARGET, null), Target.LOCK))
    val target: StateFlow<Target> = _target.asStateFlow()

    private val _tileTipDismissed = MutableStateFlow(prefs.getBoolean(KEY_TILE_TIP, false))
    val tileTipDismissed: StateFlow<Boolean> = _tileTipDismissed.asStateFlow()

    /** 마지막으로 배경화면을 적용한 시각(epoch ms) */
    val lastAppliedAt: Long get() = prefs.getLong(KEY_APPLIED_AT, 0L)

    fun find(id: String?): Memo? = id?.let { i -> _memos.value.firstOrNull { it.id == i } }

    fun applied(): Memo? = find(_appliedId.value)

    fun upsert(memo: Memo) {
        val list = _memos.value.toMutableList()
        val idx = list.indexOfFirst { it.id == memo.id }
        if (idx >= 0) list[idx] = memo else list.add(memo)
        setMemos(list)
    }

    fun delete(id: String) {
        setMemos(_memos.value.filterNot { it.id == id })
        Shortcuts.disable(appContext, listOf(id))
        if (_schedules.value.any { it.memoId == id }) setSchedules(_schedules.value.filterNot { it.memoId == id })
        // 이 메모를 쓰던 장소는 '알림 문구 그대로'로
        if (_places.value.any { it.memoId == id }) setPlaces(_places.value.map { if (it.memoId == id) it.copy(memoId = null) else it })
        if (_appliedId.value == id) {
            _appliedId.value = null
            prefs.edit().remove(KEY_APPLIED).apply()
            onChanged()
        }
    }

    /** 배경화면 적용이 끝났을 때 호출. 목록에 없는 즉석 메모(장소 알림 문구 등)는 null */
    fun markApplied(id: String?) {
        _appliedId.value = id
        prefs.edit().putString(KEY_APPLIED, id).putLong(KEY_APPLIED_AT, System.currentTimeMillis()).apply()
        onChanged()
    }

    fun setTarget(t: Target) {
        _target.value = t
        prefs.edit().putString(KEY_TARGET, t.name).apply()
    }

    fun dismissTileTip() {
        _tileTipDismissed.value = true
        prefs.edit().putBoolean(KEY_TILE_TIP, true).apply()
    }

    fun upsertSchedule(s: Schedule) {
        val list = _schedules.value.toMutableList()
        val idx = list.indexOfFirst { it.id == s.id }
        if (idx >= 0) list[idx] = s else list.add(s)
        setSchedules(list.sortedWith(compareBy({ it.hour }, { it.minute })))
    }

    fun deleteSchedule(id: String) = setSchedules(_schedules.value.filterNot { it.id == id })

    fun findPlace(id: String?): Place? = id?.let { i -> _places.value.firstOrNull { it.id == i } }

    fun upsertPlace(p: Place) {
        val list = _places.value.toMutableList()
        val idx = list.indexOfFirst { it.id == p.id }
        if (idx >= 0) list[idx] = p else list.add(p)
        setPlaces(list)
    }

    fun deletePlace(id: String) = setPlaces(_places.value.filterNot { it.id == id })

    /** 같은 장소 알림이 너무 자주 오지 않게(경계에서 들락날락할 때) 마지막 알림 시각 기록 */
    fun lastPlaceAlertAt(placeId: String, entering: Boolean): Long =
        prefs.getLong("place_alert_${placeId}_$entering", 0L)

    fun markPlaceAlert(placeId: String, entering: Boolean) {
        prefs.edit().putLong("place_alert_${placeId}_$entering", System.currentTimeMillis()).apply()
    }

    // ---------- 카운터 ----------

    fun findCounter(id: String?): Counter? = id?.let { i -> _counters.value.firstOrNull { it.id == i } }

    @Synchronized
    fun upsertCounter(c: Counter) {
        val list = _counters.value.toMutableList()
        val idx = list.indexOfFirst { it.id == c.id }
        if (idx >= 0) list[idx] = c else list.add(c)
        _counters.value = list
        saveList(KEY_COUNTERS, list) { it.toJson() }
    }

    fun deleteCounter(id: String) {
        _counters.value = _counters.value.filterNot { it.id == id }
        saveList(KEY_COUNTERS, _counters.value) { it.toJson() }
    }

    /** 횟수 바꾸기(0 아래로는 안 내려감). 바뀐 카운터를 돌려줘요 */
    @Synchronized
    fun addToCounter(id: String, delta: Int): Counter? {
        val c = findCounter(id) ?: return null
        val updated = c.copy(count = (c.count + delta).coerceAtLeast(0))
        upsertCounter(updated)
        return updated
    }

    fun setActiveCounter(id: String?) {
        _activeCounterId.value = id
    }

    // ---------- 타이머 ----------

    fun findTimer(id: String?): TimerItem? = id?.let { i -> _timers.value.firstOrNull { it.id == i } }

    @Synchronized
    fun upsertTimer(t: TimerItem) {
        val list = _timers.value.toMutableList()
        val idx = list.indexOfFirst { it.id == t.id }
        if (idx >= 0) list[idx] = t else list.add(0, t)
        _timers.value = list
        saveList(KEY_TIMERS, list) { it.toJson() }
    }

    fun deleteTimer(id: String) {
        _timers.value = _timers.value.filterNot { it.id == id }
        saveList(KEY_TIMERS, _timers.value) { it.toJson() }
    }

    // ---------- 소리 설정 ----------

    private val _soundVersion = MutableStateFlow(0)
    /** 소리 설정이 바뀔 때마다 올라가는 값(화면 갱신용) */
    val soundVersion: StateFlow<Int> = _soundVersion.asStateFlow()

    var placeSound: SoundChoice
        get() = enumOr(prefs.getString("place_sound", null), SoundChoice.CHIME)
        set(v) { prefs.edit().putString("place_sound", v.name).apply(); _soundVersion.value++ }

    var timerSound: SoundChoice
        get() = enumOr(prefs.getString("timer_sound", null), SoundChoice.BELL)
        set(v) { prefs.edit().putString("timer_sound", v.name).apply(); _soundVersion.value++ }

    /** 무음·진동 모드에서도 장소 알림 소리 내기(알람 볼륨 사용) */
    var soundInSilentMode: Boolean
        get() = prefs.getBoolean("sound_in_silent", false)
        set(v) { prefs.edit().putBoolean("sound_in_silent", v).apply(); _soundVersion.value++ }

    private fun <T> loadList(key: String, parse: (org.json.JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { parse(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun <T> saveList(key: String, list: List<T>, toJson: (T) -> org.json.JSONObject) {
        val arr = JSONArray().apply { list.forEach { put(toJson(it)) } }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    /** 위치 감지 등록 오류(위치 꺼짐 등). 없으면 null */
    var geofenceError: String?
        get() = prefs.getString(KEY_GEOFENCE_ERROR, null)
        set(value) = prefs.edit().putString(KEY_GEOFENCE_ERROR, value).apply()

    /** 사진·그림 파일 중 메모가 쓰고 있는 것 */
    fun usedMediaFiles(): Set<String> =
        _memos.value.flatMap { listOfNotNull(it.image, it.drawing) }.toSet()

    private fun setMemos(list: List<Memo>) {
        _memos.value = list
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_MEMOS, arr.toString()).apply()
        Shortcuts.update(appContext, list)
        onChanged()
    }

    private fun setSchedules(list: List<Schedule>) {
        _schedules.value = list
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_SCHEDULES, arr.toString()).apply()
        onChanged()
    }

    private fun setPlaces(list: List<Place>) {
        _places.value = list
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_PLACES, arr.toString()).apply()
        GeofenceManager.sync(appContext)
    }

    /** 위젯·알람을 최신 상태로 */
    private fun onChanged() {
        WidgetUpdater.updateAll(appContext)
        Scheduler.reschedule(appContext)
    }

    private fun loadMemos(): List<Memo> {
        val raw = prefs.getString(KEY_MEMOS, null) ?: return emptyList()
        val list = runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { Memo.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())

        // 1.0.0에서 넣었던 예시 메모(손대지 않은 것만) 제거
        if (!prefs.getBoolean(KEY_SAMPLES_REMOVED, false)) {
            val cleaned = list.filterNot { m -> OLD_SAMPLES.any { (t, x) -> m.title == t && m.text == x } }
            val arr = JSONArray().apply { cleaned.forEach { put(it.toJson()) } }
            prefs.edit().putString(KEY_MEMOS, arr.toString()).putBoolean(KEY_SAMPLES_REMOVED, true).apply()
            return cleaned
        }
        return list
    }

    private fun loadPlaces(): List<Place> {
        val raw = prefs.getString(KEY_PLACES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { Place.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun loadSchedules(): List<Schedule> {
        val raw = prefs.getString(KEY_SCHEDULES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { Schedule.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_MEMOS = "memos"
        private const val KEY_SCHEDULES = "schedules"
        private const val KEY_PLACES = "places"
        private const val KEY_COUNTERS = "counters"
        private const val KEY_TIMERS = "timers"
        private const val KEY_GEOFENCE_ERROR = "geofence_error"
        private const val KEY_APPLIED = "applied_id"
        private const val KEY_APPLIED_AT = "applied_at"
        private const val KEY_TARGET = "target"
        private const val KEY_TILE_TIP = "tile_tip_dismissed"
        private const val KEY_SAMPLES_REMOVED = "samples_removed_v2"

        private val OLD_SAMPLES = listOf(
            "외출 전" to "약 먹기\n지갑 · 열쇠 · 폰 챙기기\n나가기 전에 가스 밸브 확인",
            "오늘 집중" to "물 2L 마시기\n저녁 10시 전에 폰 내려놓기",
            "다짐" to "미루지 말자",
        )

        @Volatile
        private var instance: MemoStore? = null

        fun get(context: Context): MemoStore =
            instance ?: synchronized(this) {
                instance ?: MemoStore(context.applicationContext).also { instance = it }
            }
    }
}
