package com.reatv.launcher.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.reatv.launcher.data.AppEntry
import com.reatv.launcher.data.TileArt
import com.reatv.launcher.glass.Blur
import com.reatv.launcher.glass.glassEdge
import com.reatv.launcher.ui.LocalArt
import com.reatv.launcher.ui.Rea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * tvOS Top Shelf: full-bleed, edge to edge, fading into the black behind the app rows. The focused
 * top-row app's own artwork fills it, blurred and enlarged, with the artwork crisp on the right and
 * the app's name on the left. The first row of apps sits over the bottom of the fade, as on tvOS.
 */
@Composable
fun TopShelf(app: AppEntry?, metrics: HomeMetrics, collapse: () -> Float) {
    val shelfHeight = metrics.firstRowTop + metrics.tileH * 0.6f
    Box(
        Modifier
            .fillMaxWidth()
            .height(shelfHeight)
            .graphicsLayer {
                val c = collapse()
                alpha = 1f - c
                translationY = -60.dp.toPx() * c
            },
    ) {
        if (app != null) ShelfContent(app, metrics)
    }
}

private class ShelfModel(val app: AppEntry, val art: TileArt?, val ambient: ImageBitmap?)

@Composable
private fun ShelfContent(app: AppEntry, metrics: HomeMetrics) {
    val artLoader = LocalArt.current
    val density = LocalDensity.current
    // Around the banner's native size: big enough to read as the hero, never a billboard.
    val artHeight = min(metrics.firstRowTop * 0.52f, 180.dp)
    val artWidth = artHeight * (16f / 9f)
    val artWidthPx = with(density) { artWidth.roundToPx() }
    val artHeightPx = with(density) { artHeight.roundToPx() }

    val model by produceState(ShelfModel(app, artLoader.peekHero(app.key), null), app.key) {
        value = ShelfModel(app, artLoader.peekHero(app.key), null)
        val art = value.art ?: artLoader.hero(app, artWidthPx, artHeightPx)
        // The banner's own blur fills the shelf, unless the banner is mostly white (YouTube,
        // Netflix's light variant...), which would glare: those get a wash of their colors instead.
        val banner = art.banner
        val ambient = if (banner != null && luminance(art.secondary) < 0.62f) {
            withContext(Dispatchers.Default) { Blur.downscaleBlur(banner.asAndroidBitmap(), 96, radius = 4).asImageBitmap() }
        } else {
            null
        }
        value = ShelfModel(app, art, ambient)
    }

    Crossfade(targetState = model, animationSpec = tween(420), label = "shelf") { m ->
        val art = m.art
        val primary = art?.let { Color(it.primary) } ?: Color(0xFF2A3040)
        Box(Modifier.fillMaxSize()) {
            val ambient = m.ambient
            if (ambient != null) {
                Image(
                    bitmap = ambient,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = 1.2f
                        scaleY = 1.2f
                    },
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(lerp(primary, Color.Black, 0.35f), lerp(primary, Color.Black, 0.85f), Color.Black),
                            center = Offset(with(density) { metrics.width.toPx() }, 0f),
                            radius = with(density) { metrics.width.toPx() } * 1.1f,
                        ),
                    ),
                )
            }
            // Legibility on the left, and the fade into the black behind the rows.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to Color(0xCC000000), 0.5f to Color(0x40000000), 1f to Color(0x1A000000)),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color(0x33000000), 0.45f to Color.Transparent, 0.78f to Color(0xD9000000), 1f to Color.Black),
                ),
            )

            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = metrics.edge, top = metrics.firstRowTop - 150.dp)
                    .width(metrics.width * 0.4f),
            ) {
                BasicText(
                    m.app.categoryLabel.uppercase(),
                    style = Rea.text(13.sp, FontWeight.SemiBold, Rea.TextSecondary).copy(letterSpacing = 1.4.sp),
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    m.app.label,
                    style = Rea.text(48.sp, FontWeight.Bold, shadow = true),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = metrics.edge, top = metrics.height * 0.11f)
                    .size(artWidth, artHeight)
                    .shadow(28.dp, RoundedCornerShape(20.dp), clip = false)
                    .clip(RoundedCornerShape(20.dp))
                    .glassEdge(20.dp, strength = 0.8f),
            ) {
                TileArtwork(m.app, art)
            }
        }
    }
}

private fun luminance(argb: Int): Float {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
}
