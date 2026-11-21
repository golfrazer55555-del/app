package com.memowallpaper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memowallpaper.GeofenceManager
import com.memowallpaper.MemoStore
import com.memowallpaper.Place
import com.memowallpaper.PlaceAlerts
import com.memowallpaper.SoundChoice
import com.memowallpaper.Sounds
import com.memowallpaper.Trigger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MemoStore.get(context) }
    // 설정이 바뀌면 다시 그리기
    val version = store.soundVersion.collectAsStateWithLifecycle().value
    check(version >= 0)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) { onDispose { Sounds.stopPreview() } }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("소리 · 설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "일반 알림 소리는 무심코 넘기게 되니까, 이 앱만의 소리와 진동(길게-짧게-길게)을 써요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("장소 알림 소리", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SoundPicker(selected = store.placeSound, alarm = false) { store.placeSound = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("무음·진동 모드에서도 소리 내기", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "알람 볼륨으로 울려요. 기기·방해금지 설정에 따라 안 될 수도 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = store.soundInSilentMode, onCheckedChange = { store.soundInSilentMode = it })
            }
            FilledTonalButton(onClick = {
                if (!GeofenceManager.hasNotificationPermission(context)) {
                    scope.launch { snackbar.showSnackbar("알림 권한을 먼저 허용해 주세요(장소 탭 위쪽)") }
                } else {
                    PlaceAlerts.notify(
                        context,
                        Place(id = "test", name = "테스트 장소", trigger = Trigger.ENTER, message = "이런 소리와 진동으로 알려줘요"),
                        entering = true,
                        store = store,
                    )
                }
            }) { Text("실제 알림으로 들어보기") }

            HorizontalDivider()

            Text("타이머 · 카운트 목표 소리", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "타이머가 끝나면 끌 때까지(최대 1분) 알람 볼륨으로 반복해서 울려요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SoundPicker(selected = store.timerSound, alarm = true) { store.timerSound = it }
        }
    }
}

@Composable
private fun SoundPicker(selected: SoundChoice, alarm: Boolean, onSelect: (SoundChoice) -> Unit) {
    val context = LocalContext.current
    Column {
        SoundChoice.entries.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) {
                        onSelect(s)
                        Sounds.play(context, s, alarm)
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = s == selected, onClick = null)
                Text(s.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp))
                IconButton(onClick = { Sounds.play(context, s, alarm) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "${s.label} 미리 듣기")
                }
            }
        }
    }
}
