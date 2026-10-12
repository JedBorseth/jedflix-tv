package com.jedflix.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.recommendations.DiscoveryException
import com.jedflix.tv.data.recommendations.RecommendationRepository
import com.jedflix.tv.data.recommendations.RecommendationRequest
import com.jedflix.tv.data.tmdb.MissingTmdbKeyException
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.ui.home.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
    private val recommendations: RecommendationRepository? = null,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _prompt = MutableStateFlow<String?>(null)
    val prompt: StateFlow<String?> = _prompt.asStateFlow()
    private var discoveryJob: Job? = null
    var selectedTitleKey: String? = null
        private set

    fun rememberSelectedTitle(key: String) { selectedTitleKey = key }

    private val _results = MutableStateFlow<SearchUiState>(SearchUiState.Idle())
    val recents: StateFlow<List<String>> = library.observeRecentSearches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<SearchUiState> = combine(_results, recents) { results, recent ->
        if (results is SearchUiState.Idle) SearchUiState.Idle(recent) else results
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Idle())

    init {
        viewModelScope.launch {
            combine(_query, _prompt) { query, prompt -> query.trim() to prompt }
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { (q, selectedPrompt) ->
                    if (selectedPrompt != null) return@collectLatest
                    if (q.isEmpty()) {
                        _results.value = SearchUiState.Idle()
                        return@collectLatest
                    }
                    _results.value = SearchUiState.Loading
                    try {
                        val hits = repository.search(q)
                        if (_prompt.value != null) return@collectLatest
                        _results.value = if (hits.isEmpty()) {
                            SearchUiState.Empty
                        } else {
                            SearchUiState.Results(hits)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: MissingTmdbKeyException) {
                        if (_prompt.value == null) _results.value = SearchUiState.Error(ErrorKind.MISSING_KEY)
                    } catch (e: Exception) {
                        if (_prompt.value == null) _results.value = SearchUiState.Error(ErrorKind.NETWORK)
                    }
                }
        }
        viewModelScope.launch {
            var previous: Long? = null
            library.observeActiveProfile().collect { profile ->
                val id = profile?.id
                if (previous != null && previous != id && _prompt.value != null) {
                    selectedTitleKey = null
                    discoveryJob?.cancel()
                    _prompt.value = null
                    _results.value = SearchUiState.Idle()
                }
                previous = id
            }
        }
    }

    fun onQueryChange(value: String) {
        selectedTitleKey = null
        discoveryJob?.cancel()
        _prompt.value = null
        _query.value = value
    }

    fun discover(value: String) {
        val client = recommendations ?: return
        val text = value.trim().take(500)
        if (text.isEmpty() || discoveryJob?.isActive == true) return
        selectedTitleKey = null
        _prompt.value = text
        _results.value = SearchUiState.Discovering(text)
        discoveryJob = viewModelScope.launch {
            try {
                val signals = library.recommendationSignals()
                val result = client.discover(RecommendationRequest.from(signals, emptyList()), text)
                val current = library.recommendationSignals()
                if (current.profileId != signals.profileId || _prompt.value != text) return@launch
                val hits = result.toTitles()
                _results.value = if (hits.isEmpty()) SearchUiState.Empty else SearchUiState.Results(hits, text)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _results.value = SearchUiState.DiscoveryError(text,
                    limited = (error as? DiscoveryException)?.status == 429)
            }
        }
    }

    fun clearDiscovery() {
        selectedTitleKey = null
        discoveryJob?.cancel()
        _prompt.value = null
        _results.value = SearchUiState.Idle()
    }

    fun onSearchSubmit() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch { library.saveSearchQuery(q) }
    }

    class Factory(
        private val repository: TmdbRepository,
        private val library: UserLibraryRepository,
        private val recommendations: RecommendationRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            SearchViewModel(repository, library, recommendations) as T
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
