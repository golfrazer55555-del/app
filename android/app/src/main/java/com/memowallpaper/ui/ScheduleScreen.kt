package com.memowallpaper.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memowallpaper.Memo
import com.memowallpaper.MemoStore
import com.memowallpaper.Schedule
import com.memowallpaper.Scheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MemoStore.get(context) }
    val schedules by store.schedules.collectAsStateWithLifecycle()
    val memos by store.memos.collectAsStateWithLifecycle()
    val target by store.target.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var exactAllowed by remember { mutableStateOf(Scheduler.canExact(context)) }
    LifecycleResumeEffect(Unit) {
        exactAllowed = Scheduler.canExact(context)
        if (exactAllowed) Scheduler.reschedule(context)
        onPauseOrDispose { }
    }
    var editingTimeOf by remember { mutableStateOf<Schedule?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("시간대별 자동 변경") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (memos.isEmpty()) {
                        scope.launch { snackbar.showSnackbar("먼저 메모를 하나 만들어 주세요") }
                    } else {
                        // 새 일정은 마지막 일정 몇 시간 뒤로
                        val last = schedules.lastOrNull()
                        val hour = if (last == null) 8 else (last.hour + 4) % 24
                        val memo = memos.getOrNull(schedules.size % memos.size) ?: memos.first()
                        val s = Schedule(hour = hour, minute = 0, memoId = memo.id)
                        store.upsertSchedule(s)
                        editingTimeOf = s
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("시간 추가") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp,
                bottom = pad.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "정한 시간이 되면 그 메모가 ${target.label}에 자동으로 적용돼요. " +
                        "중간에 직접 바꿔도 다음 정한 시간에 다시 바뀌어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("정확한 시간에 바꾸려면 권한이 필요해요", fontWeight = FontWeight.Bold)
                            Text(
                                "'알람 및 리마인더' 권한을 켜지 않으면 몇 분 늦게 바뀔 수 있어요.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            FilledTonalButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                                )
                            }) { Text("권한 켜기") }
                        }
                    }
                }
            }
            val next = Scheduler.nextChange(context)
            if (next != null) {
                item {
                    val memo = store.find(next.second.memoId)
                    val dayFmt = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
                    val day = if (next.first.toLocalDate() == LocalDate.now()) "오늘" else next.first.format(dayFmt)
                    Text(
                        "다음 변경: $day ${next.second.timeLabel()} → '${memo?.displayName()}'",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (schedules.isEmpty()) {
                item {
                    Text(
                        "아직 정한 시간이 없어요.\n예) 오전 7:30 '출근 전', 오후 9:00 '자기 전'",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            items(schedules, key = { it.id }) { s ->
                ScheduleCard(
                    schedule = s,
                    memos = memos,
                    onChange = store::upsertSchedule,
                    onDelete = { store.deleteSchedule(s.id) },
                    onEditTime = { editingTimeOf = s },
                )
            }
        }
    }

    editingTimeOf?.let { s ->
        val state = rememberTimePickerState(initialHour = s.hour, initialMinute = s.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { editingTimeOf = null },
            title = { Text("시간 정하기") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    store.upsertSchedule(
                        (store.schedules.value.firstOrNull { it.id == s.id } ?: s).copy(hour = state.hour, minute = state.minute),
                    )
                    editingTimeOf = null
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { editingTimeOf = null }) { Text("취소") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleCard(
    schedule: Schedule,
    memos: List<Memo>,
    onChange: (Schedule) -> Unit,
    onDelete: () -> Unit,
    onEditTime: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClickLabel = "시간 바꾸기", onClick = onEditTime),
                ) {
                    Text(
                        schedule.timeLabel(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (schedule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    )
                    Text(schedule.daysLabel(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = schedule.enabled, onCheckedChange = { onChange(schedule.copy(enabled = it)) })
            }

            var expanded by remember { mutableStateOf(false) }
            val current = memos.firstOrNull { it.id == schedule.memoId }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = current?.displayName() ?: "(메모를 골라 주세요)",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("바꿀 메모") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    memos.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                onChange(schedule.copy(memoId = m.id))
                                expanded = false
                            },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                Schedule.DAY_NAMES.forEachIndexed { i, name ->
                    val day = i + 1
                    val on = day in schedule.days
                    FilterChip(
                        selected = on,
                        onClick = {
                            onChange(schedule.copy(days = if (on) schedule.days - day else schedule.days + day))
                        },
                        label = { Text(name) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text(" 삭제")
                }
            }
        }
    }
}
