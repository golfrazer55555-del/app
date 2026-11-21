package com.memowallpaper

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memowallpaper.ui.MemoTheme

/** 타이머가 끝났을 때 잠금화면 위에 뜨는 화면 */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val store = MemoStore.get(this)
        setContent {
            MemoTheme {
                val timers by store.timers.collectAsStateWithLifecycle()
                val ringing = timers.filter { it.state == TimerState.RINGING }
                LaunchedEffect(ringing.isEmpty()) { if (ringing.isEmpty()) finish() }
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF7F1D1D))
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("⏰", fontSize = 72.sp)
                    Text("시간 끝!", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(12.dp))
                    ringing.forEach { t ->
                        Text(
                            t.displayName(),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(48.dp))
                    Button(
                        onClick = { ringing.forEach { TimerManager.dismiss(this@RingActivity, it.id) } },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF7F1D1D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                    ) { Text("끄기", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(16.dp))
                    Row {
                        OutlinedButton(
                            onClick = { ringing.forEach { TimerManager.addMinute(this@RingActivity, it.id) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) { Text("1분 더", color = Color.White, fontSize = 18.sp) }
                    }
                }
            }
        }
    }
}
