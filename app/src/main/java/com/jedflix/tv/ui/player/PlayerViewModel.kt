package com.jedflix.tv.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jedflix.tv.data.library.PlaybackProgress
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.playback.PlaybackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val item: PlaybackItem,
    val error: Boolean = false,
)

/** Owns the ExoPlayer so it survives recomposition and is released exactly once. */
class PlayerViewModel(
    context: Context,
    item: PlaybackItem,
    private val library: UserLibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState(item))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .build()

    private var didSeek = item.startPositionMs <= 0L
    private var persistJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = true)
            persistJob?.cancel()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && !didSeek) {
                val start = item.startPositionMs
                val duration = player.duration
                if (start > 0L && duration > 0L && start < duration - 2_000L) {
                    player.seekTo(start)
                }
                didSeek = true
            }
            if (playbackState == Player.STATE_ENDED) {
                persistProgress()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) persistProgress()
        }
    }

    init {
        player.addListener(listener)
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(item.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setSubtitle(item.subtitle)
                        .build(),
                )
                .build(),
        )
        player.setAudioAttributes(player.audioAttributes, true)
        player.playWhenReady = true
        player.prepare()
        persistJob = viewModelScope.launch {
            while (isActive) {
                delay(PERSIST_INTERVAL_MS)
                persistProgress()
            }
        }
    }

    fun onBackground() {
        player.pause()
        persistProgress()
    }

    fun onForeground() {
        if (!_state.value.error && player.playbackState != Player.STATE_IDLE) {
            player.play()
        }
    }

    override fun onCleared() {
        persistJob?.cancel()
        val snapshot = captureProgress()
        player.removeListener(listener)
        player.release()
        if (snapshot != null) {
            runBlocking {
                withContext(Dispatchers.IO) { library.recordPlayback(snapshot) }
            }
        }
    }

    private fun persistProgress() {
        if (_state.value.error) return
        val snapshot = captureProgress() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            library.recordPlayback(snapshot)
        }
    }

    private fun captureProgress(): PlaybackProgress? {
        val item = _state.value.item
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val position = when {
            player.playbackState == Player.STATE_ENDED && duration > 0L -> duration
            else -> player.currentPosition.coerceAtLeast(0L)
        }
        if (position <= 0L && duration <= 0L) return null
        return PlaybackProgress(
            mediaType = item.mediaType,
            tmdbId = item.tmdbId,
            season = item.season,
            episode = item.episode,
            positionMs = position,
            durationMs = duration,
            title = item.title,
            overview = item.overview,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl,
            year = item.year,
            rating = item.rating,
            genres = item.genres,
        )
    }

    class Factory(
        private val context: Context,
        private val item: PlaybackItem,
        private val library: UserLibraryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            PlayerViewModel(context, item, library) as T
    }

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
        const val PERSIST_INTERVAL_MS = 10_000L
    }
}
