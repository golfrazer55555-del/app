package com.memowallpaper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews

/** 홈 화면 위젯: 지금 적용된 메모를 보여주고, 누르면 바로 편집 */
class MemoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.update(context, manager, ids)
    }
}

object WidgetUpdater {
    const val EXTRA_EDIT_MEMO_ID = "edit_memo_id"

    fun updateAll(context: Context) {
        runCatching {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, MemoWidgetProvider::class.java))
            if (ids.isNotEmpty()) update(context, mgr, ids)
        }
    }

    fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val store = MemoStore.get(context)
        val memo = store.applied()
        val views = RemoteViews(context.packageName, R.layout.widget_memo)

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (memo != null) intent.putExtra(EXTRA_EDIT_MEMO_ID, memo.id)
        val pi = PendingIntent.getActivity(
            context, 7100, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)

        if (memo == null) {
            views.setInt(R.id.widget_bg, "setColorFilter", Memo.DEFAULT_BG)
            views.setTextViewText(R.id.widget_title, "깜빡 방지")
            views.setTextViewText(R.id.widget_text, "앱에서 메모를 적용하면 여기에 보여요")
            views.setTextColor(R.id.widget_title, 0xCCFFFFFF.toInt())
            views.setTextColor(R.id.widget_text, 0xFFFFFFFF.toInt())
            views.setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, 14f)
            views.setInt(R.id.widget_text, "setGravity", Gravity.START or Gravity.CENTER_VERTICAL)
        } else {
            val fg = memo.textColor ?: WallpaperRenderer.textColorFor(memo.bgColor)
            views.setInt(R.id.widget_bg, "setColorFilter", memo.bgColor or 0xFF000000.toInt())
            views.setTextViewText(R.id.widget_title, "✎ ${memo.displayName()}")
            views.setTextViewText(R.id.widget_text, widgetText(memo))
            views.setTextColor(R.id.widget_title, (fg and 0x00FFFFFF) or 0xB3000000.toInt())
            views.setTextColor(R.id.widget_text, fg or 0xFF000000.toInt())
            val size = (memo.sizePct * 2.4f).coerceIn(14f, 34f)
            views.setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, size)
            val g = when (memo.align) {
                Align.LEFT -> Gravity.START
                Align.CENTER -> Gravity.CENTER_HORIZONTAL
                Align.RIGHT -> Gravity.END
            } or Gravity.CENTER_VERTICAL
            views.setInt(R.id.widget_text, "setGravity", g)
        }
        manager.updateAppWidget(ids, views)
    }

    /** 기호와 자동 문구를 붙인 위젯용 글 */
    fun widgetText(memo: Memo): String {
        var n = 0
        val text = TextTokens.resolve(memo.text).trim('\n', ' ')
        if (text.isEmpty()) return if (memo.drawing != null || memo.image != null) "(그림 메모)" else "(빈 메모)"
        return text.lines().joinToString("\n") { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@joinToString ""
            n += 1
            when (memo.prefix) {
                Prefix.NONE -> line
                Prefix.DOT -> "•  $line"
                Prefix.CHECK -> "□  $line"
                Prefix.NUM -> "$n.  $line"
            }
        }
    }
}
