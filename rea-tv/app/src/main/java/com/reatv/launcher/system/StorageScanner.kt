package com.reatv.launcher.system

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class AppStorage(val packageName: String, val label: String, val totalBytes: Long, val cacheBytes: Long)

sealed interface StorageScan {
    data class Ready(val apps: List<AppStorage>) : StorageScan
    /** Usage access not granted (run scripts/rea.sh setup) or Android < 8. */
    data object Unavailable : StorageScan
}

object StorageScanner {
    suspend fun scan(context: Context): StorageScan = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 26) return@withContext StorageScan.Unavailable
        val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val pm = context.packageManager
        val user = Process.myUserHandle()
        @Suppress("DEPRECATION")
        val installed = pm.getInstalledApplications(0)
        val result = ArrayList<AppStorage>(installed.size)
        for (ai in installed) {
            try {
                val stats = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, ai.packageName, user)
                val total = stats.appBytes + stats.dataBytes
                if (total <= 0) continue
                result += AppStorage(ai.packageName, ai.loadLabel(pm).toString(), total, stats.cacheBytes)
            } catch (e: SecurityException) {
                return@withContext StorageScan.Unavailable
            } catch (ignored: Exception) {
            }
        }
        StorageScan.Ready(result.sortedByDescending { it.totalBytes })
    }
}
