package com.memowallpaper.ui

import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memowallpaper.Memo
import com.memowallpaper.MemoStore
import com.memowallpaper.MemoTileService
import com.memowallpaper.MemoWidgetProvider
import com.memowallpaper.Scheduler
import com.memowallpaper.TEMPLATES
import com.memowallpaper.R
import com.memowallpaper.Target
import com.memowallpaper.WallpaperApplier
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onEdit: (String) -> Unit, onNew: (Memo) -> Unit, onSchedule: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MemoStore.get(context) }
    val memos by store.memos.collectAsStateWithLifecycle()
    val appliedId by store.appliedId.collectAsStateWithLifecycle()
    val target by store.target.collectAsStateWithLifecycle()
    val tipDismissed by store.tileTipDismissed.collectAsStateWithLifecycle()
    val screen = remember { WallpaperApplier.screenSize(context) }
    val ratio = screen.first.toFloat() / screen.second

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var applyingId by remember { mutableStateOf<String?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    val schedules by store.schedules.collectAsStateWithLifecycle()

    fun apply(memo: Memo) {
        if (applyingId != null) return
        applyingId = memo.id
        scope.launch {
            val result = WallpaperApplier.apply(context, memo, target)
            applyingId = null
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                if (result.isSuccess) "'${memo.displayName()}' → ${target.label}에 적용했어요"
                else "적용하지 못했어요: ${result.exceptionOrNull()?.message ?: "알 수 없는 오류"}",
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("깜빡 방지", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "사용법")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showTemplates = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("새 메모") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp,
                bottom = pad.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TargetSelector(target, onChange = store::setTarget)
            }
            if (schedules.any { it.enabled }) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val next = Scheduler.nextChange(context)
                    Card(
                        onClick = onSchedule,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("시간대별 자동 변경 켜짐", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (next != null) {
                                Text(
                                    "다음: ${next.second.daysLabel()} ${next.second.timeLabel()} → '${store.find(next.second.memoId)?.displayName()}'",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            if (!tipDismissed) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TileTipCard(
                        onAdd = {
                            requestAddTile(context) { added ->
                                if (added) store.dismissTileTip()
                            }
                        },
                        onAddWidget = { requestAddWidget(context) },
                        onDismiss = store::dismissTileTip,
                    )
                }
            }
            if (memos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "아직 메모가 없어요.\n아래 '새 메모'를 눌러 템플릿을 고르고 만들어 보세요.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 48.dp),
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "카드를 누르면 바로 적용, 길게 누르면 편집",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(memos, key = { it.id }) { memo ->
                MemoCard(
                    memo = memo,
                    ratio = ratio,
                    applied = memo.id == appliedId,
                    busy = memo.id == applyingId,
                    onApply = { apply(memo) },
                    onEdit = { onEdit(memo.id) },
                )
            }
        }
    }

    if (showHelp) HelpDialog(onDismiss = { showHelp = false })

    if (showTemplates) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showTemplates = false }, sheetState = sheet) {
            Text(
                "어떤 메모를 만들까요?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            TEMPLATES.forEach { t ->
                val sample = remember(t) { t.make() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showTemplates = false
                            onNew(t.make())
                        }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WallpaperCanvas(
                        memo = sample,
                        modifier = Modifier
                            .height(76.dp)
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    )
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(t.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSelector(target: Target, onChange: (Target) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("적용할 곳", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Target.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = t == target,
                    onClick = { onChange(t) },
                    shape = SegmentedButtonDefaults.itemShape(i, Target.entries.size),
                ) { Text(t.shortLabel) }
            }
        }
    }
}

@Composable
private fun TileTipCard(onAdd: () -> Unit, onAddWidget: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("더 빠르게 바꾸기", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "알림창 타일로 앱을 열지 않고 메모를 바꾸고, 홈 위젯으로 메모를 보면서 바로 고칠 수 있어요."
                } else {
                    "알림창을 내려 연필(편집) 버튼 → '깜빡 방지' 타일을 끌어다 추가하면 앱을 열지 않고 바꿀 수 있어요. 홈 위젯도 추가해 보세요."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    FilledTonalButton(onClick = onAdd) { Text("타일 추가") }
                }
                FilledTonalButton(onClick = onAddWidget) { Text("위젯 추가") }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoCard(
    memo: Memo,
    ratio: Float,
    applied: Boolean,
    busy: Boolean,
    onApply: () -> Unit,
    onEdit: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.combinedClickable(
            onClickLabel = "적용",
            onLongClickLabel = "편집",
            onClick = onApply,
            onLongClick = onEdit,
        ),
    ) {
        Box {
            WallpaperCanvas(
                memo = memo,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(shape)
                    .border(
                        width = if (applied) 3.dp else 1.dp,
                        color = if (applied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    ),
            )
            FilledTonalIconButton(
                onClick = onEdit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(34.dp),
            ) {
                Icon(Icons.Default.Edit, contentDescription = "편집", modifier = Modifier.size(18.dp))
            }
            if (applied) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("적용 중", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (busy) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(shape)
                        .then(Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(color = Color(0x99000000), shape = RoundedCornerShape(50)) {
                        CircularProgressIndicator(Modifier.padding(10.dp).size(28.dp), color = Color.White, strokeWidth = 3.dp)
                    }
                }
            }
        }
        Text(
            memo.displayName(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (applied) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
        title = { Text("사용법") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpItem("바로 적용", "카드를 누르면 위에서 고른 곳(잠금화면 등)에 바로 적용돼요.")
                HelpItem("편집", "카드를 길게 누르거나 연필 버튼을 눌러요.")
                HelpItem(
                    "알림창 타일",
                    "알림창 빠른 설정에 '깜빡 방지' 타일을 추가하세요. 메모가 2개 이하면 누를 때마다 다음 메모로, 3개 이상이면 목록에서 골라요. 잠긴 상태에서는 다음 메모로 넘어가요.",
                )
                HelpItem("장소 알림", "아래 '장소' 탭에서 집·마트 같은 곳을 등록하면 도착하거나 떠날 때 알림이 와요. 잠금화면도 같이 바꿀 수 있어요.")
                HelpItem("시간대별 자동 변경", "아래 '시간' 탭에서 '오전 7:30엔 출근 전, 오후 9시엔 자기 전'처럼 정해 두면 그 시간에 알아서 바뀌어요.")
                HelpItem("날짜 · D-day · 며칠째", "편집 화면의 버튼으로 넣으면 매일 자정에 숫자가 자동으로 바뀌어요.")
                HelpItem("홈 위젯", "지금 적용된 메모를 보여줘요. 누르면 바로 편집 화면이 열리고, 저장하면 배경화면도 같이 바뀌어요.")
                HelpItem("사진 · 그림", "편집 화면 아래쪽에서 갤러리 사진을 배경으로 넣거나 직접 그림을 그릴 수 있어요.")
                HelpItem("앱 아이콘 바로가기", "앱 아이콘을 길게 누르면 앞쪽 메모 4개가 나와요. 눌러서 바로 적용하거나 홈 화면에 끌어다 둘 수 있어요.")
                HelpItem(
                    "잠금화면이 안 바뀌면 (갤럭시)",
                    "설정 → 잠금화면 → 배경화면 서비스(다이내믹 잠금화면 등)를 '사용 안 함'으로 바꿔 주세요.",
                )
            }
        },
    )
}

@Composable
private fun HelpItem(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 홈 화면에 위젯 추가 요청 */
private fun requestAddWidget(context: android.content.Context) {
    val mgr = AppWidgetManager.getInstance(context) ?: return
    if (mgr.isRequestPinAppWidgetSupported) {
        mgr.requestPinAppWidget(ComponentName(context, MemoWidgetProvider::class.java), null, null)
    } else {
        android.widget.Toast.makeText(context, "홈 화면 빈 곳을 길게 누르고 '위젯'에서 '깜빡 방지'를 추가해 주세요", android.widget.Toast.LENGTH_LONG).show()
    }
}

/** Android 13+에서 빠른 설정 타일 추가 요청 */
private fun requestAddTile(context: android.content.Context, onResult: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val sbm = context.getSystemService(StatusBarManager::class.java) ?: return
    sbm.requestAddTileService(
        ComponentName(context, MemoTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_tile),
        context.mainExecutor,
    ) { result ->
        onResult(
            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
        )
    }
}
