package com.reatv.launcher.data.shelf

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * What's popular on a streaming service right now, from TMDB (themoviedb.org), so hovering Netflix
 * shows Netflix's series and movies the way tvOS's Top Shelf does. Runs only with a TMDB key; the
 * device's country decides what "on Netflix" means. This product uses the TMDB API but is not
 * endorsed or certified by TMDB.
 *
 * Blocking: call from a background dispatcher.
 */
class TmdbCatalog(private val http: Http, key: String, private val region: String, private val language: String) {
    // A v4 read access token is a JWT and goes in a header; a v3 key goes in the query.
    private val token = key.startsWith("eyJ") || key.length > 40
    private val headers = if (token) mapOf("Authorization" to "Bearer $key", "Accept" to "application/json") else mapOf("Accept" to "application/json")
    private val keyParam = if (token) "" else "&api_key=${enc(key)}"
    private val imageLanguages = listOf(language.substringBefore('-'), "en", "null").distinct().joinToString(",")

    /** Series and movies alternating, most popular first, each with its logo and rating when TMDB has them. */
    fun popular(providers: String, limit: Int = 8): List<ShelfItem> {
        val series = discover(series = true, providers)
        val movies = discover(series = false, providers)
        return TmdbJson.interleave(series, movies).take(limit).map { details(it) }
    }

    private fun discover(series: Boolean, providers: String): List<TmdbJson.Pick> {
        val url = "$API/discover/${type(series)}?watch_region=$region&with_watch_providers=${enc(providers)}" +
            "&with_watch_monetization_types=${enc("flatrate|free|ads")}&sort_by=popularity.desc" +
            "&include_adult=false&language=$language&page=1$keyParam"
        val body = http.text(url, DISCOVER_TTL_MS, headers) ?: return emptyList()
        return TmdbJson.discover(body, series)
    }

    private fun details(pick: TmdbJson.Pick): ShelfItem {
        val append = if (pick.series) "images,content_ratings" else "images,release_dates"
        val url = "$API/${type(pick.series)}/${pick.id}?append_to_response=$append" +
            "&include_image_language=${enc(imageLanguages)}&language=$language$keyParam"
        // Without details the title still shows, just without a logo or rating.
        return TmdbJson.item(pick, http.text(url, DETAILS_TTL_MS, headers), region, language.substringBefore('-'))
    }

    private fun type(series: Boolean) = if (series) "tv" else "movie"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val API = "https://api.themoviedb.org/3"
        const val DISCOVER_TTL_MS = 6 * 60 * 60 * 1000L
        const val DETAILS_TTL_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

/** Fire TV and Android TV packages → TMDB watch-provider ids ("|" means any of them). */
object TmdbProviders {
    private const val PRIME = "9|119"
    private const val MAX = "1899|384"

    private val byPackage = mapOf(
        "com.netflix.ninja" to "8",
        "com.amazon.firebat" to PRIME,
        "com.amazon.avod" to PRIME,
        "com.amazon.avod.thirdpartyclient" to PRIME,
        "com.amazon.amazonvideo.livingroom" to PRIME,
        "com.disney.disneyplus" to "337",
        "com.hulu.plus" to "15",
        "com.hulu.livingroomplus" to "15",
        "com.wbd.stream" to MAX,
        "com.hbo.hbonow" to MAX,
        "com.apple.atve.amazon.appletv" to "350",
        "com.apple.atve.androidtv.appletv" to "350",
        "com.cbs.ott" to "531",
        "com.peacocktv.peacockandroid" to "386|387",
        "com.crunchyroll.crunchyroid" to "283",
        "com.tubitv" to "73",
        "tv.pluto.android" to "300",
        "com.mubi" to "11",
    )

    fun forPackage(packageName: String): String? = byPackage[packageName]
}

/** TMDB's JSON, read into shelf items. Pure, so it's unit-tested against sample responses. */
object TmdbJson {
    class Pick(
        val id: Long,
        val series: Boolean,
        val title: String,
        val overview: String?,
        val backdropPath: String,
        val year: String?,
    )

    fun image(size: String, path: String) = "https://image.tmdb.org/t/p/$size$path"

    /** Discover results that have a backdrop and a title, in TMDB's (popularity) order. */
    fun discover(body: String, series: Boolean): List<Pick> {
        val results = try {
            JSONObject(body).optJSONArray("results")
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val picks = ArrayList<Pick>(results.length())
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val backdrop = o.string("backdrop_path") ?: continue
            val title = o.string(if (series) "name" else "title") ?: continue
            val id = o.optLong("id", -1L).takeIf { it > 0 } ?: continue
            picks += Pick(id, series, title, o.string("overview"), backdrop, year(o.string(if (series) "first_air_date" else "release_date")))
        }
        return picks
    }

    /** Series first, then a movie, and so on; whatever's left of the longer list follows. */
    fun interleave(series: List<Pick>, movies: List<Pick>): List<Pick> {
        val out = ArrayList<Pick>(series.size + movies.size)
        for (i in 0 until maxOf(series.size, movies.size)) {
            series.getOrNull(i)?.let { out += it }
            movies.getOrNull(i)?.let { out += it }
        }
        return out
    }

    fun item(pick: Pick, details: String?, region: String, language: String): ShelfItem {
        val d = details?.let {
            try {
                JSONObject(it)
            } catch (e: Exception) {
                null
            }
        }
        val genres = d?.optJSONArray("genres")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.string("name") } }.orEmpty()
        val logo = d?.optJSONObject("images")?.optJSONArray("logos")?.let { bestLogo(it, language) }
        return ShelfItem(
            key = "tmdb:${if (pick.series) "tv" else "movie"}:${pick.id}",
            title = d?.string(if (pick.series) "name" else "title") ?: pick.title,
            kind = if (pick.series) ShelfItem.Kind.Series else ShelfItem.Kind.Movie,
            genres = genres,
            year = pick.year,
            rating = d?.let { certification(it, pick.series, region) },
            description = d?.string("overview") ?: pick.overview,
            backdropUrl = image("w1280", pick.backdropPath),
            logoUrl = logo?.let { image("w500", it) },
        )
    }

    /** A PNG logo in the viewer's language, else English, else a language-free one; best voted wins. */
    fun bestLogo(logos: JSONArray, language: String): String? {
        var best: String? = null
        var bestScore = -1.0
        for (i in 0 until logos.length()) {
            val o = logos.optJSONObject(i) ?: continue
            val path = o.string("file_path") ?: continue
            if (!path.endsWith(".png", ignoreCase = true)) continue // some are SVG
            val lang = o.string("iso_639_1")
            val rank = when (lang) {
                language -> 3
                "en" -> 2
                null -> 1
                else -> continue
            }
            val score = rank * 100 + o.optDouble("vote_average", 0.0).coerceIn(0.0, 10.0)
            if (score > bestScore) {
                bestScore = score
                best = path
            }
        }
        return best
    }

    /** The age rating for the viewer's country, else the US one. */
    fun certification(details: JSONObject, series: Boolean, region: String): String? {
        val results = details.optJSONObject(if (series) "content_ratings" else "release_dates")?.optJSONArray("results") ?: return null
        fun forCountry(country: String): String? {
            for (i in 0 until results.length()) {
                val r = results.optJSONObject(i) ?: continue
                if (r.string("iso_3166_1") != country) continue
                if (series) return r.string("rating")
                val dates = r.optJSONArray("release_dates") ?: return null
                for (j in 0 until dates.length()) dates.optJSONObject(j)?.string("certification")?.let { return it }
            }
            return null
        }
        return forCountry(region) ?: forCountry("US")
    }

    private fun year(date: String?): String? = date?.takeIf { it.length >= 4 && it.take(4).all(Char::isDigit) }?.take(4)

    /** org.json hands back "null" for JSON nulls and "" for missing keys; both mean nothing here. */
    private fun JSONObject.string(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }
}
