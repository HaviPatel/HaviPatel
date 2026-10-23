package com.reatv.launcher.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.reatv.launcher.data.WallpaperStyle

/** What sits behind the glass: a sharp image for the screen and a blurred copy for glass to sample. */
class Backdrop(val image: ImageBitmap, val blurred: Bitmap)

@Stable
class BackdropSource {
    var current: Backdrop? by mutableStateOf(null)
}

/** The backdrop glass in this subtree refracts. Overlays provide a blurred snapshot of the home screen. */
val LocalBackdrop = staticCompositionLocalOf { BackdropSource() }

class AuroraPalette(val base: Int, val blobs: IntArray)

/**
 * Procedural "aurora" wallpapers: a few soft radial lights over a deep base, rendered once at a
 * fraction of screen resolution (they're smooth, so bilinear upscaling is invisible). No image
 * assets ship in the APK.
 */
object Aurora {
    private val LAYOUT = arrayOf(
        floatArrayOf(0.10f, 0.05f, 0.80f),
        floatArrayOf(0.90f, 0.15f, 0.65f),
        floatArrayOf(0.55f, 1.05f, 0.80f),
        floatArrayOf(1.00f, 0.85f, 0.50f),
    )

    fun palette(style: WallpaperStyle): AuroraPalette = when (style) {
        WallpaperStyle.Aurora -> AuroraPalette(0xFF070B1A.toInt(), intArrayOf(0xFF4A2BE0.toInt(), 0xFF00B3A4.toInt(), 0xFFC2349B.toInt(), 0xFF1F6FEB.toInt()))
        WallpaperStyle.Ocean -> AuroraPalette(0xFF03121C.toInt(), intArrayOf(0xFF0077B6.toInt(), 0xFF00B4D8.toInt(), 0xFF023E8A.toInt(), 0xFF48CAE4.toInt()))
        WallpaperStyle.Sunset -> AuroraPalette(0xFF1A0710.toInt(), intArrayOf(0xFFFF6B35.toInt(), 0xFFD7263D.toInt(), 0xFF6A0572.toInt(), 0xFFF4A261.toInt()))
        WallpaperStyle.Forest -> AuroraPalette(0xFF04140C.toInt(), intArrayOf(0xFF2D6A4F.toInt(), 0xFF52B788.toInt(), 0xFF1B4332.toInt(), 0xFF95D5B2.toInt()))
        WallpaperStyle.Graphite -> AuroraPalette(0xFF0C0D10.toInt(), intArrayOf(0xFF3A3D46.toInt(), 0xFF5B5F6B.toInt(), 0xFF22252B.toInt(), 0xFF7A7F8C.toInt()))
        WallpaperStyle.Midnight -> AuroraPalette(0xFF000000.toInt(), intArrayOf(0xFF101830.toInt(), 0xFF182848.toInt(), 0xFF0A0F1F.toInt(), 0xFF202A44.toInt()))
    }

    /** Palette tinted by the focused app's artwork (the "Top Shelf" color wash). */
    fun fromColors(primary: Int, secondary: Int): AuroraPalette {
        val hsv = FloatArray(3)
        Color.colorToHSV(primary, hsv)
        val base = Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] * 0.8f).coerceAtMost(0.9f), 0.10f))
        val vivid = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1].coerceAtLeast(0.55f), hsv[2].coerceIn(0.55f, 0.9f)))
        val shifted = Color.HSVToColor(floatArrayOf((hsv[0] + 38f) % 360f, hsv[1].coerceAtLeast(0.45f), 0.65f))
        Color.colorToHSV(secondary, hsv)
        val second = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1].coerceAtLeast(0.35f), hsv[2].coerceIn(0.35f, 0.75f)))
        val deep = Color.HSVToColor(floatArrayOf((hsv[0] + 320f) % 360f, 0.6f, 0.4f))
        return AuroraPalette(base, intArrayOf(vivid, shifted, second, deep))
    }

    fun render(width: Int, height: Int, palette: AuroraPalette): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(palette.base)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        palette.blobs.forEachIndexed { i, color ->
            val l = LAYOUT[i % LAYOUT.size]
            paint.shader = RadialGradient(
                l[0] * w, l[1] * h, l[2] * w,
                intArrayOf(withAlpha(color, 0.85f), withAlpha(color, 0.32f), withAlpha(color, 0f)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        // Legibility: darken toward the bottom (app rows) and the left (Top Shelf text).
        paint.shader = LinearGradient(0f, h * 0.35f, 0f, h, 0x00000000, 0x99000000.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = LinearGradient(0f, 0f, w * 0.65f, 0f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        return bitmap
    }

    private fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
}

object Backdrops {
    fun aurora(palette: AuroraPalette, width: Int, height: Int, blurWidth: Int): Backdrop {
        val sharp = Aurora.render(width, height, palette)
        return Backdrop(sharp.asImageBitmap(), Blur.downscaleBlur(sharp, blurWidth, radius = 3))
    }

    /** A PixelCopy snapshot of the window, heavily blurred, for overlays (Control Center, menus, settings). */
    fun snapshot(shot: Bitmap): Backdrop {
        val blurred = Blur.downscaleBlur(shot, shot.width, radius = 4)
        shot.recycle()
        return Backdrop(blurred.asImageBitmap(), blurred)
    }
}
