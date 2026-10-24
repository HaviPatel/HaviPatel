package com.reatv.launcher.system

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import com.reatv.launcher.MainActivity
import com.reatv.launcher.data.AppRepository

/**
 * Shared state and the redirect itself, used by the launcher UI, [HomeButtonService] (Fire OS 7/8)
 * and [HomeWatchService] (Fire OS 6).
 *
 * Fire OS has no "default home app" setting and never hands the Home key to third-party apps, so
 * the watchers notice Amazon's home activity coming to the front and bring REA TV back.
 */
object HomeRedirect {
    const val BOOT_WINDOW_MS = 3 * 60 * 1000L
    private const val DEBOUNCE_MS = 700L
    private const val REQUEST_CODE = 0x5EA

    @Volatile
    private var bypassUntil = 0L

    @Volatile
    private var lastRedirect = 0L

    @Volatile
    var serviceConnected = false
        internal set

    /** Packages and activity classes that answer the HOME intent, other than REA TV. */
    class StockHomes(val packages: Set<String>, val classes: Set<String>)

    fun stockHomes(context: Context): StockHomes {
        val components = AppRepository.stockHomeComponents(context.packageManager, context.packageName)
        return StockHomes(
            packages = components.mapTo(HashSet()) { it.packageName } + AppRepository.AMAZON_HOMES,
            classes = components.mapTo(HashSet()) { it.className },
        )
    }

    /** True for the stock home *screen*, false for other activities and popups in the same package. */
    fun isHomeScreen(cls: String, homeClasses: Set<String>): Boolean {
        if (cls in homeClasses) return true
        if (cls.startsWith("android.")) return false // dialogs, toasts, popups
        val simple = cls.substringAfterLast('.')
        return simple.contains("Home", ignoreCase = true) || cls.contains("firehomestarter", ignoreCase = true)
    }

    /**
     * Brings REA TV to the front. After a Home press Android holds activity starts from background
     * apps for ~5 s; an exact alarm is delivered by the system (which may switch apps), so its
     * PendingIntent opens REA TV right away instead. Debounced across both watchers.
     */
    fun redirect(context: Context) {
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (now - lastRedirect < DEBOUNCE_MS) return
            lastRedirect = now
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = PendingIntent.getActivity(context, REQUEST_CODE, intent, flags)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
                pending.send()
            } else {
                alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), pending)
            }
        } catch (e: Exception) {
            try {
                context.startActivity(intent)
            } catch (ignored: Exception) {
            }
        }
    }

    /** READ_LOGS is granted over ADB by scripts/rea.sh setup and powers [HomeWatchService]. */
    fun canReadLogs(context: Context): Boolean =
        context.checkPermission(Manifest.permission.READ_LOGS, Process.myPid(), Process.myUid()) ==
            PackageManager.PERMISSION_GRANTED

    /** Either watcher is able to bring REA TV back when Home opens Amazon's home. */
    fun isLinked(context: Context): Boolean = isServiceEnabled(context) || canReadLogs(context)

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
