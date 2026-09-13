package com.jedflix.tv.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.jedflix.tv.R
import com.jedflix.tv.data.comet.CometClient
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.playback.PlaybackSession
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.delay

private enum class PlayerMenu { None, Audio, Captions }

@Composable
fun PlayerScreen(
    playbackSession: PlaybackSession,
    library: UserLibraryRepository,
    settingsStore: SettingsStore,
    tmdb: TmdbRepository,
    comet: CometClient,
    onExit: () -> Unit,
    onSeriesComplete: () -> Unit,
    onNeedPicker: (season: Int?, episode: Int?) -> Unit,
) {
    val item = remember { playbackSession.current }
    if (item == null) {
        LaunchedEffect(Unit) { onExit() }
        return
    }

    val context = LocalContext.current
    val viewModel: PlayerViewModel = viewModel(
        key = "player",
        factory = PlayerViewModel.Factory(
            context,
            item,
            library,
            settingsStore,
            tmdb,
            comet,
            playbackSession,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(PlayerMenu.None) }
    var seekHintSec by remember { mutableStateOf<Int?>(null) }
    var hideGeneration by remember { mutableIntStateOf(0) }
    val transportFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    val upNextFocus = remember { FocusRequester() }

    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose { viewModel.onBackground() }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PlayerEvent.SeriesComplete -> onSeriesComplete()
                is PlayerEvent.OpenPicker -> onNeedPicker(event.season, event.episode)
            }
        }
    }

    fun showChrome() {
        controlsVisible = true
        hideGeneration += 1
    }

    fun hideChrome() {
        if (menu != PlayerMenu.None || state.upNext != null) return
        controlsVisible = false
        menu = PlayerMenu.None
    }

    fun seekBy(seconds: Int) {
        if (seconds >= 0) viewModel.seekForward() else viewModel.seekBack()
        seekHintSec = seconds
        showChrome()
    }

    LaunchedEffect(controlsVisible, state.isPlaying, state.isEnded, menu, state.upNext, hideGeneration, state.error) {
        if (state.error || state.upNext != null || menu != PlayerMenu.None) return@LaunchedEffect
        if (!controlsVisible) return@LaunchedEffect
        if (!state.isPlaying || state.isEnded) return@LaunchedEffect
        delay(CONTROLLER_TIMEOUT_MS)
        hideChrome()
    }

    LaunchedEffect(seekHintSec) {
        if (seekHintSec == null) return@LaunchedEffect
        delay(SEEK_HINT_MS)
        seekHintSec = null
    }

    val onSurfaceTap by rememberUpdatedState {
        if (menu != PlayerMenu.None || state.upNext != null || state.error) return@rememberUpdatedState
        viewModel.togglePlayPause()
        showChrome()
    }

    LaunchedEffect(state.error, state.upNext, menu, controlsVisible) {
        val target = when (
            playerFocusWhenChromeChanges(
                error = state.error,
                upNextOpen = state.upNext != null,
                menuOpen = menu != PlayerMenu.None,
                controlsVisible = controlsVisible,
            )
        ) {
            PlayerFocusRequest.UpNext -> upNextFocus
            PlayerFocusRequest.Menu -> menuFocus
            PlayerFocusRequest.Play -> playFocus
            PlayerFocusRequest.Transport -> transportFocus
            PlayerFocusRequest.Skip, PlayerFocusRequest.Unchanged -> return@LaunchedEffect
        }
        withFrameNanos { }
        runCatching { target.requestFocus() }
    }

    LaunchedEffect(state.error, state.upNext, menu, controlsVisible, state.skip) {
        if (
            playerFocusWhenSkipChanges(
                error = state.error,
                upNextOpen = state.upNext != null,
                menuOpen = menu != PlayerMenu.None,
                controlsVisible = controlsVisible,
                skipVisible = state.skip != null,
            ) != PlayerFocusRequest.Skip
        ) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        runCatching { skipFocus.requestFocus() }
    }

    BackHandler(enabled = menu != PlayerMenu.None || state.upNext != null) {
        when {
            menu != PlayerMenu.None -> menu = PlayerMenu.None
            else -> viewModel.dismissUpNext()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player")
            .focusRequester(transportFocus)
            .focusable(
                enabled = !controlsVisible &&
                    menu == PlayerMenu.None &&
                    state.upNext == null &&
                    state.skip == null &&
                    !state.error,
            )
            .onKeyEvent { event ->
                handlePlayerKey(
                    event = event,
                    controlsVisible = controlsVisible,
                    menuOpen = menu != PlayerMenu.None,
                    upNextOpen = state.upNext != null,
                    error = state.error,
                    skipVisible = state.skip != null,
                    onShowChrome = { showChrome() },
                    onTogglePlay = {
                        viewModel.togglePlayPause()
                        showChrome()
                    },
                    onPlay = {
                        viewModel.play()
                        showChrome()
                    },
                    onPause = {
                        viewModel.pause()
                        showChrome()
                    },
                    onSeekBack = { seekBy(-10) },
                    onSeekForward = { seekBy(10) },
                    onSkip = { viewModel.skipSegment() },
                    onStop = onExit,
                )
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    keepScreenOn = true
                    subtitleView?.apply {
                        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.35f)
                        setBottomPaddingFraction(0.12f)
                    }
                    isFocusable = false
                    isClickable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            update = { view ->
                if (view.player !== viewModel.player) view.player = viewModel.player
            },
            onRelease = { view -> view.player = null },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("player-surface")
                .pointerInput(Unit) {
                    detectTapGestures { onSurfaceTap() }
                },
        )

        AnimatedVisibility(
            visible = controlsVisible && !state.error && state.upNext == null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PlayerChrome(
                state = state,
                seekHintSec = seekHintSec,
                playFocus = playFocus,
                timelineFocus = timelineFocus,
                onPlayPause = {
                    viewModel.togglePlayPause()
                    showChrome()
                },
                onSeekBack = { seekBy(-10) },
                onSeekForward = { seekBy(10) },
                onCaptions = {
                    showChrome()
                    menu = PlayerMenu.Captions
                },
                onAudio = {
                    showChrome()
                    menu = PlayerMenu.Audio
                },
                onSwitchStream = {
                    showChrome()
                    viewModel.switchStream()
                },
                onScrubBy = { deltaMs ->
                    viewModel.scrubBy(deltaMs)
                    showChrome()
                },
                onSurfaceTap = { onSurfaceTap() },
            )
        }

        if (!state.error && state.upNext == null && menu == PlayerMenu.None) {
            state.skip?.let { skip ->
                SkipOverlay(
                    skip = skip,
                    skipFocus = skipFocus,
                    raised = controlsVisible,
                    onSkip = { viewModel.skipSegment() },
                )
            }
        }

        if (menu != PlayerMenu.None && !state.error) {
            TrackMenu(
                title = stringResource(
                    if (menu == PlayerMenu.Audio) R.string.player_audio else R.string.player_captions,
                ),
                tracks = if (menu == PlayerMenu.Audio) state.audioTracks else state.textTracks,
                selectedId = if (menu == PlayerMenu.Audio) state.selectedAudioId else state.selectedTextId,
                includeOff = menu == PlayerMenu.Captions,
                emptyMessage = stringResource(
                    if (menu == PlayerMenu.Audio) R.string.player_no_audio else R.string.player_no_captions,
                ),
                firstFocus = menuFocus,
                onSelect = { id ->
                    if (menu == PlayerMenu.Audio) {
                        if (id != null) viewModel.selectAudio(id)
                    } else {
                        viewModel.selectText(id)
                    }
                    menu = PlayerMenu.None
                    showChrome()
                },
            )
        }

        state.upNext?.let { upNext ->
            UpNextOverlay(
                upNext = upNext,
                playFocus = upNextFocus,
                onPlayNow = viewModel::playUpNextNow,
            )
        }

        if (state.error) {
            PlayerError(onBack = onExit)
        }
    }
}

private const val CONTROLLER_TIMEOUT_MS = 4_000L
private const val SEEK_HINT_MS = 1_000L
