package com.jedflix.tv.data.playback

import com.jedflix.tv.data.comet.StreamOption
import com.jedflix.tv.data.tmdb.MediaType

/** What the player needs; kept in memory because resolved URLs are too long for nav arguments. */
data class PlaybackItem(
    val streamUrl: String,
    val title: String,
    val subtitle: String?,
    val mediaType: MediaType,
    val tmdbId: Int,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: String? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val startPositionMs: Long = 0L,
    /** IMDb id used to fetch the next episode's streams without another picker hop. */
    val imdbId: String? = null,
    /** Stream picker resolution token (e.g. "1080P"). */
    val resolution: String? = null,
    /** Remaining auto-pick candidates, already ranked, tried if this file fails to play. */
    val fallbacks: List<StreamOption> = emptyList(),
)

/** Single-slot hand-off between the stream picker and the player. */
class PlaybackSession {
    @Volatile
    var current: PlaybackItem? = null
        private set

    fun start(item: PlaybackItem) {
        current = item
    }

    fun clear() {
        current = null
    }
}
