package com.reatv.launcher.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.reatv.launcher.HomeEvent
import com.reatv.launcher.MainActivity
import com.reatv.launcher.data.AppEntry
import com.reatv.launcher.data.AppOrder
import com.reatv.launcher.data.GlassLevel
import com.reatv.launcher.glass.Aurora
import com.reatv.launcher.glass.BackdropSource
import com.reatv.launcher.glass.Backdrops
import com.reatv.launcher.glass.GlassLight
import com.reatv.launcher.glass.GlassStyle
import com.reatv.launcher.glass.LightTarget
import com.reatv.launcher.glass.LocalBackdrop
import com.reatv.launcher.glass.LocalGlassLight
import com.reatv.launcher.glass.LocalGlassStyle
import com.reatv.launcher.glass.LocalLightTarget
import com.reatv.launcher.glass.LocalReduceTransparency
import com.reatv.launcher.glass.WindowSnapshot
import com.reatv.launcher.rea
import com.reatv.launcher.system.Booster
import com.reatv.launcher.system.HomeRedirect
import com.reatv.launcher.system.SystemInfo
import com.reatv.launcher.system.SystemIntents
import com.reatv.launcher.ui.home.HomeScreen
import com.reatv.launcher.ui.home.HomeState
import com.reatv.launcher.ui.overlay.AppAction
import com.reatv.launcher.ui.overlay.AppMenu
import com.reatv.launcher.ui.overlay.ControlCenter
import com.reatv.launcher.ui.overlay.ControlCenterActions
import com.reatv.launcher.ui.overlay.SettingsActions
import com.reatv.launcher.ui.overlay.SettingsScreen
import com.reatv.launcher.ui.overlay.SettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Overlay {
    data object ControlCenter : Overlay
    data class AppOptions(val app: AppEntry) : Overlay
    data class Settings(val section: SettingsSection) : Overlay
}

@Composable
fun ReaRoot(activity: MainActivity, events: SharedFlow<HomeEvent>) {
    val rea = activity.rea
    val settings = rea.settings
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val allApps by rea.apps.apps.collectAsState()
    val apps = remember(allApps, settings.order, settings.hidden) {
        AppOrder.arrange(allApps, settings.order, settings.hidden)
    }
    val currentApps by rememberUpdatedState(apps)

    val home = remember { HomeState() }
    val sounds = remember(view) { UiSounds(view) }
    val wallpaper = remember { BackdropSource() }
    val overlayBackdrop = remember { BackdropSource() }
    val light = remember { GlassLight() }
    val lightTarget = remember { LightTarget() }
    val toast = remember { ToastState() }
    val isFireTv = remember { SystemInfo.isFireTv(activity) }

    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var openingOverlay by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var settingsSection by remember { mutableStateOf(SettingsSection.Appearance) }
    var homeLinked by remember { mutableStateOf(!isFireTv || HomeRedirect.isLinked(activity)) }

    val glassStyle = when (settings.glass) {
        GlassLevel.Clear -> GlassStyle.Clear
        GlassLevel.Regular -> GlassStyle.Regular
        GlassLevel.Frosted -> GlassStyle.Frosted
    }

    fun show(target: Overlay) {
        when (target) {
            is Overlay.AppOptions -> menuApp = target.app
            is Overlay.Settings -> settingsSection = target.section
            Overlay.ControlCenter -> Unit
        }
        if (overlay != null) {
            overlay = target // keep the existing snapshot: it shows the home, not the previous overlay
            return
        }
        if (openingOverlay) return
        openingOverlay = true
        scope.launch {
            // Freeze what's on screen into a tiny blurred image for the overlay's glass to refract.
            val shot = WindowSnapshot.capture(activity, if (rea.lowRam) 192 else 256)
            overlayBackdrop.current = if (shot != null) {
                withContext(Dispatchers.Default) { Backdrops.snapshot(shot) }
            } else {
                wallpaper.current
            }
            overlay = target
            openingOverlay = false
        }
    }

    fun dismiss() {
        overlay = null
    }

    fun launchApp(app: AppEntry, rect: IntRect) {
        val ok = SystemIntents.launchApp(activity, app, view, rect.left, rect.top, rect.width, rect.height)
        if (ok) home.launchedApp = true else toast.show("Couldn't open ${app.label}")
    }

    fun finishMove() {
        home.movingKey = null
        toast.dismiss()
    }

    fun onAppAction(app: AppEntry, action: AppAction) {
        dismiss()
        val columns = settings.columns.coerceIn(4, 6)
        when (action) {
            AppAction.Open -> launchApp(app, IntRect(view.width / 2 - 1, view.height / 2 - 1, view.width / 2 + 1, view.height / 2 + 1))
            AppAction.Move -> {
                home.movingKey = app.key
                toast.show("Move ${app.label} with the arrows, then press OK")
            }
            AppAction.ToggleTopShelf -> {
                val index = apps.indexOfFirst { it.key == app.key }
                settings.order = AppOrder.move(apps, app.key, if (index in 0 until columns) columns else 0)
            }
            AppAction.Hide -> {
                settings.hidden = settings.hidden + app.key
                toast.show("${app.label} hidden · Settings › Apps to show it again")
            }
            AppAction.Info -> SystemIntents.openAppInfo(activity, app.packageName)
            AppAction.Uninstall -> SystemIntents.uninstall(activity, app.packageName)
        }
    }

    fun handleMoveKey(native: AndroidKeyEvent): Boolean {
        val key = home.movingKey ?: return false
        if (native.action != AndroidKeyEvent.ACTION_DOWN) return true
        val columns = settings.columns.coerceIn(4, 6)
        val delta = when (native.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> -1
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> 1
            AndroidKeyEvent.KEYCODE_DPAD_UP -> -columns
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> columns
            AndroidKeyEvent.KEYCODE_BACK, AndroidKeyEvent.KEYCODE_ESCAPE, AndroidKeyEvent.KEYCODE_BUTTON_B -> {
                finishMove()
                return true
            }
            else -> {
                if (isSelectKey(native.keyCode)) finishMove()
                return true
            }
        }
        // Re-derive the list: key repeats can arrive before the previous move has recomposed.
        val list = AppOrder.arrange(allApps, settings.order, settings.hidden)
        val from = list.indexOfFirst { it.key == key }
        if (from < 0) {
            finishMove()
            return true
        }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to != from) settings.order = AppOrder.move(list, key, to)
        return true
    }

    fun onKey(event: KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        if (native.action == AndroidKeyEvent.ACTION_DOWN) {
            sounds.onDpad(native.keyCode)
            when (native.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> home.lastDirX = -1
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> home.lastDirX = 1
                AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> home.lastDirX = 0
            }
        }
        if (home.movingKey != null) return handleMoveKey(native)
        if (native.keyCode == AndroidKeyEvent.KEYCODE_MENU) {
            if (native.action == AndroidKeyEvent.ACTION_UP) {
                when (overlay) {
                    null -> show(Overlay.ControlCenter)
                    Overlay.ControlCenter -> dismiss()
                    else -> Unit
                }
            }
            return true
        }
        return false
    }

    // --- Effects ---------------------------------------------------------------------------

    LaunchedEffect(Unit) {
        // Our backdrop covers the whole window: drop the window background to save a full-screen fill.
        withFrameNanos { }
        withFrameNanos { }
        activity.window.setBackgroundDrawable(null)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lightTarget.target }.collectLatest { target ->
            if (target.isSpecified) light.moveTo(target, settings.reduceMotion)
        }
    }

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty() && home.focusedKey == null) {
            withFrameNanos { }
            home.requestFocus(apps.first().key)
        }
    }

    // While rearranging, keep focus on the moving tile even when it jumps to another row.
    LaunchedEffect(apps) {
        val moving = home.movingKey ?: return@LaunchedEffect
        withFrameNanos { }
        home.requestFocus(moving)
    }

    // Overlay closed: give focus back to the tile you were on.
    LaunchedEffect(overlay == null) {
        if (overlay == null) {
            withFrameNanos { }
            if (!home.requestFocus(home.focusedKey)) home.requestFocus(currentApps.firstOrNull()?.key)
        }
    }

    LaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                HomeEvent.HomePressed -> {
                    if (home.movingKey != null) finishMove()
                    if (home.launchedApp) {
                        home.launchedApp = false
                    } else {
                        // Home pressed while already home: back to the first app, like tvOS.
                        dismiss()
                        withFrameNanos { }
                        home.requestFocus(currentApps.firstOrNull()?.key)
                    }
                }
                HomeEvent.Resumed -> {
                    homeLinked = !isFireTv || !settings.replaceAmazonHome ||
                        HomeRedirect.isLinked(activity) || HomeRedirect.ensureServiceEnabled(activity)
                }
                HomeEvent.Hidden -> {
                    if (overlay == null) overlayBackdrop.current = null
                }
            }
        }
    }

    BackHandler {
        when {
            home.movingKey != null -> finishMove()
            overlay != null -> dismiss()
            home.focusedRow > 0 -> home.requestFocus(currentApps.getOrNull(home.focusedColumn)?.key ?: currentApps.firstOrNull()?.key)
        }
    }

    // --- UI --------------------------------------------------------------------------------

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth
        val screenH = constraints.maxHeight
        val topApp = home.topApp

        // Wallpaper: a preset aurora, or one tinted by the focused top-row app (debounced).
        LaunchedEffect(settings.wallpaper, settings.dynamicShelf, topApp?.key, screenW, screenH) {
            if (screenW <= 0 || screenH <= 0) return@LaunchedEffect
            val dynamicApp = if (settings.dynamicShelf) topApp else null
            if (dynamicApp != null && wallpaper.current != null) delay(160)
            val palette = if (dynamicApp != null) {
                val art = rea.art.tile(dynamicApp, screenW / 6, screenW / 6 * 9 / 16)
                Aurora.fromColors(art.primary, art.secondary)
            } else {
                Aurora.palette(settings.wallpaper)
            }
            val renderW = if (rea.lowRam) 480 else 640
            val renderH = (renderW.toLong() * screenH / screenW).toInt()
            wallpaper.current = withContext(Dispatchers.Default) {
                Backdrops.aurora(palette, renderW, renderH, if (rea.lowRam) 120 else 160)
            }
        }

        CompositionLocalProvider(
            LocalSettings provides settings,
            LocalArt provides rea.art,
            LocalBackdrop provides wallpaper,
            LocalGlassLight provides light,
            LocalLightTarget provides lightTarget,
            LocalGlassStyle provides glassStyle,
            LocalReduceTransparency provides settings.reduceTransparency,
            LocalUiSounds provides sounds,
        ) {
            Box(Modifier.fillMaxSize().onPreviewKeyEvent { onKey(it) }) {
                WallpaperLayer(wallpaper)

                HomeScreen(
                    apps = apps,
                    state = home,
                    interactive = overlay == null,
                    homeButtonWarning = isFireTv && settings.replaceAmazonHome && !homeLinked,
                    onLaunch = ::launchApp,
                    onOptions = { show(Overlay.AppOptions(it)) },
                    onFixHomeButton = { show(Overlay.Settings(SettingsSection.HomeButton)) },
                )

                CompositionLocalProvider(LocalBackdrop provides overlayBackdrop) {
                    AnimatedVisibility(
                        visible = overlay == Overlay.ControlCenter,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(200)),
                    ) {
                        ControlCenter(
                            onDismiss = ::dismiss,
                            actions = ControlCenterActions(
                                wifi = { dismiss(); SystemIntents.openWifi(activity) },
                                bluetooth = { dismiss(); SystemIntents.openBluetooth(activity) },
                                freeMemory = {
                                    scope.launch {
                                        val freed = Booster.freeMemory(activity)
                                        toast.show("Freed ${SystemInfo.formatBytes(freed)} of memory")
                                    }
                                },
                                storage = { show(Overlay.Settings(SettingsSection.Storage)) },
                                screensaver = { dismiss(); SystemIntents.startScreensaver(activity) },
                                systemSettings = { dismiss(); SystemIntents.openSystemSettings(activity) },
                                reaSettings = { show(Overlay.Settings(SettingsSection.Appearance)) },
                                stockHome = {
                                    dismiss()
                                    if (!SystemIntents.openStockHome(activity)) toast.show("No other home screen on this device")
                                },
                            ),
                        )
                    }

                    AnimatedVisibility(
                        visible = overlay is Overlay.AppOptions,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(160)),
                    ) {
                        menuApp?.let { app ->
                            AppMenu(
                                app = app,
                                inTopRow = apps.indexOfFirst { it.key == app.key } in 0 until settings.columns.coerceIn(4, 6),
                                onDismiss = ::dismiss,
                                onAction = { onAppAction(app, it) },
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = overlay is Overlay.Settings,
                        enter = fadeIn(tween(240)),
                        exit = fadeOut(tween(200)),
                    ) {
                        SettingsScreen(
                            initial = settingsSection,
                            actions = SettingsActions(
                                allApps = { allApps },
                                toast = toast::show,
                                openStockHome = {
                                    dismiss()
                                    if (!SystemIntents.openStockHome(activity)) toast.show("No other home screen on this device")
                                },
                            ),
                            onDismiss = ::dismiss,
                        )
                    }
                }

                ToastHost(toast, Modifier.align(Alignment.TopCenter).padding(top = 26.dp))
            }
        }
    }
}

@Composable
private fun WallpaperLayer(source: BackdropSource) {
    Crossfade(targetState = source.current, animationSpec = tween(700), label = "wallpaper") { backdrop ->
        if (backdrop != null) {
            Image(
                bitmap = backdrop.image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Low,
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
    }
}
