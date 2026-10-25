package com.reatv.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.reatv.launcher.data.AppEntry
import com.reatv.launcher.data.LabelMode
import com.reatv.launcher.data.TileArt
import com.reatv.launcher.ui.Glyph
import com.reatv.launcher.ui.LocalArt
import com.reatv.launcher.ui.LocalSettings
import com.reatv.launcher.ui.LocalUiSounds
import com.reatv.launcher.ui.Rea
import com.reatv.launcher.ui.ReaIcon
import com.reatv.launcher.ui.tvClickable
import kotlinx.coroutines.launch

/**
 * One app on the home grid. Focus lifts it toward you (scale + deep shadow), tilts it in the
 * direction you arrived from and settles with a spring, and sweeps a specular glint across it.
 * All motion runs in graphicsLayer/draw: nothing recomposes per frame.
 */
@Composable
fun AppTile(
    app: AppEntry,
    metrics: HomeMetrics,
    row: Int,
    column: Int,
    state: HomeState,
    interactive: Boolean,
    loadArt: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    labelGap: Dp = 10.dp,
) {
    val settings = LocalSettings.current
    val sounds = LocalUiSounds.current
    val artLoader = LocalArt.current
    val density = LocalDensity.current
    val widthPx = with(density) { metrics.tileW.roundToPx() }
    val heightPx = with(density) { metrics.tileH.roundToPx() }
    val cornerPx = with(density) { metrics.corner.toPx() }

    // Rows far from focus drop their artwork reference, so memory scales with what's on screen,
    // not with how many apps are installed. The byte-bounded cache makes coming back instant.
    val art by produceState(if (loadArt) artLoader.peek(app.key) else null, app.key, loadArt) {
        value = if (loadArt) artLoader.peek(app.key) ?: artLoader.tile(app, widthPx, heightPx) else null
    }

    val requester = remember { FocusRequester() }
    DisposableEffect(app.key) {
        state.requesters[app.key] = requester
        onDispose { if (state.requesters[app.key] === requester) state.requesters.remove(app.key) }
    }

    var focused by remember { mutableStateOf(false) }
    val reduceMotion = settings.reduceMotion
    val lift = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "lift",
    )
    val tilt = remember { Animatable(0f) }
    val sheen = remember { Animatable(1f) }
    val moving = state.movingKey == app.key
    // Edit Home Screen: every app on screen jiggles, a little out of step with its neighbours like
    // tvOS, and the one you're holding jiggles harder. Rows out of view stay still.
    val jiggles = !reduceMotion && (moving || (state.editing && loadArt))
    val jiggle: State<Float>? = if (jiggles) {
        val amplitude = if (moving) 1.6f else 1.1f
        val period = 118 + ((row * 7 + column * 13) % 5) * 9
        rememberInfiniteTransition(label = "jiggle").animateFloat(
            initialValue = -amplitude,
            targetValue = amplitude,
            animationSpec = infiniteRepeatable(
                tween(period, easing = LinearEasing),
                RepeatMode.Reverse,
                initialStartOffset = StartOffset(if ((row + column) % 2 == 0) 0 else period),
            ),
            label = "jiggleAngle",
        )
    } else {
        null
    }

    LaunchedEffect(focused) {
        if (focused && !reduceMotion) {
            launch {
                tilt.snapTo(state.lastDirX * 9f)
                tilt.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 150f))
            }
            sheen.snapTo(0f)
            sheen.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
    }

    val shape = remember(metrics.corner) { RoundedCornerShape(metrics.corner) }
    val showAlways = settings.labels == LabelMode.Always

    Column(
        modifier = Modifier.width(metrics.tileW).zIndex(if (focused || moving) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(metrics.tileW, metrics.tileH)
                .graphicsLayer {
                    val l = lift.value
                    val s = 1f + 0.13f * l + if (moving) 0.04f else 0f
                    scaleX = s
                    scaleY = s
                    rotationY = tilt.value * l
                    rotationZ = jiggle?.value ?: 0f
                    cameraDistance = 14f * this.density
                    shadowElevation = (1f + 26f * l).dp.toPx()
                    this.shape = shape
                    clip = true
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) {
                        state.onTileFocused(app, row, column)
                        sounds?.onFocusMoved()
                    }
                }
                .tvClickable(onLongClick, onClick)
                .focusRequester(requester)
                .focusable(interactive)
                .drawWithContent {
                    drawContent()
                    drawGloss(lift.value, sheen.value, cornerPx, moving)
                },
        ) {
            TileArtwork(app, art)
        }
        Spacer(Modifier.height(labelGap))
        BasicText(
            text = app.label,
            modifier = Modifier.graphicsLayer {
                val l = lift.value
                alpha = if (showAlways) 0.7f + 0.3f * l else l
                translationY = 7.dp.toPx() * l
            },
            style = Rea.text(15.sp, FontWeight.Medium, shadow = true).copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun ContentDrawScope.drawGloss(lift: Float, sheen: Float, cornerPx: Float, moving: Boolean) {
    val w = size.width
    val h = size.height
    // Layered-glass highlight across the top edge.
    drawRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.05f + 0.12f * lift),
            0.5f to Color.Transparent,
            startY = 0f,
            endY = h,
        ),
    )
    // Specular glint that sweeps across on focus.
    if (lift > 0.01f && sheen < 0.999f) {
        val x = -w * 0.6f + w * 2.2f * sheen
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to Color.White.copy(alpha = 0.26f * lift),
                1f to Color.Transparent,
                start = Offset(x - w * 0.3f, 0f),
                end = Offset(x + w * 0.3f, h),
            ),
        )
    }
    // Rim light.
    val stroke = 1.2.dp.toPx()
    val rim = if (moving) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.10f + 0.45f * lift)
    drawRoundRect(
        brush = Brush.linearGradient(listOf(rim, rim.copy(alpha = rim.alpha * 0.25f)), start = Offset.Zero, end = Offset(w, h)),
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(w - stroke, h - stroke),
        cornerRadius = CornerRadius(cornerPx),
        style = Stroke(stroke),
    )
}

/** Banner art when the app has one; otherwise its icon on a glassy gradient made from its colors. */
@Composable
fun TileArtwork(app: AppEntry, art: TileArt?) {
    val banner = art?.banner
    when {
        app.kind == AppEntry.Kind.SystemSettings -> Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF6B7280), Color(0xFF2A2E37)))),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(ReaIcon.Gear, Modifier.fillMaxHeight(0.5f).aspectRatio(1f))
        }
        banner != null -> Image(
            bitmap = banner,
            contentDescription = app.label,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Low,
        )
        else -> {
            val primary = art?.let { Color(it.primary) } ?: Color(0xFF2A3040)
            val secondary = art?.let { Color(it.secondary) } ?: Color(0xFF161A24)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(lerp(primary, Color.Black, 0.35f), lerp(secondary, Color.Black, 0.7f))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                val icon = art?.icon
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = app.label,
                        modifier = Modifier.fillMaxHeight(0.56f).aspectRatio(1f),
                        filterQuality = FilterQuality.Low,
                    )
                } else {
                    BasicText(
                        app.label.take(1).uppercase(),
                        modifier = Modifier.padding(4.dp),
                        style = Rea.text(34.sp, FontWeight.Bold, Rea.TextSecondary),
                    )
                }
            }
        }
    }
}
