package com.reatv.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.reatv.launcher.R
import com.reatv.launcher.data.ArtLoader
import com.reatv.launcher.data.LauncherSettings
import com.reatv.launcher.data.shelf.ShelfContent

object Rea {
    val Ink = Color(0xFF0E1015)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xB3FFFFFF)
    val TextTertiary = Color(0x80FFFFFF)
    val Warning = Color(0xFFFFB340)
    val Accent = Color(0xFF0A84FF)
    val Positive = Color(0xFF30D158)

    private val TextShadow = Shadow(Color(0x73000000), Offset(0f, 2f), 10f)

    /**
     * Inter (OFL), the closest open typeface to Apple's SF Pro, which may not ship outside Apple
     * devices. Latin subset, four weights, ~54 KB each.
     */
    val Family = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

    /** SF-style tracking: display sizes tighten, body text stays neutral. Set [tabular] for clocks. */
    fun text(
        size: TextUnit,
        weight: FontWeight = FontWeight.Normal,
        color: Color = TextPrimary,
        shadow: Boolean = false,
        tabular: Boolean = false,
    ) = TextStyle(
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = Family,
        fontFeatureSettings = if (tabular) "tnum" else null,
        letterSpacing = when {
            size.value >= 40f -> (-0.025).em
            size.value >= 22f -> (-0.015).em
            else -> 0.sp
        },
        shadow = if (shadow) TextShadow else null,
    )

    /** Content color on glass: white at rest, ink once the glass turns bright on focus. */
    fun onGlass(progress: Float, rest: Color = TextPrimary): Color = lerp(rest, Ink, progress)
}

val LocalSettings = staticCompositionLocalOf<LauncherSettings> { error("LauncherSettings not provided") }
val LocalArt = staticCompositionLocalOf<ArtLoader> { error("ArtLoader not provided") }
val LocalShelfContent = staticCompositionLocalOf<ShelfContent> { error("ShelfContent not provided") }
