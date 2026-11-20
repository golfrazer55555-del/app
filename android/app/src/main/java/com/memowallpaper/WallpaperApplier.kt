package com.memowallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.service.quicksettings.TileService
import android.util.DisplayMetrics
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 화면이 닫혀도 끝까지 실행돼야 하는 작업용(배경화면 적용 등) */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

object WallpaperApplier {

    private val lock = Mutex()

    /** 실제 화면 해상도(세로 방향 기준) */
    fun screenSize(context: Context): Pair<Int, Int> {
        val dm = DisplayMetrics()
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        display?.getRealMetrics(dm)
        var w = dm.widthPixels
        var h = dm.heightPixels
        if (w <= 0 || h <= 0) {
            w = 1080
            h = 2400
        }
        return minOf(w, h) to maxOf(w, h)
    }

    /**
     * 메모를 배경화면으로 적용합니다. 호출한 화면이 사라져도 적용은 끝까지 진행돼요.
     */
    suspend fun apply(context: Context, memo: Memo, target: Target): Result<Unit> {
        val app = context.applicationContext
        return AppScope.async { lock.withLock { applyNow(app, memo, target) } }.await()
    }

    private fun applyNow(context: Context, memo: Memo, target: Target): Result<Unit> = runCatching {
        val wm = WallpaperManager.getInstance(context)
        check(wm.isWallpaperSupported) { "이 기기는 배경화면 변경을 지원하지 않아요." }
        check(wm.isSetWallpaperAllowed) { "기기 정책 때문에 배경화면을 바꿀 수 없어요." }

        val (w, h) = screenSize(context)
        val bmp = WallpaperRenderer.render(memo, w, h, ImageStore.assets(context))
        val flags = when (target) {
            Target.LOCK -> WallpaperManager.FLAG_LOCK
            Target.HOME -> WallpaperManager.FLAG_SYSTEM
            Target.BOTH -> WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
        }
        try {
            val id = wm.setBitmap(bmp, null, true, flags)
            check(id != 0) { "배경화면을 설정하지 못했어요." }
        } finally {
            bmp.recycle()
        }

        val store = MemoStore.get(context)
        store.markApplied(if (store.find(memo.id) != null) memo.id else null)
        TileService.requestListeningState(context, ComponentName(context, MemoTileService::class.java))
        Unit
    }
}
