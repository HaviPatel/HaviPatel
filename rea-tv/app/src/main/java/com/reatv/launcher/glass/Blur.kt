package com.reatv.launcher.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Cheap CPU blur for tiny bitmaps: downscale, then three box-blur passes (≈ Gaussian).
 * Glass panels sample the result scaled back up with bilinear filtering, which costs the GPU nothing
 * extra. This works on every Fire OS version (RenderEffect needs Android 12, Fire OS 8 is Android 11).
 */
object Blur {
    fun downscaleBlur(source: Bitmap, targetWidth: Int, radius: Int, passes: Int = 3): Bitmap {
        val w = targetWidth.coerceIn(8, max(8, source.width))
        val h = max(8, (source.height * w / source.width.toFloat()).roundToInt())
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        if (radius > 0) {
            val a = IntArray(w * h)
            val b = IntArray(w * h)
            out.getPixels(a, 0, w, 0, 0, w, h)
            repeat(passes) {
                boxHorizontal(a, b, w, h, radius)
                boxVertical(b, a, w, h, radius)
            }
            out.setPixels(a, 0, w, 0, 0, w, h)
        }
        return out
    }

    private fun boxHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        val last = w - 1
        for (y in 0 until h) {
            val row = y * w
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, last)]
                sa += p ushr 24
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val pOut = src[row + (x - r).coerceIn(0, last)]
                val pIn = src[row + (x + r + 1).coerceIn(0, last)]
                sa += (pIn ushr 24) - (pOut ushr 24)
                sr += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                sg += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                sb += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
    }

    private fun boxVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        val last = h - 1
        for (x in 0 until w) {
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, last) * w + x]
                sa += p ushr 24
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val pOut = src[(y - r).coerceIn(0, last) * w + x]
                val pIn = src[(y + r + 1).coerceIn(0, last) * w + x]
                sa += (pIn ushr 24) - (pOut ushr 24)
                sr += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                sg += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                sb += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
    }
}
