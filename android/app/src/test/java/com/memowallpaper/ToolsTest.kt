package com.memowallpaper

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.memowallpaper.ui.CounterDetailScreen
import com.memowallpaper.ui.CountersScreen
import com.memowallpaper.ui.MemoTheme
import com.memowallpaper.ui.SettingsScreen
import com.memowallpaper.ui.TimerScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class ToolsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun capture(view: android.view.View, name: String) {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun formatDurations() {
        assertEquals("5:00", formatDuration(300_000))
        assertEquals("1:05:09", formatDuration(3_909_000))
        assertEquals("0:01", formatDuration(400)) // 남은 1초 미만은 1초로 올림
    }

    @Test
    fun volumeButtonsCount() {
        val store = MemoStore.get(ctx)
        val c = Counter(name = "스쿼트", goal = 3)
        store.upsertCounter(c)
        val intent = Intent(ctx, CounterService::class.java).setAction("counter.START").putExtra("counter_id", c.id)
        val controller = Robolectric.buildService(CounterService::class.java, intent).create().startCommand(0, 1)
        val service = controller.get()
        assertEquals(c.id, store.activeCounterId.value)

        repeat(3) { service.volumeProvider.onAdjustVolume(1) }   // 볼륨 ↑ 3번
        service.volumeProvider.onAdjustVolume(-1)               // 볼륨 ↓ 1번(−1)
        assertEquals(2, store.findCounter(c.id)!!.count)
        service.volumeProvider.onAdjustVolume(0)                // 방향 없음은 무시
        assertEquals(2, store.findCounter(c.id)!!.count)

        // 아래 버튼도 +1로 바꾸면
        store.upsertCounter(store.findCounter(c.id)!!.copy(downAction = DownAction.PLUS))
        service.volumeProvider.onAdjustVolume(-1)
        assertEquals(3, store.findCounter(c.id)!!.count)

        // 0 아래로는 안 내려감
        store.upsertCounter(store.findCounter(c.id)!!.copy(count = 0, downAction = DownAction.MINUS))
        service.volumeProvider.onAdjustVolume(-1)
        assertEquals(0, store.findCounter(c.id)!!.count)

        // 포그라운드 알림에 숫자가 보임
        val n = shadowOf(service).lastForegroundNotification
        assertNotNull(n)
        assertTrue(n!!.extras.getString("android.title")!!.startsWith("스쿼트: 0"))

        service.onStartCommand(Intent(ctx, CounterService::class.java).setAction("counter.STOP"), 0, 2)
        assertNull(store.activeCounterId.value)
        store.deleteCounter(c.id)
    }

    @Test
    fun timerFlow() {
        val store = MemoStore.get(ctx)
        val t0 = System.currentTimeMillis()
        val t = TimerManager.createAndStart(ctx, 60_000L, label = "라면")
        var cur = store.findTimer(t.id)!!
        assertEquals(TimerState.RUNNING, cur.state)
        assertTrue(cur.endAt in t0 + 59_000..t0 + 61_000 + 1000)
        // 알람이 예약됨
        val am = ctx.getSystemService(android.app.AlarmManager::class.java)
        assertNotNull(shadowOf(am).peekNextScheduledAlarm())

        TimerManager.pause(ctx, t.id, now = cur.endAt - 30_000)
        cur = store.findTimer(t.id)!!
        assertEquals(TimerState.PAUSED, cur.state)
        assertEquals(30_000, cur.remainingMs)

        TimerManager.addMinute(ctx, t.id)
        assertEquals(90_000, store.findTimer(t.id)!!.remainingMs)

        TimerManager.start(ctx, t.id, now = 1_000_000)
        assertEquals(1_090_000, store.findTimer(t.id)!!.endAt)

        TimerManager.fire(ctx, t.id)
        assertEquals(TimerState.RINGING, store.findTimer(t.id)!!.state)
        val started = shadowOf(ctx as Application).nextStartedService
        assertEquals(RingService::class.java.name, started.component!!.className)

        TimerManager.dismiss(ctx, t.id)
        cur = store.findTimer(t.id)!!
        assertEquals(TimerState.IDLE, cur.state)
        assertEquals(60_000, cur.remainingMs)
        TimerManager.delete(ctx, t.id)
    }

    @Test
    fun customSoundChannels() {
        val store = MemoStore.get(ctx)
        assertTrue(ctx.resources.openRawResource(R.raw.snd_chime).use { it.available() } > 10_000)
        store.placeSound = SoundChoice.CHIME
        val id1 = Sounds.placeChannelId(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch1 = nm.getNotificationChannel(id1)
        assertTrue(ch1.sound.toString().endsWith("raw/snd_chime"))
        assertTrue(ch1.vibrationPattern.contentEquals(Sounds.VIBRATION))

        store.placeSound = SoundChoice.BEEP
        val id2 = Sounds.placeChannelId(ctx)
        assertTrue(id1 != id2)
        assertNull("예전 소리 채널은 지워짐", nm.getNotificationChannel(id1))
        assertTrue(nm.getNotificationChannel(id2).sound.toString().endsWith("raw/snd_beep"))
        store.placeSound = SoundChoice.CHIME
    }

    @Test
    fun screens() {
        val store = MemoStore.get(ctx)
        val squat = Counter(name = "스쿼트", count = 17, goal = 30)
        store.upsertCounter(squat)
        store.upsertCounter(Counter(name = "물 마신 잔", count = 4))
        val running = TimerItem(label = "빨래", durationMs = 40 * 60_000L, state = TimerState.RUNNING, endAt = System.currentTimeMillis() + 23 * 60_000L)
        store.upsertTimer(running)
        store.upsertTimer(TimerItem(label = "라면", durationMs = 4 * 60_000L, state = TimerState.PAUSED, remainingMs = 95_000))
        store.upsertTimer(TimerItem(durationMs = 10 * 60_000L))

        compose.activity.setContent { MemoTheme { CountersScreen(onOpen = {}) } }
        compose.waitForIdle()
        capture(compose.activity.window.decorView, "screen_counters")

        compose.activity.setContent { MemoTheme { CounterDetailScreen(counterId = squat.id, onClose = {}) } }
        compose.waitForIdle()
        capture(compose.activity.window.decorView, "screen_counter_detail")

        compose.activity.setContent { MemoTheme { TimerScreen() } }
        compose.waitForIdle()
        capture(compose.activity.window.decorView, "screen_timer")

        compose.activity.setContent { MemoTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        capture(compose.activity.window.decorView, "screen_settings")

        store.upsertTimer(running.copy(state = TimerState.RINGING))
        val ring = Robolectric.buildActivity(RingActivity::class.java).setup().get()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        capture(ring.window.decorView, "screen_ring")
    }
}
