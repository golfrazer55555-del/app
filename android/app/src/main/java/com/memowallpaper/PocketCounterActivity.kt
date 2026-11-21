package com.memowallpaper

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memowallpaper.ui.MemoTheme
import kotlinx.coroutines.delay

/**
 * 주머니 모드: 화면을 검게 켜 둔 채 볼륨 버튼으로 세기.
 * 화면에 떠 있는 앱이 볼륨 버튼을 가장 먼저 받기 때문에, 음악을 듣는 중에도 확실히 세져요.
 * 주머니 속 오터치를 막으려고 화면 터치는 무시하고, 1.5초 길게 눌러야 '끝내기'가 나와요.
 */
class PocketCounterActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_ID = "counter_id"

        fun start(context: Context, counterId: String) {
            context.startActivity(
                Intent(context, PocketCounterActivity::class.java)
                    .putExtra(EXTRA_ID, counterId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private var counterId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        counterId = intent?.getStringExtra(EXTRA_ID)
        val store = MemoStore.get(this)
        if (store.findCounter(counterId) == null) {
            finish()
            return
        }

        // 화면 꺼짐 방지 + 최저 밝기 + 잠금화면 위에서도 보이기
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(true)
        // 전체 화면(가장자리를 스쳐도 바로 나가지 않게)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MemoTheme {
                val counters by store.counters.collectAsStateWithLifecycle()
                val c = counters.firstOrNull { it.id == counterId }
                var showExit by remember { mutableStateOf(false) }
                LaunchedEffect(showExit) {
                    if (showExit) {
                        delay(4000)
                        showExit = false
                    }
                }
                // 뒤로 가기(실수로 스친 제스처)로는 안 나가고 끝내기 버튼만 보여줌
                BackHandler { showExit = true }

                // 번인 방지: 숫자 위치를 조금씩 옮김
                val shift = ((c?.count ?: 0) % 7 - 3) * 10

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { showExit = true })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(x = shift.dp, y = (shift * 2).dp),
                    ) {
                        Text(
                            "${c?.count ?: 0}",
                            color = Color(0xFF4A4A4A),
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            c?.displayName() ?: "",
                            color = Color(0xFF333333),
                            fontSize = 18.sp,
                        )
                        if (c != null && c.goal > 0) {
                            Text("목표 ${c.goal}", color = Color(0xFF333333), fontSize = 14.sp)
                        }
                    }
                    Text(
                        "볼륨 ↑ +1 · 볼륨 ↓ ${if (c?.downAction == DownAction.PLUS) "+1" else "−1"}\n" +
                            "화면 터치는 무시돼요 · 1.5초 길게 누르면 끝내기",
                        color = Color(0xFF2A2A2A),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp),
                    )
                    if (showExit) {
                        Column(
                            Modifier
                                .align(Alignment.Center)
                                .background(Color(0xFF111111))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("주머니 모드를 끝낼까요?", color = Color.White, fontSize = 18.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { showExit = false }, modifier = Modifier.height(52.dp)) {
                                    Text("계속 세기", color = Color.White)
                                }
                                Button(
                                    onClick = { finish() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .width(120.dp),
                                ) { Text("끝내기", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val id = counterId
        if (id != null && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            // 꾹 누르고 있을 때 반복 입력은 무시(한 번 누름 = 한 번)
            if (event.repeatCount == 0) CounterLogic.press(this, id, up = keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return true
        return super.onKeyUp(keyCode, event)
    }
}
