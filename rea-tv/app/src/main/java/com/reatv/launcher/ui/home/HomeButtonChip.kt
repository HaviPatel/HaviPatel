package com.reatv.launcher.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reatv.launcher.ui.GlassButton
import com.reatv.launcher.ui.Glyph
import com.reatv.launcher.ui.Rea
import com.reatv.launcher.ui.ReaIcon

/**
 * Like tvOS, Home has no clock or buttons: time lives in Control Center (☰). The only thing shown
 * here is a glass chip when the Home button isn't linked yet on a Fire TV.
 */
@Composable
fun HomeButtonChip(modifier: Modifier, interactive: Boolean, onClick: () -> Unit) {
    GlassButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
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
