package com.memowallpaper

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 렌더러가 사진·그림을 가져오는 통로 */
interface RenderAssets {
    fun bitmap(name: String): Bitmap?

    /** 사진의 평균 색(자동 글자색 계산용) */
    fun averageColor(name: String): Int?

    object None : RenderAssets {
        override fun bitmap(name: String): Bitmap? = null
        override fun averageColor(name: String): Int? = null
    }
}

/**
 * 사진·그림 파일 관리. 파일은 앱 내부 저장소 `files/media/`에 있어요.
 * 메모에는 파일 이름만 저장되고, 어떤 메모도 쓰지 않는 파일은 앱 시작 시 정리돼요.
 */
object ImageStore {
    private const val DIR = "media"
    private const val MAX_IMPORT_SIDE = 2560

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val avgCache = HashMap<String, Int>()

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun assets(context: Context): RenderAssets {
        val app = context.applicationContext
        return object : RenderAssets {
            override fun bitmap(name: String): Bitmap? = load(app, name)
            override fun averageColor(name: String): Int? = average(app, name)
        }
    }

    @Synchronized
    fun load(context: Context, name: String): Bitmap? {
        cache.get(name)?.let { return it }
        val f = file(context, name)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        cache.put(name, bmp)
        return bmp
    }

    @Synchronized
    private fun average(context: Context, name: String): Int? {
        avgCache[name]?.let { return it }
        val bmp = load(context, name) ?: return null
        val one = Bitmap.createScaledBitmap(bmp, 1, 1, true)
        val c = one.getPixel(0, 0)
        if (one != bmp) one.recycle()
        avgCache[name] = c
        return c
    }

    /** 갤러리에서 고른 사진을 앱 안으로 복사(너무 크면 줄임) */
    suspend fun importImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bmp = decodeScaled(context, uri, MAX_IMPORT_SIDE)
        val name = "img_${UUID.randomUUID()}.jpg"
        file(context, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        name
    }

    private fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxSide.toFloat() / maxOf(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("사진을 열 수 없어요")
    }

    /** 손그림을 투명 PNG로 저장 */
    suspend fun saveDrawing(context: Context, bmp: Bitmap): String = withContext(Dispatchers.IO) {
        val name = "draw_${UUID.randomUUID()}.png"
        file(context, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        synchronized(this@ImageStore) { cache.put(name, bmp) }
        name
    }

    /** 어떤 메모에서도 쓰지 않는 파일 삭제 */
    fun cleanup(context: Context, used: Set<String>) {
        dir(context).listFiles()?.forEach { f ->
            if (f.name !in used) f.delete()
        }
    }

    /** 완성된 배경화면을 갤러리(Pictures/깜빡방지)에 저장 */
    suspend fun saveToGallery(context: Context, memo: Memo): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Android 10 이상에서만 지원해요" }
            val (w, h) = WallpaperApplier.screenSize(context)
            val bmp = WallpaperRenderer.render(memo, w, h, assets(context))
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "memo_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/깜빡방지")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("갤러리에 저장할 수 없어요")
            try {
                resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    ?: error("갤러리에 저장할 수 없어요")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            } finally {
                bmp.recycle()
            }
            Unit
        }
    }

    /** 사진 위에 어둡게·흑백을 적용했을 때의 대략적인 평균 색 */
    fun effectiveImageColor(avg: Int, memo: Memo): Int {
        var c = avg or 0xFF000000.toInt()
        if (memo.imgGray) {
            val l = (ColorUtils.calculateLuminance(c) * 255).toInt().coerceIn(0, 255)
            c = android.graphics.Color.rgb(l, l, l)
        }
        return ColorUtils.blendARGB(c, android.graphics.Color.BLACK, memo.imgDim)
    }
}
