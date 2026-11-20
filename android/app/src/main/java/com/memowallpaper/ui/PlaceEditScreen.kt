package com.memowallpaper.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memowallpaper.GeofenceManager
import com.memowallpaper.LocationHelper
import com.memowallpaper.MemoStore
import com.memowallpaper.Place
import com.memowallpaper.PlaceAlerts
import com.memowallpaper.TextTokens
import com.memowallpaper.Trigger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

private val PlaceSaver = Saver<Place, String>(
    save = { it.toJson().toString() },
    restore = { Place.fromJson(JSONObject(it)) },
)

/** 지도를 그릴지(테스트 환경에서는 끌 수 있음) */
var showMapInPlaceEditor = true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditScreen(placeId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MemoStore.get(context) }
    val memos by store.memos.collectAsStateWithLifecycle()
    val target by store.target.collectAsStateWithLifecycle()
    val existing = remember { store.findPlace(placeId) }

    var place by rememberSaveable(stateSaver = PlaceSaver) { mutableStateOf(existing ?: Place(id = placeId)) }
    var recenter by remember { mutableIntStateOf(0) }
    var address by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LocationHelper.Found>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var memoMenu by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun useCurrentLocation() {
        locating = true
        scope.launch {
            val here = LocationHelper.current(context)
            locating = false
            if (here == null) {
                snackbar.showSnackbar("현재 위치를 찾지 못했어요. 위치가 켜져 있는지 확인해 주세요")
            } else {
                place = place.copy(lat = here.first, lon = here.second)
                recenter++
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) useCurrentLocation()
        else scope.launch { snackbar.showSnackbar("위치 권한이 없으면 지도나 주소 검색으로 골라 주세요") }
    }

    fun requestCurrentLocation() {
        if (GeofenceManager.hasForegroundLocation(context)) useCurrentLocation()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // 새 장소면 처음에 현재 위치로
    LaunchedEffect(Unit) {
        if (existing == null && GeofenceManager.hasForegroundLocation(context)) useCurrentLocation()
    }
    // 고른 위치의 주소 보여주기
    LaunchedEffect(place.lat, place.lon) {
        delay(400)
        address = runCatching { LocationHelper.addressOf(context, place.lat, place.lon) }.getOrNull()
    }

    fun search() {
        if (query.isBlank()) return
        searching = true
        scope.launch {
            results = runCatching { LocationHelper.search(context, query) }.getOrDefault(emptyList())
            searching = false
            if (results.isEmpty()) snackbar.showSnackbar("검색 결과가 없어요. 지도에서 직접 눌러 골라도 돼요")
        }
    }

    fun save(): Boolean {
        if (place.message.isBlank() && store.find(place.memoId) == null) {
            scope.launch { snackbar.showSnackbar("알림 문구를 적어 주세요") }
            return false
        }
        val named = if (place.name.isBlank()) place.copy(name = address?.substringAfterLast(' ')?.take(20) ?: "장소") else place
        store.upsertPlace(named)
        return true
    }

    /** 뒤로 가기: 바뀐 게 있으면 저장하고 닫기 */
    fun leave() {
        if (place == existing || (existing == null && place.message.isBlank() && place.name.isBlank())) onClose()
        else if (save()) onClose()
    }

    BackHandler { leave() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "장소 추가" else "장소 편집") },
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "저장하고 돌아가기")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "삭제") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!GeofenceManager.hasNotificationPermission(context)) {
                                scope.launch { snackbar.showSnackbar("알림 권한을 먼저 허용해 주세요(장소 탭 위쪽)") }
                            } else {
                                PlaceAlerts.notify(context, place, place.trigger != Trigger.EXIT, store)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) { Text("알림 미리 보기") }
                    Button(
                        onClick = { if (save()) onClose() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) { Text("저장", fontWeight = FontWeight.Bold) }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            OutlinedTextField(
                value = place.name,
                onValueChange = { place = place.copy(name = it) },
                label = { Text("장소 이름") },
                placeholder = { Text("예) 집, 회사, OO마트") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---------- 위치 ----------
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("위치", style = MaterialTheme.typography.titleSmall)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    if (showMapInPlaceEditor && !LocalInspectionMode.current) {
                        MapPicker(
                            lat = place.lat,
                            lon = place.lon,
                            radius = place.radius,
                            recenterKey = recenter,
                            onPick = { la, lo -> place = place.copy(lat = la, lon = lo) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.Center) { Text("지도") }
                        }
                    }
                    if (locating) CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                Text(
                    address ?: "%.5f, %.5f".format(place.lat, place.lon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "지도를 누르면 그 자리로 핀이 옮겨져요. 두 손가락으로 확대할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = ::requestCurrentLocation, enabled = !locating) {
                    Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp))
                    Text("  지금 있는 곳으로")
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("주소·장소 검색") },
                    placeholder = { Text("예) 서울 중구 세종대로 110") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    trailingIcon = {
                        if (searching) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = ::search) { Icon(Icons.Default.Search, contentDescription = "검색") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                results.forEach { r ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                place = place.copy(lat = r.lat, lon = r.lon)
                                results = emptyList()
                                recenter++
                            },
                    ) {
                        Text(
                            r.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text("반경", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${place.radius}m", style = MaterialTheme.typography.labelLarge)
                }
                Slider(
                    value = place.radius.toFloat(),
                    onValueChange = { place = place.copy(radius = ((it / 50f).roundToInt() * 50).coerceIn(Place.MIN_RADIUS, Place.MAX_RADIUS)) },
                    valueRange = Place.MIN_RADIUS.toFloat()..Place.MAX_RADIUS.toFloat(),
                )
                Text(
                    "휴대폰 위치에는 오차가 있어서 150~300m를 추천해요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("언제 알릴까요?", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Trigger.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = place.trigger == t,
                            onClick = { place = place.copy(trigger = t) },
                            shape = SegmentedButtonDefaults.itemShape(i, Trigger.entries.size),
                            icon = {},
                        ) { Text(t.label) }
                    }
                }
            }

            OutlinedTextField(
                value = place.message,
                onValueChange = { place = place.copy(message = it) },
                label = { Text("알림 문구") },
                placeholder = { Text(if (place.trigger == Trigger.EXIT) "예) 가스밸브 잠갔나요?\n열쇠·지갑 챙기기" else "예) 우유, 계란 사기") },
                supportingText = { Text("${TextTokens.TODAY}, D-day 같은 자동 문구도 쓸 수 있어요") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${target.label}도 같이 바꾸기", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "알림을 놓쳐도 폰을 켜면 보이게 해요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = place.changeWallpaper, onCheckedChange = { place = place.copy(changeWallpaper = it) })
            }
            if (place.changeWallpaper) {
                ExposedDropdownMenuBox(expanded = memoMenu, onExpandedChange = { memoMenu = it }) {
                    OutlinedTextField(
                        value = store.find(place.memoId)?.displayName() ?: "알림 문구 그대로",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("어떤 메모로 바꿀까요?") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memoMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = memoMenu, onDismissRequest = { memoMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("알림 문구 그대로") },
                            onClick = {
                                place = place.copy(memoId = null)
                                memoMenu = false
                            },
                        )
                        memos.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    place = place.copy(memoId = m.id)
                                    memoMenu = false
                                },
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("이 장소 알림 사용", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = place.enabled, onCheckedChange = { place = place.copy(enabled = it) })
            }

            if (!GeofenceManager.ready(context)) {
                Text(
                    "위치 권한이 아직 다 켜지지 않았어요. 저장한 뒤 '장소' 탭 위쪽에서 켜 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("장소 삭제") },
            text = { Text("'${place.displayName()}' 장소 알림을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    store.deletePlace(place.id)
                    showDelete = false
                    onClose()
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("취소") } },
        )
    }
}
