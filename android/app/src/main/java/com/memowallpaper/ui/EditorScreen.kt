package com.memowallpaper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memowallpaper.Align
import com.memowallpaper.Memo
import com.memowallpaper.MemoStore
import com.memowallpaper.PRESET_COLORS
import com.memowallpaper.Prefix
import com.memowallpaper.WallpaperApplier
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val MemoSaver = Saver<Memo, String>(
    save = { it.toJson().toString() },
    restore = { Memo.fromJson(JSONObject(it)) },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(memoId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MemoStore.get(context) }
    val target by store.target.collectAsStateWithLifecycle()
    val existsInStore = store.find(memoId) != null

    var memo by rememberSaveable(stateSaver = MemoSaver) {
        mutableStateOf(store.find(memoId) ?: Memo(id = memoId, text = ""))
    }
    var textValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(memo.text))
    }
    var showDelete by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }

    val screen = remember { WallpaperApplier.screenSize(context) }
    val ratio = screen.first.toFloat() / screen.second
    val guide = guideFor(target)
    val warning = remember(memo, target) { overlapWarning(memo, screen, guide) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun save(): Boolean {
        if (memo.isBlank()) return false
        store.upsert(memo)
        return true
    }

    fun saveAndClose() {
        save()
        onClose()
    }

    fun applyNow() {
        if (applying) return
        if (!save()) {
            scope.launch { snackbar.showSnackbar("메모를 먼저 적어 주세요") }
            return
        }
        applying = true
        val m = memo
        scope.launch {
            val result = WallpaperApplier.apply(context, m, target)
            applying = false
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                if (result.isSuccess) "${target.label}에 적용했어요"
                else "적용하지 못했어요: ${result.exceptionOrNull()?.message ?: "알 수 없는 오류"}",
            )
        }
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
                    if (existsInStore) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "삭제")
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
                    WallpaperCanvas(
                        memo = memo,
                        guide = guide,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
                if (warning != null) {
                    Row(
                        Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = {
                            val label = todayLabel()
                            val sel = textValue.selection
                            val newText = textValue.text.replaceRange(sel.min, sel.max, label)
                            textValue = TextFieldValue(newText, TextRange(sel.min + label.length))
                            memo = memo.copy(text = newText)
                        },
                        label = { Text("오늘 날짜 넣기") },
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize))
                        },
                    )
                }

                OutlinedTextField(
                    value = memo.title,
                    onValueChange = { memo = memo.copy(title = it) },
                    label = { Text("이름 (선택)") },
                    placeholder = { Text("예) 출근 전") },
                    supportingText = { Text("목록·타일·바로가기에 보이는 이름이에요. 비우면 첫 줄을 써요.") },
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

                Section("글자 크기", trailing = "${(memo.sizePct * 10).roundToInt() / 10f}") {
                    Slider(
                        value = memo.sizePct,
                        onValueChange = { memo = memo.copy(sizePct = (it * 2).roundToInt() / 2f) },
                        valueRange = 3f..14f,
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("굵게", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = memo.bold, onCheckedChange = { memo = memo.copy(bold = it) })
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

private fun todayLabel(): String =
    SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(Date())
