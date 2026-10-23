package com.reatv.launcher.system

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * "Free up memory": asks Android to stop the cached / background processes of every app. Foreground,
 * visible and system-bound processes are never touched by this API, so it is safe to run any time.
 */
object Booster {
    private val PROTECTED = setOf("android", "com.android.systemui", "com.android.phone")

    suspend fun freeMemory(context: Context): Long = withContext(Dispatchers.Default) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = SystemInfo.memory(context).availableBytes
        val own = context.packageName
        @Suppress("DEPRECATION")
        val packages = try {
            context.packageManager.getInstalledApplications(0).map { it.packageName }
        } catch (e: RuntimeException) {
            emptyList()
        }
        for (pkg in packages) {
            if (pkg == own || pkg in PROTECTED) continue
            try {
                am.killBackgroundProcesses(pkg)
            } catch (ignored: Exception) {
            }
        }
        delay(700)
        (SystemInfo.memory(context).availableBytes - before).coerceAtLeast(0)
    }
}
