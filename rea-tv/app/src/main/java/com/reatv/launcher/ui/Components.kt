package com.reatv.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.reatv.launcher.glass.GlassStyle
import com.reatv.launcher.glass.LocalBackdrop
import com.reatv.launcher.glass.LocalLightTarget
import com.reatv.launcher.glass.liquidGlass
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The basic focusable glass control. Unfocused it is clear glass; focused it lifts, scales up and
 * turns bright white (tvOS style) while the shared light swings toward it.
 *
 * [content] receives the focus progress (0..1) as a lambda so text/icon colors can follow it in the
 * draw phase without recomposing.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    style: GlassStyle? = null,
    scaleOnFocus: Float = 1.05f,
    restOpacity: Float = 1f,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.(progress: () -> Float) -> Unit,
) {
    val settings = LocalSettings.current
    val lightTarget = LocalLightTarget.current
    val sounds = LocalUiSounds.current
    var focused by remember { mutableStateOf(false) }
    var center by remember { mutableStateOf(Offset.Unspecified) }
    val progress = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (settings.reduceMotion) snap() else spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "glassFocus",
    )
    val progressFn = remember(progress) { { progress.value } }

    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .graphicsLayer {
                val s = 1f + (scaleOnFocus - 1f) * progress.value
                scaleX = s
                scaleY = s
            }
            .liquidGlass(cornerRadius, style, progressFn, restOpacity)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    if (center != Offset.Unspecified) lightTarget.target = center - Offset(0f, 260f)
                    sounds?.onFocusMoved()
                    onFocused?.invoke()
                }
            }
            .tvClickable(onLongClick, onClick)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(enabled),
        contentAlignment = contentAlignment,
    ) {
        content(progressFn)
    }
}

/** A labelled settings/menu row on glass with an optional trailing value. */
@Composable
fun GlassRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ReaIcon? = null,
    value: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    tint: Color = Rea.TextPrimary,
    quiet: Boolean = true,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(if (subtitle != null) 64.dp else 54.dp),
        cornerRadius = 18.dp,
        enabled = enabled,
        focusRequester = focusRequester,
        onFocused = onFocused,
        scaleOnFocus = 1.03f,
        // tvOS lists: rows are plain text until focus turns them into bright glass.
        restOpacity = if (quiet) 0.22f else 1f,
    ) { p ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Glyph(icon, Modifier.size(22.dp), tint = { Rea.onGlass(p(), tint) })
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                BasicText(
                    title,
                    style = Rea.text(17.sp, FontWeight.Medium),
                    color = { Rea.onGlass(p(), tint) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    BasicText(
                        subtitle,
                        style = Rea.text(13.sp),
                        color = { Rea.onGlass(p(), Rea.TextSecondary).copy(alpha = 0.75f) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (value != null) {
                Spacer(Modifier.width(12.dp))
                BasicText(value, style = Rea.text(16.sp), color = { Rea.onGlass(p(), Rea.TextSecondary) }, maxLines = 1)
            }
        }
    }
}

/** Non-focusable glass capsule (clock, toasts, info). */
@Composable
fun GlassPill(modifier: Modifier = Modifier, cornerRadius: Dp = 22.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.liquidGlass(cornerRadius), contentAlignment = Alignment.Center, content = content)
}

/** Thin glass meter bar (RAM / storage). */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier.height(6.dp).fillMaxWidth()) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.14f), cornerRadius = r)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color)),
                size = Size(size.width * f, size.height),
                cornerRadius = r,
            )
        }
    }
}

/** Full-screen frosted copy of whatever was behind (the current [LocalBackdrop]). */
@Composable
fun FrostedBackground(dim: Float = 0.45f) {
    val source = LocalBackdrop.current
    Canvas(Modifier.fillMaxSize()) {
        val backdrop = source.current
        if (backdrop != null && !backdrop.blurred.isRecycled) {
            drawImage(
                backdrop.image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        } else {
            drawRect(Color(0xFF0B0E16))
        }
        drawRect(Color.Black.copy(alpha = dim), topLeft = Offset.Zero, size = size)
    }
}

@Stable
class ToastState {
    var message: String? by mutableStateOf(null)
        private set
    var token by mutableIntStateOf(0)
        private set

    fun show(text: String) {
        message = text
        token++
    }

    fun dismiss() {
        message = null
    }
}

@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val message = state.message
    // Keeps the last text around while the toast animates out.
    val last = remember { arrayOf("") }
    if (message != null) last[0] = message
    LaunchedEffect(state.token) {
        if (state.message != null) {
            delay(2600)
            state.dismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        GlassPill(Modifier.height(48.dp), cornerRadius = 24.dp) {
            BasicText(last[0], Modifier.padding(horizontal = 24.dp), style = Rea.text(16.sp, FontWeight.Medium))
        }
    }
}

@Composable
fun rememberTimeText(is24h: Boolean): State<String> {
    val format = remember(is24h) { SimpleDateFormat(if (is24h) "HH:mm" else "h:mm", Locale.getDefault()) }
    return produceState(format.format(Date()), format) {
        while (true) {
            value = format.format(Date())
            val now = System.currentTimeMillis()
            delay(60_000 - now % 60_000 + 50)
        }
    }
}

@Composable
fun rememberDateText(): State<String> {
    val format = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    return produceState(format.format(Date())) {
        while (true) {
            delay(60_000)
            value = format.format(Date())
        }
    }
}

/** Waits a frame (so the target is laid out) and requests focus, ignoring detached requesters. */
suspend fun FocusRequester.requestFocusSoon() {
    withFrameNanos { }
    try {
        requestFocus()
    } catch (e: IllegalStateException) {
    }
}
