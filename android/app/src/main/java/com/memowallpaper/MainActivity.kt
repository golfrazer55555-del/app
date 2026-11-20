package com.memowallpaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.memowallpaper.ui.EditorScreen
import com.memowallpaper.ui.HomeScreen
import com.memowallpaper.ui.MemoTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 처음 실행 시 바로가기 등록
        Shortcuts.update(this, MemoStore.get(this).memos.value)
        setContent { MemoTheme { App() } }
    }
}

@Composable
private fun App() {
    // 편집 중인 메모 id (없으면 목록 화면)
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val id = editingId
    if (id == null) {
        HomeScreen(
            onEdit = { editingId = it },
            onNew = { editingId = UUID.randomUUID().toString() },
        )
    } else {
        EditorScreen(memoId = id, onClose = { editingId = null })
    }
}
