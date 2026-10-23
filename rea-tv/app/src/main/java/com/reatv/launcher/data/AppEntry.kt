package com.reatv.launcher.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Immutable

@Immutable
data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName?,
    val isSystem: Boolean,
    val category: Int,
    val kind: Kind = Kind.App,
) {
    enum class Kind { App, SystemSettings }

    val categoryLabel: String
        get() = when {
            kind == Kind.SystemSettings -> "System"
            category == ApplicationInfo.CATEGORY_GAME -> "Game"
            category == ApplicationInfo.CATEGORY_AUDIO -> "Music & Audio"
            category == ApplicationInfo.CATEGORY_VIDEO -> "Video"
            category == ApplicationInfo.CATEGORY_IMAGE -> "Photos"
            category == ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            category == ApplicationInfo.CATEGORY_NEWS -> "News"
            category == ApplicationInfo.CATEGORY_MAPS -> "Maps"
            category == ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            else -> "App"
        }
}
