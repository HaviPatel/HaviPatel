package com.reatv.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/** Icons are Lucide outlines (see LucideIcons.kt): thin, rounded strokes in the spirit of SF Symbols. */
enum class ReaIcon {
    Gear, Moon, Wifi, Bluetooth, Bolt, Storage, Home, Info, EyeOff, Trash, Move, Star, Grid, Remote,
    Sparkle, Speed, Check, Play, Power, Display, Chevron, Apps,
}

private val vectors = HashMap<ReaIcon, ImageVector>()

/** Built once per icon and kept: each is a handful of path nodes. */
fun reaIconVector(icon: ReaIcon): ImageVector = vectors.getOrPut(icon) {
    ImageVector.Builder(
        name = icon.name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        for (data in lucidePaths(icon)) {
            addPath(
                pathData = addPathNodes(data),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

/** An icon whose color is read in the draw phase, so focus animations never recompose it. */
@Composable
fun Glyph(icon: ReaIcon, modifier: Modifier = Modifier, tint: () -> Color = { Color.White }) {
    val painter = rememberVectorPainter(reaIconVector(icon))
    Canvas(modifier) {
        with(painter) { draw(size, colorFilter = ColorFilter.tint(tint())) }
    }
}
