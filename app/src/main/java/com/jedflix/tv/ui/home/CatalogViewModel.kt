package com.jedflix.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.jedflix.tv.data.library.LibraryItem
import com.jedflix.tv.data.library.LibraryRows
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.tmdb.Catalog
import com.jedflix.tv.data.tmdb.CatalogRow
import com.jedflix.tv.data.tmdb.CatalogSection
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import com.jedflix.tv.data.tmdb.MissingTmdbKeyException
import com.jedflix.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CatalogViewModel(
    private val section: CatalogSection,
    private val repository: TmdbRepository,
    private val library: UserLibraryRepository,
) : ViewModel() {

    private val tmdb = MutableStateFlow<CatalogUiState>(
        repository.peek(section)?.let { CatalogUiState.Ready(it) } ?: CatalogUiState.Loading,
    )
    private val _state = MutableStateFlow(tmdb.value)
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private val mediaFilter: MediaType? = when (section) {
        CatalogSection.HOME -> null
        CatalogSection.MOVIES -> MediaType.MOVIE
        CatalogSection.SHOWS -> MediaType.TV
    }

    init {
        viewModelScope.launch {
            combine(
                tmdb,
                library.observeContinueWatching(mediaFilter),
                library.observeMyList(mediaFilter),
                library.observeWatchHistory(mediaFilter),
            ) { tmdbState, continueWatching, myList, history ->
                when (tmdbState) {
                    is CatalogUiState.Ready -> CatalogUiState.Ready(
                        catalog = mergePersonalRows(tmdbState.catalog, continueWatching, myList, history),
                        myListKeys = myList.map { it.key }.toSet(),
                        continueWatching = continueWatching,
                    )
                    else -> tmdbState
                }
            }.collect { _state.value = it }
        }
        if (tmdb.value is CatalogUiState.Loading) load(force = false)
    }

    fun retry() = load(force = true)

    fun toggleMyList(title: MediaTitle) {
        viewModelScope.launch { library.toggleMyList(title) }
    }

    private fun load(force: Boolean) {
        viewModelScope.launch {
            tmdb.value = CatalogUiState.Loading
            try {
                tmdb.value = CatalogUiState.Ready(repository.loadCatalog(section, force))
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingTmdbKeyException) {
                tmdb.value = CatalogUiState.Error(ErrorKind.MISSING_KEY)
            } catch (e: Exception) {
                tmdb.value = CatalogUiState.Error(ErrorKind.NETWORK)
            }
        }
    }

    class Factory(
        private val section: CatalogSection,
        private val repository: TmdbRepository,
        private val library: UserLibraryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            CatalogViewModel(section, repository, library) as T
    }
}

private fun mergePersonalRows(
    catalog: Catalog,
    continueWatching: List<LibraryItem>,
    myList: List<MediaTitle>,
    history: List<LibraryItem>,
): Catalog {
    val tmdbRows = catalog.rows.mapIndexed { index, row -> row.copy(drivesHero = index == 0) }
    val personal = buildList {
        if (continueWatching.isNotEmpty()) {
            add(
                CatalogRow(
                    id = LibraryRows.CONTINUE_WATCHING,
                    title = "Continue Watching",
                    items = continueWatching.map { it.title },
                    showProgress = true,
                ),
            )
        }
        if (myList.isNotEmpty()) {
            add(
                CatalogRow(
                    id = LibraryRows.MY_LIST,
                    title = "My List",
                    items = myList,
                ),
            )
        }
        if (history.isNotEmpty()) {
            add(
                CatalogRow(
                    id = LibraryRows.WATCH_HISTORY,
                    title = "Watch History",
                    items = history.map { it.title },
                ),
            )
        }
    }
    return catalog.copy(rows = personal + tmdbRows)
}
