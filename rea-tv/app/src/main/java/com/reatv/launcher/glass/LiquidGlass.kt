package com.reatv.launcher.glass

import android.graphics.BitmapShader
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.max
import kotlin.math.min

/**
 * Look of a Liquid Glass surface. Two tints: [shade] darkens for legibility, [tint] adds the milky
 * light. [lens] magnifies the backdrop (refraction), [saturation] adds the vibrancy real glass has.
 */
@Immutable
data class GlassStyle(
    val shade: Color,
    val tint: Color,
    val lens: Float,
    val saturation: Float,
    val rim: Float,
    val glow: Float,
    val focusTint: Color = Color(0xEBFFFFFF),
    val solid: Color = Color(0xF2232733),
) {
    companion object {
        val Clear = GlassStyle(Color(0x0F000000), Color(0x0FFFFFFF), lens = 0.09f, saturation = 2.0f, rim = 0.70f, glow = 0.12f)
        val Regular = GlassStyle(Color(0x24000000), Color(0x1FFFFFFF), lens = 0.06f, saturation = 1.7f, rim = 0.55f, glow = 0.14f)
        val Frosted = GlassStyle(Color(0x33000000), Color(0x3DFFFFFF), lens = 0.04f, saturation = 1.4f, rim = 0.45f, glow = 0.16f)
    }

    /** Same glass, darker: for large panels carrying lots of text. */
    fun panel(): GlassStyle = copy(shade = Color(0x73080A10), tint = tint.copy(alpha = tint.alpha * 0.7f))
}

/**
 * A single light that every glass surface reflects. It glides toward whatever has focus, so rim
 * highlights and glows sweep across all the glass on screen as you move around: the "liquid" feel.
 */
@Stable
class GlassLight {
    private val anim = Animatable(Offset(480f, -320f), Offset.VectorConverter)
    val position: Offset get() = anim.value

    suspend fun moveTo(target: Offset, instant: Boolean) {
        if (instant) anim.snapTo(target) else anim.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 140f))
    }
}

/** Where the light should go next (set by whatever gains focus, in root pixels). */
@Stable
class LightTarget {
    var target: Offset by mutableStateOf(Offset.Unspecified)
}

val LocalGlassLight = staticCompositionLocalOf { GlassLight() }
val LocalLightTarget = staticCompositionLocalOf { LightTarget() }
val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.Regular }
val LocalReduceTransparency = staticCompositionLocalOf { false }

/**
 * Draws a Liquid Glass surface behind the content: refracted + saturated backdrop, shade/tint,
 * a glow and a rim highlight that both follow [GlassLight], and a top sheen.
 *
 * [emphasis] (0..1, read during draw only) morphs the glass into the bright focused state without
 * recomposing.
 */
fun Modifier.liquidGlass(
    cornerRadius: Dp,
    style: GlassStyle? = null,
    emphasis: (() -> Float)? = null,
): Modifier = this then LiquidGlassElement(cornerRadius, style, emphasis)

private class LiquidGlassElement(
    val cornerRadius: Dp,
    val style: GlassStyle?,
    val emphasis: (() -> Float)?,
) : ModifierNodeElement<LiquidGlassNode>() {
    override fun create() = LiquidGlassNode(cornerRadius, style, emphasis)

    override fun update(node: LiquidGlassNode) {
        node.cornerRadius = cornerRadius
        node.style = style
        node.emphasis = emphasis
    }

    override fun equals(other: Any?): Boolean =
        other is LiquidGlassElement && other.cornerRadius == cornerRadius && other.style == style && other.emphasis === emphasis

    override fun hashCode(): Int {
        var result = cornerRadius.hashCode()
        result = 31 * result + (style?.hashCode() ?: 0)
        result = 31 * result + (emphasis?.hashCode() ?: 0)
        return result
    }
}

private class LiquidGlassNode(
    var cornerRadius: Dp,
    var style: GlassStyle?,
    var emphasis: (() -> Float)?,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var origin = Offset.Zero
    private var rootSize = Size.Zero
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private var filterSaturation = Float.NaN

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val position = coordinates.positionInRoot()
        val root = coordinates.findRootCoordinates().size.toSize()
        if (position != origin || root != rootSize) {
            origin = position
            rootSize = root
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) {
            drawContent()
            return
        }
        val s = style ?: currentValueOf(LocalGlassStyle)
        val solid = currentValueOf(LocalReduceTransparency)
        val e = (emphasis?.invoke() ?: 0f).coerceIn(0f, 1f)
        val r = min(cornerRadius.toPx(), min(w, h) / 2f)
        val corner = CornerRadius(r, r)

        // 1. Refracted, saturated backdrop.
        val blurred = currentValueOf(LocalBackdrop).current?.blurred
        if (!solid && blurred != null && !blurred.isRecycled && rootSize.width > 0f) {
            val lens = 1f + s.lens
            matrix.reset()
            matrix.setScale(rootSize.width / blurred.width, rootSize.height / blurred.height)
            matrix.postTranslate(-origin.x, -origin.y)
            matrix.postScale(lens, lens, w / 2f, h / 2f)
            val shader = BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            if (filterSaturation != s.saturation) {
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(s.saturation) })
                filterSaturation = s.saturation
            }
            drawContext.canvas.nativeCanvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
            paint.shader = null
        } else {
            drawRoundRect(color = s.solid, cornerRadius = corner)
        }

        // 2. Shade + tint, morphing to bright white when focused.
        if (e < 1f) drawRoundRect(color = s.shade.copy(alpha = s.shade.alpha * (1f - e)), cornerRadius = corner)
        drawRoundRect(color = lerp(s.tint, s.focusTint, e), cornerRadius = corner)

        // 3. Light: glow on the side facing the light, rim brightest where the light hits.
        val center = Offset(w / 2f, h / 2f)
        val toLight = currentValueOf(LocalGlassLight).position - origin - center
        val distance = toLight.getDistance()
        val dir = if (distance > 1f) toLight / distance else Offset(-0.6f, -0.8f)
        val glowCenter = Offset(center.x + dir.x * w * 0.45f, center.y + dir.y * h * 0.45f)
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = s.glow * (1f - 0.6f * e)), Color.Transparent),
                center = glowCenter,
                radius = max(w, h) * 0.8f,
            ),
            cornerRadius = corner,
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.10f * (1f - e)),
                0.45f to Color.Transparent,
                startY = 0f,
                endY = h,
            ),
            cornerRadius = corner,
        )

        val stroke = max(1f, 1.2.dp.toPx())
        val half = center.getDistance()
        drawRoundRect(
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = s.rim + 0.25f * e),
                0.42f to Color.White.copy(alpha = 0.07f),
                0.62f to Color.White.copy(alpha = 0.05f),
                1f to Color.White.copy(alpha = s.rim * 0.5f),
                start = center + dir * half,
                end = center - dir * half,
            ),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(w - stroke, h - stroke),
            cornerRadius = CornerRadius(max(0f, r - stroke / 2f)),
            style = Stroke(stroke),
        )

        drawContent()
    }
}
