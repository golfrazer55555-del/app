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
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Timer
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
import com.memowallpaper.ui.CounterDetailScreen
import com.memowallpaper.ui.CountersScreen
import com.memowallpaper.ui.EditorScreen
import com.memowallpaper.ui.HomeScreen
import com.memowallpaper.ui.MemoTheme
import com.memowallpaper.ui.PlaceEditScreen
import com.memowallpaper.ui.PlacesScreen
import com.memowallpaper.ui.ScheduleScreen
import com.memowallpaper.ui.SettingsScreen
import com.memowallpaper.ui.TimerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/** 알림·위젯에서 들어올 때 어디로 갈지 */
data class NavRequest(val editMemo: String? = null, val tab: String? = null, val counter: String? = null)

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_OPEN_COUNTER = "open_counter"
    }

    private val pendingNav = mutableStateOf<NavRequest?>(null)

    private fun navFrom(intent: Intent?): NavRequest? {
        intent ?: return null
        val r = NavRequest(
            editMemo = intent.getStringExtra(WidgetUpdater.EXTRA_EDIT_MEMO_ID),
            tab = intent.getStringExtra(EXTRA_OPEN_TAB),
            counter = intent.getStringExtra(EXTRA_OPEN_COUNTER),
        )
        return if (r == NavRequest()) null else r
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = MemoStore.get(this)
        if (savedInstanceState == null) {
            pendingNav.value = navFrom(intent)
            val app = applicationContext
            AppScope.launch(Dispatchers.IO) {
                // 쓰지 않는 사진·그림 정리, 놓친 자동 변경 처리, 바로가기·위젯·장소 감지·타이머 갱신
                ImageStore.cleanup(app, store.usedMediaFiles())
                Shortcuts.update(app, store.memos.value)
                GeofenceManager.sync(app)
                Scheduler.runDue(app)
            }
            TimerManager.restore(app)
        }
        setContent { MemoTheme { App(pendingNav) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navFrom(intent)?.let { pendingNav.value = it }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    MEMO("메모", Icons.Outlined.StickyNote2),
    SCHEDULE("일정", Icons.Outlined.Event),
    PLACE("장소", Icons.Outlined.Place),
    COUNT("카운트", Icons.Outlined.Exposure),
    TIMER("타이머", Icons.Outlined.Timer),
}

@Composable
private fun App(pendingNav: MutableState<NavRequest?>) {
    var tab by rememberSaveable { mutableStateOf(Tab.MEMO) }
    var editingMemoId by rememberSaveable { mutableStateOf<String?>(null) }
    // 템플릿으로 만든 새 메모의 처음 내용
    var draftJson by rememberSaveable { mutableStateOf<String?>(null) }
    var editingPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var openCounterId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val nav = pendingNav.value
    LaunchedEffect(nav) {
        if (nav == null) return@LaunchedEffect
        nav.tab?.let { name -> Tab.entries.firstOrNull { it.name == name }?.let { tab = it } }
        nav.counter?.let {
            tab = Tab.COUNT
            openCounterId = it
        }
        nav.editMemo?.let {
            editingMemoId = it
            draftJson = null
        }
        pendingNav.value = null
    }

    // ---------- 전체 화면 ----------
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
    openCounterId?.let { id ->
        CounterDetailScreen(counterId = id, onClose = { openCounterId = null })
        return
    }
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
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
                    onSchedule = { tab = Tab.SCHEDULE },
                    onSettings = { showSettings = true },
                )
                Tab.SCHEDULE -> ScheduleScreen()
                Tab.PLACE -> PlacesScreen(
                    onEdit = { editingPlaceId = it },
                    onNew = { editingPlaceId = UUID.randomUUID().toString() },
                )
                Tab.COUNT -> CountersScreen(onOpen = { openCounterId = it })
                Tab.TIMER -> TimerScreen()
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
