package com.memowallpaper

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** 메모 목록과 설정을 SharedPreferences에 저장합니다. 앱 전체에서 하나만 씁니다. */
class MemoStore private constructor(private val appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("memo_store", Context.MODE_PRIVATE)

    private val _memos = MutableStateFlow(loadMemos())
    val memos: StateFlow<List<Memo>> = _memos.asStateFlow()

    private val _appliedId = MutableStateFlow(prefs.getString(KEY_APPLIED, null))
    val appliedId: StateFlow<String?> = _appliedId.asStateFlow()

    private val _target = MutableStateFlow(enumOr(prefs.getString(KEY_TARGET, null), Target.LOCK))
    val target: StateFlow<Target> = _target.asStateFlow()

    private val _tileTipDismissed = MutableStateFlow(prefs.getBoolean(KEY_TILE_TIP, false))
    val tileTipDismissed: StateFlow<Boolean> = _tileTipDismissed.asStateFlow()

    fun find(id: String?): Memo? = id?.let { i -> _memos.value.firstOrNull { it.id == i } }

    fun upsert(memo: Memo) {
        val list = _memos.value.toMutableList()
        val idx = list.indexOfFirst { it.id == memo.id }
        if (idx >= 0) list[idx] = memo else list.add(memo)
        setMemos(list)
    }

    fun delete(id: String) {
        setMemos(_memos.value.filterNot { it.id == id })
        Shortcuts.disable(appContext, listOf(id))
        if (_appliedId.value == id) setApplied(null)
    }

    fun setApplied(id: String?) {
        _appliedId.value = id
        prefs.edit().putString(KEY_APPLIED, id).apply()
    }

    fun setTarget(t: Target) {
        _target.value = t
        prefs.edit().putString(KEY_TARGET, t.name).apply()
    }

    fun dismissTileTip() {
        _tileTipDismissed.value = true
        prefs.edit().putBoolean(KEY_TILE_TIP, true).apply()
    }

    private fun setMemos(list: List<Memo>) {
        _memos.value = list
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_MEMOS, arr.toString()).apply()
        Shortcuts.update(appContext, list)
    }

    private fun loadMemos(): List<Memo> {
        val raw = prefs.getString(KEY_MEMOS, null) ?: return sampleMemos().also { samples ->
            val arr = JSONArray().apply { samples.forEach { put(it.toJson()) } }
            prefs.edit().putString(KEY_MEMOS, arr.toString()).apply()
        }
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { Memo.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun sampleMemos() = listOf(
        Memo(
            title = "외출 전",
            text = "약 먹기\n지갑 · 열쇠 · 폰 챙기기\n나가기 전에 가스 밸브 확인",
            bgColor = 0xFF1E3A8A.toInt(),
        ),
        Memo(
            title = "오늘 집중",
            text = "물 2L 마시기\n저녁 10시 전에 폰 내려놓기",
            prefix = Prefix.DOT,
            bgColor = 0xFF14532D.toInt(),
        ),
        Memo(
            title = "다짐",
            text = "미루지 말자",
            prefix = Prefix.NONE,
            bgColor = 0xFFFDE68A.toInt(),
            sizePct = 10f,
            align = Align.CENTER,
            posPct = 50f,
        ),
    )

    companion object {
        private const val KEY_MEMOS = "memos"
        private const val KEY_APPLIED = "applied_id"
        private const val KEY_TARGET = "target"
        private const val KEY_TILE_TIP = "tile_tip_dismissed"

        @Volatile
        private var instance: MemoStore? = null

        fun get(context: Context): MemoStore =
            instance ?: synchronized(this) {
                instance ?: MemoStore(context.applicationContext).also { instance = it }
            }
    }
}
