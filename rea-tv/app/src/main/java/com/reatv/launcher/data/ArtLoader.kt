package com.reatv.launcher.data

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.DisplayMetrics
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.reatv.launcher.glass.Blur
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
 * Top Shelf artwork: the banner at shelf size, a blurred fill for behind it (null when the banner is
 * mostly white and would glare), and the app's logo lifted off the banner when [LogoKey] can.
 */
@Immutable
class ShelfArt(val art: TileArt, val ambient: ImageBitmap?, val logo: ImageBitmap?)

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
    private val shelfCache = LruCache<String, ShelfArt>(if (lowRam) 2 else 5)

    private val dispatcher = Executors.newFixedThreadPool(2) { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "rea-art")
    }.asCoroutineDispatcher()

    fun peek(key: String): TileArt? = cache.get(key)

    fun peekShelf(key: String): ShelfArt? = shelfCache.get(key)

    suspend fun tile(app: AppEntry, widthPx: Int, heightPx: Int): TileArt {
        cache.get(app.key)?.let { return it }
        return withContext(dispatcher) {
            cache.get(app.key) ?: render(app, widthPx, heightPx).also { cache.put(app.key, it) }
        }
    }

    suspend fun shelf(app: AppEntry, widthPx: Int, heightPx: Int): ShelfArt {
        shelfCache.get(app.key)?.let { return it }
        return withContext(dispatcher) {
            shelfCache.get(app.key) ?: renderShelf(app, widthPx, heightPx).also { shelfCache.put(app.key, it) }
        }
    }

    fun evict(packageName: String) {
        cache.remove(packageName)
        shelfCache.remove(packageName)
    }

    fun clear() {
        cache.evictAll()
        shelfCache.evictAll()
    }

    fun onTrimMemory(level: Int) {
        @Suppress("DEPRECATION")
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> clear()
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                shelfCache.evictAll()
                cache.trimToSize(maxBytes / 3)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                shelfCache.evictAll()
                if (lowRam) cache.trimToSize(maxBytes / 2)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> clear()
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                shelfCache.evictAll()
                cache.trimToSize(maxBytes / 2)
            }
        }
    }

    private fun renderShelf(app: AppEntry, width: Int, height: Int): ShelfArt {
        val art = render(app, width, height, dense = true)
        val banner = art.banner?.asAndroidBitmap() ?: return ShelfArt(art, null, null)
        val ambient = if (luminance(art.secondary) < 0.62f) {
            Blur.downscaleBlur(banner, 96, radius = 4).asImageBitmap()
        } else {
            null
        }
        val pixels = IntArray(banner.width * banner.height)
        banner.getPixels(pixels, 0, banner.width, 0, 0, banner.width, banner.height)
        val logo = LogoKey.extract(pixels, banner.width, banner.height)?.let {
            Bitmap.createBitmap(it.pixels, it.width, it.height, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
        // With a logo the shelf never shows the banner card: keep only its colors.
        val kept = if (logo != null) TileArt(null, null, art.primary, art.secondary) else art
        return ShelfArt(kept, ambient, logo)
    }

    private fun render(app: AppEntry, width: Int, height: Int, dense: Boolean = false): TileArt {
        if (app.kind == AppEntry.Kind.SystemSettings) {
            return TileArt(null, null, 0xFF5B6270.toInt(), 0xFF22252D.toInt())
        }
        val w = max(16, width)
        val h = max(9, height)
        val component = app.component
        val banner: Drawable? = (if (dense) denseBanner(app) else null) ?: try {
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

    /**
     * The densest banner the app ships, for the shelf, which draws it far larger than a tile: the
     * system would hand back the one for the screen's density, often 320x180, and upscale it.
     */
    private fun denseBanner(app: AppEntry): Drawable? = try {
        val info = pm.getApplicationInfo(app.packageName, 0)
        val id = app.component?.let { pm.getActivityInfo(it, 0).banner }?.takeIf { it != 0 } ?: info.banner
        if (id == 0) null else pm.getResourcesForApplication(info).getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null)
    } catch (e: Exception) {
        null
    }

    private fun luminance(argb: Int): Float =
        (0.2126f * Color.red(argb) + 0.7152f * Color.green(argb) + 0.0722f * Color.blue(argb)) / 255f

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
