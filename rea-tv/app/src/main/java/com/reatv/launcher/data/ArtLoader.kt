package com.reatv.launcher.data

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Artwork for one tile: a TV banner when the app has one, otherwise its icon. Plus two accent colors. */
@Immutable
class TileArt(
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val primary: Int,
    val secondary: Int,
) {
    val byteSize: Int
        get() = (banner?.asAndroidBitmap()?.allocationByteCount ?: 0) +
            (icon?.asAndroidBitmap()?.allocationByteCount ?: 0)
}

/**
 * Loads and caches tile artwork, sized exactly to the tile so nothing larger than what is on screen
 * stays in memory. The cache is bounded by bytes and dropped when the system asks for memory back,
 * so the streaming app in front always wins.
 */
class ArtLoader(context: Context, private val lowRam: Boolean) {
    private val pm = context.packageManager

    private val maxBytes: Int = run {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val budget = am.memoryClass.toLong() * 1024 * 1024 / (if (lowRam) 12 else 7)
        budget.coerceIn(4L shl 20, 40L shl 20).toInt()
    }

    private val cache = object : LruCache<String, TileArt>(maxBytes) {
        override fun sizeOf(key: String, value: TileArt): Int = max(1, value.byteSize)
    }
    private val heroCache = LruCache<String, TileArt>(2)

    private val dispatcher = Executors.newFixedThreadPool(2) { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "rea-art")
    }.asCoroutineDispatcher()

    fun peek(key: String): TileArt? = cache.get(key)

    fun peekHero(key: String): TileArt? = heroCache.get(key)

    suspend fun tile(app: AppEntry, widthPx: Int, heightPx: Int): TileArt {
        cache.get(app.key)?.let { return it }
        return withContext(dispatcher) {
            cache.get(app.key) ?: render(app, widthPx, heightPx).also { cache.put(app.key, it) }
        }
    }

    suspend fun hero(app: AppEntry, widthPx: Int, heightPx: Int): TileArt {
        heroCache.get(app.key)?.let { return it }
        return withContext(dispatcher) {
            heroCache.get(app.key) ?: render(app, widthPx, heightPx).also { heroCache.put(app.key, it) }
        }
    }

    fun evict(packageName: String) {
        cache.remove(packageName)
        heroCache.remove(packageName)
    }

    fun clear() {
        cache.evictAll()
        heroCache.evictAll()
    }

    fun onTrimMemory(level: Int) {
        @Suppress("DEPRECATION")
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> clear()
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                heroCache.evictAll()
                cache.trimToSize(maxBytes / 3)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                heroCache.evictAll()
                if (lowRam) cache.trimToSize(maxBytes / 2)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> clear()
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                heroCache.evictAll()
                cache.trimToSize(maxBytes / 2)
            }
        }
    }

    private fun render(app: AppEntry, width: Int, height: Int): TileArt {
        if (app.kind == AppEntry.Kind.SystemSettings) {
            return TileArt(null, null, 0xFF5B6270.toInt(), 0xFF22252D.toInt())
        }
        val w = max(16, width)
        val h = max(9, height)
        val component = app.component
        val banner: Drawable? = try {
            if (component != null) pm.getActivityBanner(component) else pm.getApplicationBanner(app.packageName)
        } catch (e: Exception) {
            null
        }
        if (banner != null) {
            val bitmap = drawableToBitmap(banner, w, h, crop = true)
            val (primary, secondary) = accentColors(bitmap)
            return TileArt(bitmap.asImageBitmap(), null, primary, secondary)
        }
        val icon: Drawable = try {
            if (component != null) pm.getActivityIcon(component) else pm.getApplicationIcon(app.packageName)
        } catch (e: Exception) {
            pm.defaultActivityIcon
        }
        val size = max(32, (h * 0.56f).roundToInt())
        val bitmap = drawableToBitmap(icon, size, size, crop = false)
        val (primary, secondary) = accentColors(bitmap)
        return TileArt(null, bitmap.asImageBitmap(), primary, secondary)
    }

    private fun drawableToBitmap(drawable: Drawable, w: Int, h: Int, crop: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val iw = drawable.intrinsicWidth
        val ih = drawable.intrinsicHeight
        if (iw > 0 && ih > 0) {
            val sx = w / iw.toFloat()
            val sy = h / ih.toFloat()
            val scale = if (crop) max(sx, sy) else min(sx, sy)
            val dw = (iw * scale).roundToInt()
            val dh = (ih * scale).roundToInt()
            val left = (w - dw) / 2
            val top = (h - dh) / 2
            drawable.setBounds(left, top, left + dw, top + dh)
        } else {
            drawable.setBounds(0, 0, w, h)
        }
        drawable.draw(canvas)
        return bitmap
    }

    /** Most vivid color and average color, from a 12x8 thumbnail. */
    private fun accentColors(source: Bitmap): Pair<Int, Int> {
        val tw = 12
        val th = 8
        val thumb = Bitmap.createScaledBitmap(source, tw, th, true)
        val pixels = IntArray(tw * th)
        thumb.getPixels(pixels, 0, tw, 0, 0, tw, th)
        if (thumb !== source) thumb.recycle()

        val hsv = FloatArray(3)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        var best = 0
        var bestScore = -1f
        for (p in pixels) {
            if (Color.alpha(p) < 128) continue
            r += Color.red(p)
            g += Color.green(p)
            b += Color.blue(p)
            n++
            Color.colorToHSV(p, hsv)
            val score = hsv[1] * hsv[2]
            if (score > bestScore) {
                bestScore = score
                best = p
            }
        }
        if (n == 0) return 0xFF2A3040.toInt() to 0xFF161A24.toInt()
        val average = Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        val primary = if (bestScore > 0.15f) best or (0xFF shl 24) else average
        return primary to average
    }
}
