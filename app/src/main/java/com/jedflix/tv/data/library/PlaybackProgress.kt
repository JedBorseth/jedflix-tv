package com.jedflix.tv.data.library

import com.jedflix.tv.data.tmdb.MediaType

/** Snapshot written from the player so shelves can render without TMDB. */
data class PlaybackProgress(
    val mediaType: MediaType,
    val tmdbId: Int,
    val season: Int?,
    val episode: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: String?,
    val rating: Double?,
    val genres: List<String>,
)
