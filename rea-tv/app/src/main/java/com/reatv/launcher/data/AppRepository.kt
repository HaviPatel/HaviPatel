package com.reatv.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Every launchable app (TV and phone style), plus a synthetic system Settings tile. */
class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    /** Package names of the current list; read by the Home-button service. */
    @Volatile
    var packages: Set<String> = emptySet()
        private set

    suspend fun refresh() {
        val list = withContext(Dispatchers.IO) { query() }
        packages = list.mapTo(HashSet()) { it.packageName }
        _apps.value = list
    }

    private fun query(): List<AppEntry> {
        val own = context.packageName
        val homes = stockHomePackages(pm, own)
        val result = LinkedHashMap<String, AppEntry>()

        fun collect(category: String) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val infos: List<ResolveInfo> = try {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            } catch (e: RuntimeException) {
                emptyList()
            }
            for (ri in infos) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName
                if (pkg == own || pkg in homes || pkg in SETTINGS_PACKAGES || result.containsKey(pkg)) continue
                val appInfo = ai.applicationInfo
                if (!ai.enabled || !appInfo.enabled) continue
                result[pkg] = AppEntry(
                    key = pkg,
                    label = ri.loadLabel(pm)?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: pkg,
                    packageName = pkg,
                    component = ComponentName(pkg, ai.name),
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    category = if (Build.VERSION.SDK_INT >= 26) appInfo.category else -1,
                )
            }
        }

        collect(Intent.CATEGORY_LEANBACK_LAUNCHER)
        collect(Intent.CATEGORY_LAUNCHER)
        result[SETTINGS_KEY] = AppEntry(
            key = SETTINGS_KEY,
            label = "Settings",
            packageName = "",
            component = null,
            isSystem = true,
            category = -1,
            kind = AppEntry.Kind.SystemSettings,
        )
        return result.values.toList()
    }

    companion object {
        const val SETTINGS_KEY = "rea:settings"

        /** Amazon's home packages are protected on Fire OS; they're always treated as "stock home". */
        val AMAZON_HOMES = setOf("com.amazon.tv.launcher", "com.amazon.firehomestarter")

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.android.tv.settings",
            "com.amazon.tv.settings.v2",
        )

        fun stockHomeComponents(pm: PackageManager, own: String): List<ComponentName> {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            return try {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
                    .mapNotNull { it.activityInfo }
                    .filter { it.packageName != own }
                    .map { ComponentName(it.packageName, it.name) }
            } catch (e: RuntimeException) {
                emptyList()
            }
        }

        fun stockHomePackages(pm: PackageManager, own: String): Set<String> =
            stockHomeComponents(pm, own).mapTo(HashSet()) { it.packageName } + AMAZON_HOMES
    }
}
