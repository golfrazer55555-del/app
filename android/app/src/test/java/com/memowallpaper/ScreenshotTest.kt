package com.memowallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.memowallpaper.ui.DrawScreen
import com.memowallpaper.ui.EditorScreen
import com.memowallpaper.ui.HomeScreen
import com.memowallpaper.ui.MemoTheme
import com.memowallpaper.ui.ScheduleScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 기능 확인 + 렌더링 결과와 화면을 PNG로 남기는 테스트.
 * 결과 이미지: app/build/screenshots/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun captureScreen(): Bitmap {
        val v = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        return bmp
    }

    private fun save(bmp: Bitmap, name: String) {
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 테스트용 '사진'(그라데이션)과 '그림'(동그라미)을 앱 저장소에 만듦 */
    private fun makeMedia(): Pair<String, String> {
        val photo = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        Canvas(photo).drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, 1600f, 1200f, Color.rgb(255, 140, 60), Color.rgb(40, 90, 200), Shader.TileMode.CLAMP)
        })
        val photoName = "img_test.jpg"
        ImageStore.file(ctx, photoName).outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 90, it) }

        val drawing = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        Canvas(drawing).drawCircle(820f, 1900f, 140f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            style = Paint.Style.STROKE
            strokeWidth = 24f
        })
        val drawName = "draw_test.png"
        ImageStore.file(ctx, drawName).outputStream().use { drawing.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return photoName to drawName
    }

    // ---------------- 기능 ----------------

    @Test
    fun tokens() {
        val today = LocalDate.of(2026, 11, 20)
        assertEquals("11월 20일 (금)", TextTokens.resolve("{오늘}", today))
        assertEquals("시험 D-35", TextTokens.resolve("시험 {D-day:2026-12-25}", today))
        assertEquals("D-Day", TextTokens.resolve("{D-day:2026-11-20}", today))
        assertEquals("D+3", TextTokens.resolve("{D-day:2026-11-17}", today))
        assertEquals("금연 1일째", TextTokens.resolve("금연 {며칠째:2026-11-20}", today))
        assertEquals("100일째", TextTokens.resolve("{며칠째:2026-08-13}", today))
        assertTrue(TextTokens.hasTokens("a {며칠째:2026-08-13}"))
        assertFalse(TextTokens.hasTokens("{그냥 괄호}"))
    }

    @Test
    fun scheduleOccurrences() {
        // 2026-11-20 = 금요일(5)
        val now = LocalDateTime.of(2026, 11, 20, 10, 0)
        val weekdays = Schedule(hour = 8, minute = 30, memoId = "m", days = setOf(1, 2, 3, 4, 5))
        assertEquals(LocalDateTime.of(2026, 11, 20, 8, 30), Scheduler.latestOccurrence(weekdays, now))
        assertEquals(LocalDateTime.of(2026, 11, 23, 8, 30), Scheduler.nextOccurrence(weekdays, now)) // 다음 월요일
        val evening = Schedule(hour = 21, minute = 0, memoId = "m")
        assertEquals(LocalDateTime.of(2026, 11, 20, 21, 0), Scheduler.nextOccurrence(evening, now))
        assertEquals(LocalDateTime.of(2026, 11, 19, 21, 0), Scheduler.latestOccurrence(evening, now))
        assertEquals(Schedule.fromJson(weekdays.toJson()), weekdays)
    }

    @Test
    fun storeRoundTripAndNoSamples() {
        val store = MemoStore.get(ctx)
        assertTrue(store.memos.value.none { it.title == "외출 전" })
        val before = store.memos.value.size
        val m = Memo(title = "테스트", text = "a\nb", prefix = Prefix.DOT, align = Align.RIGHT, textColor = Color.RED, image = "x.jpg")
        store.upsert(m)
        assertNotNull(store.find(m.id))
        assertEquals(m, Memo.fromJson(m.toJson()))
        store.upsertSchedule(Schedule(memoId = m.id))
        store.delete(m.id)
        assertEquals(before, store.memos.value.size)
        assertTrue(store.schedules.value.none { it.memoId == m.id }) // 메모를 지우면 일정도 삭제
    }

    @Test
    fun widgetText() {
        val m = Memo(text = "약 먹기\n\n시험 {D-day:2099-01-01}", prefix = Prefix.CHECK)
        val t = WidgetUpdater.widgetText(m)
        assertTrue(t.startsWith("□  약 먹기"))
        assertTrue(t.contains("D-"))
    }

    // ---------------- 렌더링 결과 ----------------

    @Test
    fun renderWallpapers() {
        val (photo, drawing) = makeMedia()
        val assets = ImageStore.assets(ctx)
        val samples = listOf(
            "check" to Memo(text = "약 먹기\n지갑 · 열쇠 · 폰 챙기기\n나가기 전에 가스 밸브 확인"),
            "dday" to TEMPLATES.first { it.name == "D-day 카운트" }.make(),
            "bigword" to TEMPLATES.first { it.name.startsWith("큰 글씨") }.make(),
            "photo_drawing" to Memo(
                text = "물 2L 마시기\n스트레칭 10분",
                prefix = Prefix.DOT,
                image = photo,
                imgScale = 1.4f,
                imgX = 0.1f,
                imgDim = 0.3f,
                textBox = true,
                drawing = drawing,
            ),
            "photo_gray_darktext" to Memo(text = "흑백 사진 + 노란 글씨", image = photo, imgGray = true, textColor = 0xFFFDE047.toInt(), align = Align.CENTER, prefix = Prefix.NONE),
        )
        for ((name, memo) in samples) {
            val bmp = WallpaperRenderer.render(memo, 1080, 2400, assets)
            assertEquals(1080, bmp.width)
            save(bmp, "wallpaper_$name")
        }
        // 사진을 넣으면 배경색과 다른 픽셀이 나와야 함
        val p = WallpaperRenderer.render(samples[3].second, 1080, 2400, assets)
        assertNotEquals(samples[3].second.bgColor, p.getPixel(5, 5))
        // 손그림(노란 원)이 위에 그려졌는지
        val ring = p.getPixel(820 + 140, 1900)
        assertTrue(Color.red(ring) > 200 && Color.green(ring) > 200 && Color.blue(ring) < 80)
    }

    @Test
    fun emptyMemoHasNoRows() {
        val l = WallpaperRenderer.layout(Memo(text = "  \n \n"), 1080, 2400)
        assertTrue(l.rows.isEmpty())
    }

    // ---------------- 화면 ----------------

    private fun seedMemos(): List<Memo> {
        val store = MemoStore.get(ctx)
        val (photo, drawing) = makeMedia()
        val list = listOf(
            Memo(title = "출근 전", text = "약 먹기\n지갑 · 열쇠 · 폰"),
            TEMPLATES.first { it.name == "D-day 카운트" }.make(),
            Memo(title = "사진 메모", text = "오늘도 화이팅", prefix = Prefix.NONE, align = Align.CENTER, image = photo, drawing = drawing, textBox = true),
            TEMPLATES.first { it.name.startsWith("며칠째") }.make(),
        )
        list.forEach(store::upsert)
        return list
    }

    @Test
    fun homeScreen() {
        val memos = seedMemos()
        MemoStore.get(ctx).upsertSchedule(Schedule(hour = 21, minute = 0, memoId = memos[0].id))
        compose.setContent { MemoTheme { HomeScreen(onEdit = {}, onNew = {}, onSchedule = {}) } }
        compose.waitForIdle()
        save(captureScreen(), "screen_home")
    }

    @Test
    fun editorScreen() {
        val memos = seedMemos()
        compose.setContent { MemoTheme { EditorScreen(memoId = memos[1].id, initial = null, onClose = {}) } }
        compose.waitForIdle()
        save(captureScreen(), "screen_editor")
    }

    @Test
    fun editorWithPhoto() {
        val memos = seedMemos()
        compose.setContent { MemoTheme { EditorScreen(memoId = memos[2].id, initial = null, onClose = {}) } }
        compose.waitForIdle()
        save(captureScreen(), "screen_editor_photo")
    }

    @Test
    fun scheduleScreen() {
        val memos = seedMemos()
        val store = MemoStore.get(ctx)
        store.upsertSchedule(Schedule(hour = 7, minute = 30, memoId = memos[0].id, days = setOf(1, 2, 3, 4, 5)))
        store.upsertSchedule(Schedule(hour = 21, minute = 0, memoId = memos[1].id))
        compose.setContent { MemoTheme { ScheduleScreen(onBack = {}) } }
        compose.waitForIdle()
        save(captureScreen(), "screen_schedule")
    }

    @Test
    fun drawScreen() {
        val memos = seedMemos()
        compose.setContent { MemoTheme { DrawScreen(memo = memos[0], onCancel = {}, onDone = {}) } }
        compose.waitForIdle()
        save(captureScreen(), "screen_draw")
    }

    @Test
    fun drawAndSave() {
        val memo = Memo(text = "그림 테스트")
        var result: String? = "not-called"
        compose.setContent { MemoTheme { DrawScreen(memo = memo, onCancel = {}, onDone = { result = it }) } }
        compose.onNodeWithTag("drawCanvas").performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.7f), Offset(width * 0.8f, height * 0.8f), durationMillis = 300)
        }
        compose.waitForIdle()
        save(captureScreen(), "screen_draw_stroke")
        compose.onNodeWithText("완료").performClick()
        compose.waitUntil(5000) { result != "not-called" }
        val name = result
        assertNotNull(name)
        val bmp = ImageStore.load(ctx, name!!)
        assertNotNull(bmp)
        // 획이 지나간 자리(가운데쯤)는 불투명, 위쪽 빈 곳은 투명
        val (w, h) = WallpaperApplier.screenSize(ctx)
        assertEquals(w, bmp!!.width)
        assertTrue(Color.alpha(bmp.getPixel(w / 2, (h * 0.75f).toInt())) > 0)
        assertEquals(0, Color.alpha(bmp.getPixel(w / 2, h / 10)))
    }
}
