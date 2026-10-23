package com.reatv.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.reatv.launcher.data.AppEntry
import com.reatv.launcher.glass.LocalLightTarget
import com.reatv.launcher.ui.LocalSettings
import com.reatv.launcher.ui.Rea
import kotlin.math.roundToInt

/**
 * tvOS-style home: Top Shelf above, then one grid whose first row "owns" the shelf. The grid is a
 * plain Column moved with a graphicsLayer translation (no relayout while scrolling); only rows near
 * focus load artwork, so memory stays flat no matter how many apps are installed.
 */
@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    state: HomeState,
    interactive: Boolean,
    homeButtonWarning: Boolean,
    onLaunch: (AppEntry, IntRect) -> Unit,
    onOptions: (AppEntry) -> Unit,
    onControlCenter: () -> Unit,
    onSettings: () -> Unit,
    onFixHomeButton: () -> Unit,
) {
    val settings = LocalSettings.current
    val lightTarget = LocalLightTarget.current
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = settings.columns.coerceIn(4, 6)
        val metrics = remember(maxWidth, maxHeight, columns) { HomeMetrics(maxWidth, maxHeight, columns) }
        val rows = remember(apps, columns) { apps.chunked(columns) }
        val targetDp = metrics.scrollFor(state.focusedRow, rows.size)
        val targetPx = with(density) { targetDp.toPx() }
        val firstRowPx = with(density) { metrics.firstRowTop.toPx() }

        val scroll = remember { Animatable(0f) }
        LaunchedEffect(targetPx, settings.reduceMotion) {
            if (settings.reduceMotion) {
                scroll.snapTo(targetPx)
            } else {
                scroll.animateTo(targetPx, spring(dampingRatio = 1f, stiffness = 230f))
            }
        }
        val collapse = remember(firstRowPx) { { (scroll.value / firstRowPx).coerceIn(0f, 1f) } }

        // The shared glass light hovers above whichever tile has focus.
        LaunchedEffect(state.focusTick, metrics, targetDp) {
            if (state.focusedKey == null) return@LaunchedEffect
            val o = metrics.tileOffset(state.focusedRow, state.focusedColumn, targetDp)
            with(density) {
                lightTarget.target = Offset((o.x + metrics.tileW / 2).toPx(), (o.y - metrics.tileH * 2.5f).toPx())
            }
        }

        TopShelf(state.topApp, metrics, collapse)

        // Dim the wallpaper while browsing deeper rows, like tvOS.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = collapse() * 0.4f }
                .background(Color.Black),
        )

        if (apps.isEmpty()) {
            BasicText(
                "Loading apps…",
                Modifier.align(Alignment.Center),
                style = Rea.text(18.sp, color = Rea.TextSecondary),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = -scroll.value },
        ) {
            Spacer(Modifier.height(metrics.firstRowTop))
            val loadRange = (state.focusedRow - 2)..(state.focusedRow + 3)
            rows.forEachIndexed { r, row ->
                Row(
                    Modifier
                        .padding(horizontal = metrics.edge)
                        .height(metrics.pitch)
                        .zIndex(if (r == state.focusedRow) 1f else 0f),
                    horizontalArrangement = Arrangement.spacedBy(metrics.gap),
                ) {
                    row.forEachIndexed { c, app ->
                        key(app.key) {
                            AppTile(
                                app = app,
                                metrics = metrics,
                                row = r,
                                column = c,
                                state = state,
                                interactive = interactive,
                                loadArt = r in loadRange,
                                onClick = {
                                    if (state.movingKey == null) {
                                        val o = metrics.tileOffset(r, c, targetDp)
                                        val rect = with(density) {
                                            IntRect(
                                                o.x.toPx().roundToInt(),
                                                o.y.toPx().roundToInt(),
                                                (o.x + metrics.tileW).toPx().roundToInt(),
                                                (o.y + metrics.tileH).toPx().roundToInt(),
                                            )
                                        }
                                        onLaunch(app, rect)
                                    }
                                },
                                onLongClick = { if (state.movingKey == null) onOptions(app) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(metrics.height * 0.4f))
        }

        StatusBar(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 22.dp, end = metrics.edge)
                .graphicsLayer { alpha = 1f - collapse() },
            interactive = interactive,
            homeButtonWarning = homeButtonWarning,
            onControlCenter = onControlCenter,
            onSettings = onSettings,
            onFixHomeButton = onFixHomeButton,
        )
    }
}
