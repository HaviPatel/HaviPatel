package com.reatv.launcher.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
 * tvOS-style Top Shelf: one full-width pane above the top row. The focused app's artwork is shown
 * crisp on the right and, blurred and enlarged, fills the whole pane behind its name, the way tvOS
 * extends a show's artwork across the shelf. A Liquid Glass edge catches the shared light.
 */
@Composable
fun TopShelf(app: AppEntry?, metrics: HomeMetrics, collapse: () -> Float) {
    val top = metrics.height * 0.05f
    val bottom = 26.dp
    val shelfHeight = metrics.firstRowTop - top - bottom
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .padding(start = metrics.edge, end = metrics.edge, top = top)
            .fillMaxWidth()
            .height(shelfHeight)
            .graphicsLayer {
                val c = collapse()
                alpha = 1f - c
                translationY = -40.dp.toPx() * c
            },
    ) {
        if (app != null) {
            val artHeight = shelfHeight - 36.dp
            Box(
                Modifier
                    .fillMaxSize()
                    .shadow(24.dp, shape, clip = false)
                    .clip(shape)
                    .glassEdge(28.dp),
            ) {
                ShelfContent(app, artHeightPx = with(LocalDensity.current) { artHeight.roundToPx() })
            }
        }
    }
}

private class ShelfModel(val app: AppEntry, val art: TileArt?, val ambient: ImageBitmap?)

@Composable
private fun ShelfContent(app: AppEntry, artHeightPx: Int) {
    val artLoader = LocalArt.current
    val artWidthPx = artHeightPx * 16 / 9
    val model by produceState(ShelfModel(app, artLoader.peekHero(app.key), null), app.key) {
        value = ShelfModel(app, artLoader.peekHero(app.key), null)
        val art = value.art ?: artLoader.hero(app, artWidthPx, artHeightPx)
        // A tiny blurred copy of the banner, stretched across the shelf: costs ~30 KB.
        val ambient = art.banner?.let { banner ->
            withContext(Dispatchers.Default) { Blur.downscaleBlur(banner.asAndroidBitmap(), 96, radius = 4).asImageBitmap() }
        }
        value = ShelfModel(app, art, ambient)
    }

    Crossfade(targetState = model, animationSpec = tween(360), label = "shelf") { m ->
        val art = m.art
        val primary = art?.let { Color(it.primary) } ?: Color(0xFF2A3040)
        val secondary = art?.let { Color(it.secondary) } ?: Color(0xFF161A24)
        Box(Modifier.fillMaxSize()) {
            // Fill: the artwork's own blur, or a wash of its colors for icon-only apps.
            val ambient = m.ambient
            if (ambient != null) {
                Image(
                    bitmap = ambient,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.15f; scaleY = 1.15f },
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(lerp(primary, Color.Black, 0.25f), lerp(secondary, Color.Black, 0.75f))),
                    ),
                )
            }
            // Legibility: darken toward the title on the left.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to Color(0xB3000000), 0.55f to Color(0x33000000), 1f to Color(0x14000000)),
                ),
            )

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 40.dp, bottom = 34.dp)
                    .fillMaxWidth(0.42f),
            ) {
                BasicText(
                    m.app.categoryLabel.uppercase(),
                    style = Rea.text(13.sp, FontWeight.SemiBold, Rea.TextSecondary).copy(letterSpacing = 1.4.sp),
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    m.app.label,
                    style = Rea.text(44.sp, FontWeight.Bold, shadow = true),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .fillMaxHeight()
                    .padding(vertical = 18.dp)
                    .aspectRatio(16f / 9f)
                    .shadow(18.dp, RoundedCornerShape(18.dp), clip = false)
                    .clip(RoundedCornerShape(18.dp))
                    .glassEdge(18.dp, strength = 0.8f),
            ) {
                TileArtwork(m.app, art)
            }
        }
    }
}
