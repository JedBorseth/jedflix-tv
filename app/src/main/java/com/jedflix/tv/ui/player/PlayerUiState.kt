package com.jedflix.tv.ui.player

import com.jedflix.tv.data.playback.PlaybackItem

data class SelectableTrack(
    val id: String,
    val label: String,
    val language: String?,
    val groupIndex: Int,
    val trackIndex: Int,
)

data class UpNextUi(
    val season: Int,
    val episode: Int,
    val title: String,
    val stillUrl: String?,
    /** Null while the next file is still being resolved. */
    val secondsRemaining: Int?,
)

data class PlayerUiState(
    val item: PlaybackItem,
    val error: Boolean = false,
    val isPlaying: Boolean = false,
    val isEnded: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioTracks: List<SelectableTrack> = emptyList(),
    val textTracks: List<SelectableTrack> = emptyList(),
    val selectedAudioId: String? = null,
    val selectedTextId: String? = null,
    val hasNextEpisode: Boolean = false,
    val upNext: UpNextUi? = null,
)

sealed interface PlayerEvent {
    data object SeriesComplete : PlayerEvent
    data class OpenPicker(val season: Int, val episode: Int) : PlayerEvent
}
