package com.jedflix.tv.ui.search

import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.ui.home.ErrorKind

sealed interface SearchUiState {
    data class Idle(val recents: List<String> = emptyList()) : SearchUiState
    data object Loading : SearchUiState
    data class Discovering(val prompt: String) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val kind: ErrorKind) : SearchUiState
    data class DiscoveryError(val prompt: String, val limited: Boolean = false) : SearchUiState
    data class Results(val titles: List<MediaTitle>, val prompt: String? = null) : SearchUiState
}
