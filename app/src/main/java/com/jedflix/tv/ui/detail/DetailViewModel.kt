package com.jedflix.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import com.jedflix.tv.data.tmdb.MissingTmdbKeyException
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.ui.focus.DetailRail
import com.jedflix.tv.ui.home.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DetailViewModel(
    private val mediaType: MediaType,
    private val mediaId: Int,
    private val repository: TmdbRepository,
    private val library: UserLibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()
    private var libraryJob: Job? = null

    var focusRail: DetailRail = DetailRail.HERO
        private set
    var castId: Int? = null
        private set
    var seasonChip: Int? = null
        private set
    var episodeNumber: Int? = null
        private set
    var similarKey: String? = null
        private set
    private var restoringFocus = true

    init {
        load(force = false)
    }

    fun retry() = load(force = true)

    fun toggleMyList(title: MediaTitle) {
        viewModelScope.launch { library.toggleMyList(title) }
    }

    fun onHeroFocused() {
        if (restoringFocus && focusRail != DetailRail.HERO) return
        focusRail = DetailRail.HERO
    }

    fun onCastFocused(id: Int) {
        focusRail = DetailRail.CAST
        castId = id
    }

    fun onSeasonFocused(seasonNumber: Int) {
        focusRail = DetailRail.SEASONS
        seasonChip = seasonNumber
    }

    fun onEpisodeFocused(number: Int) {
        focusRail = DetailRail.EPISODES
        episodeNumber = number
    }

    fun onSimilarFocused(itemKey: String) {
        focusRail = DetailRail.SIMILAR
        similarKey = itemKey
    }

    fun finishRestore() {
        restoringFocus = false
    }

    fun beginRestore() {
        restoringFocus = true
    }

    fun selectSeason(seasonNumber: Int) {
        val current = _state.value as? DetailUiState.Ready ?: return
        if (current.selectedSeason == seasonNumber && current.episodes.isNotEmpty()) return
        episodeNumber = null
        viewModelScope.launch {
            _state.value = current.copy(selectedSeason = seasonNumber, episodesLoading = true)
            val episodes = runCatching { repository.loadSeasonEpisodes(mediaId, seasonNumber) }
                .getOrDefault(emptyList())
            val latest = _state.value as? DetailUiState.Ready ?: return@launch
            if (latest.selectedSeason != seasonNumber) return@launch
            _state.value = latest.copy(episodes = episodes, episodesLoading = false)
        }
    }

    private fun load(force: Boolean) {
        libraryJob?.cancel()
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            try {
                val details = repository.loadDetails(mediaType, mediaId, force)
                val firstSeason = details.seasons.firstOrNull()?.seasonNumber
                _state.value = DetailUiState.Ready(
                    details = details,
                    selectedSeason = firstSeason,
                    episodes = emptyList(),
                    episodesLoading = firstSeason != null,
                )
                observeLibrary()
                if (firstSeason != null) {
                    val episodes = repository.loadSeasonEpisodes(mediaId, firstSeason)
                    val latest = _state.value as? DetailUiState.Ready ?: return@launch
                    _state.value = latest.copy(episodes = episodes, episodesLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingTmdbKeyException) {
                _state.value = DetailUiState.Error(ErrorKind.MISSING_KEY)
            } catch (e: Exception) {
                _state.value = DetailUiState.Error(ErrorKind.NETWORK)
            }
        }
    }

    private fun observeLibrary() {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            combine(
                library.observeInMyList(mediaType, mediaId),
                library.observeTitleProgress(mediaType, mediaId),
            ) { inList, resume -> inList to resume }
                .collect { (inList, resume) ->
                    val latest = _state.value as? DetailUiState.Ready ?: return@collect
                    _state.value = latest.copy(inMyList = inList, resume = resume)
                }
        }
    }

    class Factory(
        private val mediaType: MediaType,
        private val mediaId: Int,
        private val repository: TmdbRepository,
        private val library: UserLibraryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            DetailViewModel(mediaType, mediaId, repository, library) as T
    }
}
