package com.memowallpaper.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memowallpaper.GeofenceManager
import com.memowallpaper.MemoStore
import com.memowallpaper.TimerItem
import com.memowallpaper.TimerManager
import com.memowallpaper.TimerState
import com.memowallpaper.formatDuration
import kotlinx.coroutines.delay

private val PRESETS = listOf(1, 3, 5, 10, 15, 30, 60)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimerScreen() {
    val context = LocalContext.current
    val store = remember { MemoStore.get(context) }
    val timers by store.timers.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCustom by remember { mutableStateOf(false) }
    var notifOk by remember { mutableStateOf(GeofenceManager.hasNotificationPermission(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    LifecycleResumeEffect(Unit) {
        notifOk = GeofenceManager.hasNotificationPermission(context)
        onPauseOrDispose { }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }

    Scaffold(
        topBar = { TopAppBar(title = { Text("타이머", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCustom = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("직접 설정") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp,
                bottom = pad.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!notifOk && Build.VERSION.SDK_INT >= 33) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "알림을 허용해야 남은 시간과 '시간 끝' 화면이 보여요.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("허용") }
                        }
                    }
                }
            }
            item {
                Text("바로 시작", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    PRESETS.forEach { m ->
                        AssistChip(
                            onClick = { TimerManager.createAndStart(context, m * 60_000L) },
                            label = { Text(if (m < 60) "${m}분" else "${m / 60}시간") },
                        )
                    }
                }
            }
            if (timers.isEmpty()) {
                item {
                    Text(
                        "위의 버튼을 누르면 바로 시작해요.\n'직접 설정'으로 이름(예: 빨래, 라면)과 시간을 정할 수도 있어요.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(timers, key = { it.id }) { t -> TimerCard(t, now) }
        }
    }

    if (showCustom) CustomTimerDialog(onDismiss = { showCustom = false }) { label, ms ->
        showCustom = false
        val t = TimerItem(label = label, durationMs = ms)
        store.upsertTimer(t)
        TimerManager.start(context, t.id)
    }
}

@Composable
private fun TimerCard(t: TimerItem, now: Long) {
    val context = LocalContext.current
    val remaining = t.remaining(now)
    val ringing = t.state == TimerState.RINGING
    Card(
        colors = when {
            ringing -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            t.state == TimerState.RUNNING -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            else -> CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when (t.state) {
                            TimerState.RUNNING -> "진행 중"
                            TimerState.PAUSED -> "일시정지"
                            TimerState.RINGING -> "시간 끝!"
                            TimerState.IDLE -> "전체 ${formatDuration(t.durationMs)}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (t.state == TimerState.IDLE) {
                    IconButton(onClick = { TimerManager.delete(context, t.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "삭제")
                    }
                }
            }
            Text(
                if (ringing) "0:00" else formatDuration(remaining),
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
            )
            if (t.state == TimerState.RUNNING || t.state == TimerState.PAUSED) {
                LinearProgressIndicator(
                    progress = { 1f - remaining.toFloat() / (t.durationMs.coerceAtLeast(remaining)).coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (t.state) {
                    TimerState.IDLE -> Button(onClick = { TimerManager.start(context, t.id) }) { Text("시작") }
                    TimerState.RUNNING -> FilledTonalButton(onClick = { TimerManager.pause(context, t.id) }) { Text("일시정지") }
                    TimerState.PAUSED -> Button(onClick = { TimerManager.start(context, t.id) }) { Text("계속") }
                    TimerState.RINGING -> Button(onClick = { TimerManager.dismiss(context, t.id) }) { Text("끄기") }
                }
                OutlinedButton(onClick = { TimerManager.addMinute(context, t.id) }) { Text("+1분") }
                if (t.state != TimerState.IDLE && !ringing) {
                    TextButton(onClick = { TimerManager.reset(context, t.id) }) { Text("취소") }
                }
            }
        }
    }
}

@Composable
private fun CustomTimerDialog(onDismiss: () -> Unit, onStart: (String, Long) -> Unit) {
    var label by remember { mutableStateOf("") }
    var h by remember { mutableIntStateOf(0) }
    var m by remember { mutableIntStateOf(5) }
    var s by remember { mutableIntStateOf(0) }
    val total = (h * 3600L + m * 60L + s) * 1000L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("타이머 직접 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("이름 (선택)") },
                    placeholder = { Text("예) 빨래, 라면, 약 먹기") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("시간", h, 23, Modifier.weight(1f)) { h = it }
                    NumberField("분", m, 59, Modifier.weight(1f)) { m = it }
                    NumberField("초", s, 59, Modifier.weight(1f)) { s = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(label.trim(), total) }, enabled = total >= 1000) { Text("시작") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun NumberField(label: String, value: Int, max: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter(Char::isDigit).take(2)
            onChange((text.toIntOrNull() ?: 0).coerceIn(0, max))
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
