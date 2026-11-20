package com.memowallpaper.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memowallpaper.ImageStore
import com.memowallpaper.Memo
import com.memowallpaper.PEN_COLORS
import com.memowallpaper.WallpaperApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 한 획. 좌표와 굵기는 화면 크기에 대한 비율(0~1)이라 어떤 해상도로도 다시 그릴 수 있어요. */
private class Stroke(
    val points: SnapshotStateList<Offset>,
    val color: Int,
    val width: Float,
    val eraser: Boolean,
)

private val clearMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

private fun drawStroke(canvas: AndroidCanvas, s: Stroke, w: Float, h: Float) {
    val pts = s.points
    if (pts.isEmpty()) return
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = s.color
        strokeWidth = s.width * w
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        if (s.eraser) xfermode = clearMode
    }
    if (pts.size == 1) {
        p.style = Paint.Style.FILL
        canvas.drawCircle(pts[0].x * w, pts[0].y * h, s.width * w / 2f, p)
        return
    }
    p.style = Paint.Style.STROKE
    val path = Path()
    path.moveTo(pts[0].x * w, pts[0].y * h)
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        // 두 점의 가운데로 곡선을 이어 부드럽게
        path.quadTo(a.x * w, a.y * h, (a.x + b.x) / 2f * w, (a.y + b.y) / 2f * h)
    }
    val last = pts.last()
    path.lineTo(last.x * w, last.y * h)
    canvas.drawPath(path, p)
}

/** 그림 층(기존 그림 + 이번 획들)을 그림. 지우개는 이 층만 지워요. */
private fun drawLayer(canvas: AndroidCanvas, base: Bitmap?, strokes: List<Stroke>, w: Float, h: Float) {
    val save = canvas.saveLayer(0f, 0f, w, h, null)
    if (base != null) {
        canvas.drawBitmap(base, null, RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }
    strokes.forEach { drawStroke(canvas, it, w, h) }
    canvas.restoreToCount(save)
}

/**
 * 손그림 화면. 완료하면 새 그림 파일 이름(그림이 없으면 null)을 돌려줘요.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawScreen(memo: Memo, onCancel: () -> Unit, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screen = remember { WallpaperApplier.screenSize(context) }
    val ratio = screen.first.toFloat() / screen.second

    val base = remember { memo.drawing?.let { ImageStore.load(context, it) } }
    var baseCleared by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<Stroke>() }
    val redo = remember { mutableStateListOf<Stroke>() }
    var current by remember { mutableStateOf<Stroke?>(null) }

    var color by remember { mutableStateOf(PEN_COLORS.first()) }
    var width by remember { mutableStateOf(0.012f) }
    var eraser by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    val penColor by rememberUpdatedState(color)
    val penWidth by rememberUpdatedState(width)
    val isEraser by rememberUpdatedState(eraser)

    val changed = strokes.isNotEmpty() || baseCleared

    fun finish() {
        if (!changed) {
            onDone(memo.drawing)
            return
        }
        val layerBase = if (baseCleared) null else base
        if (strokes.isEmpty() && layerBase == null) {
            onDone(null)
            return
        }
        saving = true
        scope.launch {
            val (w, h) = screen
            val out = withContext(Dispatchers.Default) {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    drawLayer(AndroidCanvas(it), layerBase, strokes.toList(), w.toFloat(), h.toFloat())
                }
            }
            val name = ImageStore.saveDrawing(context, out)
            saving = false
            onDone(name)
        }
    }

    BackHandler { if (changed) confirmLeave = true else onCancel() }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = { Text("그림 그리기") },
                navigationIcon = {
                    IconButton(onClick = { if (changed) confirmLeave = true else onCancel() }) {
                        Icon(Icons.Default.Close, contentDescription = "취소")
                    }
                },
                actions = {
                    Button(onClick = ::finish, enabled = !saving, modifier = Modifier.padding(end = 8.dp)) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("완료")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !eraser, onClick = { eraser = false }, label = { Text("펜") })
                        FilterChip(selected = eraser, onClick = { eraser = true }, label = { Text("지우개") })
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { strokes.removeLastOrNull()?.let { redo.add(it) } },
                            enabled = strokes.isNotEmpty(),
                        ) { Text("되돌리기") }
                        TextButton(
                            onClick = { redo.removeLastOrNull()?.let { strokes.add(it) } },
                            enabled = redo.isNotEmpty(),
                        ) { Text("다시") }
                        TextButton(onClick = { confirmClear = true }) { Text("모두 지우기") }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        items(PEN_COLORS) { c ->
                            val selected = !eraser && c == color
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        color = c
                                        eraser = false
                                    }
                                    .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x55888888), CircleShape)
                                    .padding(if (selected) 4.dp else 0.dp)
                                    .clip(CircleShape)
                                    .background(Color(c)),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("굵기", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(12.dp))
                        Slider(
                            value = width,
                            onValueChange = { width = it },
                            valueRange = 0.003f..0.06f,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size((width * 600).dp.coerceAtMost(36.dp))
                                    .clip(CircleShape)
                                    .background(if (eraser) Color.Gray else Color(color))
                                    .border(1.dp, Color(0x55888888), CircleShape),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .aspectRatio(ratio, matchHeightConstraintsFirst = maxWidth / maxHeight > ratio)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                WallpaperCanvas(memo = memo.copy(drawing = null), modifier = Modifier.fillMaxSize())
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .testTag("drawCanvas")
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                fun norm(o: Offset) = Offset((o.x / w).coerceIn(0f, 1f), (o.y / h).coerceIn(0f, 1f))
                                val pts = mutableStateListOf(norm(down.position))
                                val stroke = Stroke(pts, penColor, if (isEraser) penWidth * 2f else penWidth, isEraser)
                                current = stroke
                                down.consume()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    pts.add(norm(change.position))
                                    change.consume()
                                }
                                strokes.add(stroke)
                                redo.clear()
                                current = null
                            }
                        },
                ) {
                    val all = strokes.toList() + listOfNotNull(current)
                    // 획 안의 점 목록을 읽어 그리는 중에도 다시 그려지게 함
                    all.forEach { it.points.size }
                    drawIntoCanvas { c ->
                        drawLayer(c.nativeCanvas, if (baseCleared) null else base, all, size.width, size.height)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("그림을 모두 지울까요?") },
            confirmButton = {
                TextButton(onClick = {
                    strokes.clear()
                    redo.clear()
                    baseCleared = true
                    confirmClear = false
                }) { Text("모두 지우기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("그린 내용을 저장할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    finish()
                }) { Text("저장", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onCancel()
                }) { Text("버리기") }
            },
        )
    }
}
