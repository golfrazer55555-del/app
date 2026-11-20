package com.memowallpaper

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 바로가기에서 호출됨. 화면 없이 메모를 적용하고 바로 닫혀요. */
class ApplyShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        val store = MemoStore.get(app)
        val memo = store.find(intent?.getStringExtra(Shortcuts.EXTRA_MEMO_ID))
        finish()

        if (memo == null) {
            Toast.makeText(app, "메모를 찾을 수 없어요. 앱에서 확인해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val target = store.target.value
        AppScope.launch {
            val result = WallpaperApplier.apply(app, memo, target)
            withContext(Dispatchers.Main) {
                val msg = if (result.isSuccess) {
                    "'${memo.displayName()}' → ${target.label}에 적용했어요"
                } else {
                    "적용하지 못했어요: ${result.exceptionOrNull()?.message ?: "알 수 없는 오류"}"
                }
                Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
