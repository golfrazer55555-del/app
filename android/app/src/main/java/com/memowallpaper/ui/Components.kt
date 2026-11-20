package com.memowallpaper.ui

import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.memowallpaper.ImageStore
import com.memowallpaper.Memo
import com.memowallpaper.Target
import com.memowallpaper.WallpaperRenderer

/** 화면에서 가려지는 영역(화면 높이 비율) */
data class Zone(val from: Float, val to: Float, val label: String)

data class Guide(val top: Zone, val bottom: Zone)

/** 갤럭시(One UI) 기준 대략적인 값 */
fun guideFor(target: Target): Guide = when (target) {
    Target.HOME -> Guide(Zone(0f, 0.05f, "상태 표시줄"), Zone(0.80f, 1f, "앱 아이콘"))
    Target.LOCK, Target.BOTH -> Guide(Zone(0f, 0.28f, "시계 · 알림"), Zone(0.88f, 1f, "바로가기"))
}

/** 메모 글자가 가림 영역과 겹치면 안내 문구를 돌려줌 */
fun overlapWarning(memo: Memo, screen: Pair<Int, Int>, guide: Guide): String? {
    val l = WallpaperRenderer.layout(memo, screen.first, screen.second)
    val top = l.topFraction ?: return null
    val bottom = l.bottomFraction ?: return null
    return when {
        top < guide.top.to -> "글자 일부가 ${guide.top.label} 영역에 가려질 수 있어요"
        bottom > guide.bottom.from -> "글자 일부가 ${guide.bottom.label} 영역에 가려질 수 있어요"
        else -> null
    }
}

/**
 * 실제 배경화면과 같은 방식으로 그리는 미리보기.
 * [guide]가 있으면 가려지는 영역을 반투명하게 표시합니다.
 */
@Composable
fun WallpaperCanvas(memo: Memo, modifier: Modifier = Modifier, guide: Guide? = null) {
    val context = LocalContext.current
    val assets = remember { ImageStore.assets(context) }
    val zonePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(70, 0, 0, 0) }
    }
    val linePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 255, 255, 255)
            style = Paint.Style.STROKE
        }
    }
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    val pillPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(150, 0, 0, 0) }
    }

    Canvas(modifier) {
        val w = size.width.toInt().coerceAtLeast(1)
        val h = size.height.toInt().coerceAtLeast(1)
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            WallpaperRenderer.draw(nc, memo, w, h, assets)
            guide ?: return@drawIntoCanvas
            linePaint.strokeWidth = 1.dp.toPx()
            linePaint.pathEffect = DashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)
            labelPaint.textSize = 10.dp.toPx()
            for ((zone, edgeAtBottom) in listOf(guide.top to true, guide.bottom to false)) {
                val top = zone.from * h
                val bottom = zone.to * h
                nc.drawRect(0f, top, w.toFloat(), bottom, zonePaint)
                val edge = if (edgeAtBottom) bottom else top
                nc.drawLine(0f, edge, w.toFloat(), edge, linePaint)
                val cy = (top + bottom) / 2f
                val tw = labelPaint.measureText(zone.label)
                val ph = labelPaint.textSize * 1.6f
                nc.drawRoundRect(w / 2f - tw / 2f - 6.dp.toPx(), cy - ph / 2f, w / 2f + tw / 2f + 6.dp.toPx(), cy + ph / 2f, ph / 2f, ph / 2f, pillPaint)
                val fm = labelPaint.fontMetrics
                nc.drawText(zone.label, w / 2f, cy - (fm.ascent + fm.descent) / 2f, labelPaint)
            }
        }
    }
}

/** 색상 동그라미 버튼 */
@Composable
fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(44.dp)
            .semantics {
                this.selected = selected
                contentDescription = "배경색"
            }
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .border(if (selected) 3.dp else 1.dp, if (selected) ring else Color(0x33000000), CircleShape)
            .padding(if (selected) 5.dp else 0.dp)
            .clip(CircleShape)
            .background(Color(color)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color(WallpaperRenderer.textColorFor(color)),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 직접 고르기 버튼(무지개) */
@Composable
fun CustomColorButton(selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "색 직접 고르기", onClick = onClick)
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .background(
                Brush.sweepGradient(
                    listOf(
                        Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF84CC16), Color(0xFF06B6D4),
                        Color(0xFF6366F1), Color(0xFFD946EF), Color(0xFFEF4444),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = "색 직접 고르기", modifier = Modifier.size(16.dp))
        }
    }
}

/** 색상·채도·밝기로 고르는 간단한 색 선택 창 */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("배경색 고르기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(color)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("미리보기", color = Color(WallpaperRenderer.textColorFor(color)))
                }
                LabeledSlider("색상", hue, 0f..360f) { hue = it }
                LabeledSlider("채도", sat, 0f..1f) { sat = it }
                LabeledSlider("밝기", value, 0f..1f) { value = it }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("선택") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
