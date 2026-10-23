package com.reatv.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.reatv.launcher.data.ArtLoader
import com.reatv.launcher.data.LauncherSettings

object Rea {
    val Ink = Color(0xFF0E1015)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xB3FFFFFF)
    val TextTertiary = Color(0x80FFFFFF)
    val Warning = Color(0xFFFFB340)
    val Accent = Color(0xFF0A84FF)
    val Positive = Color(0xFF30D158)

    private val TextShadow = Shadow(Color(0x73000000), Offset(0f, 2f), 10f)

    fun text(
        size: TextUnit,
        weight: FontWeight = FontWeight.Normal,
        color: Color = TextPrimary,
        shadow: Boolean = false,
    ) = TextStyle(
        color = color,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = if (size.value >= 28f) (-0.6).sp else 0.sp,
        shadow = if (shadow) TextShadow else null,
    )

    /** Content color on glass: white at rest, ink once the glass turns bright on focus. */
    fun onGlass(progress: Float, rest: Color = TextPrimary): Color = lerp(rest, Ink, progress)
}

val LocalSettings = staticCompositionLocalOf<LauncherSettings> { error("LauncherSettings not provided") }
val LocalArt = staticCompositionLocalOf<ArtLoader> { error("ArtLoader not provided") }
