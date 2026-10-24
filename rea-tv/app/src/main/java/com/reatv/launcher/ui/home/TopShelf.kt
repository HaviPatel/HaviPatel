package com.reatv.launcher.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import com.reatv.launcher.data.ShelfArt
import com.reatv.launcher.glass.glassEdge
import com.reatv.launcher.ui.LocalArt
import com.reatv.launcher.ui.Rea
import kotlinx.coroutines.delay
import kotlin.math.sqrt

/**
 * tvOS Top Shelf: full-bleed, edge to edge, fading into the black behind the app rows. The focused
 * top-row app's banner colors fill it, and its logo, lifted off the banner, sits bottom-left as the
 * title treatment. Banners whose logo can't be lifted show the app's name and the banner as a card.
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

private class ShelfModel(val app: AppEntry, val shelf: ShelfArt)

@Composable
private fun ShelfContent(app: AppEntry, metrics: HomeMetrics) {
    val artLoader = LocalArt.current
    val density = LocalDensity.current
    // The fallback card, around the banner's native size: big enough to read, never a billboard.
    val cardHeight = min(metrics.firstRowTop * 0.52f, 180.dp)
    val cardWidth = cardHeight * (16f / 9f)
    val cardWidthPx = with(density) { cardWidth.roundToPx() }
    val cardHeightPx = with(density) { cardHeight.roundToPx() }

    // The shelf being left stays up until the next one is ready, then crossfades: no empty frame.
    val model by produceState(artLoader.peekShelf(app.key)?.let { ShelfModel(app, it) }, app.key) {
        val cached = artLoader.peekShelf(app.key)
        if (cached != null) {
            value = ShelfModel(app, cached)
            return@produceState
        }
        // Gliding along the top row shouldn't decode and key every banner it passes.
        delay(140)
        value = ShelfModel(app, artLoader.shelf(app, cardWidthPx, cardHeightPx))
    }

    Crossfade(targetState = model, animationSpec = tween(420), label = "shelf") { m ->
        if (m == null) return@Crossfade
        val art = m.shelf.art
        val primary = Color(art.primary)
        Box(Modifier.fillMaxSize()) {
            val ambient = m.shelf.ambient
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

            val logo = m.shelf.logo
            if (logo != null) {
                TitleLogo(logo, m.app.label, metrics)
            } else {
                BasicText(
                    m.app.label,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = metrics.edge, top = metrics.firstRowTop - 128.dp)
                        .width(metrics.width * 0.4f),
                    style = Rea.text(48.sp, FontWeight.Bold, shadow = true),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = metrics.edge, top = metrics.height * 0.11f)
                        .size(cardWidth, cardHeight)
                        .shadow(28.dp, RoundedCornerShape(20.dp), clip = false)
                        .clip(RoundedCornerShape(20.dp))
                        .glassEdge(20.dp, strength = 0.8f),
                ) {
                    TileArtwork(m.app, art)
                }
            }
        }
    }
}

/**
 * The logo as a title treatment, bottom-left above the first row. Logos are sized to a common area
 * rather than a common width, so a wide wordmark and a stacked mark carry the same visual weight.
 */
@Composable
private fun BoxScope.TitleLogo(logo: ImageBitmap, label: String, metrics: HomeMetrics) {
    val aspect = logo.width / logo.height.toFloat()
    val maxW = metrics.width * 0.34f
    val maxH = 112.dp
    var w = 300.dp * sqrt(80f * aspect / 300f)
    var h = w / aspect
    if (w > maxW) {
        w = maxW
        h = w / aspect
    }
    if (h > maxH) {
        h = maxH
        w = h * aspect
    }
    Image(
        bitmap = logo,
        contentDescription = label,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = metrics.edge, top = metrics.firstRowTop - 56.dp - h)
            .size(w, h),
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.Medium,
    )
}
