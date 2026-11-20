package com.memowallpaper.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memowallpaper.GeofenceManager
import com.memowallpaper.MemoStore
import com.memowallpaper.Place
import com.memowallpaper.PlaceAlerts

/** 장소 알림 목록 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(onEdit: (String) -> Unit, onNew: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MemoStore.get(context) }
    val places by store.places.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("장소 알림", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("장소 추가") },
            )
        },
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
                    "정해 둔 장소 근처에 도착하거나 떠나면 알림으로 알려줘요. " +
                        "예) 마트 근처에 가면 '우유 사기', 집을 나서면 '가스밸브 확인'",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { LocationSetupCard() }
            if (places.isEmpty()) {
                item {
                    Text(
                        "아직 장소가 없어요.\n'장소 추가'를 눌러 집, 회사, 마트 같은 곳을 등록해 보세요.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            items(places, key = { it.id }) { p ->
                PlaceCard(
                    place = p,
                    preview = PlaceAlerts.body(p, store),
                    onClick = { onEdit(p.id) },
                    onToggle = { store.upsertPlace(p.copy(enabled = it)) },
                )
            }
        }
    }
}

@Composable
private fun PlaceCard(place: Place, preview: String, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = if (place.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(place.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(place.summary(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    preview.replace('\n', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(checked = place.enabled, onCheckedChange = onToggle)
        }
    }
}

/**
 * 장소 알림에 필요한 준비(권한·설정)를 한눈에 보여주고 바로 켤 수 있게 해요.
 */
@Composable
fun LocationSetupCard() {
    val context = LocalContext.current
    val store = remember { MemoStore.get(context) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        GeofenceManager.sync(context)
        onPauseOrDispose { }
    }

    @Suppress("UNUSED_EXPRESSION") tick
    val fg = GeofenceManager.hasForegroundLocation(context)
    val bg = GeofenceManager.hasBackgroundLocation(context)
    val notif = GeofenceManager.hasNotificationPermission(context)
    val battery = GeofenceManager.isIgnoringBatteryOptimizations(context)
    val error = store.geofenceError

    var explainBackground by remember { mutableStateOf(false) }

    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick++
        GeofenceManager.sync(context)
    }
    val fgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        tick++
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            explainBackground = true
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val allOk = fg && bg && notif && battery && error == null
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (allOk) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (allOk) "장소 알림 준비 완료" else "장소 알림을 쓰려면 아래를 켜 주세요",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            SetupRow("위치 권한", fg, "허용") {
                fgLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            SetupRow("위치 '항상 허용' (앱을 안 켜도 알림)", bg, "설정", enabled = fg) { explainBackground = true }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SetupRow("알림 권한", notif, "허용") { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
            SetupRow("배터리 사용 제한 없음 (갤럭시 권장)", battery, "설정") {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    )
                }.onFailure {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            if (error != null && fg && bg) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text(" $error", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
                FilledTonalButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                    Text("위치 설정 열기")
                }
            }
        }
    }

    if (explainBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        AlertDialog(
            onDismissRequest = { explainBackground = false },
            title = { Text("위치 '항상 허용'") },
            text = {
                Text(
                    "앱을 닫아 둬도 장소에 도착·출발할 때 알리려면 위치를 '항상 허용'해야 해요.\n\n" +
                        "다음 화면에서 '위치' → '항상 허용'을 골라 주세요.\n\n" +
                        "위치 정보는 휴대폰 안에서만 쓰이고 어디로도 보내지 않아요.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explainBackground = false
                    bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text("계속") }
            },
            dismissButton = { TextButton(onClick = { explainBackground = false }) { Text("나중에") } },
        )
    }
}

@Composable
private fun SetupRow(label: String, ok: Boolean, action: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (ok) "완료" else "필요",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier
            .weight(1f)
            .padding(start = 8.dp))
        if (!ok) TextButton(onClick = onClick, enabled = enabled) { Text(action) }
    }
}
