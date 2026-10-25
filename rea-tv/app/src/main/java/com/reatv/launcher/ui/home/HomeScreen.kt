package com.reatv.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.reatv.launcher.data.AppEntry
import com.reatv.launcher.data.shelf.ShelfItem
import com.reatv.launcher.glass.BackdropSource
import com.reatv.launcher.glass.Backdrops
import com.reatv.launcher.glass.LocalBackdrop
import com.reatv.launcher.glass.LocalGlassStyle
import com.reatv.launcher.glass.LocalLightTarget
import com.reatv.launcher.glass.liquidGlass
import com.reatv.launcher.ui.GlassPill
import com.reatv.launcher.ui.LocalSettings
import com.reatv.launcher.ui.Rea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * tvOS-style home: Top Shelf above, then one grid whose first row "owns" the shelf and sits in a
 * Liquid Glass dock that refracts the shelf's art. The grid is a
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
    onOpenShelfItem: (AppEntry, ShelfItem) -> Unit,
    onOptions: (AppEntry) -> Unit,
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

        TopShelf(state.topApp, metrics, state, interactive, collapse, onOpenShelfItem)

        // The dock refracts the shelf's current art, rebuilt (at glass resolution) when it changes.
        val dockGlass = remember { BackdropSource() }
        val screenPx = with(density) { Triple(metrics.width.roundToPx(), metrics.height.roundToPx(), metrics.shelfHeight.roundToPx()) }
        LaunchedEffect(state.shelfArt, screenPx) {
            val art = state.shelfArt ?: return@LaunchedEffect
            dockGlass.current = withContext(Dispatchers.Default) {
                Backdrops.dock(art.image, art.color, screenPx.first, screenPx.second, screenPx.third, blurWidth = 192)
            }
        }

        // Stepping up into the Top Shelf slides the rows down out of its way, as on tvOS.
        val shelfOffsetPx = with(density) { (metrics.tileH * 0.8f).toPx() }
        val shelfOffset by animateFloatAsState(
            if (state.shelfFocused) shelfOffsetPx else 0f,
            if (settings.reduceMotion) spring(stiffness = 2000f) else spring(dampingRatio = 0.85f, stiffness = 300f),
            label = "shelfRows",
        )

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
                .graphicsLayer { translationY = shelfOffset - scroll.value },
        ) {
            Spacer(Modifier.height(metrics.firstRowTop))
            val loadRange = (state.focusedRow - 2)..(state.focusedRow + 3)
            rows.forEachIndexed { r, row ->
                Box(
                    Modifier
                        .height(if (r == 0) metrics.pitch + metrics.dockGap else metrics.pitch)
                        .zIndex(if (r == state.focusedRow) 1f else 0f),
                ) {
                    if (r == 0) Dock(metrics, dockGlass)
                    Row(
                        Modifier.padding(horizontal = metrics.edge),
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
                                    labelGap = if (r == 0) 10.dp + metrics.dockPad else 10.dp,
                                    onClick = {
                                        if (state.movingKey == null && !state.editing) {
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
                                    onLongClick = { if (state.movingKey == null && !state.editing) onOptions(app) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(metrics.height * 0.4f))
        }

        if (homeButtonWarning) {
            HomeButtonChip(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = metrics.height * 0.05f + 16.dp, end = metrics.edge + 16.dp)
                    .graphicsLayer { alpha = 1f - collapse() },
                interactive = interactive,
                onClick = onFixHomeButton,
            )
        }

        if (state.editing) {
            val moving = apps.firstOrNull { it.key == state.movingKey }
            GlassPill(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp)
                    .height(44.dp),
                cornerRadius = 22.dp,
            ) {
                BasicText(
                    if (moving != null) "Moving ${moving.label}  ·  Arrows to move  ·  OK to drop" else "Editing Home Screen  ·  OK picks up an app  ·  Back when done",
                    Modifier.padding(horizontal = 22.dp),
                    style = Rea.text(16.sp, FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The top row's Liquid Glass dock: the row plus a margin, behind the tiles, refracting the shelf. */
@Composable
private fun Dock(metrics: HomeMetrics, glass: BackdropSource) {
    // Until the shelf has shown something, refract the wallpaper like every other glass.
    CompositionLocalProvider(LocalBackdrop provides if (glass.current != null) glass else LocalBackdrop.current) {
        Box(
            Modifier
                .offset(x = metrics.edge - metrics.dockPad, y = -metrics.dockPad)
                .size(metrics.width - (metrics.edge - metrics.dockPad) * 2, metrics.tileH + metrics.dockPad * 2)
                .liquidGlass(metrics.dockCorner, LocalGlassStyle.current.dock()),
        )
    }
}
