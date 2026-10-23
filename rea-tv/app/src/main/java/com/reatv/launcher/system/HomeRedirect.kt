package com.reatv.launcher.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings

/**
 * Shared state between the launcher UI and [HomeButtonService].
 *
 * Fire OS has no "default home app" setting and never hands the Home key to third-party apps, so
 * the service watches for Amazon's home activity coming to the front and brings REA TV back.
 */
object HomeRedirect {
    @Volatile
    private var bypassUntil = 0L

    @Volatile
    var serviceConnected = false
        internal set

    /** Let the stock home stay up (the user explicitly opened it) until REA TV is shown again. */
    fun allowStockHomeFor(millis: Long) {
        bypassUntil = SystemClock.elapsedRealtime() + millis
    }

    fun isBypassed(): Boolean = SystemClock.elapsedRealtime() < bypassUntil

    fun clearBypass() {
        bypassUntil = 0L
    }

    fun onLauncherVisible() = clearBypass()

    fun serviceComponent(context: Context) = ComponentName(context, HomeButtonService::class.java)

    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = serviceComponent(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkPermission(Manifest.permission.WRITE_SECURE_SETTINGS, Process.myPid(), Process.myUid()) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Fire OS can drop third-party accessibility services after an app update. If the user granted
     * WRITE_SECURE_SETTINGS over ADB (scripts/rea.sh setup), re-link ourselves silently.
     */
    fun ensureServiceEnabled(context: Context): Boolean {
        if (isServiceEnabled(context)) return true
        if (!canWriteSecureSettings(context)) return false
        return try {
            val resolver = context.contentResolver
            val me = serviceComponent(context).flattenToString()
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val updated = current.split(':').filter { it.isNotBlank() && it != me }.plus(me).joinToString(":")
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun disableService(context: Context): Boolean {
        if (!canWriteSecureSettings(context)) return false
        return try {
            val resolver = context.contentResolver
            val me = serviceComponent(context)
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val updated = current.split(':').filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != me }
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated.joinToString(":"))
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
