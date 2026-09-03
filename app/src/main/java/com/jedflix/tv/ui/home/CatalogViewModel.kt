package com.jedflix.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.jedflix.tv.data.tmdb.CatalogSection
import com.jedflix.tv.data.tmdb.MissingTmdbKeyException
import com.jedflix.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CatalogViewModel(
    private val section: CatalogSection,
    private val repository: TmdbRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<CatalogUiState>(
        // Skip the skeleton entirely when this section is already cached.
        repository.peek(section)?.let { CatalogUiState.Ready(it) } ?: CatalogUiState.Loading,
    )
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        if (_state.value is CatalogUiState.Loading) load(force = false)
    }

    fun retry() = load(force = true)

    private fun load(force: Boolean) {
        viewModelScope.launch {
            _state.value = CatalogUiState.Loading
            try {
                _state.value = CatalogUiState.Ready(repository.loadCatalog(section, force))
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingTmdbKeyException) {
                _state.value = CatalogUiState.Error(ErrorKind.MISSING_KEY)
            } catch (e: Exception) {
                _state.value = CatalogUiState.Error(ErrorKind.NETWORK)
            }
        }
    }

    class Factory(
        private val section: CatalogSection,
        private val repository: TmdbRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            CatalogViewModel(section, repository) as T
    }
}
