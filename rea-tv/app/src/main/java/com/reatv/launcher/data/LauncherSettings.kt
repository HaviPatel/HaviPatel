package com.reatv.launcher.data

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class WallpaperStyle(val title: String) {
    Aurora("Aurora"), Ocean("Ocean"), Sunset("Sunset"), Forest("Forest"), Graphite("Graphite"), Midnight("Midnight")
}

enum class GlassLevel(val title: String) { Clear("Clear"), Regular("Regular"), Frosted("Frosted") }

enum class LabelMode(val title: String) { Focused("When focused"), Always("Always") }

/**
 * SharedPreferences exposed as Compose state: reading a property in a composable subscribes to it,
 * writing it persists asynchronously.
 */
class LauncherSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rea_settings", Context.MODE_PRIVATE)

    var wallpaper by enumPref("wallpaper", WallpaperStyle.Aurora, WallpaperStyle.entries)
    var glass by enumPref("glass", GlassLevel.Regular, GlassLevel.entries)
    var labels by enumPref("labels", LabelMode.Focused, LabelMode.entries)
    var dynamicShelf by boolPref("dynamic_shelf", true)
    var columns by intPref("columns", 5)
    var clock24h by boolPref("clock_24h", DateFormat.is24HourFormat(context))
    var reduceMotion by boolPref("reduce_motion", false)
    var reduceTransparency by boolPref("reduce_transparency", false)
    var replaceAmazonHome by boolPref("replace_amazon_home", true)

    /** Top Shelf series and movies: from the apps' own recommendations, and from TMDB. */
    var shelfRecommendations by boolPref("shelf_recommendations", true)
    var shelfCatalog by boolPref("shelf_catalog", true)

    /** Set over ADB by scripts/rea.sh tmdb (see ConfigReceiver); a build can also bake one in. */
    var tmdbKey by stringPref("tmdb_key", "")

    /** Saved home order (app keys). Apps missing from it are appended. */
    var order by listPref("order")
    var hidden by listPref("hidden")

    fun resetLayout() {
        order = emptyList()
        hidden = emptyList()
    }

    private class Pref<T>(initial: T, private val write: (T) -> Unit) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(initial)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (state.value != value) {
                state.value = value
                write(value)
            }
        }
    }

    private fun boolPref(key: String, default: Boolean) =
        Pref(prefs.getBoolean(key, default)) { prefs.edit().putBoolean(key, it).apply() }

    private fun stringPref(key: String, default: String) =
        Pref(prefs.getString(key, default) ?: default) { prefs.edit().putString(key, it).apply() }

    private fun intPref(key: String, default: Int) =
        Pref(prefs.getInt(key, default)) { prefs.edit().putInt(key, it).apply() }

    private fun <E : Enum<E>> enumPref(key: String, default: E, all: List<E>): Pref<E> {
        val stored = prefs.getString(key, null)
        return Pref(all.firstOrNull { it.name == stored } ?: default) { prefs.edit().putString(key, it.name).apply() }
    }

    private fun listPref(key: String): Pref<List<String>> {
        val stored = prefs.getString(key, "").orEmpty().split('\n').filter { it.isNotEmpty() }
        return Pref(stored) { prefs.edit().putString(key, it.joinToString("\n")).apply() }
    }
}
