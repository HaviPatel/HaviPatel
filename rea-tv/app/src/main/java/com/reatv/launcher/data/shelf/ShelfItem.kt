package com.reatv.launcher.data.shelf

import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

/**
 * One title in an app's Top Shelf: a movie, a series, or a video the app recommends. Comes either
 * from the app itself ([Recommendations]) or from TMDB's catalog of what's on that service
 * ([TmdbCatalog]).
 */
@Immutable
class ShelfItem(
    /** Stable within its app: the notification key or the TMDB id. */
    val key: String,
    val title: String,
    val kind: Kind,
    val genres: List<String> = emptyList(),
    val year: String? = null,
    /** Maturity rating as the source gives it: "TV-MA", "PG-13", "16+". */
    val rating: String? = null,
    val description: String? = null,
    /** Full-bleed artwork: https, or content:// from the recommending app. */
    val backdropUrl: String? = null,
    /** Transparent title treatment (TMDB logos). */
    val logoUrl: String? = null,
    /** The app's own card image, used when there's no backdrop. */
    val card: Bitmap? = null,
    /** Continue-watching position, 0..1. */
    val progress: Float? = null,
    /** Opens this title in its app. Null opens the app itself. */
    val open: PendingIntent? = null,
) {
    enum class Kind { Movie, Series, Video }

    /** tvOS-style metadata line: "Series · Drama · 2019 · TV-MA". */
    val metadata: String
        get() = listOfNotNull(
            when (kind) {
                Kind.Movie -> "Movie"
                Kind.Series -> "Series"
                Kind.Video -> null
            },
            genres.firstOrNull(),
            year,
            rating,
        ).joinToString(" · ")
}
