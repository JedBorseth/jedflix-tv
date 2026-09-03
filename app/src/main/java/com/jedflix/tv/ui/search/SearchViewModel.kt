package com.jedflix.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.tmdb.MissingTmdbKeyException
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.ui.home.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: TmdbRepository,
    private val library: UserLibraryRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<SearchUiState>(SearchUiState.Idle())
    val recents: StateFlow<List<String>> = library.observeRecentSearches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<SearchUiState> = combine(_results, recents) { results, recent ->
        if (results is SearchUiState.Idle) SearchUiState.Idle(recent) else results
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Idle())

    init {
        viewModelScope.launch {
            _query
                .map { it.trim() }
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.isEmpty()) {
                        _results.value = SearchUiState.Idle()
                        return@collectLatest
                    }
                    _results.value = SearchUiState.Loading
                    try {
                        val hits = repository.search(q)
                        library.saveSearchQuery(q)
                        _results.value = if (hits.isEmpty()) {
                            SearchUiState.Empty
                        } else {
                            SearchUiState.Results(hits)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: MissingTmdbKeyException) {
                        _results.value = SearchUiState.Error(ErrorKind.MISSING_KEY)
                    } catch (e: Exception) {
                        _results.value = SearchUiState.Error(ErrorKind.NETWORK)
                    }
                }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    class Factory(
        private val repository: TmdbRepository,
        private val library: UserLibraryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            SearchViewModel(repository, library) as T
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
