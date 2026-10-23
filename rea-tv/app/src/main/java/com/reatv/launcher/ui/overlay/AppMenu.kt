package com.reatv.launcher.ui.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reatv.launcher.data.AppEntry
import com.reatv.launcher.glass.LocalGlassStyle
import com.reatv.launcher.glass.liquidGlass
import com.reatv.launcher.ui.GlassRow
import com.reatv.launcher.ui.LocalArt
import com.reatv.launcher.ui.Rea
import com.reatv.launcher.ui.ReaIcon
import com.reatv.launcher.ui.home.TileArtwork
import com.reatv.launcher.ui.requestFocusSoon

enum class AppAction { Open, Move, ToggleTopShelf, Hide, Info, Uninstall }

/** Hold-OK menu for an app: a glass card that pops from the center. */
@Composable
fun AnimatedVisibilityScope.AppMenu(
    app: AppEntry,
    inTopRow: Boolean,
    onDismiss: () -> Unit,
    onAction: (AppAction) -> Unit,
) {
    val artLoader = LocalArt.current
    val density = LocalDensity.current
    val first = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(app.key) { first.requestFocusSoon() }

    val art by produceState(artLoader.peek(app.key), app.key) {
        if (value == null) {
            value = artLoader.tile(app, with(density) { 112.dp.roundToPx() }, with(density) { 63.dp.roundToPx() })
        }
    }

    val isApp = app.kind == AppEntry.Kind.App
    val items = buildList {
        add(Triple(AppAction.Open, ReaIcon.Play, "Open"))
        add(Triple(AppAction.Move, ReaIcon.Move, "Move"))
        add(Triple(AppAction.ToggleTopShelf, ReaIcon.Star, if (inTopRow) "Remove from Top Shelf" else "Move to Top Shelf"))
        add(Triple(AppAction.Hide, ReaIcon.EyeOff, "Hide from Home"))
        if (isApp) add(Triple(AppAction.Info, ReaIcon.Info, "App info & cache"))
        if (isApp && !app.isSystem) add(Triple(AppAction.Uninstall, ReaIcon.Trash, "Uninstall"))
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .animateEnterExit(
                    enter = scaleIn(spring(dampingRatio = 0.72f, stiffness = 420f), initialScale = 0.88f),
                    exit = scaleOut(targetScale = 0.92f),
                )
                .width(420.dp)
                .liquidGlass(30.dp, LocalGlassStyle.current.panel())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Box(Modifier.size(112.dp, 63.dp).clip(RoundedCornerShape(10.dp))) { TileArtwork(app, art) }
                Spacer(Modifier.width(14.dp))
                Column {
                    BasicText(
                        app.label,
                        style = Rea.text(20.sp, FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(app.categoryLabel, style = Rea.text(13.sp, color = Rea.TextSecondary))
                }
            }
            items.forEachIndexed { i, (action, icon, title) ->
                GlassRow(
                    title = title,
                    icon = icon,
                    onClick = { onAction(action) },
                    focusRequester = if (i == 0) first else null,
                    tint = if (action == AppAction.Uninstall) Color(0xFFFF6961) else Rea.TextPrimary,
                )
            }
        }
    }
}
