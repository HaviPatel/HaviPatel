package com.reatv.launcher.system

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Immutable

@Immutable
data class MemoryStats(val totalBytes: Long, val availableBytes: Long, val low: Boolean) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

@Immutable
data class StorageStats(val totalBytes: Long, val freeBytes: Long) {
    val usedFraction: Float get() = if (totalBytes > 0) (totalBytes - freeBytes).toFloat() / totalBytes else 0f
}

enum class NetworkState(val title: String) { WiFi("Wi-Fi"), Ethernet("Ethernet"), Other("Connected"), Offline("Offline") }

object SystemInfo {
    fun memory(context: Context): MemoryStats {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return MemoryStats(info.totalMem, info.availMem, info.lowMemory)
    }

    fun storage(): StorageStats {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageStats(stat.totalBytes, stat.availableBytes)
    }

    /** Sticks with 1.5 GB or less (Lite, 4K 1st gen, and older) get smaller buffers and caches. */
    fun isLowRamDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.isLowRamDevice || memory(context).totalBytes < 1_600L * 1024 * 1024
    }

    fun isFireTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature("amazon.hardware.fire_tv") ||
            Build.MANUFACTURER.equals("Amazon", ignoreCase = true)

    fun osName(context: Context): String {
        val android = "Android ${Build.VERSION.RELEASE}"
        if (!isFireTv(context)) return android
        val fire = when {
            Build.VERSION.SDK_INT >= 30 -> "Fire OS 8"
            Build.VERSION.SDK_INT >= 28 -> "Fire OS 7"
            Build.VERSION.SDK_INT >= 25 -> "Fire OS 6"
            else -> "Fire OS 5"
        }
        return "$fire ($android)"
    }

    @Suppress("DEPRECATION")
    fun network(context: Context): NetworkState {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= 23) {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkState.Offline
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState.WiFi
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkState.Ethernet
                else -> NetworkState.Other
            }
        }
        val info = cm.activeNetworkInfo ?: return NetworkState.Offline
        if (!info.isConnected) return NetworkState.Offline
        return when (info.type) {
            ConnectivityManager.TYPE_WIFI -> NetworkState.WiFi
            ConnectivityManager.TYPE_ETHERNET -> NetworkState.Ethernet
            else -> NetworkState.Other
        }
    }

    /** Approximate memory REA TV itself is using (Java heap + native allocations). */
    fun ownFootprintBytes(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) + Debug.getNativeHeapAllocatedSize()
    }

    fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024 * 1024)
        if (gb >= 1) return if (gb >= 10) "%.0f GB".format(gb) else "%.1f GB".format(gb)
        val mb = bytes / (1024.0 * 1024)
        return "%.0f MB".format(mb)
    }
}
