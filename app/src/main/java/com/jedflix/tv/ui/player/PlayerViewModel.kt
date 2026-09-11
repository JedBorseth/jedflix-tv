@file:OptIn(UnstableApi::class)

package com.jedflix.tv.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.jedflix.tv.data.comet.CometClient
import com.jedflix.tv.data.library.PlaybackProgress
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.playback.AudioFormatLabel
import com.jedflix.tv.data.playback.AudioTrackOption
import com.jedflix.tv.data.playback.EpisodeRef
import com.jedflix.tv.data.playback.NextEpisode
import com.jedflix.tv.data.playback.NextStream
import com.jedflix.tv.data.playback.PlaybackItem
import com.jedflix.tv.data.playback.PlaybackSession
import com.jedflix.tv.data.playback.PlayerAudioSelection
import com.jedflix.tv.data.playback.PlayerLanguages
import com.jedflix.tv.data.playback.TextTrackLabel
import com.jedflix.tv.data.settings.PlaybackPrefs
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.MediaType
import com.jedflix.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Owns the ExoPlayer so it survives recomposition and is released exactly once. */
class PlayerViewModel(
    context: Context,
    item: PlaybackItem,
    private val library: UserLibraryRepository,
    private val settingsStore: SettingsStore,
    private val tmdb: TmdbRepository,
    private val comet: CometClient,
    private val playbackSession: PlaybackSession,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState(item))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private val configured = createConfiguredPlayer(context)
    val player: ExoPlayer = configured.player
    private val trackSelector = configured.trackSelector

    private var didSeek = item.startPositionMs <= 0L
    private var persistJob: Job? = null
    private var prefs: PlaybackPrefs = PlaybackPrefs()
    private var nextRef: EpisodeRef? = null
    private var nextTitle: String = ""
    private var nextStillUrl: String? = null
    private var resolvedNext: PlaybackItem? = null
    private var nextLookupJob: Job? = null
    private var prefetchJob: Job? = null
    private var countdownJob: Job? = null
    private var handlingEnded = false
    private var audioFallbackAttempted = false

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            countdownJob?.cancel()
            prefetchJob?.cancel()
            _state.value = _state.value.copy(error = true, upNext = null, isPlaying = false)
            persistJob?.cancel()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && !didSeek) {
                val start = _state.value.item.startPositionMs
                val duration = player.duration
                if (start > 0L && duration > 0L && start < duration - 2_000L) {
                    player.seekTo(start)
                }
                didSeek = true
            }
            if (playbackState == Player.STATE_ENDED) {
                persistProgress()
                onEnded()
            }
            publishTimeline()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (!isPlaying) persistProgress()
        }

        override fun onTracksChanged(tracks: Tracks) {
            publishTracks(tracks)
        }
    }

    init {
        prefs = runBlocking { settingsStore.playbackPrefs.first() }
        applyPrefs(prefs)
        player.addListener(listener)
        attachItem(_state.value.item, play = true)
        persistJob = viewModelScope.launch {
            launch {
                while (isActive) {
                    delay(250)
                    publishTimeline()
                    maybePrefetch()
                }
            }
            launch {
                while (isActive) {
                    delay(PERSIST_INTERVAL_MS)
                    persistProgress()
                }
            }
        }
        nextLookupJob = viewModelScope.launch { refreshNextEpisode() }
    }

    fun togglePlayPause() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
            player.play()
            return
        }
        if (player.playWhenReady) player.pause() else player.play()
    }

    fun play() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun seekBack() {
        player.seekBack()
        publishTimeline()
    }

    fun seekForward() {
        player.seekForward()
        publishTimeline()
    }

    fun selectAudio(trackId: String) {
        val track = _state.value.audioTracks.firstOrNull { it.id == trackId } ?: return
        val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        applyTrackParams {
            clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            setPreferredAudioLanguage(PlayerLanguages.normalize(track.language) ?: prefs.audioLanguage)
        }
        _state.value = _state.value.copy(selectedAudioId = track.id)
        viewModelScope.launch { settingsStore.setAudioLanguage(track.language.orEmpty()) }
    }

    fun selectText(trackId: String?) {
        if (trackId == null) {
            applyTrackParams {
                clearOverridesOfType(C.TRACK_TYPE_TEXT)
                setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                setPreferredTextLanguage(null)
            }
            _state.value = _state.value.copy(selectedTextId = null)
            viewModelScope.launch { settingsStore.setCaptionsEnabled(false) }
            return
        }
        val track = _state.value.textTracks.firstOrNull { it.id == trackId } ?: return
        val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        applyTrackParams {
            setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            clearOverridesOfType(C.TRACK_TYPE_TEXT)
            setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            setPreferredTextLanguage(PlayerLanguages.normalize(track.language) ?: prefs.captionLanguage)
        }
        _state.value = _state.value.copy(selectedTextId = track.id)
        viewModelScope.launch { settingsStore.setCaptionLanguage(track.language ?: PlayerLanguages.ENGLISH) }
    }

    fun skipToNext() {
        val ref = nextRef ?: return
        countdownJob?.cancel()
        viewModelScope.launch { playNext(ref, countdown = false) }
    }

    fun playUpNextNow() {
        val ref = nextRef ?: return
        countdownJob?.cancel()
        viewModelScope.launch { playNext(ref, countdown = false) }
    }

    fun dismissUpNext() {
        countdownJob?.cancel()
        _state.value = _state.value.copy(upNext = null)
    }

    fun onBackground() {
        player.pause()
        persistProgress()
    }

    fun onForeground() {
        if (!_state.value.error && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
            player.play()
        }
    }

    override fun onCleared() {
        persistJob?.cancel()
        countdownJob?.cancel()
        prefetchJob?.cancel()
        nextLookupJob?.cancel()
        val snapshot = captureProgress()
        player.removeListener(listener)
        player.release()
        if (snapshot != null) {
            runBlocking {
                withContext(Dispatchers.IO) { library.recordPlayback(snapshot) }
            }
        }
    }

    private fun attachItem(item: PlaybackItem, play: Boolean) {
        didSeek = item.startPositionMs <= 0L
        handlingEnded = false
        audioFallbackAttempted = false
        resolvedNext = null
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
        player.playWhenReady = play
        player.prepare()
    }

    private fun applyPrefs(prefs: PlaybackPrefs) {
        applyTrackParams {
            clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            clearOverridesOfType(C.TRACK_TYPE_TEXT)
            setPreferredAudioLanguage(prefs.audioLanguage)
            setPreferredTextLanguage(prefs.captionLanguage.takeIf { prefs.captionsEnabled })
            setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !prefs.captionsEnabled)
        }
    }

    private fun applyTrackParams(mutate: DefaultTrackSelector.Parameters.Builder.() -> Unit) {
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setExceedRendererCapabilitiesIfNecessary(false)
            .setConstrainAudioChannelCountToDeviceCapabilities(false)
            .apply(mutate)
            .build()
    }

    private fun publishTimeline() {
        if (_state.value.error) return
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val position = when {
            player.playbackState == Player.STATE_ENDED && duration > 0L -> duration
            else -> player.currentPosition.coerceAtLeast(0L)
        }
        val current = _state.value
        if (current.positionMs == position &&
            current.durationMs == duration &&
            current.isPlaying == player.isPlaying &&
            current.isEnded == (player.playbackState == Player.STATE_ENDED)
        ) {
            return
        }
        _state.value = current.copy(
            positionMs = position,
            durationMs = duration,
            isPlaying = player.isPlaying,
            isEnded = player.playbackState == Player.STATE_ENDED,
        )
    }

    private fun publishTracks(tracks: Tracks) {
        val audioOptions = mutableListOf<AudioTrackOption>()
        val audio = mutableListOf<SelectableTrack>()
        val text = mutableListOf<SelectableTrack>()
        var selectedAudio: String? = null
        var selectedText: String? = null
        tracks.groups.forEachIndexed { groupIndex, group ->
            for (index in 0 until group.length) {
                val format = group.getTrackFormat(index)
                val supported = group.isTrackSupported(index)
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        audioOptions += AudioTrackOption(
                            groupIndex = groupIndex,
                            trackIndex = index,
                            language = format.language,
                            supported = supported,
                            selected = group.isTrackSelected(index),
                            channelCount = format.channelCount,
                            isDefault = (format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0,
                        )
                        if (!supported) continue
                        val id = "a:$groupIndex:$index"
                        audio += SelectableTrack(
                            id = id,
                            label = AudioFormatLabel.format(
                                languageCode = format.language,
                                codecs = format.codecs,
                                mimeType = format.sampleMimeType,
                                channelCount = format.channelCount,
                                trackLabel = format.label,
                            ),
                            language = format.language,
                            groupIndex = groupIndex,
                            trackIndex = index,
                        )
                        if (group.isTrackSelected(index)) selectedAudio = id
                    }
                    C.TRACK_TYPE_TEXT -> {
                        if (!supported) continue
                        val id = "t:$groupIndex:$index"
                        text += SelectableTrack(
                            id = id,
                            label = TextTrackLabel.format(format.language, format.label),
                            language = format.language,
                            groupIndex = groupIndex,
                            trackIndex = index,
                        )
                        if (group.isTrackSelected(index)) selectedText = id
                    }
                }
            }
        }
        val fallback = PlayerAudioSelection.pickPlayable(audioOptions, prefs.audioLanguage)
        if (fallback != null && !fallback.selected && !audioFallbackAttempted) {
            audioFallbackAttempted = true
            val group = tracks.groups.getOrNull(fallback.groupIndex) ?: return
            applyTrackParams {
                clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, fallback.trackIndex))
            }
            return
        }
        _state.value = _state.value.copy(
            audioTracks = audio,
            textTracks = text,
            selectedAudioId = selectedAudio,
            selectedTextId = selectedText,
        )
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

    private suspend fun refreshNextEpisode() {
        val item = _state.value.item
        if (item.mediaType != MediaType.TV || item.season == null || item.episode == null) {
            nextRef = null
            _state.value = _state.value.copy(hasNextEpisode = false)
            return
        }
        val details = runCatching { tmdb.loadDetails(item.mediaType, item.tmdbId) }.getOrNull()
        val currentEpisodes = runCatching { tmdb.loadSeasonEpisodes(item.tmdbId, item.season) }.getOrNull().orEmpty()
        val nextInSeason = NextEpisode.nextInSeason(currentEpisodes.map { it.episodeNumber }, item.episode)
        if (nextInSeason != null) {
            val ep = currentEpisodes.firstOrNull { it.episodeNumber == nextInSeason }
            setNext(EpisodeRef(item.season, nextInSeason), ep?.title.orEmpty(), ep?.stillUrl)
            return
        }
        val nextSeason = NextEpisode.nextSeasonNumber(details?.seasons.orEmpty().map { it.seasonNumber }, item.season)
        if (nextSeason == null) {
            nextRef = null
            _state.value = _state.value.copy(hasNextEpisode = false)
            return
        }
        val nextEpisodes = runCatching { tmdb.loadSeasonEpisodes(item.tmdbId, nextSeason) }.getOrNull().orEmpty()
        val first = NextEpisode.firstEpisode(nextEpisodes.map { it.episodeNumber })
        if (first == null) {
            nextRef = null
            _state.value = _state.value.copy(hasNextEpisode = false)
            return
        }
        val ep = nextEpisodes.firstOrNull { it.episodeNumber == first }
        setNext(EpisodeRef(nextSeason, first), ep?.title.orEmpty(), ep?.stillUrl)
    }

    private fun setNext(ref: EpisodeRef, title: String, stillUrl: String?) {
        nextRef = ref
        nextTitle = title
        nextStillUrl = stillUrl
        _state.value = _state.value.copy(hasNextEpisode = true)
    }

    private fun remainingMs(): Long {
        val duration = player.duration
        if (duration <= 0L) return Long.MAX_VALUE
        return (duration - player.currentPosition).coerceAtLeast(0L)
    }

    private fun maybePrefetch() {
        if (_state.value.error || _state.value.upNext != null) return
        val ref = nextRef ?: return
        if (resolvedNext != null || prefetchJob?.isActive == true) return
        if (remainingMs() > PREFETCH_REMAINING_MS) return
        prefetchJob = viewModelScope.launch {
            resolvedNext = resolveNext(ref)
        }
    }

    private fun onEnded() {
        if (handlingEnded || _state.value.error) return
        handlingEnded = true
        viewModelScope.launch {
            nextLookupJob?.join()
            val item = _state.value.item
            val ref = nextRef
            if (item.mediaType == MediaType.TV && ref != null) {
                playNext(ref, countdown = true)
            } else if (item.mediaType == MediaType.TV && item.season != null) {
                _events.tryEmit(PlayerEvent.SeriesComplete)
            }
        }
    }

    private suspend fun playNext(ref: EpisodeRef, countdown: Boolean) {
        val existing = resolvedNext
        if (existing != null && !countdown) {
            commitNext(existing)
            return
        }
        _state.value = _state.value.copy(
            upNext = UpNextUi(
                season = ref.season,
                episode = ref.episode,
                title = nextTitle,
                stillUrl = nextStillUrl,
                secondsRemaining = if (existing != null && countdown) COUNTDOWN_SECONDS else null,
            ),
        )
        val resolved = existing ?: resolveNext(ref)
        if (resolved == null) {
            _state.value = _state.value.copy(upNext = null)
            _events.tryEmit(PlayerEvent.OpenPicker(ref.season, ref.episode))
            return
        }
        resolvedNext = resolved
        if (!countdown) {
            commitNext(resolved)
            return
        }
        startCountdown(resolved)
    }

    private fun startCountdown(next: PlaybackItem) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (seconds in COUNTDOWN_SECONDS downTo 1) {
                _state.value = _state.value.copy(
                    upNext = _state.value.upNext?.copy(secondsRemaining = seconds)
                        ?: UpNextUi(
                            season = next.season ?: 0,
                            episode = next.episode ?: 0,
                            title = nextTitle,
                            stillUrl = nextStillUrl,
                            secondsRemaining = seconds,
                        ),
                )
                delay(1_000)
            }
            commitNext(next)
        }
    }

    private fun commitNext(next: PlaybackItem) {
        countdownJob?.cancel()
        persistProgress()
        playbackSession.start(next)
        prefs = runBlocking { settingsStore.playbackPrefs.first() }
        applyPrefs(prefs)
        _state.value = PlayerUiState(item = next, hasNextEpisode = false)
        attachItem(next, play = true)
        nextLookupJob?.cancel()
        nextLookupJob = viewModelScope.launch { refreshNextEpisode() }
        handlingEnded = false
    }

    private suspend fun resolveNext(ref: EpisodeRef): PlaybackItem? {
        val current = _state.value.item
        val resolution = current.resolution ?: return null
        val imdbId = current.imdbId ?: tmdb.loadDetails(current.mediaType, current.tmdbId).imdbId ?: return null
        val apiKey = settingsStore.realDebridApiKey.first()
        if (apiKey.isBlank()) return null
        val options = runCatching {
            comet.fetchStreams(apiKey, MediaType.TV, imdbId, ref.season, ref.episode, nextTitle)
        }.getOrNull() ?: return null
        val option = NextStream.pickCachedAtResolution(options, resolution) ?: return null
        val url = runCatching { comet.resolvePlaybackUrl(option.playbackUrl) }.getOrNull() ?: return null
        val startPositionMs = library.playbackPosition(MediaType.TV, current.tmdbId, ref.season, ref.episode)
        val details = runCatching { tmdb.loadDetails(current.mediaType, current.tmdbId) }.getOrNull()
        val title = details?.title ?: return PlaybackItem(
            streamUrl = url,
            title = current.title,
            subtitle = NextEpisode.episodeSubtitle(ref.season, ref.episode, nextTitle),
            mediaType = MediaType.TV,
            tmdbId = current.tmdbId,
            season = ref.season,
            episode = ref.episode,
            overview = current.overview,
            posterUrl = current.posterUrl,
            backdropUrl = current.backdropUrl,
            year = current.year,
            rating = current.rating,
            genres = current.genres,
            startPositionMs = startPositionMs,
            imdbId = imdbId,
            resolution = option.resolution,
        )
        return PlaybackItem(
            streamUrl = url,
            title = title.title,
            subtitle = NextEpisode.episodeSubtitle(ref.season, ref.episode, nextTitle),
            mediaType = MediaType.TV,
            tmdbId = title.id,
            season = ref.season,
            episode = ref.episode,
            overview = title.overview,
            posterUrl = title.posterUrl,
            backdropUrl = title.backdropUrl,
            year = title.year,
            rating = title.rating,
            genres = title.genres,
            startPositionMs = startPositionMs,
            imdbId = imdbId,
            resolution = option.resolution,
        )
    }

    class Factory(
        private val context: Context,
        private val item: PlaybackItem,
        private val library: UserLibraryRepository,
        private val settingsStore: SettingsStore,
        private val tmdb: TmdbRepository,
        private val comet: CometClient,
        private val playbackSession: PlaybackSession,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            PlayerViewModel(context, item, library, settingsStore, tmdb, comet, playbackSession) as T
    }

    private companion object {
        const val PERSIST_INTERVAL_MS = 10_000L
        const val PREFETCH_REMAINING_MS = 45_000L
        const val COUNTDOWN_SECONDS = 8
    }
}
