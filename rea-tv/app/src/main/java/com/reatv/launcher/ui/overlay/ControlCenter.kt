package com.reatv.launcher.ui.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reatv.launcher.glass.LocalGlassStyle
import com.reatv.launcher.glass.liquidGlass
import com.reatv.launcher.system.MemoryStats
import com.reatv.launcher.system.NetworkState
import com.reatv.launcher.system.StorageStats
import com.reatv.launcher.system.SystemInfo
import com.reatv.launcher.ui.GlassButton
import com.reatv.launcher.ui.Glyph
import com.reatv.launcher.ui.LocalSettings
import com.reatv.launcher.ui.Meter
import com.reatv.launcher.ui.Rea
import com.reatv.launcher.ui.ReaIcon
import com.reatv.launcher.ui.rememberDateText
import com.reatv.launcher.ui.rememberTimeText
import com.reatv.launcher.ui.requestFocusSoon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ControlCenterActions(
    val wifi: () -> Unit,
    val bluetooth: () -> Unit,
    val freeMemory: () -> Unit,
    val storage: () -> Unit,
    val screensaver: () -> Unit,
    val systemSettings: () -> Unit,
    val reaSettings: () -> Unit,
    val stockHome: () -> Unit,
)

@Immutable
private data class Vitals(val memory: MemoryStats?, val storage: StorageStats?, val network: NetworkState)

private class Tile(val icon: ReaIcon, val title: String, val subtitle: String, val tint: Color, val action: () -> Unit)

/** tvOS-style Control Center: a Liquid Glass panel sliding in from the right over the frozen home. */
@Composable
fun AnimatedVisibilityScope.ControlCenter(onDismiss: () -> Unit, actions: ControlCenterActions) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val time by rememberTimeText(settings.clock24h)
    val date by rememberDateText()
    val first = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) { first.requestFocusSoon() }

    val vitals by produceState(Vitals(null, null, NetworkState.Other)) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                Vitals(SystemInfo.memory(context), SystemInfo.storage(), SystemInfo.network(context))
            }
            delay(2000)
        }
    }

    val tiles = listOf(
        Tile(ReaIcon.Wifi, "Network", vitals.network.title, Rea.Accent, actions.wifi),
        Tile(ReaIcon.Bluetooth, "Bluetooth", "Remotes & devices", Rea.Accent, actions.bluetooth),
        Tile(ReaIcon.Bolt, "Free memory", vitals.memory?.let { SystemInfo.formatBytes(it.availableBytes) + " free" } ?: "…", Rea.Positive, actions.freeMemory),
        Tile(ReaIcon.Storage, "Storage", vitals.storage?.let { SystemInfo.formatBytes(it.freeBytes) + " free" } ?: "…", Rea.Warning, actions.storage),
        Tile(ReaIcon.Moon, "Screensaver", "Start now", Color(0xFFBF5AF2), actions.screensaver),
        Tile(ReaIcon.Gear, "Fire TV", "System settings", Color.White, actions.systemSettings),
        Tile(ReaIcon.Sparkle, "REA TV", "Look & feel", Color(0xFF64D2FF), actions.reaSettings),
        Tile(ReaIcon.Home, "Amazon Home", "Open stock home", Color.White, actions.stockHome),
    )

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .animateEnterExit(
                    enter = slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 380f)) { it / 2 },
                    exit = slideOutHorizontally { it / 2 },
                )
                .padding(24.dp)
                .width(352.dp)
                .fillMaxHeight()
                .liquidGlass(34.dp, LocalGlassStyle.current.panel())
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            BasicText(time, style = Rea.text(44.sp, FontWeight.Light))
            BasicText(date, style = Rea.text(15.sp, color = Rea.TextSecondary))
            Spacer(Modifier.height(16.dp))
            tiles.chunked(2).forEachIndexed { r, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { c, tile ->
                        ControlTile(
                            tile = tile,
                            modifier = Modifier.weight(1f),
                            focusRequester = if (r == 0 && c == 0) first else null,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(6.dp))
            vitals.memory?.let { m ->
                VitalRow("Memory", "${SystemInfo.formatBytes(m.usedBytes)} of ${SystemInfo.formatBytes(m.totalBytes)}", m.usedFraction, Rea.Positive)
            }
            Spacer(Modifier.height(10.dp))
            vitals.storage?.let { s ->
                VitalRow("Storage", "${SystemInfo.formatBytes(s.freeBytes)} free", s.usedFraction, Rea.Warning)
            }
        }
    }
}

@Composable
private fun ControlTile(tile: Tile, modifier: Modifier, focusRequester: FocusRequester?) {
    GlassButton(
        onClick = tile.action,
        modifier = modifier.height(62.dp),
        cornerRadius = 18.dp,
        focusRequester = focusRequester,
        scaleOnFocus = 1.06f,
    ) { p ->
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(tile.icon, Modifier.size(22.dp), tint = { Rea.onGlass(p(), tile.tint) })
            Spacer(Modifier.width(10.dp))
            Column {
                BasicText(
                    tile.title,
                    style = Rea.text(14.sp, FontWeight.SemiBold),
                    color = { Rea.onGlass(p()) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    tile.subtitle,
                    style = Rea.text(11.sp),
                    color = { Rea.onGlass(p(), Rea.TextSecondary) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun VitalRow(title: String, value: String, fraction: Float, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            BasicText(title, Modifier.weight(1f), style = Rea.text(13.sp, FontWeight.Medium, Rea.TextSecondary))
            BasicText(value, style = Rea.text(13.sp, color = Rea.TextSecondary))
        }
        Spacer(Modifier.height(6.dp))
        Meter(fraction, color = color)
    }
}
