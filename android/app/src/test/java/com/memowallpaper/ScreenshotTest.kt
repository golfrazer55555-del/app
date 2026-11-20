package com.memowallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.memowallpaper.ui.EditorScreen
import com.memowallpaper.ui.HomeScreen
import com.memowallpaper.ui.MemoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 렌더링 결과와 화면을 PNG로 남겨 눈으로 확인하기 위한 테스트.
 * 결과: app/build/screenshots/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    /** 액티비티 화면 전체를 비트맵으로 그림 */
    private fun captureScreen(): Bitmap {
        val v = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        return bmp
    }

    private fun save(bmp: Bitmap, name: String) {
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderWallpapers() {
        val samples = listOf(
            "lock_check" to Memo(text = "약 먹기\n지갑 · 열쇠 · 폰 챙기기\n나가기 전에 가스 밸브 확인"),
            "center_num_long" to Memo(
                text = "병원 예약 전화하기 (오후 3시 전에) 그리고 아주아주길어서줄바꿈이필요한단어\n\n택배 반품",
                prefix = Prefix.NUM,
                align = Align.CENTER,
                bgColor = 0xFFFDE68A.toInt(),
            ),
        )
        for ((name, memo) in samples) {
            val bmp = WallpaperRenderer.render(memo, 1080, 2400)
            assertEquals(1080, bmp.width)
            // 배경색이 그대로 칠해졌는지
            assertEquals(memo.bgColor, bmp.getPixel(5, 5))
            save(bmp, "wallpaper_$name")
        }
        val l = WallpaperRenderer.layout(samples[0].second, 1080, 2400)
        assertTrue(l.rows.size >= 3) // 글꼴에 따라 마지막 줄이 줄바꿈될 수 있음
        assertTrue(l.topFraction!! > 0.28f) // 기본 위치는 시계 영역 아래
    }

    @Test
    fun emptyMemoHasNoRows() {
        val l = WallpaperRenderer.layout(Memo(text = "  \n \n"), 1080, 2400)
        assertTrue(l.rows.isEmpty())
    }

    @Test
    fun storeRoundTrip() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = MemoStore.get(ctx)
        assertEquals(3, store.memos.value.size) // 첫 실행 예시 메모
        val m = Memo(title = "테스트", text = "a\nb", prefix = Prefix.DOT, align = Align.RIGHT)
        store.upsert(m)
        assertNotNull(store.find(m.id))
        assertEquals(m, Memo.fromJson(m.toJson()))
        store.delete(m.id)
        assertEquals(3, store.memos.value.size)
    }

    @Test
    fun homeScreen() {
        compose.setContent { MemoTheme { HomeScreen(onEdit = {}, onNew = {}) } }
        compose.waitForIdle()
        save(captureScreen(), "screen_home")
    }

    @Test
    fun editorScreen() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = MemoStore.get(ctx).memos.value.first()
        compose.setContent { MemoTheme { EditorScreen(memoId = first.id, onClose = {}) } }
        compose.waitForIdle()
        save(captureScreen(), "screen_editor")
    }
}
