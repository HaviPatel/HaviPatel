package com.reatv.launcher.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import com.reatv.launcher.R
import com.reatv.launcher.rea
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Home-button redirect for Fire OS 6, which never binds third-party accessibility services.
 *
 * Follows the system log (READ_LOGS, granted over ADB by scripts/rea.sh setup) for two things:
 * a HOME start aimed at Amazon's launcher (the Home button) and Amazon's home screen resuming (Back
 * out of an app). Either one brings REA TV back through [HomeRedirect.redirect]. One `logcat`
 * child process filtered to two tags, read by one background thread: a few MB and no polling.
 * Runs as a foreground service so Fire OS doesn't stop it.
 */
class HomeWatchService : Service() {
    @Volatile
    private var running = false

    @Volatile
    private var logcat: java.lang.Process? = null

    @Volatile
    private var homes = HomeRedirect.StockHomes(emptySet(), emptySet())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!HomeRedirect.canReadLogs(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            homes = HomeRedirect.stockHomes(this)
            Thread({ follow() }, "rea-home-watch").apply {
                isDaemon = true
                start()
            }
            // Amazon's home is usually already up by the time the boot broadcast arrives.
            if (intent?.getBooleanExtra(EXTRA_BOOT, false) == true && enabled()) HomeRedirect.redirect(this)
        }
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        isRunning = false
        logcat?.destroy()
        super.onDestroy()
    }

    private fun enabled() = rea.settings.replaceAmazonHome

    private fun follow() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        val myUid = Process.myUid()
        var retryMs = RETRY_MIN_MS
        while (running) {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                // Only lines from now on: never react to a Home press that happened before we started.
                val since = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val process = ProcessBuilder(
                    "logcat", "-b", "system", "-b", "events", "-v", "brief", "-T", since,
                    "ActivityManager:I", "am_resume_activity:I", "*:S",
                ).redirectErrorStream(true).start()
                logcat = process
                process.inputStream.bufferedReader().use { reader ->
                    while (running) {
                        val line = reader.readLine() ?: break
                        handle(line, myUid)
                    }
                }
                process.destroy()
            } catch (ignored: Exception) {
            }
            // logcat ended (e.g. log buffer reset): start again. If it keeps dying straight away (a
            // logcat that rejects the arguments), back off instead of respawning every 2 s forever.
            retryMs = if (SystemClock.elapsedRealtime() - startedAt < 10_000) min(retryMs * 2, RETRY_MAX_MS) else RETRY_MIN_MS
            if (running) SystemClock.sleep(retryMs)
        }
    }

    private fun handle(line: String, myUid: Int) {
        val start = LogcatHome.parseHomeStart(line)
        if (start != null) {
            // Our own "open Amazon Home" also starts HOME; only presses from the system count.
            if (start.fromUid == myUid || start.activity.packageName !in homes.packages) return
            HomeRedirect.clearBypass()
            if (enabled()) HomeRedirect.redirect(this)
            return
        }
        val resumed = LogcatHome.parseResume(line) ?: return
        val pkg = resumed.packageName
        if (pkg == packageName) return
        if (pkg !in homes.packages) {
            if (pkg in rea.apps.packages) HomeRedirect.clearBypass()
            return
        }
        if (HomeRedirect.isHomeScreen(resumed.className, homes.classes) && enabled() && !HomeRedirect.isBypassed()) {
            HomeRedirect.redirect(this)
        }
    }

    private fun goForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Home button", NotificationManager.IMPORTANCE_MIN))
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_glyph)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Keeps the Home button on REA TV")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "home"
        private const val NOTIFICATION_ID = 7
        private const val EXTRA_BOOT = "boot"
        private const val RETRY_MIN_MS = 1_000L
        private const val RETRY_MAX_MS = 5 * 60 * 1000L

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context, fromBoot: Boolean = false) {
            if (!HomeRedirect.canReadLogs(context)) return
            val intent = Intent(context, HomeWatchService::class.java).putExtra(EXTRA_BOOT, fromBoot)
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (ignored: Exception) {
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HomeWatchService::class.java))
        }
    }
}
