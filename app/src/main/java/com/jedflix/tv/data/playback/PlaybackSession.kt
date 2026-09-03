package com.jedflix.tv.data.playback

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
