package com.memowallpaper.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memowallpaper.Counter
import com.memowallpaper.CounterService
import com.memowallpaper.DownAction
import com.memowallpaper.GeofenceManager
import com.memowallpaper.Haptics
import com.memowallpaper.MemoStore

/** 카운터 목록 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountersScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { MemoStore.get(context) }
    val counters by store.counters.collectAsStateWithLifecycle()
    val active by store.activeCounterId.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("카운트", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val c = Counter(name = "카운터 ${counters.size + 1}")
                    store.upsertCounter(c)
                    onOpen(c.id)
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("카운터 추가") },
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
            item {
                Text(
                    "몇 번 했는지 헷갈릴 때 세어 줘요. 카운터를 열고 '볼륨 버튼으로 세기'를 켜면 " +
                        "폰을 주머니에 넣어 화면이 꺼져 있어도 볼륨 버튼으로 셀 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (counters.isEmpty()) {
                item {
                    Text(
                        "아직 카운터가 없어요.\n예) 스쿼트, 약 먹은 횟수, 물 마신 잔 수",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            items(counters, key = { it.id }) { c ->
                Card(
                    onClick = { onOpen(c.id) },
                    colors = if (c.id == active) {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        CardDefaults.cardColors()
                    },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (c.id == active) {
                                Text("볼륨 버튼으로 세는 중", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            if (c.goal > 0) {
                                Text("목표 ${c.goal}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "${c.count}",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        FilledTonalButton(onClick = {
                            store.addToCounter(c.id, 1)
                            Haptics.play(context, Haptics.PLUS)
                        }) { Text("+1") }
                    }
                }
            }
        }
    }
}

/** 카운터 하나 크게 보기 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CounterDetailScreen(counterId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MemoStore.get(context) }
    val counters by store.counters.collectAsStateWithLifecycle()
    val active by store.activeCounterId.collectAsStateWithLifecycle()
    val c = counters.firstOrNull { it.id == counterId }
    if (c == null) {
        onClose()
        return
    }
    val volumeMode = active == c.id

    var rename by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        CounterService.start(context, c.id)
    }

    fun setVolumeMode(on: Boolean) {
        if (on) {
            if (!GeofenceManager.hasNotificationPermission(context) && android.os.Build.VERSION.SDK_INT >= 33) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                CounterService.start(context, c.id)
            }
        } else {
            CounterService.stop(context)
        }
    }

    fun add(delta: Int) {
        val u = store.addToCounter(c.id, delta) ?: return
        Haptics.play(
            context,
            when {
                delta < 0 -> Haptics.MINUS
                u.goal > 0 && u.count == u.goal -> Haptics.GOAL
                u.buzzEvery10 && u.count % 10 == 0 -> Haptics.TENTH
                else -> Haptics.PLUS
            },
        )
    }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(c.displayName(), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    IconButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, contentDescription = "이름 바꾸기") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "삭제") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 큰 숫자: 눌러도 +1
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (volumeMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .combinedClickable(onClickLabel = "1 더하기", onClick = { add(1) }),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${c.count}", fontSize = 120.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        Text(
                            if (c.goal > 0) "목표 ${c.goal} · 눌러서 +1" else "눌러서 +1",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (c.goal > 0) {
                LinearProgressIndicator(
                    progress = { (c.count.toFloat() / c.goal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { add(-1) }, modifier = Modifier
                    .weight(1f)
                    .height(64.dp)) { Text("−1", fontSize = 22.sp) }
                Button(onClick = { add(1) }, modifier = Modifier
                    .weight(2f)
                    .height(64.dp)) { Text("+1", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            }
            TextButton(onClick = { confirmReset = true }, modifier = Modifier.align(Alignment.End)) { Text("0으로 초기화") }

            // 볼륨 버튼 모드
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (volumeMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("볼륨 버튼으로 세기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("화면이 꺼져 있어도 돼요", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = volumeMode, onCheckedChange = ::setVolumeMode)
                    }
                    val down = if (c.downAction == DownAction.PLUS) "+1" else "−1"
                    Text(
                        "볼륨 ↑ +1 · 볼륨 ↓ $down · 이어폰 버튼 +1\n" +
                            "진동으로 알려줘요: +1 짧게 한 번, −1 짧게 두 번" +
                            (if (c.buzzEvery10) ", 10번째마다 조금 길게" else "") +
                            (if (c.goal > 0) ", 목표 도달 시 길게 두 번 + 소리" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "세는 동안에는 볼륨 버튼으로 소리 크기가 바뀌지 않아요. 다 셌으면 꺼 주세요(알림에서도 끌 수 있어요).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()
            Text("설정", style = MaterialTheme.typography.titleSmall)
            var goalText by remember(c.id) { mutableStateOf(if (c.goal > 0) c.goal.toString() else "") }
            OutlinedTextField(
                value = goalText,
                onValueChange = { v ->
                    goalText = v.filter(Char::isDigit).take(6)
                    store.upsertCounter(c.copy(goal = goalText.toIntOrNull() ?: 0))
                },
                label = { Text("목표 횟수 (선택)") },
                placeholder = { Text("예) 30") },
                supportingText = { Text("도달하면 길게 진동하고 타이머 소리가 나요") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("볼륨 ↓ 버튼", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DownAction.entries.forEachIndexed { i, d ->
                    SegmentedButton(
                        selected = c.downAction == d,
                        onClick = { store.upsertCounter(c.copy(downAction = d)) },
                        shape = SegmentedButtonDefaults.itemShape(i, DownAction.entries.size),
                        icon = {},
                    ) { Text(d.label, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("10번째마다 조금 길게 진동", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = c.buzzEvery10, onCheckedChange = { store.upsertCounter(c.copy(buzzEvery10 = it)) })
            }
        }
    }

    if (rename) {
        var name by remember { mutableStateOf(c.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("이름 바꾸기") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("예) 스쿼트") })
            },
            confirmButton = {
                TextButton(onClick = {
                    store.upsertCounter(c.copy(name = name.trim()))
                    rename = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("취소") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("0으로 초기화할까요?") },
            text = { Text("지금 ${c.count}회예요.") },
            confirmButton = {
                TextButton(onClick = {
                    store.upsertCounter(c.copy(count = 0))
                    confirmReset = false
                }) { Text("초기화") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("취소") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("카운터 삭제") },
            text = { Text("'${c.displayName()}'을(를) 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    if (volumeMode) CounterService.stop(context)
                    store.deleteCounter(c.id)
                    confirmDelete = false
                    onClose()
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}
