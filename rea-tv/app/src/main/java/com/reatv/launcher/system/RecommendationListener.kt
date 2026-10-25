package com.reatv.launcher.system

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.reatv.launcher.data.shelf.RecommendationParser
import com.reatv.launcher.data.shelf.Recommendations
import com.reatv.launcher.data.shelf.ShelfItem
import kotlin.math.roundToInt

/**
 * Collects what apps recommend (series and movies to continue or start) for the Top Shelf, the way
 * Fire TV's own home does: apps post them as "recommendation" notifications. Every other
 * notification is ignored the moment it arrives; nothing is stored or sent anywhere.
 *
 * Fire OS has no screen to grant notification access to a launcher, so REA TV turns this on itself
 * with the WRITE_SECURE_SETTINGS permission that scripts/rea.sh setup grants ([ensureEnabled]).
 */
class RecommendationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        val all = try {
            activeNotifications
        } catch (e: RuntimeException) {
            null
        } ?: return
        Recommendations.replaceAll(all.mapNotNull { entry(it) })
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val e = sbn?.let { entry(it) } ?: return
        Recommendations.put(e)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        Recommendations.remove(sbn.key)
    }

    private fun entry(sbn: StatusBarNotification): Recommendations.Entry? {
        val n = sbn.notification ?: return null
        if (n.category != Recommendations.CATEGORY || sbn.packageName == packageName) return null
        val extras = n.extras ?: return null
        val info = extras.getBundle(RecommendationParser.CONTENT_INFO)
        val parsed = RecommendationParser.parse(lookup(extras), info?.let { lookup(it) }) ?: return null
        val item = ShelfItem(
            key = sbn.key,
            title = parsed.title,
            kind = parsed.kind,
            genres = parsed.genres,
            year = parsed.year,
            rating = parsed.rating,
            description = parsed.description,
            backdropUrl = parsed.backgroundUri,
            card = card(n),
            progress = parsed.progress,
            open = n.contentIntent,
        )
        return Recommendations.Entry(sbn.packageName, sbn.key, item, parsed.rank, parsed.lastWatched, sbn.postTime)
    }

    @Suppress("DEPRECATION")
    private fun lookup(bundle: Bundle): (String) -> Any? = { key ->
        try {
            bundle.get(key)
        } catch (e: RuntimeException) {
            null // a value whose class we can't unparcel
        }
    }

    /** The app's card image, copied down to at most 480 px wide so stored cards stay small. */
    private fun card(n: Notification): Bitmap? {
        val source: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= 23) {
                when (val d = n.getLargeIcon()?.loadDrawable(this)) {
                    null -> null
                    is BitmapDrawable -> d.bitmap
                    else -> {
                        val w = d.intrinsicWidth.coerceIn(1, 480)
                        val h = d.intrinsicHeight.coerceIn(1, 480)
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                            d.setBounds(0, 0, w, h)
                            d.draw(Canvas(it))
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                n.largeIcon ?: n.extras?.getParcelable(Notification.EXTRA_LARGE_ICON)
            }
        } catch (e: RuntimeException) {
            null
        }
        if (source == null || source.width <= 0) return null
        if (source.width <= 480) return source
        val h = (source.height * 480f / source.width).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, 480, h, true)
    }

    companion object {
        private const val SETTING = "enabled_notification_listeners"

        fun component(context: Context) = ComponentName(context, RecommendationListener::class.java)

        fun isEnabled(context: Context): Boolean {
            val me = component(context)
            return Settings.Secure.getString(context.contentResolver, SETTING).orEmpty()
                .split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Turns notification access on for this listener, if setup granted WRITE_SECURE_SETTINGS. */
        fun ensureEnabled(context: Context): Boolean {
            if (isEnabled(context)) return true
            if (!HomeRedirect.canWriteSecureSettings(context)) return false
            return try {
                val resolver = context.contentResolver
                val me = component(context).flattenToString()
                val current = Settings.Secure.getString(resolver, SETTING).orEmpty()
                Settings.Secure.putString(resolver, SETTING, current.split(':').filter { it.isNotBlank() && it != me }.plus(me).joinToString(":"))
                true
            } catch (e: SecurityException) {
                false
            }
        }

        fun disable(context: Context): Boolean {
            if (!HomeRedirect.canWriteSecureSettings(context)) return false
            return try {
                val resolver = context.contentResolver
                val me = component(context)
                val current = Settings.Secure.getString(resolver, SETTING).orEmpty()
                Settings.Secure.putString(
                    resolver,
                    SETTING,
                    current.split(':').filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != me }.joinToString(":"),
                )
                Recommendations.replaceAll(emptyList())
                true
            } catch (e: SecurityException) {
                false
            }
        }
    }
}
