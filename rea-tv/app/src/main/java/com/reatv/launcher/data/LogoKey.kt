package com.reatv.launcher.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Lifts an app's logo off its TV banner, so the Top Shelf can show it as a title treatment the way
 * tvOS does, instead of a banner card. Most banners are a logo on a flat or gently graded fill: the
 * fill is modelled from the banner's border (a Coons patch, so gradients key cleanly), each pixel's
 * alpha comes from how far it sits from that fill, and its color is un-mixed from the fill so edges
 * don't keep a halo. Photographic banners, and ones whose logo runs into the border, are rejected
 * and keep the card. Pure Kotlin on ARGB ints, so it runs off the main thread and in unit tests.
 */
object LogoKey {
    class Logo(val pixels: IntArray, val width: Int, val height: Int, val reversed: Boolean)

    /** Distance from the fill (RGB, 0..441) still counted as fill: covers gradient-model error. */
    private const val NOISE = 20f

    /** A pixel with no logo color this strong within 2 px is fill (vignettes, shading), not logo. */
    private const val NEAR_LOGO = 40f

    /** Anything this far from the fill is opaque, so two logo colors meeting don't leave a seam. */
    private const val SOLID = 240f

    /** Summed RGB variation allowed along one border line: a gradient passes, a photo doesn't. */
    private const val MAX_BORDER_VARIATION = 200f

    fun extract(argb: IntArray, w: Int, h: Int, reverseDarkInk: Boolean = true): Logo? {
        if (w < 32 || h < 18 || argb.size < w * h) return null
        val alpha = FloatArray(w * h)
        val color = IntArray(w * h)

        if (transparentBorder(argb, w, h)) {
            // Already a logo on transparency: nothing to key.
            for (i in 0 until w * h) {
                alpha[i] = (argb[i] ushr 24) / 255f
                color[i] = argb[i]
            }
        } else if (!keyFill(argb, w, h, alpha, color)) {
            return null
        }

        // Something, but not everything: a "logo" covering most of the banner means the fill was wrong.
        var coverage = 0f
        for (a in alpha) coverage += a
        coverage /= (w * h)
        if (coverage < 0.015f || coverage > 0.55f) return null

        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (alpha[y * w + x] > 0.1f) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < 0 || right - left < w * 0.08f) return null
        left = max(0, left - 2)
        top = max(0, top - 2)
        right = min(w - 1, right + 2)
        bottom = min(h - 1, bottom + 2)

        val reverse = reverseDarkInk && hasDarkInk(alpha, color)
        val cw = right - left + 1
        val ch = bottom - top + 1
        val out = IntArray(cw * ch)
        for (y in 0 until ch) {
            for (x in 0 until cw) {
                val i = (top + y) * w + left + x
                val a = alpha[i]
                if (a <= 0f) continue
                val c = if (reverse) reverseInk(color[i]) else color[i]
                out[y * cw + x] = ((a * 255f + 0.5f).toInt() shl 24) or (c and 0xFFFFFF)
            }
        }
        return Logo(out, cw, ch, reverse)
    }

    private fun transparentBorder(argb: IntArray, w: Int, h: Int): Boolean {
        var clear = 0
        var total = 0
        for (x in 0 until w) {
            if ((argb[x] ushr 24) < 128) clear++
            if ((argb[(h - 1) * w + x] ushr 24) < 128) clear++
            total += 2
        }
        for (y in 0 until h) {
            if ((argb[y * w] ushr 24) < 128) clear++
            if ((argb[y * w + w - 1] ushr 24) < 128) clear++
            total += 2
        }
        return clear > total / 2
    }

    /** Fills [alpha] and [color] by keying out the fill modelled from the border. False if there's no clean fill. */
    private fun keyFill(argb: IntArray, w: Int, h: Int, alpha: FloatArray, color: IntArray): Boolean {
        // One pixel in from the edge, clear of any resampling fringe.
        val top = borderLine(argb, w, h, horizontal = true, at = 1) ?: return false
        val bottom = borderLine(argb, w, h, horizontal = true, at = h - 2) ?: return false
        val leftEdge = borderLine(argb, w, h, horizontal = false, at = 1) ?: return false
        val rightEdge = borderLine(argb, w, h, horizontal = false, at = w - 2) ?: return false

        val distance = FloatArray(w * h)
        val fill = FloatArray(3)
        for (y in 0 until h) {
            val v = y / (h - 1f)
            for (x in 0 until w) {
                val u = x / (w - 1f)
                coons(top, bottom, leftEdge, rightEdge, x, y, u, v, w, h, fill)
                val p = argb[y * w + x]
                val dr = ((p shr 16) and 0xFF) - fill[0]
                val dg = ((p shr 8) and 0xFF) - fill[1]
                val db = (p and 0xFF) - fill[2]
                distance[y * w + x] = sqrt(dr * dr + dg * dg + db * db)
            }
        }

        // Alpha against the strongest logo color nearby, so an edge pixel half-way to its stroke
        // reads as half covered instead of solid.
        val near = maxFilter(distance, w, h, radius = 2)
        for (y in 0 until h) {
            val v = y / (h - 1f)
            for (x in 0 until w) {
                val i = y * w + x
                if (near[i] < NEAR_LOGO) continue
                val full = min(max(near[i], 48f), SOLID)
                var a = ((distance[i] - NOISE) / (full - NOISE)).coerceIn(0f, 1f)
                // A stroke's core reads a hair below its brightest neighbour on a gradient: solid is solid.
                if (a > 0.97f) a = 1f
                a *= (argb[i] ushr 24) / 255f
                alpha[i] = a
                if (a <= 0f) continue
                coons(top, bottom, leftEdge, rightEdge, x, y, x / (w - 1f), v, w, h, fill)
                val p = argb[i]
                val r = unmix((p shr 16) and 0xFF, fill[0], a)
                val g = unmix((p shr 8) and 0xFF, fill[1], a)
                val b = unmix(p and 0xFF, fill[2], a)
                color[i] = (r shl 16) or (g shl 8) or b
            }
        }
        return true
    }

    /** p = a·logo + (1 − a)·fill, solved for the logo color. */
    private fun unmix(p: Int, fill: Float, a: Float): Int = (fill + (p - fill) / a + 0.5f).toInt().coerceIn(0, 255)

    /**
     * A border line, median-smoothed so thin specks don't count, as RGB floats. Null when the line
     * isn't a clean fill: too many pixels stray from the smoothed line, or it swings too much along
     * its length (a photo, or the logo touching the edge).
     */
    private fun borderLine(argb: IntArray, w: Int, h: Int, horizontal: Boolean, at: Int): FloatArray? {
        val n = if (horizontal) w else h
        val raw = IntArray(n) { i -> if (horizontal) argb[at * w + i] else argb[i * w + at] }
        val smooth = FloatArray(n * 3)
        val window = IntArray(7)
        for (c in 0 until 3) {
            val shift = 16 - 8 * c
            for (i in 0 until n) {
                var k = 0
                for (j in i - 3..i + 3) window[k++] = (raw[j.coerceIn(0, n - 1)] shr shift) and 0xFF
                window.sort()
                smooth[i * 3 + c] = window[3].toFloat()
            }
        }
        var strays = 0
        for (i in 0 until n) {
            val p = raw[i]
            val dr = ((p shr 16) and 0xFF) - smooth[i * 3]
            val dg = ((p shr 8) and 0xFF) - smooth[i * 3 + 1]
            val db = (p and 0xFF) - smooth[i * 3 + 2]
            if (dr * dr + dg * dg + db * db > 48f * 48f) strays++
        }
        if (strays > n / 5) return null
        // Block means average out pixel noise, and a dead zone stops what's left adding up along
        // the line, so a slow gradient counts as nothing while an edge or a photo counts in full.
        val block = max(4, n / 40)
        val previous = FloatArray(3)
        val mean = FloatArray(3)
        var variation = 0f
        var start = 0
        while (start < n) {
            val end = min(n, start + block)
            for (c in 0 until 3) {
                var sum = 0f
                for (i in start until end) sum += smooth[i * 3 + c]
                mean[c] = sum / (end - start)
                if (start > 0) variation += max(0f, abs(mean[c] - previous[c]) - 2f)
                previous[c] = mean[c]
            }
            start = end
        }
        return if (variation > MAX_BORDER_VARIATION) null else smooth
    }

    /** The fill at (x, y), blended from the four border lines (a bilinear Coons patch). */
    private fun coons(
        top: FloatArray, bottom: FloatArray, left: FloatArray, right: FloatArray,
        x: Int, y: Int, u: Float, v: Float, w: Int, h: Int, out: FloatArray,
    ) {
        for (c in 0 until 3) {
            val c00 = (top[c] + left[c]) / 2f
            val c10 = (top[(w - 1) * 3 + c] + right[c]) / 2f
            val c01 = (bottom[c] + left[(h - 1) * 3 + c]) / 2f
            val c11 = (bottom[(w - 1) * 3 + c] + right[(h - 1) * 3 + c]) / 2f
            out[c] = (1 - v) * top[x * 3 + c] + v * bottom[x * 3 + c] +
                (1 - u) * left[y * 3 + c] + u * right[y * 3 + c] -
                ((1 - u) * (1 - v) * c00 + u * (1 - v) * c10 + (1 - u) * v * c01 + u * v * c11)
        }
    }

    private fun maxFilter(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var m = 0f
                for (k in max(0, x - radius)..min(w - 1, x + radius)) m = max(m, src[y * w + k])
                tmp[y * w + x] = m
            }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var m = 0f
                for (k in max(0, y - radius)..min(h - 1, y + radius)) m = max(m, tmp[k * w + x])
                out[y * w + x] = m
            }
        }
        return out
    }

    /** Dark, colorless ink (black or charcoal wordmarks) would vanish on the shelf's dark backdrop. */
    private fun hasDarkInk(alpha: FloatArray, color: IntArray): Boolean {
        var dark = 0f
        var total = 0f
        for (i in alpha.indices) {
            val a = alpha[i]
            if (a < 0.1f) continue
            total += a
            val c = color[i]
            if (luminance(c) < 0.32f && saturation(c) < 0.35f) dark += a
        }
        return total > 0f && dark / total > 0.10f
    }

    /** Dark neutrals turn light, as brands do for their logos on dark backgrounds; colors and whites stay. */
    private fun reverseInk(c: Int): Int {
        val l = luminance(c)
        val k = (1f - smoothstep(0.32f, 0.5f, l)) * (1f - smoothstep(0.25f, 0.45f, saturation(c)))
        if (k <= 0f) return c
        val level = 255f * (1f - 0.6f * l)
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (lerp(r, level, k) shl 16) or (lerp(g, level, k) shl 8) or lerp(b, level, k)
    }

    private fun lerp(from: Int, to: Float, t: Float): Int = (from + (to - from) * t + 0.5f).toInt().coerceIn(0, 255)

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun luminance(c: Int): Float =
        (0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF)) / 255f

    private fun saturation(c: Int): Float {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        return if (hi == 0) 0f else (hi - lo) / hi.toFloat()
    }
}
