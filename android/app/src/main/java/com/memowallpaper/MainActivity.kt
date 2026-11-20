package com.memowallpaper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.memowallpaper.ui.EditorScreen
import com.memowallpaper.ui.HomeScreen
import com.memowallpaper.ui.MemoTheme
import com.memowallpaper.ui.PlaceEditScreen
import com.memowallpaper.ui.PlacesScreen
import com.memowallpaper.ui.ScheduleScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {

    /** 위젯에서 눌러 들어온 경우 편집할 메모 */
    private val pendingEdit = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = MemoStore.get(this)
        if (savedInstanceState == null) {
            pendingEdit.value = intent?.getStringExtra(WidgetUpdater.EXTRA_EDIT_MEMO_ID)
            val app = applicationContext
            AppScope.launch(Dispatchers.IO) {
                // 쓰지 않는 사진·그림 정리, 놓친 자동 변경 처리, 바로가기·위젯·장소 감지 갱신
                ImageStore.cleanup(app, store.usedMediaFiles())
                Shortcuts.update(app, store.memos.value)
                GeofenceManager.sync(app)
                Scheduler.runDue(app)
            }
        }
        setContent { MemoTheme { App(pendingEdit) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(WidgetUpdater.EXTRA_EDIT_MEMO_ID)?.let { pendingEdit.value = it }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    MEMO("메모", Icons.Default.Edit),
    TIME("시간", Icons.Default.DateRange),
    PLACE("장소", Icons.Default.LocationOn),
}

@Composable
private fun App(pendingEdit: MutableState<String?>) {
    var tab by rememberSaveable { mutableStateOf(Tab.MEMO) }
    var editingMemoId by rememberSaveable { mutableStateOf<String?>(null) }
    // 템플릿으로 만든 새 메모의 처음 내용
    var draftJson by rememberSaveable { mutableStateOf<String?>(null) }
    var editingPlaceId by rememberSaveable { mutableStateOf<String?>(null) }

    val pending = pendingEdit.value
    LaunchedEffect(pending) {
        if (pending != null) {
            editingMemoId = pending
            draftJson = null
            pendingEdit.value = null
        }
    }

    // ---------- 전체 화면(편집) ----------
    editingMemoId?.let { id ->
        EditorScreen(
            memoId = id,
            initial = draftJson?.let { Memo.fromJson(JSONObject(it)) },
            onClose = {
                editingMemoId = null
                draftJson = null
            },
        )
        return
    }
    editingPlaceId?.let { id ->
        PlaceEditScreen(placeId = id, onClose = { editingPlaceId = null })
        return
    }

    // ---------- 탭 ----------
    if (tab != Tab.MEMO) BackHandler { tab = Tab.MEMO }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            when (tab) {
                Tab.MEMO -> HomeScreen(
                    onEdit = {
                        editingMemoId = it
                        draftJson = null
                    },
                    onNew = { template ->
                        draftJson = template.toJson().toString()
                        editingMemoId = UUID.randomUUID().toString()
                    },
                    onSchedule = { tab = Tab.TIME },
                )
                Tab.TIME -> ScheduleScreen()
                Tab.PLACE -> PlacesScreen(
                    onEdit = { editingPlaceId = it },
                    onNew = { editingPlaceId = UUID.randomUUID().toString() },
                )
            }
        }
        NavigationBar {
            Tab.entries.forEach { t ->
                NavigationBarItem(
                    selected = tab == t,
                    onClick = { tab = t },
                    icon = { Icon(t.icon, contentDescription = null) },
                    label = { Text(t.label) },
                )
            }
        }
    }
}
