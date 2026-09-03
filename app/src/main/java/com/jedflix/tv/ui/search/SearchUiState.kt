package com.jedflix.tv.ui.search

import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.ui.home.ErrorKind

sealed interface SearchUiState {
    data class Idle(val recents: List<String> = emptyList()) : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Error(val kind: ErrorKind) : SearchUiState
    data class Results(val titles: List<MediaTitle>) : SearchUiState
}
