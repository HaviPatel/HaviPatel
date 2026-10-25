package com.reatv.launcher.data.shelf

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbJsonTest {
    private val discoverTv = """
        {"page":1,"results":[
          {"id":1399,"name":"Stranger Signals","overview":"A town, a lab, a door.","backdrop_path":"/a.jpg","first_air_date":"2016-07-15"},
          {"id":2,"name":"No Backdrop","overview":"x","backdrop_path":null,"first_air_date":"2020-01-01"},
          {"id":3,"name":"","overview":"x","backdrop_path":"/c.jpg"},
          {"id":4,"name":"Dateless","overview":null,"backdrop_path":"/d.jpg","first_air_date":""}
        ],"total_pages":1,"total_results":4}
    """.trimIndent()

    private val discoverMovies = """
        {"results":[{"id":550,"title":"Night Club","overview":"Rules apply.","backdrop_path":"/m.jpg","release_date":"1999-10-15"}]}
    """.trimIndent()

    @Test
    fun discoverKeepsTitlesWithBackdrops() {
        val picks = TmdbJson.discover(discoverTv, series = true)
        assertEquals(listOf(1399L, 4L), picks.map { it.id })
        assertEquals("Stranger Signals", picks[0].title)
        assertEquals("2016", picks[0].year)
        assertNull(picks[1].year)
        assertNull(picks[1].overview)
    }

    @Test
    fun discoverSurvivesGarbage() {
        assertEquals(0, TmdbJson.discover("<html>rate limited</html>", series = false).size)
        assertEquals(0, TmdbJson.discover("""{"status_code":7,"status_message":"Invalid API key"}""", series = false).size)
    }

    @Test
    fun seriesAndMoviesAlternate() {
        val series = TmdbJson.discover(discoverTv, series = true)
        val movies = TmdbJson.discover(discoverMovies, series = false)
        assertEquals(listOf(1399L, 550L, 4L), TmdbJson.interleave(series, movies).map { it.id })
    }

    @Test
    fun seriesDetailsGiveGenresLogoAndRating() {
        val pick = TmdbJson.discover(discoverTv, series = true).first()
        val details = """
            {"id":1399,"name":"Stranger Signals","overview":"Localized overview.",
             "genres":[{"id":18,"name":"Drama"},{"id":9648,"name":"Mystery"}],
             "images":{"logos":[
               {"file_path":"/vector.svg","iso_639_1":"en","vote_average":9.0},
               {"file_path":"/french.png","iso_639_1":"fr","vote_average":9.0},
               {"file_path":"/neutral.png","iso_639_1":null,"vote_average":8.0},
               {"file_path":"/english.png","iso_639_1":"en","vote_average":5.2}
             ]},
             "content_ratings":{"results":[{"iso_3166_1":"DE","rating":"16"},{"iso_3166_1":"US","rating":"TV-14"}]}}
        """.trimIndent()
        val item = TmdbJson.item(pick, details, region = "GB", language = "en")
        assertEquals("tmdb:tv:1399", item.key)
        assertEquals(ShelfItem.Kind.Series, item.kind)
        assertEquals(listOf("Drama", "Mystery"), item.genres)
        assertEquals("TV-14", item.rating) // no GB rating: falls back to the US one
        assertEquals("Localized overview.", item.description)
        assertEquals("https://image.tmdb.org/t/p/w1280/a.jpg", item.backdropUrl)
        assertEquals("https://image.tmdb.org/t/p/w500/english.png", item.logoUrl)
        assertEquals("Series · Drama · 2016 · TV-14", item.metadata)
    }

    @Test
    fun movieCertificationComesFromReleaseDates() {
        val pick = TmdbJson.discover(discoverMovies, series = false).first()
        val details = """
            {"id":550,"title":"Night Club","genres":[{"id":18,"name":"Drama"}],
             "images":{"logos":[]},
             "release_dates":{"results":[{"iso_3166_1":"GB","release_dates":[{"certification":"","type":1},{"certification":"18","type":3}]}]}}
        """.trimIndent()
        val item = TmdbJson.item(pick, details, region = "GB", language = "en")
        assertEquals("18", item.rating)
        assertNull(item.logoUrl)
        assertEquals("Movie · Drama · 1999 · 18", item.metadata)
    }

    @Test
    fun missingDetailsStillMakeAnItem() {
        val pick = TmdbJson.discover(discoverMovies, series = false).first()
        val item = TmdbJson.item(pick, null, region = "US", language = "en")
        assertEquals("Night Club", item.title)
        assertEquals("Rules apply.", item.description)
        assertEquals("Movie · 1999", item.metadata)
    }

    @Test
    fun logoPrefersTheViewersLanguage() {
        val logos = JSONArray(
            """[{"file_path":"/en.png","iso_639_1":"en","vote_average":10},{"file_path":"/de.png","iso_639_1":"de","vote_average":1}]""",
        )
        assertEquals("/de.png", TmdbJson.bestLogo(logos, "de"))
        assertEquals("/en.png", TmdbJson.bestLogo(logos, "es"))
        assertNull(TmdbJson.bestLogo(JSONArray("""[{"file_path":"/ja.png","iso_639_1":"ja"}]"""), "en"))
    }

    @Test
    fun knownServicesMapToProviders() {
        assertEquals("8", TmdbProviders.forPackage("com.netflix.ninja"))
        assertEquals("9|119", TmdbProviders.forPackage("com.amazon.firebat"))
        assertNull(TmdbProviders.forPackage("org.videolan.vlc"))
    }
}
