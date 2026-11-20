package com.memowallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import java.text.BreakIterator

/**
 * 메모를 단색 배경 이미지로 그립니다.
 * 모든 크기는 이미지 가로 폭에 비례하므로 미리보기와 실제 결과가 같은 모양이 됩니다.
 */
object WallpaperRenderer {

    private const val LINE_HEIGHT = 1.45f
    private const val PAD_X = 0.08f
    private const val DARK_TEXT = 0xFF111111.toInt()

    data class Row(val text: String, val indent: Float, val prefixIndex: Int, val blank: Boolean)

    class Layout(
        val rows: List<Row>,
        val paint: TextPaint,
        val fontPx: Float,
        val prefixWidth: Float,
        val lineHeight: Float,
        val padX: Float,
        val top: Float,
        val bottom: Float,
        val width: Int,
        val height: Int,
    ) {
        /** 글자 영역의 세로 범위(0~1 비율). 글자가 없으면 null */
        val topFraction: Float? get() = if (rows.isEmpty()) null else top / height
        val bottomFraction: Float? get() = if (rows.isEmpty()) null else bottom / height
    }

    /** 배경과 대비가 더 큰 글자색(흰색 / 거의 검정) */
    fun textColorFor(bg: Int): Int {
        val opaque = bg or 0xFF000000.toInt()
        val white = ColorUtils.calculateContrast(Color.WHITE, opaque)
        val dark = ColorUtils.calculateContrast(DARK_TEXT, opaque)
        return if (white >= dark) Color.WHITE else DARK_TEXT
    }

    fun render(memo: Memo, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), memo, width, height)
        return bmp
    }

    fun draw(canvas: Canvas, memo: Memo, width: Int, height: Int): Layout {
        canvas.drawColor(memo.bgColor or 0xFF000000.toInt())
        val layout = layout(memo, width, height)
        val paint = layout.paint
        val fm = paint.fontMetrics
        val baselineOffset = -(fm.ascent + fm.descent) / 2f

        var y = layout.top
        for (row in layout.rows) {
            val h = rowHeight(row, layout.lineHeight)
            if (!row.blank) {
                val cy = y + h / 2f
                val lineW = row.indent + paint.measureText(row.text)
                val x = when (memo.align) {
                    Align.LEFT -> layout.padX
                    Align.CENTER -> (width - lineW) / 2f
                    Align.RIGHT -> width - layout.padX - lineW
                }
                if (row.prefixIndex > 0 && memo.prefix != Prefix.NONE) {
                    drawPrefix(canvas, memo.prefix, row.prefixIndex, x, cy, baselineOffset, layout)
                }
                canvas.drawText(row.text, x + row.indent, cy + baselineOffset, paint)
            }
            y += h
        }
        return layout
    }

    fun layout(memo: Memo, width: Int, height: Int): Layout {
        val fontPx = width * memo.sizePct / 100f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontPx
            typeface = if (memo.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = textColorFor(memo.bgColor)
            textAlign = Paint.Align.LEFT
        }
        val padX = width * PAD_X
        val maxW = width - padX * 2
        val lineH = fontPx * LINE_HEIGHT

        val src = memo.text.replace("\r", "").split('\n').toMutableList()
        while (src.isNotEmpty() && src.first().isBlank()) src.removeAt(0)
        while (src.isNotEmpty() && src.last().isBlank()) src.removeAt(src.lastIndex)

        val itemCount = src.count { it.isNotBlank() }
        val preW = prefixWidth(memo.prefix, itemCount, fontPx, paint)
        val hanging = memo.align == Align.LEFT

        val rows = mutableListOf<Row>()
        var n = 0
        for (line in src) {
            val t = line.trim()
            if (t.isEmpty()) {
                rows += Row("", 0f, 0, blank = true)
                continue
            }
            n += 1
            wrap(paint, t, maxW - preW).forEachIndexed { i, part ->
                rows += Row(
                    text = part,
                    indent = if (i == 0 || hanging) preW else 0f,
                    prefixIndex = if (i == 0) n else 0,
                    blank = false,
                )
            }
        }

        val totalH = rows.sumOf { rowHeight(it, lineH).toDouble() }.toFloat()
        var top = height * memo.posPct / 100f - totalH / 2f
        top = maxOf(height * 0.02f, minOf(top, height * 0.98f - totalH))

        return Layout(rows, paint, fontPx, preW, lineH, padX, top, top + totalH, width, height)
    }

    private fun rowHeight(row: Row, lineH: Float) = if (row.blank) lineH * 0.5f else lineH

    private fun prefixWidth(prefix: Prefix, count: Int, fontPx: Float, paint: Paint): Float = when (prefix) {
        Prefix.NONE -> 0f
        Prefix.DOT -> fontPx * 0.85f
        Prefix.CHECK -> fontPx * 1.05f
        Prefix.NUM -> paint.measureText("$count.") + fontPx * 0.4f
    }

    private fun drawPrefix(
        canvas: Canvas, prefix: Prefix, index: Int, x: Float, cy: Float, baselineOffset: Float, l: Layout,
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = l.paint.color }
        val f = l.fontPx
        when (prefix) {
            Prefix.DOT -> canvas.drawCircle(x + f * 0.25f, cy, f * 0.16f, p)
            Prefix.CHECK -> {
                val s = f * 0.62f
                p.style = Paint.Style.STROKE
                p.strokeWidth = maxOf(2f, f * 0.075f)
                val left = x + p.strokeWidth / 2f
                canvas.drawRoundRect(RectF(left, cy - s / 2f, left + s, cy + s / 2f), s * 0.2f, s * 0.2f, p)
            }
            Prefix.NUM -> {
                // 오른쪽 정렬로 "9."과 "10."의 점 위치를 맞춤
                val label = "$index."
                val right = x + l.prefixWidth - f * 0.4f
                canvas.drawText(label, right - l.paint.measureText(label), cy + baselineOffset, l.paint)
            }
            Prefix.NONE -> Unit
        }
    }

    /** 공백 단위로 줄바꿈하고, 한 단어가 너무 길면 글자 단위로 자릅니다. */
    private fun wrap(paint: Paint, text: String, maxW: Float): List<String> {
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        fun push() {
            lines += cur.toString().trimEnd()
            cur = StringBuilder()
        }

        for (tok in tokens(text)) {
            if (paint.measureText(cur.toString() + tok) <= maxW) {
                cur.append(tok)
                continue
            }
            if (tok.isBlank()) {
                push()
                continue
            }
            if (cur.isNotBlank()) push()
            if (paint.measureText(tok) <= maxW) {
                cur.append(tok)
                continue
            }
            for (g in graphemes(tok)) {
                if (cur.isNotEmpty() && paint.measureText(cur.toString() + g) > maxW) {
                    push()
                }
                cur.append(g)
            }
        }
        if (cur.isNotEmpty() || lines.isEmpty()) push()
        return lines.filterIndexed { i, s -> s.isNotEmpty() || i == 0 }
    }

    /** 공백 덩어리와 공백 아닌 덩어리로 나눔 */
    private fun tokens(s: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var ws: Boolean? = null
        for (ch in s) {
            val w = ch.isWhitespace()
            if (ws != null && w != ws) {
                out += sb.toString()
                sb.clear()
            }
            sb.append(ch)
            ws = w
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    private fun graphemes(s: String): List<String> {
        val it = BreakIterator.getCharacterInstance()
        it.setText(s)
        val out = mutableListOf<String>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            out += s.substring(start, end)
            start = end
            end = it.next()
        }
        return out
    }
}
