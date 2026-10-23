package com.reatv.launcher.ui.overlay

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reatv.launcher.BuildConfig
import com.reatv.launcher.data.AppEntry
import com.reatv.launcher.data.AppOrder
import com.reatv.launcher.data.GlassLevel
import com.reatv.launcher.data.LabelMode
import com.reatv.launcher.data.WallpaperStyle
import com.reatv.launcher.glass.GlassStyle
import com.reatv.launcher.glass.liquidGlass
import com.reatv.launcher.rea
import com.reatv.launcher.system.Booster
import com.reatv.launcher.system.HomeRedirect
import com.reatv.launcher.system.StorageScan
import com.reatv.launcher.system.StorageScanner
import com.reatv.launcher.system.SystemInfo
import com.reatv.launcher.system.SystemIntents
import com.reatv.launcher.ui.FrostedBackground
import com.reatv.launcher.ui.GlassRow
import com.reatv.launcher.ui.Glyph
import com.reatv.launcher.ui.LocalSettings
import com.reatv.launcher.ui.Rea
import com.reatv.launcher.ui.ReaIcon
import com.reatv.launcher.ui.requestFocusSoon
import com.reatv.launcher.ui.tvClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SettingsSection(val title: String, val icon: ReaIcon) {
    Appearance("Appearance", ReaIcon.Sparkle),
    Glass("Glass & Motion", ReaIcon.Display),
    HomeButton("Home Button", ReaIcon.Remote),
    Apps("Apps", ReaIcon.Apps),
    Performance("Performance", ReaIcon.Speed),
    Storage("Storage", ReaIcon.Storage),
    About("About", ReaIcon.Info),
}

class SettingsActions(
    val allApps: () -> List<AppEntry>,
    val toast: (String) -> Unit,
    val openStockHome: () -> Unit,
)

@Composable
fun AnimatedVisibilityScope.SettingsScreen(
    initial: SettingsSection,
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
    var section by remember(initial) { mutableStateOf(initial) }
    var navTick by remember { mutableIntStateOf(0) }
    val categories = remember { SettingsSection.entries.associateWith { FocusRequester() } }
    val contentFirst = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(initial) {
        if (initial == SettingsSection.Appearance) categories.getValue(initial).requestFocusSoon() else contentFirst.requestFocusSoon()
    }
    LaunchedEffect(navTick) { if (navTick > 0) contentFirst.requestFocusSoon() }

    Box(Modifier.fillMaxSize()) {
        FrostedBackground(dim = 0.5f)
        Row(
            Modifier
                .fillMaxSize()
                .animateEnterExit(enter = scaleIn(initialScale = 0.96f), exit = scaleOut(targetScale = 0.96f))
                .padding(horizontal = 56.dp, vertical = 34.dp),
        ) {
            Column(Modifier.width(250.dp).fillMaxHeight()) {
                BasicText("Settings", style = Rea.text(34.sp, FontWeight.Bold, shadow = true))
                Spacer(Modifier.height(22.dp))
                CategoryList(
                    selected = section,
                    onSelect = { section = it },
                    requesters = categories,
                    contentFirst = contentFirst,
                )
            }
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                BasicText(section.title, style = Rea.text(22.sp, FontWeight.SemiBold, Rea.TextSecondary))
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .focusProperties { left = categories.getValue(section) }
                        .padding(end = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionContent(
                        section = section,
                        first = contentFirst,
                        actions = actions,
                        onNavigate = {
                            section = it
                            navTick++
                        },
                    )
                }
            }
        }
    }
}

/** Category list with a Liquid Glass lens that slides to the selection and stretches with its speed. */
@Composable
private fun CategoryList(
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
    requesters: Map<SettingsSection, FocusRequester>,
    contentFirst: FocusRequester,
) {
    val density = LocalDensity.current
    val reduceMotion = LocalSettings.current.reduceMotion
    val rowHeight = 48.dp
    val gap = 6.dp
    val pitchPx = with(density) { (rowHeight + gap).toPx() }
    val rowPx = with(density) { rowHeight.toPx() }
    val index = selected.ordinal
    val y = remember { Animatable(index * pitchPx) }
    LaunchedEffect(index) {
        if (reduceMotion) y.snapTo(index * pitchPx) else y.animateTo(index * pitchPx, spring(dampingRatio = 0.66f, stiffness = 340f))
    }
    var listFocused by remember { mutableStateOf(false) }
    val emphasis = animateFloatAsState(if (listFocused) 1f else 0f, label = "lens")
    val emphasisFn = remember(emphasis) { { emphasis.value } }

    Box(Modifier.onFocusChanged { listFocused = it.hasFocus }) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val stretch = (abs(y.velocity) * 0.03f).coerceAtMost(rowPx * 0.8f)
                    val squeeze = (stretch * 0.15f).roundToInt()
                    val width = (constraints.maxWidth - squeeze).coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(width, (rowPx + stretch).roundToInt()))
                    layout(constraints.maxWidth, rowPx.roundToInt()) {
                        placeable.place(squeeze / 2, (y.value - stretch / 2f).roundToInt())
                    }
                }
                .liquidGlass(24.dp, emphasis = emphasisFn),
        )
        Column(
            Modifier.focusProperties { right = contentFirst },
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            SettingsSection.entries.forEach { s ->
                val isSelected = s == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .onFocusChanged { if (it.isFocused) onSelect(s) }
                        .tvClickable {
                            try {
                                contentFirst.requestFocus()
                            } catch (e: IllegalStateException) {
                            }
                        }
                        .focusRequester(requesters.getValue(s))
                        .focusable()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(s.icon, Modifier.size(20.dp), tint = { if (isSelected) Rea.onGlass(emphasisFn()) else Rea.TextSecondary })
                    Spacer(Modifier.width(12.dp))
                    BasicText(
                        s.title,
                        style = Rea.text(17.sp, FontWeight.Medium),
                        color = { if (isSelected) Rea.onGlass(emphasisFn()) else Rea.TextSecondary },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionContent(
    section: SettingsSection,
    first: FocusRequester,
    actions: SettingsActions,
    onNavigate: (SettingsSection) -> Unit,
) {
    when (section) {
        SettingsSection.Appearance -> AppearanceSection(first)
        SettingsSection.Glass -> GlassSection(first)
        SettingsSection.HomeButton -> HomeButtonSection(first, actions)
        SettingsSection.Apps -> AppsSection(first, actions)
        SettingsSection.Performance -> PerformanceSection(first, onNavigate)
        SettingsSection.Storage -> StorageSection(first, actions)
        SettingsSection.About -> AboutSection(first)
    }
}

private fun onOff(value: Boolean) = if (value) "On" else "Off"

private fun <E : Enum<E>> next(current: E, all: List<E>): E = all[(current.ordinal + 1) % all.size]

@Composable
private fun AppearanceSection(first: FocusRequester) {
    val s = LocalSettings.current
    GlassRow("Wallpaper", icon = ReaIcon.Sparkle, value = s.wallpaper.title, focusRequester = first, onClick = {
        s.wallpaper = next(s.wallpaper, WallpaperStyle.entries)
    })
    GlassRow("Top Shelf colors", subtitle = "Tint the wallpaper with the focused top-row app", value = onOff(s.dynamicShelf), onClick = {
        s.dynamicShelf = !s.dynamicShelf
    })
    GlassRow("Icon size", value = when (s.columns) { 4 -> "Large"; 6 -> "Small"; else -> "Medium" }, onClick = {
        s.columns = when (s.columns) { 5 -> 4; 4 -> 6; else -> 5 }
    })
    GlassRow("App names", value = s.labels.title, onClick = { s.labels = next(s.labels, LabelMode.entries) })
    GlassRow("24-hour clock", value = onOff(s.clock24h), onClick = { s.clock24h = !s.clock24h })
}

@Composable
private fun GlassSection(first: FocusRequester) {
    val s = LocalSettings.current
    GlassRow("Glass", icon = ReaIcon.Display, subtitle = "How much of the background shows through", value = s.glass.title, focusRequester = first, onClick = {
        s.glass = next(s.glass, GlassLevel.entries)
    })
    GlassRow("Reduce transparency", subtitle = "Solid panels: the lightest option for the GPU", value = onOff(s.reduceTransparency), onClick = {
        s.reduceTransparency = !s.reduceTransparency
    })
    GlassRow("Reduce motion", subtitle = "No tilt, glints or springy movement", value = onOff(s.reduceMotion), onClick = {
        s.reduceMotion = !s.reduceMotion
    })
    Spacer(Modifier.height(6.dp))
    SectionLabel("Preview")
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("Clear" to GlassStyle.Clear, "Regular" to GlassStyle.Regular, "Frosted" to GlassStyle.Frosted).forEach { (name, style) ->
            Box(Modifier.size(150.dp, 84.dp).liquidGlass(20.dp, style), contentAlignment = Alignment.Center) {
                BasicText(name, style = Rea.text(15.sp, FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun HomeButtonSection(first: FocusRequester, actions: SettingsActions) {
    val context = LocalContext.current
    val s = LocalSettings.current
    val isFire = remember { SystemInfo.isFireTv(context) }
    val linked by produceState(HomeRedirect.isServiceEnabled(context)) {
        while (true) {
            value = HomeRedirect.isServiceEnabled(context)
            delay(1500)
        }
    }
    GlassRow(
        "Status",
        icon = ReaIcon.Remote,
        value = when {
            !isFire -> "Use system setting"
            linked -> "Linked"
            else -> "Not linked"
        },
        subtitle = if (isFire) "Home returns to REA TV instead of Amazon's home" else "Choose REA TV as the Home app",
        tint = if (isFire && !linked) Rea.Warning else Rea.TextPrimary,
        focusRequester = first,
        onClick = { if (!isFire) SystemIntents.openHomeSettings(context) },
    )
    if (isFire) {
        GlassRow("Replace Amazon Home", value = onOff(s.replaceAmazonHome), onClick = {
            s.replaceAmazonHome = !s.replaceAmazonHome
            // With the ADB-granted permission, also switch the service itself so Fire OS apps
            // stop seeing an active accessibility service when it isn't wanted.
            if (s.replaceAmazonHome) HomeRedirect.ensureServiceEnabled(context) else HomeRedirect.disableService(context)
        })
        if (!linked && HomeRedirect.canWriteSecureSettings(context)) {
            GlassRow("Link now", icon = ReaIcon.Check, onClick = {
                actions.toast(if (HomeRedirect.ensureServiceEnabled(context)) "Home button linked" else "Couldn't link. Run scripts/rea.sh setup")
            })
        }
        GlassRow("Accessibility settings", subtitle = "Turn on “REA TV Home button” if it is listed", onClick = {
            SystemIntents.openAccessibility(context)
        })
        GlassRow("Open Amazon Home", icon = ReaIcon.Home, subtitle = "Stays until you come back to REA TV", onClick = actions.openStockHome)
        InfoText(
            "Fire OS has no setting to change the Home app and never gives other apps the Home key. " +
                "REA TV watches only for Amazon's home screen and brings you back. Link it once from a " +
                "computer with: scripts/rea.sh setup",
        )
    } else {
        GlassRow("Home app settings", onClick = { SystemIntents.openHomeSettings(context) })
    }
}

@Composable
private fun AppsSection(first: FocusRequester, actions: SettingsActions) {
    val s = LocalSettings.current
    val all = actions.allApps()
    var confirmReset by remember { mutableStateOf(false) }
    GlassRow("Sort A–Z", icon = ReaIcon.Apps, subtitle = "Arrange every app alphabetically", focusRequester = first, onClick = {
        s.order = AppOrder.alphabetical(all.filter { it.key !in s.hidden })
        actions.toast("Apps sorted A–Z")
    })
    GlassRow(
        if (confirmReset) "Press again to reset" else "Reset home layout",
        subtitle = "Default order and unhide everything",
        tint = if (confirmReset) Rea.Warning else Rea.TextPrimary,
        onClick = {
            if (confirmReset) {
                s.resetLayout()
                confirmReset = false
                actions.toast("Home layout reset")
            } else {
                confirmReset = true
            }
        },
    )
    Spacer(Modifier.height(6.dp))
    SectionLabel("Hidden apps")
    val hidden = s.hidden
    if (hidden.isEmpty()) {
        InfoText("Nothing hidden. Hold OK on an app and choose Hide from Home.")
    }
    hidden.forEach { key ->
        val app = all.firstOrNull { it.key == key }
        GlassRow(app?.label ?: key, icon = ReaIcon.EyeOff, value = "Show", onClick = { s.hidden = s.hidden - key })
    }
}

@Composable
private fun PerformanceSection(first: FocusRequester, onNavigate: (SettingsSection) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var freed by remember { mutableStateOf<String?>(null) }
    val memory by produceState(SystemInfo.memory(context)) {
        while (true) {
            delay(2000)
            value = SystemInfo.memory(context)
        }
    }
    GlassRow("Free up memory", icon = ReaIcon.Bolt, subtitle = "Stops apps cached in the background", value = freed, focusRequester = first, onClick = {
        freed = "Working…"
        scope.launch { freed = "+" + SystemInfo.formatBytes(Booster.freeMemory(context)) }
    })
    GlassRow(
        "Memory in use",
        value = "${SystemInfo.formatBytes(memory.usedBytes)} of ${SystemInfo.formatBytes(memory.totalBytes)}",
        subtitle = "${(memory.usedFraction * 100).roundToInt()}% used" + if (memory.low) " · system is low on memory" else "",
        onClick = {},
    )
    GlassRow(
        "REA TV footprint",
        value = SystemInfo.formatBytes(SystemInfo.ownFootprintBytes()),
        subtitle = "Artwork is released while another app is open",
        onClick = {},
    )
    GlassRow(
        "Low-memory mode",
        value = if (context.rea.lowRam) "On (auto)" else "Off (auto)",
        subtitle = "Smaller caches and buffers on 1 GB devices",
        onClick = {},
    )
    GlassRow("Storage", icon = ReaIcon.Storage, subtitle = "See which apps use the most space", onClick = { onNavigate(SettingsSection.Storage) })
    InfoText(
        "Go further from a computer: scripts/rea.sh optimize (faster animations, AOT compile, cache trim) " +
            "and scripts/rea.sh debloat (turn off Amazon ads, telemetry and unused apps, fully reversible).",
    )
}

@Composable
private fun StorageSection(first: FocusRequester, actions: SettingsActions) {
    val context = LocalContext.current
    val scan by produceState<StorageScan?>(null) { value = StorageScanner.scan(context) }
    val storage = remember { SystemInfo.storage() }
    GlassRow(
        "Internal storage",
        icon = ReaIcon.Storage,
        value = "${SystemInfo.formatBytes(storage.freeBytes)} free",
        subtitle = "of ${SystemInfo.formatBytes(storage.totalBytes)}",
        focusRequester = first,
        onClick = {},
    )
    when (val result = scan) {
        null -> InfoText("Measuring apps…")
        StorageScan.Unavailable -> {
            InfoText("Per-app sizes need Usage Access. Run scripts/rea.sh setup once, or allow it here if your device lists it.")
            GlassRow("Usage access settings", onClick = {
                if (!SystemIntents.openUsageAccess(context)) actions.toast("Not available here. Use scripts/rea.sh setup")
            })
        }
        is StorageScan.Ready -> {
            result.apps.take(40).forEach { app ->
                GlassRow(
                    app.label,
                    value = SystemInfo.formatBytes(app.totalBytes),
                    subtitle = "Cache ${SystemInfo.formatBytes(app.cacheBytes)}",
                    onClick = { SystemIntents.openAppInfo(context, app.packageName) },
                )
            }
        }
    }
    InfoText("Open an app to clear its cache, or trim every cache at once with scripts/rea.sh clean.")
}

@Composable
private fun AboutSection(first: FocusRequester) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val memory = remember { SystemInfo.memory(context) }
    GlassRow("REA TV", icon = ReaIcon.Info, value = BuildConfig.VERSION_NAME, focusRequester = first, onClick = {})
    GlassRow("Device", value = "${Build.MANUFACTURER} ${Build.MODEL}", onClick = {})
    GlassRow("System", value = SystemInfo.osName(context), onClick = {})
    GlassRow("Display", value = "${metrics.widthPixels} × ${metrics.heightPixels}", onClick = {})
    GlassRow("Memory", value = SystemInfo.formatBytes(memory.totalBytes), onClick = {})
    GlassRow("Privacy", value = "Offline", subtitle = "No internet permission, no analytics, no accounts", onClick = {})
    InfoText("An independent launcher inspired by the tvOS design language. Not affiliated with Apple or Amazon.")
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(
        text.uppercase(),
        Modifier.padding(start = 6.dp, top = 4.dp),
        style = Rea.text(12.sp, FontWeight.SemiBold, Rea.TextTertiary).copy(letterSpacing = 1.4.sp),
    )
}

@Composable
private fun InfoText(text: String) {
    BasicText(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        style = Rea.text(14.sp, color = Rea.TextSecondary).copy(lineHeight = 20.sp),
    )
}
