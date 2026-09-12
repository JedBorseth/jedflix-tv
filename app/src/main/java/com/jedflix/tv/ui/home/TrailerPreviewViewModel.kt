@file:OptIn(UnstableApi::class)

package com.jedflix.tv.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.jedflix.tv.data.settings.QualityProfile
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.data.trailer.TrailerClipUrls
import com.jedflix.tv.data.trailer.TrailerPreviewEvent
import com.jedflix.tv.data.trailer.TrailerPreviewPhase
import com.jedflix.tv.data.trailer.TrailerPreviewState
import com.jedflix.tv.data.trailer.reduce
import com.jedflix.tv.jedflixUserAgent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrailerPreviewUi(
    val title: MediaTitle? = null,
    val phase: TrailerPreviewPhase = TrailerPreviewPhase.Hidden,
)

class TrailerPreviewViewModel(
    context: Context,
    private val tmdb: TmdbRepository,
    private val clipBaseUrl: String,
    settingsStore: SettingsStore,
) : ViewModel() {

    val player: ExoPlayer = createPreviewPlayer(context)

    private val _ui = MutableStateFlow(TrailerPreviewUi())
    val ui: StateFlow<TrailerPreviewUi> = _ui.asStateFlow()

    private var machine = TrailerPreviewState()
    private var focusedTitle: MediaTitle? = null
    private var quality: QualityProfile = QualityProfile.Max
    private var holdJob: Job? = null
    private var resolveJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val key = machine.titleKey ?: return
            when (playbackState) {
                Player.STATE_READY -> dispatch(TrailerPreviewEvent.PlayerReady(key))
                Player.STATE_ENDED -> {
                    if (machine.phase == TrailerPreviewPhase.Playing) {
                        dispatch(TrailerPreviewEvent.ClipEnded)
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            dispatch(TrailerPreviewEvent.PlayerFailed)
        }
    }

    init {
        player.addListener(listener)
        viewModelScope.launch {
            settingsStore.qualityProfile.collect { profile ->
                quality = profile
                if (profile == QualityProfile.Low) reset()
            }
        }
    }

    fun onTitleFocused(title: MediaTitle) {
        if (quality == QualityProfile.Low || clipBaseUrl.isBlank()) {
            reset()
            return
        }
        val keepGoing = machine.titleKey == title.key &&
            !machine.failed &&
            machine.phase != TrailerPreviewPhase.Hidden
        focusedTitle = title
        dispatch(TrailerPreviewEvent.Focused(title.key))
        if (keepGoing) return
        stopPlayer()
        startPrepare(title)
    }

    fun onMorphFinished() {
        dispatch(TrailerPreviewEvent.MorphFinished)
        if (machine.phase == TrailerPreviewPhase.Playing) {
            player.playWhenReady = true
        }
    }

    fun reset() {
        holdJob?.cancel()
        resolveJob?.cancel()
        holdJob = null
        resolveJob = null
        focusedTitle = null
        stopPlayer()
        dispatch(TrailerPreviewEvent.Reset)
    }

    override fun onCleared() {
        player.removeListener(listener)
        player.release()
        super.onCleared()
    }

    private fun startPrepare(title: MediaTitle) {
        holdJob?.cancel()
        resolveJob?.cancel()
        holdJob = viewModelScope.launch {
            delay(HOLD_MS)
            dispatch(TrailerPreviewEvent.HoldElapsed)
            delay(READY_GRACE_MS)
            dispatch(TrailerPreviewEvent.HoldExpiredUnready)
        }
        resolveJob = viewModelScope.launch {
            val youtubeKey = tmdb.loadTrailerYoutubeKey(title.mediaType, title.id)
            if (machine.titleKey != title.key) return@launch
            val url = youtubeKey?.let { TrailerClipUrls.url(clipBaseUrl, it) }
            if (url == null) {
                dispatch(TrailerPreviewEvent.PlayerFailed)
                return@launch
            }
            dispatch(TrailerPreviewEvent.ClipReady(title.key, url))
            attachClip(url)
        }
    }

    private fun attachClip(url: String) {
        player.playWhenReady = false
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setEndPositionMs(CLIP_END_MS)
                        .build(),
                )
                .build(),
        )
        player.prepare()
    }

    private fun stopPlayer() {
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
    }

    private fun dispatch(event: TrailerPreviewEvent) {
        val previous = machine
        machine = previous.reduce(event)
        if (machine.phase == TrailerPreviewPhase.Hidden && previous.phase != TrailerPreviewPhase.Hidden) {
            holdJob?.cancel()
            resolveJob?.cancel()
            stopPlayer()
        }
        publish()
    }

    private fun publish() {
        val title = focusedTitle?.takeIf { it.key == machine.titleKey }
        _ui.value = TrailerPreviewUi(title = title, phase = machine.phase)
    }

    class Factory(
        private val context: Context,
        private val tmdb: TmdbRepository,
        private val clipBaseUrl: String,
        private val settingsStore: SettingsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            TrailerPreviewViewModel(context, tmdb, clipBaseUrl, settingsStore) as T
    }

    companion object {
        const val HOLD_MS = 5_000L
        const val READY_GRACE_MS = 2_000L
        const val MORPH_MS = 450
        private const val CLIP_END_MS = 30_000L
    }
}

private fun createPreviewPlayer(context: Context): ExoPlayer {
    val appContext = context.applicationContext
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(jedflixUserAgent())
        .setAllowCrossProtocolRedirects(true)
    return ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(http))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        .build()
}
