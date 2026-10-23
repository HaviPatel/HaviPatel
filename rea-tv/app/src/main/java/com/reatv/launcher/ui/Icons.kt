package com.reatv.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Icons are drawn in code (24-unit grid), so the APK ships zero icon assets or icon libraries. */
enum class ReaIcon {
    Gear, Moon, Wifi, Bluetooth, Bolt, Storage, Home, Info, EyeOff, Trash, Move, Star, Grid, Remote,
    Sparkle, Speed, Check, Play, Power, Display, Chevron, Apps,
}

@Composable
fun Glyph(icon: ReaIcon, modifier: Modifier = Modifier, tint: () -> Color = { Color.White }) {
    Canvas(modifier) { drawGlyph(icon, tint()) }
}

fun DrawScope.drawGlyph(icon: ReaIcon, color: Color) {
    val unit = size.minDimension / 24f
    val ox = (size.width - 24f * unit) / 2f
    val oy = (size.height - 24f * unit) / 2f
    withTransform({
        translate(ox, oy)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        glyph(icon, color)
    }
}

private fun line(width: Float = 2f) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun polyline(vararg points: Float): Path = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        lineTo(points[i], points[i + 1])
        i += 2
    }
}

private fun DrawScope.glyph(icon: ReaIcon, c: Color) {
    val mid = Offset(12f, 12f)
    when (icon) {
        ReaIcon.Gear -> {
            for (i in 0 until 8) rotate(i * 45f, pivot = mid) {
                drawLine(c, Offset(12f, 2.6f), Offset(12f, 5.6f), strokeWidth = 3.4f, cap = StrokeCap.Round)
            }
            drawCircle(c, radius = 6.4f, center = mid, style = line(2.6f))
            drawCircle(c, radius = 2.1f, center = mid, style = line(2f))
        }
        ReaIcon.Moon -> {
            val outer = Path().apply { addOval(Rect(center = mid, radius = 8.5f)) }
            val inner = Path().apply { addOval(Rect(center = Offset(16.5f, 8f), radius = 7f)) }
            val crescent = Path().apply { op(outer, inner, PathOperation.Difference) }
            drawPath(crescent, c)
        }
        ReaIcon.Wifi -> {
            val o = Offset(12f, 19f)
            for (r in floatArrayOf(4.6f, 9.2f, 13.8f)) {
                drawArc(c, 225f, 90f, false, Offset(o.x - r, o.y - r), Size(2 * r, 2 * r), style = line(2.4f))
            }
            drawCircle(c, 1.7f, o)
        }
        ReaIcon.Bluetooth -> drawPath(polyline(7f, 7.5f, 17f, 16.5f, 12f, 21f, 12f, 3f, 17f, 7.5f, 7f, 16.5f), c, style = line())
        ReaIcon.Bolt -> {
            val p = polyline(13.5f, 2f, 5f, 13.5f, 11f, 13.5f, 10f, 22f, 19f, 10f, 13f, 10f).apply { close() }
            drawPath(p, c)
        }
        ReaIcon.Storage -> {
            drawRoundRect(c, Offset(3f, 4.5f), Size(18f, 6.5f), CornerRadius(2f), style = line())
            drawRoundRect(c, Offset(3f, 13f), Size(18f, 6.5f), CornerRadius(2f), style = line())
            drawCircle(c, 1.2f, Offset(17f, 7.75f))
            drawCircle(c, 1.2f, Offset(17f, 16.25f))
        }
        ReaIcon.Home -> {
            drawPath(polyline(3.5f, 11f, 12f, 3.8f, 20.5f, 11f), c, style = line())
            drawPath(polyline(6f, 9.5f, 6f, 20f, 18f, 20f, 18f, 9.5f), c, style = line())
            drawRoundRect(c, Offset(10f, 14f), Size(4f, 6f), CornerRadius(1f))
        }
        ReaIcon.Info -> {
            drawCircle(c, 9f, mid, style = line())
            drawLine(c, Offset(12f, 11f), Offset(12f, 16.5f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawCircle(c, 1.3f, Offset(12f, 7.6f))
        }
        ReaIcon.EyeOff -> {
            val eye = Path().apply {
                moveTo(2.5f, 12f)
                cubicTo(6f, 6f, 18f, 6f, 21.5f, 12f)
                cubicTo(18f, 18f, 6f, 18f, 2.5f, 12f)
                close()
            }
            drawPath(eye, c, style = line())
            drawCircle(c, 3f, mid, style = line())
            drawLine(c, Offset(4f, 4f), Offset(20f, 20f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
        ReaIcon.Trash -> {
            drawLine(c, Offset(4f, 6.5f), Offset(20f, 6.5f), strokeWidth = 2f, cap = StrokeCap.Round)
            drawPath(polyline(9.5f, 6.5f, 9.5f, 4f, 14.5f, 4f, 14.5f, 6.5f), c, style = line())
            drawPath(polyline(6f, 6.5f, 7f, 20f, 17f, 20f, 18f, 6.5f), c, style = line())
            drawLine(c, Offset(10f, 10f), Offset(10f, 16.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            drawLine(c, Offset(14f, 10f), Offset(14f, 16.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        }
        ReaIcon.Move -> {
            drawLine(c, Offset(12f, 3f), Offset(12f, 21f), strokeWidth = 2f, cap = StrokeCap.Round)
            drawLine(c, Offset(3f, 12f), Offset(21f, 12f), strokeWidth = 2f, cap = StrokeCap.Round)
            drawPath(polyline(9.5f, 5.5f, 12f, 3f, 14.5f, 5.5f), c, style = line())
            drawPath(polyline(9.5f, 18.5f, 12f, 21f, 14.5f, 18.5f), c, style = line())
            drawPath(polyline(5.5f, 9.5f, 3f, 12f, 5.5f, 14.5f), c, style = line())
            drawPath(polyline(18.5f, 9.5f, 21f, 12f, 18.5f, 14.5f), c, style = line())
        }
        ReaIcon.Star -> drawPath(star(Offset(12f, 12.6f), 9.6f, 4.2f), c)
        ReaIcon.Grid -> {
            for (x in floatArrayOf(4f, 13f)) for (y in floatArrayOf(4f, 13f)) {
                drawRoundRect(c, Offset(x, y), Size(7f, 7f), CornerRadius(2f))
            }
        }
        ReaIcon.Remote -> {
            drawRoundRect(c, Offset(8f, 2.5f), Size(8f, 19f), CornerRadius(3f), style = line())
            drawCircle(c, 2f, Offset(12f, 8f), style = line(1.8f))
            drawCircle(c, 1f, Offset(12f, 14f))
            drawCircle(c, 1f, Offset(12f, 17.5f))
        }
        ReaIcon.Sparkle -> {
            drawPath(polyline(12f, 2f, 14f, 10f, 22f, 12f, 14f, 14f, 12f, 22f, 10f, 14f, 2f, 12f, 10f, 10f).apply { close() }, c)
        }
        ReaIcon.Speed -> {
            drawArc(c, 150f, 240f, false, Offset(3f, 4f), Size(18f, 18f), style = line())
            drawLine(c, Offset(12f, 13f), Offset(16.5f, 8.5f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawCircle(c, 1.6f, Offset(12f, 13f))
        }
        ReaIcon.Check -> drawPath(polyline(5f, 12.5f, 10f, 17.5f, 19f, 7f), c, style = line(2.6f))
        ReaIcon.Play -> drawPath(polyline(8f, 5f, 19f, 12f, 8f, 19f).apply { close() }, c)
        ReaIcon.Power -> {
            drawArc(c, 300f, 300f, false, Offset(4f, 5f), Size(16f, 16f), style = line(2.2f))
            drawLine(c, Offset(12f, 3f), Offset(12f, 11f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
        ReaIcon.Display -> {
            drawRoundRect(c, Offset(2.5f, 4f), Size(19f, 13f), CornerRadius(2f), style = line())
            drawLine(c, Offset(8f, 20.5f), Offset(16f, 20.5f), strokeWidth = 2f, cap = StrokeCap.Round)
        }
        ReaIcon.Chevron -> drawPath(polyline(9.5f, 6f, 15.5f, 12f, 9.5f, 18f), c, style = line(2.2f))
        ReaIcon.Apps -> {
            for (x in floatArrayOf(5f, 12f, 19f)) for (y in floatArrayOf(5f, 12f, 19f)) drawCircle(c, 2.1f, Offset(x, y))
        }
    }
}

private fun star(center: Offset, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val angle = -PI / 2 + i * PI / 5
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
