package com.jedflix.tv.ui.detail

import com.jedflix.tv.data.tmdb.TitleDetails
import com.jedflix.tv.data.tmdb.TvEpisode
import com.jedflix.tv.ui.home.ErrorKind

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val kind: ErrorKind) : DetailUiState
    data class Ready(
        val details: TitleDetails,
        val selectedSeason: Int?,
        val episodes: List<TvEpisode>,
        val episodesLoading: Boolean,
    ) : DetailUiState
}
