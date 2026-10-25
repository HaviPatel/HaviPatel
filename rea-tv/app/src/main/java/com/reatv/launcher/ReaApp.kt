package com.reatv.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.reatv.launcher.data.AppRepository
import com.reatv.launcher.data.ArtLoader
import com.reatv.launcher.data.LauncherSettings
import com.reatv.launcher.data.shelf.ShelfContent
import com.reatv.launcher.system.HomeRedirect
import com.reatv.launcher.system.HomeWatchService
import com.reatv.launcher.system.RecommendationListener
import com.reatv.launcher.system.SystemInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Process-wide singletons. Everything is lazy so the Home-button service starts cheaply at boot. */
class ReaApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val lowRam: Boolean by lazy { SystemInfo.isLowRamDevice(this) }
    val settings by lazy { LauncherSettings(this) }
    val apps by lazy { AppRepository(this) }
    val art by lazy { ArtLoader(this, lowRam) }
    val shelf by lazy { ShelfContent(this, lowRam, settings) }

    override fun onCreate() {
        super.onCreate()
        registerPackageReceiver()
        scope.launch { apps.refresh() }
        if (settings.replaceAmazonHome && SystemInfo.isFireTv(this)) {
            HomeRedirect.ensureServiceEnabled(this) // Fire OS 7/8
            HomeWatchService.start(this) // Fire OS 6 (needs READ_LOGS from scripts/rea.sh setup)
        }
        // Top Shelf series and movies from the apps themselves (needs WRITE_SECURE_SETTINGS from setup).
        if (settings.shelfRecommendations) RecommendationListener.ensureEnabled(this)
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.data?.schemeSpecificPart?.let { art.evict(it) }
                scope.launch { apps.refresh() }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        art.onTrimMemory(level)
        shelf.onTrimMemory(level)
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        super.onLowMemory()
        art.clear()
        shelf.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }
}

val Context.rea: ReaApp get() = applicationContext as ReaApp
