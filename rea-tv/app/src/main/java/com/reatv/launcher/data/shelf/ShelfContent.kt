package com.reatv.launcher.data.shelf

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.reatv.launcher.BuildConfig
import com.reatv.launcher.data.AppEntry
import com.reatv.launcher.data.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale

/**
 * Series and movies for the Top Shelf: what the focused app recommends ([Recommendations]), or else
 * what's popular on that service ([TmdbCatalog], when a TMDB key is set). Also loads their artwork,
 * sized for the screen and cached by bytes, and lets it go when another app is in front.
 */
class ShelfContent(private val context: Context, lowRam: Boolean, private val settings: LauncherSettings) {
    private val http = Http(File(context.cacheDir, "shelf"), if (lowRam) 24L shl 20 else 48L shl 20)
    private val catalogs = LruCache<String, List<ShelfItem>>(12)
    private val images = object : LruCache<String, Bitmap>(if (lowRam) 10 shl 20 else 24 shl 20) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val backdropWidth = if (lowRam) 960 else 1280

    val tmdbKey: String
        get() = settings.tmdbKey.ifBlank { BuildConfig.TMDB_KEY }

    /** Whether hovering this app can show TMDB's titles for it. */
    fun hasCatalog(app: AppEntry): Boolean =
        settings.shelfCatalog && tmdbKey.isNotBlank() && TmdbProviders.forPackage(app.packageName) != null

    fun peekCatalog(app: AppEntry): List<ShelfItem>? = catalogs.get(app.packageName)

    /** Empty when offline, without a key, or when the service has nothing in this country. */
    suspend fun catalog(app: AppEntry): List<ShelfItem> = withContext(Dispatchers.IO) {
        catalogs.get(app.packageName)?.let { return@withContext it }
        val providers = TmdbProviders.forPackage(app.packageName) ?: return@withContext emptyList()
        val locale = Locale.getDefault()
        val items = TmdbCatalog(http, tmdbKey, region(locale), language(locale)).popular(providers)
        // Warm the artwork, so the carousel never shows a title before its logo or an empty frame.
        for (item in items) item.logoUrl?.let { http.file(it, IMAGE_TTL_MS) }
        items.firstOrNull()?.backdropUrl?.let { http.file(it, IMAGE_TTL_MS) }
        // A failure (offline) isn't remembered: the next hover tries again.
        if (items.size >= 2) catalogs.put(app.packageName, items)
        items
    }

    fun peekBackdrop(item: ShelfItem): ImageBitmap? = item.backdropUrl?.let { images.get(it) }?.asImageBitmap()

    /** The item's backdrop at screen width; null when it has none (the shelf then uses its card). */
    suspend fun backdrop(item: ShelfItem): ImageBitmap? = image(item.backdropUrl, backdropWidth)

    fun peekLogo(item: ShelfItem): ImageBitmap? = item.logoUrl?.let { images.get(it) }?.asImageBitmap()

    suspend fun logo(item: ShelfItem): ImageBitmap? = image(item.logoUrl, LOGO_WIDTH)

    private suspend fun image(url: String?, maxWidth: Int): ImageBitmap? {
        if (url == null) return null
        images.get(url)?.let { return it.asImageBitmap() }
        return withContext(Dispatchers.IO) {
            val bitmap = when {
                url.startsWith("https://") -> http.file(url, IMAGE_TTL_MS)?.let { f -> decode({ FileInputStream(f) }, maxWidth) }
                // A recommending app's own artwork; it may not let us read it, which is fine.
                url.startsWith("content://") || url.startsWith("android.resource://") ->
                    decode({ context.contentResolver.openInputStream(Uri.parse(url)) }, maxWidth)
                else -> null
            }
            bitmap?.also { images.put(url, it) }?.asImageBitmap()
        }
    }

    /** Decoded no larger than needed: a power-of-two subsample, then a final scale to [maxWidth]. */
    private fun decode(open: () -> InputStream?, maxWidth: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            val decoded = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            if (decoded == null || decoded.width <= maxWidth) {
                decoded
            } else {
                val scaled = Bitmap.createScaledBitmap(decoded, maxWidth, decoded.height * maxWidth / decoded.width, true)
                if (scaled !== decoded) decoded.recycle()
                scaled
            }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        images.evictAll()
        null
    }

    /** The key or the toggle changed: forget lists fetched under the old one. */
    fun resetCatalogs() {
        catalogs.evictAll()
    }

    /** Backdrops are the biggest thing REA TV holds: gone as soon as another app is in front. */
    fun onTrimMemory(level: Int) {
        @Suppress("DEPRECATION")
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> images.evictAll()
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> images.trimToSize(images.maxSize() / 2)
        }
    }

    private fun region(locale: Locale): String = locale.country.uppercase(Locale.US).takeIf { it.length == 2 } ?: "US"

    private fun language(locale: Locale): String {
        val lang = locale.language.ifEmpty { "en" }
        return if (locale.country.length == 2) "$lang-${locale.country.uppercase(Locale.US)}" else lang
    }

    companion object {
        const val IMAGE_TTL_MS = 14 * 24 * 60 * 60 * 1000L
        private const val LOGO_WIDTH = 640
    }
}
