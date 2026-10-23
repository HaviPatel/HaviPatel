package com.reatv.launcher.glass

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Captures the window straight into a tiny bitmap (PixelCopy scales on the GPU), so opening a glass
 * overlay costs ~60 KB instead of a full 8 MB screenshot. Returns null before Android 8.
 */
object WindowSnapshot {
    suspend fun capture(activity: Activity, width: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < 26) return null
        val window = activity.window ?: return null
        val decor = window.decorView
        if (decor.width <= 0 || decor.height <= 0) return null
        val height = (width.toLong() * decor.height / decor.width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return withTimeoutOrNull(300) {
            suspendCancellableCoroutine<Bitmap?> { cont ->
                try {
                    PixelCopy.request(window, bitmap, { result ->
                        if (cont.isActive) cont.resume(if (result == PixelCopy.SUCCESS) bitmap else null)
                    }, Handler(Looper.getMainLooper()))
                } catch (e: IllegalArgumentException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }
}
