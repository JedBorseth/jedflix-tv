package com.jedflix.tv.ui.home

import com.jedflix.tv.data.library.LibraryItem
import com.jedflix.tv.data.tmdb.Catalog

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Error(val kind: ErrorKind) : CatalogUiState
    data class Ready(
        val catalog: Catalog,
        val myListKeys: Set<String> = emptySet(),
        val continueWatching: List<LibraryItem> = emptyList(),
    ) : CatalogUiState
}

enum class ErrorKind { MISSING_KEY, NETWORK }
