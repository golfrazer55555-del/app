package com.memowallpaper

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon

/** 앱 아이콘을 길게 누르면 나오는 메모 바로가기 */
object Shortcuts {
    const val ACTION_APPLY = "com.memowallpaper.action.APPLY"
    const val EXTRA_MEMO_ID = "memo_id"

    private fun shortcutId(memoId: String) = "memo_$memoId"

    fun update(context: Context, memos: List<Memo>) {
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return
        runCatching {
            val max = minOf(4, sm.maxShortcutCountPerActivity)
            val list = memos.take(max).mapIndexed { i, m -> build(context, m, i) }
            sm.dynamicShortcuts = list
            // 홈 화면에 고정해 둔 바로가기도 이름·색을 최신으로
            val pinnedIds = sm.pinnedShortcuts.map { it.id }.toSet()
            val pinnedUpdates = memos.filter { shortcutId(it.id) in pinnedIds }.map { build(context, it, 0) }
            if (pinnedUpdates.isNotEmpty()) sm.updateShortcuts(pinnedUpdates)
        }
    }

    fun disable(context: Context, memoIds: List<String>) {
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return
        runCatching { sm.disableShortcuts(memoIds.map(::shortcutId), "삭제된 메모예요") }
    }

    private fun build(context: Context, m: Memo, rank: Int): ShortcutInfo {
        val name = m.displayName()
        val intent = Intent(ACTION_APPLY)
            .setClass(context, ApplyShortcutActivity::class.java)
            .putExtra(EXTRA_MEMO_ID, m.id)
        return ShortcutInfo.Builder(context, shortcutId(m.id))
            .setShortLabel(name.take(10))
            .setLongLabel("${name.take(20)} 적용")
            .setIcon(Icon.createWithAdaptiveBitmap(iconBitmap(m)))
            .setIntent(intent)
            .setRank(rank)
            .build()
    }

    /** 메모 배경색으로 칠한 적응형 아이콘(108dp 기준, 가운데 66dp가 보임) */
    private fun iconBitmap(m: Memo): Bitmap {
        val size = 216
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(m.bgColor or 0xFF000000.toInt())
        val fg = WallpaperRenderer.textColorFor(m.bgColor)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg }
        val u = size / 108f
        // 줄 세 개
        val widths = floatArrayOf(36f, 28f, 32f)
        widths.forEachIndexed { i, w ->
            val top = (40f + i * 11f) * u
            c.drawRoundRect(RectF(36f * u, top, (36f + w) * u, top + 6f * u), 3f * u, 3f * u, p)
        }
        return bmp
    }
}
