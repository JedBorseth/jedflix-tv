package com.jedflix.tv.ui.streams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.jedflix.tv.data.comet.CometClient
import com.jedflix.tv.data.comet.StreamException
import com.jedflix.tv.data.comet.StreamOption
import com.jedflix.tv.data.playback.PlaybackItem
import com.jedflix.tv.data.playback.PlaybackSession
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.MediaType
import com.jedflix.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StreamPickerViewModel(
    private val mediaType: MediaType,
    private val mediaId: Int,
    private val season: Int?,
    private val episode: Int?,
    private val repository: TmdbRepository,
    private val cometClient: CometClient,
    private val settingsStore: SettingsStore,
    private val playbackSession: PlaybackSession,
) : ViewModel() {

    private val _state = MutableStateFlow<StreamPickerUiState>(StreamPickerUiState.Loading(null))
    val state: StateFlow<StreamPickerUiState> = _state.asStateFlow()

    /** Fires once the chosen stream has a Real-Debrid URL staged in [PlaybackSession]. */
    private val _play = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val play: SharedFlow<Unit> = _play.asSharedFlow()

    private var resolveJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    fun select(option: StreamOption) {
        val current = _state.value as? StreamPickerUiState.Ready ?: return
        if (current.resolving != null) return
        _state.value = current.copy(resolving = option, resolveError = null)
        resolveJob = viewModelScope.launch {
            try {
                val url = cometClient.resolvePlaybackUrl(option.playbackUrl)
                playbackSession.start(
                    PlaybackItem(
                        streamUrl = url,
                        title = current.target.title.title,
                        subtitle = current.target.subtitle,
                    ),
                )
                _state.value = current.copy(resolving = null, resolveError = null)
                _play.tryEmit(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: StreamException) {
                _state.value = current.copy(resolving = null, resolveError = e.message)
            } catch (e: Exception) {
                _state.value = current.copy(resolving = null, resolveError = "Couldn't resolve this stream")
            }
        }
    }

    fun cancelResolve() {
        resolveJob?.cancel()
        resolveJob = null
        val current = _state.value as? StreamPickerUiState.Ready ?: return
        _state.value = current.copy(resolving = null)
    }

    private fun load() {
        viewModelScope.launch {
            var target: StreamTarget? = null
            _state.value = StreamPickerUiState.Loading(null)
            try {
                val details = repository.loadDetails(mediaType, mediaId)
                val episodeTitle = if (season != null && episode != null) {
                    runCatching { repository.loadSeasonEpisodes(mediaId, season) }
                        .getOrNull()
                        ?.firstOrNull { it.episodeNumber == episode }
                        ?.title
                } else {
                    null
                }
                target = StreamTarget(details.title, season, episode, episodeTitle)
                _state.value = StreamPickerUiState.Loading(target)

                val apiKey = settingsStore.realDebridApiKey.first()
                if (apiKey.isBlank()) throw StreamException.MissingKey()
                val imdbId = details.imdbId ?: throw StreamException.NoImdbId()

                val options = cometClient.fetchStreams(apiKey, mediaType, imdbId, season, episode)
                _state.value = StreamPickerUiState.Ready(target = target, options = options)
            } catch (e: CancellationException) {
                throw e
            } catch (e: StreamException) {
                _state.value = StreamPickerUiState.Error(target, e.toKind(), e.message)
            } catch (e: Exception) {
                _state.value = StreamPickerUiState.Error(target, StreamErrorKind.NETWORK)
            }
        }
    }

    private fun StreamException.toKind(): StreamErrorKind = when (this) {
        is StreamException.MissingKey -> StreamErrorKind.MISSING_KEY
        is StreamException.NoImdbId -> StreamErrorKind.NO_IMDB
        is StreamException.NoStreams -> StreamErrorKind.NO_STREAMS
        is StreamException.DebridError -> StreamErrorKind.DEBRID
        is StreamException.ResolveFailed, is StreamException.Network -> StreamErrorKind.NETWORK
    }

    class Factory(
        private val mediaType: MediaType,
        private val mediaId: Int,
        private val season: Int?,
        private val episode: Int?,
        private val repository: TmdbRepository,
        private val cometClient: CometClient,
        private val settingsStore: SettingsStore,
        private val playbackSession: PlaybackSession,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            StreamPickerViewModel(
                mediaType, mediaId, season, episode, repository, cometClient, settingsStore, playbackSession,
            ) as T
    }
}
