package com.reatv.launcher.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.reatv.launcher.data.AppEntry
import com.reatv.launcher.data.TileArt
import com.reatv.launcher.glass.liquidGlass
import com.reatv.launcher.ui.LocalArt
import com.reatv.launcher.ui.Rea

/**
 * The area above the top row. Focusing an app in the top row shows its name and artwork here on a
 * Liquid Glass card, over a wallpaper re-tinted with the app's colors.
 */
@Composable
fun TopShelf(app: AppEntry?, metrics: HomeMetrics, collapse: () -> Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(metrics.firstRowTop)
            .graphicsLayer {
                val c = collapse()
                alpha = 1f - c
                translationY = -40.dp.toPx() * c
            },
    ) {
        Crossfade(
            targetState = app,
            animationSpec = tween(320),
            label = "shelfText",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = metrics.edge, bottom = 30.dp)
                .fillMaxWidth(0.46f),
        ) { current ->
            if (current != null) {
                Column {
                    BasicText(
                        current.categoryLabel.uppercase(),
                        style = Rea.text(13.sp, FontWeight.Medium, Rea.TextSecondary).copy(letterSpacing = 1.6.sp),
                    )
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        current.label,
                        style = Rea.text(40.sp, FontWeight.Bold, shadow = true),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    BasicText(
                        "OK to open  ·  hold OK for options  ·  ☰ for Control Center",
                        style = Rea.text(13.sp, color = Rea.TextTertiary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (app != null) {
            val cardHeight = min(metrics.tileH * 2f, metrics.firstRowTop - 120.dp)
            HeroCard(
                app = app,
                width = cardHeight * (16f / 9f),
                height = cardHeight,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = metrics.edge, bottom = 26.dp),
            )
        }
    }
}

private class HeroModel(val app: AppEntry, val art: TileArt?)

@Composable
private fun HeroCard(app: AppEntry, width: Dp, height: Dp, modifier: Modifier) {
    val artLoader = LocalArt.current
    val density = LocalDensity.current
    val wPx = with(density) { width.roundToPx() }
    val hPx = with(density) { height.roundToPx() }
    val hero by produceState(HeroModel(app, artLoader.peekHero(app.key)), app.key) {
        val cached = artLoader.peekHero(app.key)
        value = HeroModel(app, cached)
        if (cached == null) value = HeroModel(app, artLoader.hero(app, wPx, hPx))
    }
    Box(modifier.size(width, height).liquidGlass(26.dp).padding(9.dp)) {
        Crossfade(targetState = hero, animationSpec = tween(300), label = "heroArt") { model ->
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(19.dp))) {
                TileArtwork(model.app, model.art)
            }
        }
    }
}
