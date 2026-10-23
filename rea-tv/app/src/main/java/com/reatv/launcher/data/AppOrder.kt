package com.reatv.launcher.data

/** Home ordering. Like tvOS, the home is one list: the first row is the "Top Shelf" row. */
object AppOrder {
    private val FEATURED = listOf(
        "com.netflix.ninja",
        "com.amazon.firetv.youtube",
        "com.google.android.youtube.tv",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.apple.atve.amazon.appletv",
        "com.wbd.stream",
        "com.hbo.hbonow",
        "com.hulu.plus",
        "com.plexapp.android",
        "org.jellyfin.androidtv",
        "org.xbmc.kodi",
        "com.spotify.tv.android",
        "tv.twitch.android.app",
        "com.cbs.ott",
        "com.peacocktv.peacockandroid",
    )

    fun arrange(all: List<AppEntry>, order: List<String>, hidden: List<String>): List<AppEntry> {
        val hiddenSet = hidden.toHashSet()
        val byKey = all.associateBy { it.key }
        val used = HashSet<String>()
        val result = ArrayList<AppEntry>(all.size)
        for (key in order) {
            val app = byKey[key] ?: continue
            if (key in hiddenSet || !used.add(key)) continue
            result += app
        }
        val rest = all.filter { it.key !in used && it.key !in hiddenSet }
        result += if (order.isEmpty()) {
            rest.sortedWith(compareBy<AppEntry>({ rank(it) }, { it.label.lowercase() }))
        } else {
            rest.sortedBy { it.label.lowercase() }
        }
        return result
    }

    fun alphabetical(all: List<AppEntry>): List<String> =
        all.sortedWith(compareBy<AppEntry>({ it.kind == AppEntry.Kind.SystemSettings }, { it.label.lowercase() })).map { it.key }

    /** Moves [key] to [targetIndex] within the visible list and returns the new saved order. */
    fun move(visible: List<AppEntry>, key: String, targetIndex: Int): List<String> {
        val keys = visible.map { it.key }.toMutableList()
        val from = keys.indexOf(key)
        if (from < 0) return keys
        keys.removeAt(from)
        keys.add(targetIndex.coerceIn(0, keys.size), key)
        return keys
    }

    private fun rank(app: AppEntry): Int = when {
        app.kind == AppEntry.Kind.SystemSettings -> 1000
        else -> FEATURED.indexOf(app.packageName).let { if (it >= 0) it else if (app.isSystem) 200 else 100 }
    }
}
