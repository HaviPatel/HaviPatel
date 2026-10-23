package com.reatv.launcher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reatv.launcher.ui.GlassButton
import com.reatv.launcher.ui.GlassPill
import com.reatv.launcher.ui.Glyph
import com.reatv.launcher.ui.LocalSettings
import com.reatv.launcher.ui.Rea
import com.reatv.launcher.ui.ReaIcon
import com.reatv.launcher.ui.rememberTimeText

/** Top-right glass cluster: Home-button warning (Fire TV only), Control Center, Settings, clock. */
@Composable
fun StatusBar(
    modifier: Modifier,
    interactive: Boolean,
    homeButtonWarning: Boolean,
    onControlCenter: () -> Unit,
    onSettings: () -> Unit,
    onFixHomeButton: () -> Unit,
) {
    val settings = LocalSettings.current
    val time by rememberTimeText(settings.clock24h)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (homeButtonWarning) {
            GlassButton(
                onClick = onFixHomeButton,
                modifier = Modifier.height(44.dp),
                cornerRadius = 22.dp,
                enabled = interactive,
            ) { p ->
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(ReaIcon.Remote, Modifier.size(18.dp), tint = { Rea.onGlass(p(), Rea.Warning) })
                    Spacer(Modifier.width(8.dp))
                    BasicText("Link Home button", style = Rea.text(15.sp, FontWeight.Medium), color = { Rea.onGlass(p()) })
                }
            }
        }
        GlassButton(
            onClick = onControlCenter,
            modifier = Modifier.size(44.dp),
            cornerRadius = 22.dp,
            enabled = interactive,
            scaleOnFocus = 1.12f,
            contentAlignment = Alignment.Center,
        ) { p -> Glyph(ReaIcon.Grid, Modifier.size(18.dp), tint = { Rea.onGlass(p()) }) }
        GlassButton(
            onClick = onSettings,
            modifier = Modifier.size(44.dp),
            cornerRadius = 22.dp,
            enabled = interactive,
            scaleOnFocus = 1.12f,
            contentAlignment = Alignment.Center,
        ) { p -> Glyph(ReaIcon.Gear, Modifier.size(20.dp), tint = { Rea.onGlass(p()) }) }
        GlassPill(Modifier.height(44.dp)) {
            BasicText(time, Modifier.padding(horizontal = 18.dp), style = Rea.text(19.sp, FontWeight.Medium))
        }
    }
}
