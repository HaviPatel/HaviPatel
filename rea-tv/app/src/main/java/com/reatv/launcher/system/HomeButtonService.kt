package com.reatv.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.reatv.launcher.rea

/**
 * Home-button redirect for Fire OS 7/8, where third-party accessibility services run. It
 * subscribes only to window-state changes, cannot read screen content, and acts only when Amazon's
 * home *screen* (e.g. `com.amazon.tv.launcher/.ui.HomeActivity_vNext`) comes to the front, so Amazon
 * screens like Settings that live in the same package are left alone.
 *
 * Fire OS 6 never binds third-party accessibility services; there [HomeWatchService] does this job.
 */
class HomeButtonService : AccessibilityService() {
    private var homes = HomeRedirect.StockHomes(emptySet(), emptySet())

    override fun onServiceConnected() {
        super.onServiceConnected()
        HomeRedirect.serviceConnected = true
        homes = HomeRedirect.stockHomes(this)
        // Right after boot the stock home may already be up before we were bound.
        if (SystemClock.elapsedRealtime() < HomeRedirect.BOOT_WINDOW_MS && enabled()) HomeRedirect.redirect(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        if (pkg == packageName) return
        if (pkg !in homes.packages) {
            // A real app came to the front: a later Home press should come back to REA TV.
            if (pkg in rea.apps.packages) HomeRedirect.clearBypass()
            return
        }
        if (!HomeRedirect.isHomeScreen(cls, homes.classes) || !enabled() || HomeRedirect.isBypassed()) return
        HomeRedirect.redirect(this)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        HomeRedirect.serviceConnected = false
        return super.onUnbind(intent)
    }

    private fun enabled() = rea.settings.replaceAmazonHome
}
