package com.reatv.launcher.data.shelf

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What each app recommends, as Fire TV and Android TV apps publish it: a notification in the
 * "recommendation" category built by `ContentRecommendation` (title, text, card image, a background
 * image URI, a tap action that opens the title), plus Amazon's `com.amazon.extra.*` details.
 * [com.reatv.launcher.system.RecommendationListener] feeds this; the Top Shelf reads [items].
 */
object Recommendations {
    const val CATEGORY = "recommendation"

    /** Per app and overall, so a chatty app can't fill memory with card images. */
    const val MAX_PER_APP = 8
    const val MAX_TOTAL = 24

    class Entry(val packageName: String, val key: String, val item: ShelfItem, val rank: Int, val lastWatched: Long, val posted: Long)

    private val entries = LinkedHashMap<String, Entry>()
    private val _items = MutableStateFlow<Map<String, List<ShelfItem>>>(emptyMap())

    /** Package name → its recommendations, best first. */
    val items: StateFlow<Map<String, List<ShelfItem>>> = _items.asStateFlow()

    @Synchronized
    fun put(entry: Entry) {
        entries.remove(entry.key)
        entries[entry.key] = entry
        publish()
    }

    @Synchronized
    fun remove(key: String) {
        if (entries.remove(key) != null) publish()
    }

    @Synchronized
    fun replaceAll(all: List<Entry>) {
        entries.clear()
        for (e in all) entries[e.key] = e
        publish()
    }

    private fun publish() {
        val kept = arrange(entries.values.toList())
        // Drop what didn't make the cut, so its card bitmap can go too.
        val keep = kept.values.flatten().mapTo(HashSet()) { it.key }
        entries.keys.retainAll(keep)
        _items.value = kept.mapValues { (_, list) -> list.map { it.item } }
    }

    /**
     * Rank 0 is an app's top pick (Amazon's RANK extra); then the most recently watched, then the
     * newest. Each app keeps its best [MAX_PER_APP], and the newest apps win when over [MAX_TOTAL].
     */
    fun arrange(all: List<Entry>): Map<String, List<Entry>> {
        val order = compareBy<Entry> { it.rank }.thenByDescending { it.lastWatched }.thenByDescending { it.posted }
        val perApp = all.groupBy { it.packageName }.mapValues { (_, list) -> list.sortedWith(order).take(MAX_PER_APP) }
        var budget = MAX_TOTAL
        val result = LinkedHashMap<String, List<Entry>>()
        for ((pkg, list) in perApp.entries.sortedByDescending { (_, list) -> list.maxOf { it.posted } }) {
            if (budget <= 0) break
            val taken = list.take(budget)
            result[pkg] = taken
            budget -= taken.size
        }
        return result
    }
}

/**
 * Reads one recommendation notification's extras. Takes plain lookups instead of Bundles so it
 * runs in unit tests; the listener adapts the notification's extras and its content-info bundle.
 */
object RecommendationParser {
    // Notification extras (android.app.Notification.EXTRA_*).
    private const val TITLE = "android.title"
    private const val TEXT = "android.text"
    private const val BIG_TEXT = "android.bigText"
    private const val BACKGROUND_IMAGE = "android.backgroundImageUri"
    private const val PROGRESS = "android.progress"
    private const val PROGRESS_MAX = "android.progressMax"

    // androidx.recommendation.app.RecommendationExtender: nested bundle and its keys.
    const val CONTENT_INFO = "android.CONTENT_INFO_EXTENSIONS"
    private const val CONTENT_TYPE = "android.contentType"
    private const val CONTENT_GENRES = "android.contentGenre"
    private const val CONTENT_MATURITY = "android.contentMaturity"

    // Amazon's extras (Fire App Builder's RecommendationExtras).
    private const val AMAZON_MATURITY = "com.amazon.extra.MATURITY_RATING_TAG"
    private const val AMAZON_RANK = "com.amazon.extra.RANK"
    private const val AMAZON_LONG_DESCRIPTION = "com.amazon.extra.LONG_DESCRIPTION"
    private const val AMAZON_TAGS = "com.amazon.extra.TAGS"
    private const val AMAZON_RELEASE_DATE = "com.amazon.extra.CONTENT_RELEASE_DATE"
    private const val AMAZON_LAST_WATCHED = "com.amazon.extra.LAST_WATCHED_DATETIME"

    class Parsed(
        val title: String,
        val kind: ShelfItem.Kind,
        val genres: List<String>,
        val year: String?,
        val rating: String?,
        val description: String?,
        val backgroundUri: String?,
        val progress: Float?,
        val rank: Int,
        val lastWatched: Long,
    )

    private val YEAR = Regex("""\b(19|20)\d{2}\b""")

    /** Null when there's nothing to show (no title). */
    fun parse(extras: (String) -> Any?, info: ((String) -> Any?)?): Parsed? {
        val title = extras(TITLE)?.toString()?.trim().orEmpty()
        if (title.isEmpty()) return null
        val text = extras(TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val long = (extras(AMAZON_LONG_DESCRIPTION) ?: extras(BIG_TEXT))?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        val types = strings(info?.invoke(CONTENT_TYPE)) + tags(extras(AMAZON_TAGS))
        val kind = when {
            types.any { it.endsWith(".serial") || it.equals("tv", true) || it.equals("series", true) } -> ShelfItem.Kind.Series
            types.any { it.endsWith(".movie") || it.equals("movie", true) } -> ShelfItem.Kind.Movie
            else -> ShelfItem.Kind.Video
        }
        val rating = extras(AMAZON_MATURITY)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: maturity(info?.invoke(CONTENT_MATURITY)?.toString())

        val max = (extras(PROGRESS_MAX) as? Number)?.toInt() ?: 0
        val at = (extras(PROGRESS) as? Number)?.toInt() ?: 0
        val progress = if (max > 0 && at > 0) (at.toFloat() / max).coerceIn(0f, 1f) else null

        return Parsed(
            title = title,
            kind = kind,
            genres = strings(info?.invoke(CONTENT_GENRES)).filter { it.isNotBlank() },
            year = extras(AMAZON_RELEASE_DATE)?.toString()?.let { YEAR.find(it)?.value },
            rating = rating,
            // The long description reads better on the shelf; the short text is often just the episode.
            description = long ?: text,
            backgroundUri = extras(BACKGROUND_IMAGE)?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            progress = progress,
            rank = (extras(AMAZON_RANK) as? Number)?.toInt() ?: Int.MAX_VALUE,
            lastWatched = (extras(AMAZON_LAST_WATCHED) as? Number)?.toLong() ?: 0L,
        )
    }

    private fun strings(value: Any?): List<String> = when (value) {
        is Array<*> -> value.mapNotNull { it?.toString() }
        is Collection<*> -> value.mapNotNull { it?.toString() }
        is String -> listOf(value)
        else -> emptyList()
    }

    /** Amazon's tags are a list, or one string separated by commas, semicolons or colons. */
    private fun tags(value: Any?): List<String> = strings(value).flatMap { it.split(',', ';', ':') }.map { it.trim() }

    /** ContentRecommendation's coarse maturity levels, as Fire TV shows them. */
    private fun maturity(value: String?): String? = when (value) {
        "android.contentMaturity.all" -> "All"
        "android.contentMaturity.low" -> "7+"
        "android.contentMaturity.medium" -> "13+"
        "android.contentMaturity.high" -> "18+"
        else -> null
    }
}
