package com.reatv.launcher.data.shelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecommendationsTest {
    /** Extras as Fire App Builder's RecommendationBuilder puts them on a notification. */
    private val amazon = mapOf<String, Any?>(
        "android.title" to "The Long Road",
        "android.text" to "S2 · E4",
        "android.backgroundImageUri" to "https://cdn.example.com/road.jpg",
        "android.progress" to 30,
        "android.progressMax" to 100,
        "com.amazon.extra.LONG_DESCRIPTION" to "Two strangers, one car, no map.",
        "com.amazon.extra.TAGS" to arrayListOf("tv"),
        "com.amazon.extra.MATURITY_RATING_TAG" to "TV-14",
        "com.amazon.extra.CONTENT_RELEASE_DATE" to "2019-05-03",
        "com.amazon.extra.RANK" to 2,
        "com.amazon.extra.LAST_WATCHED_DATETIME" to 1_700_000_000_000L,
    )

    @Test
    fun readsAmazonRecommendations() {
        val p = RecommendationParser.parse({ amazon[it] }, null)!!
        assertEquals("The Long Road", p.title)
        assertEquals(ShelfItem.Kind.Series, p.kind)
        assertEquals("TV-14", p.rating)
        assertEquals("2019", p.year)
        assertEquals("Two strangers, one car, no map.", p.description)
        assertEquals("https://cdn.example.com/road.jpg", p.backgroundUri)
        assertEquals(0.3f, p.progress!!, 0.001f)
        assertEquals(2, p.rank)
        assertEquals(1_700_000_000_000L, p.lastWatched)
    }

    @Test
    fun readsContentRecommendationExtensions() {
        val extras = mapOf<String, Any?>("android.title" to "Deep Field", "android.text" to "A documentary")
        val info = mapOf<String, Any?>(
            "android.contentType" to arrayOf("android.contentType.movie"),
            "android.contentGenre" to arrayOf("Documentary", "Science"),
            "android.contentMaturity" to "android.contentMaturity.high",
        )
        val p = RecommendationParser.parse({ extras[it] }, { info[it] })!!
        assertEquals(ShelfItem.Kind.Movie, p.kind)
        assertEquals(listOf("Documentary", "Science"), p.genres)
        assertEquals("18+", p.rating)
        assertEquals("A documentary", p.description)
        assertNull(p.progress)
        assertEquals(Int.MAX_VALUE, p.rank)
    }

    @Test
    fun tagsCanBeOneString() {
        val extras = mapOf<String, Any?>("android.title" to "Clip", "com.amazon.extra.TAGS" to "short; movie")
        assertEquals(ShelfItem.Kind.Movie, RecommendationParser.parse({ extras[it] }, null)!!.kind)
    }

    @Test
    fun noTitleNoItem() {
        assertNull(RecommendationParser.parse({ if (it == "android.title") "  " else null }, null))
    }

    private fun entry(pkg: String, key: String, rank: Int = Int.MAX_VALUE, watched: Long = 0, posted: Long = 0) =
        Recommendations.Entry(pkg, key, ShelfItem(key, key, ShelfItem.Kind.Video), rank, watched, posted)

    @Test
    fun rankThenRecentlyWatchedThenNewest() {
        val arranged = Recommendations.arrange(
            listOf(
                entry("a", "new", posted = 9),
                entry("a", "watched", watched = 5),
                entry("a", "top", rank = 0),
                entry("a", "second", rank = 1),
            ),
        )
        assertEquals(listOf("top", "second", "watched", "new"), arranged.getValue("a").map { it.key })
    }

    @Test
    fun capsPerAppAndOverall() {
        val many = (0 until 20).map { entry("big", "b$it", posted = 100L + it) } +
            (0 until 20).map { entry("other$it", "o$it", posted = it.toLong()) }
        val arranged = Recommendations.arrange(many)
        assertEquals(Recommendations.MAX_PER_APP, arranged.getValue("big").size)
        assertEquals(Recommendations.MAX_TOTAL, arranged.values.sumOf { it.size })
    }
}
