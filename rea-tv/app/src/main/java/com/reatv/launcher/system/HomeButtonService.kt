package com.reatv.launcher.system

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.reatv.launcher.MainActivity
import com.reatv.launcher.data.AppRepository
import com.reatv.launcher.rea

/**
 * Home-button redirect for Fire TV. It subscribes only to window-state changes, cannot read screen
 * content, and acts only when Amazon's home *screen* (e.g. `com.amazon.tv.launcher/.ui.HomeActivity_vNext`)
 * comes to the front, so Amazon screens like Settings that live in the same package are left alone.
 */
class HomeButtonService : AccessibilityService() {
    private var stockHomes: Set<String> = AppRepository.AMAZON_HOMES
    private var homeClasses: Set<String> = emptySet()
    private var lastRedirect = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        HomeRedirect.serviceConnected = true
        refreshHomes()
        // Right after boot the stock home may already be up before we were bound.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS && enabled()) redirect()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        if (pkg == packageName) return
        if (pkg !in stockHomes) {
            // A real app came to the front: a later Home press should come back to REA TV.
            if (pkg in rea.apps.packages) HomeRedirect.clearBypass()
            return
        }
        if (!isHomeScreen(cls) || !enabled() || HomeRedirect.isBypassed()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirect < DEBOUNCE_MS) return
        lastRedirect = now
        redirect()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        HomeRedirect.serviceConnected = false
        return super.onUnbind(intent)
    }

    private fun enabled() = rea.settings.replaceAmazonHome

    private fun refreshHomes() {
        val components = AppRepository.stockHomeComponents(packageManager, packageName)
        stockHomes = components.mapTo(HashSet()) { it.packageName } + AppRepository.AMAZON_HOMES
        homeClasses = components.mapTo(HashSet()) { it.className }
    }

    private fun isHomeScreen(cls: String): Boolean {
        if (cls in homeClasses) return true
        if (cls.startsWith("android.")) return false // dialogs, toasts, popups
        val simple = cls.substringAfterLast('.')
        return simple.contains("Home", ignoreCase = true) || cls.contains("firehomestarter", ignoreCase = true)
    }

    /**
     * After a Home press Android holds activity starts from background apps for ~5 s. An exact alarm
     * is delivered by the system, so its PendingIntent opens REA TV immediately instead.
     */
    private fun redirect() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = PendingIntent.getActivity(this, REQUEST_CODE, intent, flags)
        val alarms = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
                pending.send()
            } else {
                alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), pending)
            }
        } catch (e: Exception) {
            try {
                startActivity(intent)
            } catch (ignored: Exception) {
            }
        }
    }

    private companion object {
        const val REQUEST_CODE = 0x5EA
        const val DEBOUNCE_MS = 700L
        const val BOOT_WINDOW_MS = 3 * 60 * 1000L
    }
}
