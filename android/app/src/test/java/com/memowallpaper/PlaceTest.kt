package com.memowallpaper

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.Geofence
import com.memowallpaper.ui.MemoTheme
import com.memowallpaper.ui.PlaceEditScreen
import com.memowallpaper.ui.PlacesScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class PlaceTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun capture(name: String) {
        val v = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun placeJsonAndGeofence() {
        val p = Place(name = "마트", lat = 37.5, lon = 127.0, radius = 250, trigger = Trigger.BOTH, message = "우유 사기", changeWallpaper = true)
        assertEquals(p, Place.fromJson(p.toJson()))
        val g = GeofenceManager.buildGeofence(p)
        assertEquals(p.id, g.requestId)
        assertEquals(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT, g.transitionTypes)
        // 연결된 메모가 없으면 알림 문구로 즉석 메모
        val adHoc = p.wallpaperMemo(MemoStore.get(ctx))!!
        assertEquals("우유 사기", adHoc.text)
    }

    @Test
    fun alertRulesAndCooldown() = runBlocking {
        shadowOf(ctx as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val store = MemoStore.get(ctx)
        val p = Place(name = "집", trigger = Trigger.EXIT, message = "가스밸브 확인\n열쇠 챙기기")
        store.upsertPlace(p)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val before = shadowOf(nm).allNotifications.size

        assertFalse("떠날 때만 알리는 장소는 도착 때 안 알림", PlaceAlerts.handle(ctx, p.id, entering = true))
        assertTrue(PlaceAlerts.handle(ctx, p.id, entering = false))
        assertEquals(before + 1, shadowOf(nm).allNotifications.size)
        val n = shadowOf(nm).allNotifications.last()
        assertEquals("📍 집에서 나왔어요", n.extras.getString("android.title"))
        assertFalse("15분 안에는 다시 안 알림", PlaceAlerts.handle(ctx, p.id, entering = false))
        assertTrue("15분 지나면 다시 알림", PlaceAlerts.handle(ctx, p.id, entering = false, now = System.currentTimeMillis() + 16 * 60_000))

        store.upsertPlace(p.copy(enabled = false))
        assertFalse(PlaceAlerts.handle(ctx, p.id, entering = false, now = System.currentTimeMillis() + 60 * 60_000))
        store.deletePlace(p.id)
    }

    @Test
    fun deletingMemoUnlinksPlace() {
        val store = MemoStore.get(ctx)
        val m = Memo(text = "장보기")
        store.upsert(m)
        val p = Place(name = "마트", memoId = m.id, changeWallpaper = true)
        store.upsertPlace(p)
        store.delete(m.id)
        assertNull(store.findPlace(p.id)!!.memoId)
        store.deletePlace(p.id)
    }

    @Test
    fun screens() {
        val store = MemoStore.get(ctx)
        store.upsert(Memo(title = "출근 전", text = "약 먹기\n지갑 · 열쇠 · 폰"))
        val home = Place(name = "집", trigger = Trigger.EXIT, message = "가스밸브 확인\n열쇠 챙기기", changeWallpaper = true)
        store.upsertPlace(home)
        store.upsertPlace(Place(name = "OO마트", trigger = Trigger.ENTER, radius = 150, message = "우유, 계란 사기"))
        compose.waitForIdle()
        capture("app_memo_tab")

        compose.activity.runOnUiThread { }
        compose.activity.setContent { MemoTheme { PlacesScreen(onEdit = {}, onNew = {}) } }
        compose.waitForIdle()
        capture("screen_places")

        compose.activity.setContent { MemoTheme { PlaceEditScreen(placeId = home.id, onClose = {}) } }
        compose.waitForIdle()
        capture("screen_place_edit")
    }
}
