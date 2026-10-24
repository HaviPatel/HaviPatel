package com.reatv.launcher.system

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import com.reatv.launcher.data.AppEntry
import com.reatv.launcher.data.AppRepository

/** Every outbound intent REA TV fires, each with a graceful fallback for Fire OS quirks. */
object SystemIntents {
    fun launchApp(context: Context, app: AppEntry, source: View?, x: Int, y: Int, w: Int, h: Int): Boolean {
        if (app.kind == AppEntry.Kind.SystemSettings) return openSystemSettings(context)
        val pm = context.packageManager
        val intent = app.component?.let { Intent.makeMainActivity(it) }
            ?: pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val options = source?.let { ActivityOptions.makeScaleUpAnimation(it, x, y, w, h).toBundle() }
        return try {
            context.startActivity(intent, options)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * On Fire OS 6, Settings is a row inside Amazon's home screen, so the Home watchers are told to
     * leave Amazon's home alone; pressing Home brings REA TV back as usual.
     */
    fun openSystemSettings(context: Context): Boolean {
        HomeRedirect.allowStockHomeFor(10 * 60 * 1000L)
        return start(context, Intent(Settings.ACTION_SETTINGS))
    }

    fun openWifi(context: Context) = start(context, Intent(Settings.ACTION_WIFI_SETTINGS)) || openSystemSettings(context)

    fun openBluetooth(context: Context) =
        start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) || openSystemSettings(context)

    fun openAccessibility(context: Context) =
        start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) || openSystemSettings(context)

    fun openUsageAccess(context: Context) = start(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openHomeSettings(context: Context) = start(context, Intent(Settings.ACTION_HOME_SETTINGS)) || openSystemSettings(context)

    fun openAppInfo(context: Context, packageName: String) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

    fun uninstall(context: Context, packageName: String) =
        start(context, Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))) ||
            openAppInfo(context, packageName)

    /** Starts the screensaver (daydream) right away, falling back to its settings page. */
    fun startScreensaver(context: Context): Boolean {
        val dream = Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
        return start(context, dream) || start(context, Intent(Settings.ACTION_DREAM_SETTINGS)) || openSystemSettings(context)
    }

    /** Opens Amazon's (or the stock) home screen and tells the redirect service to leave it alone. */
    fun openStockHome(context: Context): Boolean {
        val home = AppRepository.stockHomeComponents(context.packageManager, context.packageName).firstOrNull()
            ?: return false
        HomeRedirect.allowStockHomeFor(10 * 60 * 1000L)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(home)
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
