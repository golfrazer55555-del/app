package com.memowallpaper

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 알림창 빠른 설정 타일.
 * - 메모가 2개 이하이거나 폰이 잠겨 있으면: 탭할 때마다 다음 메모로 바로 적용
 * - 메모가 3개 이상이면: 목록에서 골라 적용
 */
class MemoTileService : TileService() {

    private val store by lazy { MemoStore.get(applicationContext) }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        store.dismissTileTip()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val memos = store.memos.value
        when {
            memos.isEmpty() -> openApp()
            memos.size <= 2 || isLocked -> applyMemo(nextMemo(memos))
            else -> showPicker(memos)
        }
    }

    private fun nextMemo(memos: List<Memo>): Memo {
        val idx = memos.indexOfFirst { it.id == store.appliedId.value }
        return memos[(idx + 1) % memos.size]
    }

    private fun showPicker(memos: List<Memo>) {
        val applied = store.appliedId.value
        val names = memos.map { m -> if (m.id == applied) "✓  ${m.displayName()}" else m.displayName() }
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("${store.target.value.label}에 적용할 메모")
            .setItems(names.toTypedArray()) { d, which ->
                d.dismiss()
                applyMemo(memos[which])
            }
            .setNeutralButton("앱 열기") { _, _ -> openApp() }
            .setNegativeButton("닫기", null)
            .create()
        showDialog(dialog)
    }

    private fun applyMemo(memo: Memo) {
        qsTile?.let { t ->
            t.state = Tile.STATE_UNAVAILABLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) t.subtitle = "적용 중…"
            t.updateTile()
        }
        val app = applicationContext
        val target = store.target.value
        AppScope.launch {
            val result = WallpaperApplier.apply(app, memo, target)
            withContext(Dispatchers.Main) {
                refreshTile()
                val msg = if (result.isSuccess) {
                    "'${memo.displayName()}' 적용"
                } else {
                    "적용하지 못했어요: ${result.exceptionOrNull()?.message ?: "알 수 없는 오류"}"
                }
                Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshTile() {
        val t = qsTile ?: return
        val applied = store.find(store.appliedId.value)
        t.state = Tile.STATE_ACTIVE
        t.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            t.subtitle = applied?.displayName() ?: "탭해서 적용"
        }
        t.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
