package com.memowallpaper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.memowallpaper.ui.EditorScreen
import com.memowallpaper.ui.HomeScreen
import com.memowallpaper.ui.MemoTheme
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
                // 쓰지 않는 사진·그림 정리, 놓친 자동 변경 처리, 바로가기·위젯 갱신
                ImageStore.cleanup(app, store.usedMediaFiles())
                Shortcuts.update(app, store.memos.value)
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

private const val HOME = "home"
private const val EDIT = "edit"
private const val SCHEDULE = "schedule"

@Composable
private fun App(pendingEdit: MutableState<String?>) {
    var screen by rememberSaveable { mutableStateOf(HOME) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    // 템플릿으로 만든 새 메모의 처음 내용
    var draftJson by rememberSaveable { mutableStateOf<String?>(null) }

    val pending = pendingEdit.value
    LaunchedEffect(pending) {
        if (pending != null) {
            editingId = pending
            draftJson = null
            screen = EDIT
            pendingEdit.value = null
        }
    }

    when (screen) {
        EDIT -> {
            val id = editingId
            if (id == null) {
                screen = HOME
            } else {
                EditorScreen(
                    memoId = id,
                    initial = draftJson?.let { Memo.fromJson(JSONObject(it)) },
                    onClose = {
                        screen = HOME
                        editingId = null
                        draftJson = null
                    },
                )
            }
        }
        SCHEDULE -> ScheduleScreen(onBack = { screen = HOME })
        else -> HomeScreen(
            onEdit = {
                editingId = it
                draftJson = null
                screen = EDIT
            },
            onNew = { template ->
                editingId = UUID.randomUUID().toString()
                draftJson = template.toJson().toString()
                screen = EDIT
            },
            onSchedule = { screen = SCHEDULE },
        )
    }
}
