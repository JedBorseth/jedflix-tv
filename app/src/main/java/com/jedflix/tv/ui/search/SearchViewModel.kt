package com.jedflix.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.jedflix.tv.data.tmdb.MissingTmdbKeyException
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.ui.home.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: TmdbRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .map { it.trim() }
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.isEmpty()) {
                        _state.value = SearchUiState.Idle
                        return@collectLatest
                    }
                    _state.value = SearchUiState.Loading
                    try {
                        val hits = repository.search(q)
                        _state.value = if (hits.isEmpty()) {
                            SearchUiState.Empty
                        } else {
                            SearchUiState.Results(hits)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: MissingTmdbKeyException) {
                        _state.value = SearchUiState.Error(ErrorKind.MISSING_KEY)
                    } catch (e: Exception) {
                        _state.value = SearchUiState.Error(ErrorKind.NETWORK)
                    }
                }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    class Factory(
        private val repository: TmdbRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            SearchViewModel(repository) as T
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
