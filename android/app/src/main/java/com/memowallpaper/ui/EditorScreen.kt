package com.memowallpaper.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memowallpaper.AppScope
import com.memowallpaper.Align
import com.memowallpaper.ImageStore
import com.memowallpaper.Memo
import com.memowallpaper.MemoStore
import com.memowallpaper.PRESET_COLORS
import com.memowallpaper.Prefix
import com.memowallpaper.TEXT_COLORS
import com.memowallpaper.TextTokens
import com.memowallpaper.WallpaperApplier
import com.memowallpaper.WallpaperRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

val MemoSaver = Saver<Memo, String>(
    save = { it.toJson().toString() },
    restore = { Memo.fromJson(JSONObject(it)) },
)

/** 메모 안의 날짜 문구 하나(D-day / 며칠째) */
private data class DateToken(val raw: String, val kind: DateKind, val date: LocalDate)

private enum class DateKind(val label: String) { DDAY("D-day (남은 날)"), COUNT_UP("며칠째 (지난 날)") }

private val DATE_TOKEN = Regex("""\{(D-day|며칠째):(\d{4}-\d{2}-\d{2})\}""")
private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)

private fun dateTokens(text: String): List<DateToken> = DATE_TOKEN.findAll(text).mapNotNull { m ->
    runCatching {
        DateToken(
            raw = m.value,
            kind = if (m.groupValues[1] == "D-day") DateKind.DDAY else DateKind.COUNT_UP,
            date = LocalDate.parse(m.groupValues[2]),
        )
    }.getOrNull()
}.distinctBy { it.raw }.toList()

private fun tokenFor(kind: DateKind, date: LocalDate) =
    if (kind == DateKind.DDAY) TextTokens.dday(date) else TextTokens.countUp(date)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(memoId: String, initial: Memo?, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MemoStore.get(context) }
    val target by store.target.collectAsStateWithLifecycle()
    val appliedId by store.appliedId.collectAsStateWithLifecycle()
    val original = remember { store.find(memoId) }
    /** 배경화면에 마지막으로 반영된 내용(바뀌었을 때만 다시 적용) */
    var lastApplied by remember { mutableStateOf(original) }
    val existsInStore = store.find(memoId) != null

    var memo by rememberSaveable(stateSaver = MemoSaver) {
        mutableStateOf(original ?: initial?.copy(id = memoId) ?: Memo(id = memoId, text = ""))
    }
    var textValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(memo.text))
    }
    var drawingMode by rememberSaveable { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    /** 날짜 고르기: 새로 넣기(editRaw=null) 또는 기존 문구 바꾸기 */
    var datePick by remember { mutableStateOf<Pair<DateKind, String?>?>(null) }

    val screen = remember { WallpaperApplier.screenSize(context) }
    val ratio = screen.first.toFloat() / screen.second
    val guide = guideFor(target)
    val warning = remember(memo, target) { overlapWarning(memo, screen, guide) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun setText(newText: String, cursor: Int = newText.length) {
        textValue = TextFieldValue(newText, TextRange(cursor.coerceIn(0, newText.length)))
        memo = memo.copy(text = newText)
    }

    fun insertAtCursor(s: String) {
        val sel = textValue.selection
        val newText = textValue.text.replaceRange(sel.min, sel.max, s)
        setText(newText, sel.min + s.length)
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val r = runCatching { ImageStore.importImage(context, uri) }
            importing = false
            r.onSuccess { name ->
                memo = memo.copy(image = name, imgScale = 1f, imgX = 0f, imgY = 0f)
            }.onFailure {
                snackbar.showSnackbar("사진을 불러오지 못했어요: ${it.message ?: "알 수 없는 오류"}")
            }
        }
    }

    /** 저장. 지금 배경화면으로 쓰는 메모가 바뀌었으면 배경화면도 다시 만들어요. */
    fun save(): Boolean {
        if (memo.isBlank()) return false
        store.upsert(memo)
        if (memo.id == appliedId && memo != lastApplied) {
            lastApplied = memo
            val m = memo
            val t = target
            val app = context.applicationContext
            AppScope.launch {
                val r = WallpaperApplier.apply(app, m, t)
                withContext(Dispatchers.Main) {
                    if (r.isSuccess) Toast.makeText(app, "배경화면도 새로 바꿨어요", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
    }

    fun saveAndClose() {
        save()
        onClose()
    }

    fun applyNow() {
        if (applying) return
        if (memo.isBlank()) {
            scope.launch { snackbar.showSnackbar("메모를 먼저 적어 주세요") }
            return
        }
        store.upsert(memo)
        applying = true
        val m = memo
        scope.launch {
            val result = WallpaperApplier.apply(context, m, target)
            applying = false
            if (result.isSuccess) lastApplied = m
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                if (result.isSuccess) "${target.label}에 적용했어요"
                else "적용하지 못했어요: ${result.exceptionOrNull()?.message ?: "알 수 없는 오류"}",
            )
        }
    }

    // ---------- 그림 그리기 화면 ----------
    if (drawingMode) {
        DrawScreen(
            memo = memo,
            onCancel = { drawingMode = false },
            onDone = { name ->
                memo = memo.copy(drawing = name)
                drawingMode = false
            },
        )
        return
    }

    BackHandler { saveAndClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existsInStore) "메모 편집" else "새 메모") },
                navigationIcon = {
                    IconButton(onClick = ::saveAndClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "저장하고 돌아가기")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "더 보기")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("갤러리에 이미지로 저장") },
                                onClick = {
                                    showMenu = false
                                    val m = memo
                                    scope.launch {
                                        val r = ImageStore.saveToGallery(context, m)
                                        snackbar.showSnackbar(
                                            if (r.isSuccess) "갤러리(Pictures/메모배경)에 저장했어요"
                                            else "저장하지 못했어요: ${r.exceptionOrNull()?.message}",
                                        )
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("복제") },
                                enabled = !memo.isBlank(),
                                onClick = {
                                    showMenu = false
                                    save()
                                    store.upsert(
                                        memo.copy(id = UUID.randomUUID().toString(), title = "${memo.displayName()} 복사"),
                                    )
                                    scope.launch { snackbar.showSnackbar("복제했어요. 목록에서 볼 수 있어요") }
                                },
                            )
                            if (existsInStore) {
                                DropdownMenuItem(
                                    text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showDelete = true
                                    },
                                )
                            }
                        }
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
                        onClick = ::saveAndClose,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) { Text("저장") }
                    Button(
                        onClick = ::applyNow,
                        enabled = !applying,
                        modifier = Modifier
                            .weight(1.8f)
                            .height(52.dp),
                    ) {
                        if (applying) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("${target.shortLabel}에 적용", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding(),
        ) {
            // ---------- 미리보기 ----------
            val previewHeight = if (WindowInsets.isImeVisible) 160.dp else 340.dp
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.height(previewHeight)) {
                    val imageName = memo.image
                    WallpaperCanvas(
                        memo = memo,
                        guide = guide,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(14.dp))
                            .pointerInput(imageName) {
                                if (imageName == null) return@pointerInput
                                // 사진: 두 손가락으로 확대, 끌어서 이동
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val m = memo
                                    val img = ImageStore.load(context, imageName) ?: return@detectTransformGestures
                                    val moved = m.copy(
                                        imgScale = (m.imgScale * zoom).coerceIn(1f, 6f),
                                        imgX = m.imgX + pan.x / size.width,
                                        imgY = m.imgY + pan.y / size.height,
                                    )
                                    val (x, y) = WallpaperRenderer.clampImageOffset(moved, img.width, img.height, size.width, size.height)
                                    memo = moved.copy(imgX = x, imgY = y)
                                }
                            },
                    )
                    if (importing) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
                if (warning != null) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            " $warning",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            HorizontalDivider()

            // ---------- 설정 ----------
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // 글
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = {
                            textValue = it
                            memo = memo.copy(text = it.text)
                        },
                        label = { Text("메모") },
                        placeholder = { Text("한 줄에 하나씩 적어요\n예) 약 먹기") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { insertAtCursor(TextTokens.TODAY) },
                            label = { Text("오늘 날짜") },
                            leadingIcon = {
                                Icon(Icons.Default.DateRange, null, Modifier.size(AssistChipDefaults.IconSize))
                            },
                        )
                        AssistChip(
                            onClick = { datePick = DateKind.DDAY to null },
                            label = { Text("D-day") },
                            leadingIcon = {
                                Icon(Icons.Default.Star, null, Modifier.size(AssistChipDefaults.IconSize))
                            },
                        )
                        AssistChip(
                            onClick = { datePick = DateKind.COUNT_UP to null },
                            label = { Text("며칠째") },
                            leadingIcon = {
                                Icon(Icons.Default.Star, null, Modifier.size(AssistChipDefaults.IconSize))
                            },
                        )
                    }
                    Text(
                        "날짜·D-day·며칠째 문구는 매일 자정에 배경화면과 위젯에서 자동으로 바뀌어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 들어 있는 날짜 문구 목록(날짜 바꾸기)
                    dateTokens(memo.text).forEach { tok ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    val now = if (tok.kind == DateKind.DDAY) TextTokens.ddayLabel(tok.date) else TextTokens.countUpLabel(tok.date)
                                    Text(
                                        "${if (tok.kind == DateKind.DDAY) "D-day" else "며칠째"} · 오늘은 $now",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        (if (tok.kind == DateKind.DDAY) "목표일 " else "시작일 ") + tok.date.format(FULL_DATE),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = { datePick = tok.kind to tok.raw }) { Text("날짜 바꾸기") }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = memo.title,
                    onValueChange = { memo = memo.copy(title = it) },
                    label = { Text("이름 (선택)") },
                    placeholder = { Text("예) 출근 전") },
                    supportingText = { Text("목록·타일·위젯·바로가기에 보이는 이름이에요. 비우면 첫 줄을 써요.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Section("줄 앞 기호") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Prefix.entries.forEachIndexed { i, p ->
                            SegmentedButton(
                                selected = memo.prefix == p,
                                onClick = { memo = memo.copy(prefix = p) },
                                shape = SegmentedButtonDefaults.itemShape(i, Prefix.entries.size),
                                icon = {},
                            ) { Text(p.label) }
                        }
                    }
                }

                Section("글자 크기", trailing = "${(memo.sizePct * 10).roundToInt() / 10f}") {
                    Slider(
                        value = memo.sizePct,
                        onValueChange = { memo = memo.copy(sizePct = (it * 2).roundToInt() / 2f) },
                        valueRange = Memo.MIN_SIZE..Memo.MAX_SIZE,
                    )
                }

                Section("글자색") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TEXT_COLORS.forEach { (label, c) ->
                            FilterChip(
                                selected = memo.textColor == c,
                                onClick = { memo = memo.copy(textColor = c) },
                                label = { Text(label) },
                                leadingIcon = if (c == null) null else {
                                    {
                                        Box(
                                            Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color(c))
                                                .border(1.dp, Color(0x55888888), CircleShape),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                SwitchRow("굵게", memo.bold) { memo = memo.copy(bold = it) }
                SwitchRow("글자 뒤 반투명 상자", memo.textBox, "사진이나 그림 위에서 글자가 잘 보이게 해요") {
                    memo = memo.copy(textBox = it)
                }

                Section("정렬") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Align.entries.forEachIndexed { i, a ->
                            SegmentedButton(
                                selected = memo.align == a,
                                onClick = { memo = memo.copy(align = a) },
                                shape = SegmentedButtonDefaults.itemShape(i, Align.entries.size),
                                icon = {},
                            ) { Text(a.label) }
                        }
                    }
                }

                val posLabel = when {
                    memo.posPct < 40 -> "위쪽"
                    memo.posPct > 60 -> "아래쪽"
                    else -> "가운데"
                }
                Section("세로 위치", trailing = posLabel) {
                    Slider(
                        value = memo.posPct,
                        onValueChange = { memo = memo.copy(posPct = it.roundToInt().toFloat()) },
                        valueRange = 10f..90f,
                    )
                }

                HorizontalDivider()

                // 배경
                Section("배경색") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PRESET_COLORS.forEach { c ->
                            ColorSwatch(c, selected = memo.bgColor == c) { memo = memo.copy(bgColor = c) }
                        }
                        CustomColorButton(selected = memo.bgColor !in PRESET_COLORS) { showPicker = true }
                    }
                }

                Section("배경 사진") {
                    if (memo.image == null) {
                        OutlinedButton(
                            onClick = {
                                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !importing,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("갤러리에서 사진 넣기") }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "미리보기에서 두 손가락으로 벌려 확대하고, 끌어서 위치를 맞춰요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }) { Text("사진 바꾸기") }
                                TextButton(onClick = { memo = memo.copy(imgScale = 1f, imgX = 0f, imgY = 0f) }) {
                                    Text("위치 초기화")
                                }
                                TextButton(onClick = { memo = memo.copy(image = null) }) {
                                    Text("사진 빼기", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("어둡게", style = MaterialTheme.typography.labelLarge)
                                Slider(
                                    value = memo.imgDim,
                                    onValueChange = { memo = memo.copy(imgDim = it) },
                                    valueRange = 0f..0.8f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp),
                                )
                            }
                            SwitchRow("흑백", memo.imgGray) { memo = memo.copy(imgGray = it) }
                        }
                    }
                }

                Section("그림") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { drawingMode = true }) {
                            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                            Text(if (memo.drawing == null) "  그림 그리기" else "  그림 고치기")
                        }
                        if (memo.drawing != null) {
                            TextButton(onClick = { memo = memo.copy(drawing = null) }) {
                                Text("그림 지우기", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = memo.bgColor,
            onDismiss = { showPicker = false },
            onPick = {
                memo = memo.copy(bgColor = it)
                showPicker = false
            },
        )
    }

    datePick?.let { (initialKind, editRaw) ->
        val existing = editRaw?.let { raw -> dateTokens(memo.text).firstOrNull { it.raw == raw } }
        val startDate = existing?.date ?: if (initialKind == DateKind.DDAY) LocalDate.now().plusDays(7) else LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        var kind by remember(editRaw, initialKind) { mutableStateOf(initialKind) }
        DatePickerDialog(
            onDismissRequest = { datePick = null },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        val token = tokenFor(kind, date)
                        if (editRaw != null) {
                            setText(memo.text.replace(editRaw, token), textValue.selection.min)
                        } else {
                            insertAtCursor(token)
                        }
                    }
                    datePick = null
                }) { Text(if (editRaw != null) "바꾸기" else "넣기") }
            },
            dismissButton = { TextButton(onClick = { datePick = null }) { Text("취소") } },
        ) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                DateKind.entries.forEachIndexed { i, k ->
                    SegmentedButton(
                        selected = kind == k,
                        onClick = { kind = k },
                        shape = SegmentedButtonDefaults.itemShape(i, DateKind.entries.size),
                        icon = {},
                    ) { Text(k.label, style = MaterialTheme.typography.labelMedium) }
                }
            }
            DatePicker(
                state = state,
                showModeToggle = false,
                title = {
                    Text(
                        if (kind == DateKind.DDAY) "목표일을 골라요" else "시작일을 골라요 (그날이 1일째)",
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp),
                    )
                },
            )
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("메모 삭제") },
            text = { Text("'${memo.displayName()}' 메모를 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(memo.id)
                    showDelete = false
                    onClose()
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Section(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}
