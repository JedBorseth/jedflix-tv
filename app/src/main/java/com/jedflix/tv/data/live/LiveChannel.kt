package com.jedflix.tv.data.live

import com.jedflix.tv.data.tmdb.MediaType

data class LiveProgram(
    val mediaType: MediaType,
    val tmdbId: Int,
    val displayTitle: String,
    val durationMs: Long,
    val season: Int? = null,
    val episode: Int? = null,
) {
    val key: String get() = "${mediaType.apiValue}:$tmdbId"
    val identity: String get() = "$key:${season ?: ""}:${episode ?: ""}"

    fun sameAs(other: LiveProgram): Boolean =
        mediaType == other.mediaType &&
            tmdbId == other.tmdbId &&
            season == other.season &&
            episode == other.episode
}

data class LiveChannel(
    val id: String,
    val name: String,
    val lineup: List<LiveProgram>,
) {
    init {
        require(id.isNotBlank())
        require(lineup.isNotEmpty())
        require(lineup.all { it.durationMs > 0L })
    }
}

data class LiveTune(
    val channel: LiveChannel,
    val program: LiveProgram,
    val index: Int,
    val offsetMs: Long,
    val next: LiveProgram,
)

data class LiveEpgCell(
    val channelId: String,
    val program: LiveProgram,
    val startEpochMs: Long,
    val endEpochMs: Long,
) {
    val snappedDurationMs: Long get() = endEpochMs - startEpochMs

    fun contains(nowMs: Long): Boolean = nowMs in startEpochMs until endEpochMs
}

data class LiveChannelGuide(
    val channel: LiveChannel,
    val cells: List<LiveEpgCell>,
)
