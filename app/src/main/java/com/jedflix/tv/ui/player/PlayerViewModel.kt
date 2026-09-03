package com.jedflix.tv.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jedflix.tv.data.playback.PlaybackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUiState(
    val item: PlaybackItem,
    val error: Boolean = false,
)

/** Owns the ExoPlayer so it survives recomposition and is released exactly once. */
class PlayerViewModel(
    context: Context,
    item: PlaybackItem,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState(item))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .build()

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = true)
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
    }

    fun onBackground() {
        player.pause()
    }

    fun onForeground() {
        if (!_state.value.error && player.playbackState != Player.STATE_IDLE) {
            player.play()
        }
    }

    override fun onCleared() {
        player.removeListener(listener)
        player.release()
    }

    class Factory(
        private val context: Context,
        private val item: PlaybackItem,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            PlayerViewModel(context, item) as T
    }

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
    }
}
