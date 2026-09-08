package com.jedflix.tv.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
    onNeedPicker: (season: Int, episode: Int) -> Unit,
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

    LaunchedEffect(state.error, state.upNext, menu, controlsVisible) {
        val target = when {
            state.error -> return@LaunchedEffect
            state.upNext != null -> upNextFocus
            menu != PlayerMenu.None -> menuFocus
            controlsVisible -> playFocus
            else -> transportFocus
        }
        runCatching { target.requestFocus() }
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
                    !state.error,
            )
            .onKeyEvent { event ->
                handlePlayerKey(
                    event = event,
                    controlsVisible = controlsVisible,
                    menuOpen = menu != PlayerMenu.None,
                    upNextOpen = state.upNext != null,
                    error = state.error,
                    onShowChrome = { showChrome() },
                    onHideChrome = { hideChrome() },
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

        AnimatedVisibility(
            visible = controlsVisible && !state.error && state.upNext == null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PlayerChrome(
                state = state,
                seekHintSec = seekHintSec,
                playFocus = playFocus,
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
                onNext = {
                    showChrome()
                    viewModel.skipToNext()
                },
            )
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

private fun handlePlayerKey(
    event: KeyEvent,
    controlsVisible: Boolean,
    menuOpen: Boolean,
    upNextOpen: Boolean,
    error: Boolean,
    onShowChrome: () -> Unit,
    onHideChrome: () -> Unit,
    onTogglePlay: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onStop: () -> Unit,
): Boolean {
    if (error || event.type != KeyEventType.KeyDown) return false
    if (menuOpen || upNextOpen) return false
    val media = when (event.key) {
        Key.MediaPlay -> {
            onPlay(); true
        }
        Key.MediaPause -> {
            onPause(); true
        }
        Key.MediaPlayPause, Key.Spacebar -> {
            onTogglePlay(); true
        }
        Key.MediaRewind -> {
            onSeekBack(); true
        }
        Key.MediaFastForward -> {
            onSeekForward(); true
        }
        Key.MediaStop -> {
            onStop(); true
        }
        else -> false
    }
    if (media) return true
    if (!controlsVisible) {
        return when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                onTogglePlay(); true
            }
            Key.DirectionLeft -> {
                onSeekBack(); true
            }
            Key.DirectionRight -> {
                onSeekForward(); true
            }
            Key.DirectionUp, Key.DirectionDown -> {
                onShowChrome(); true
            }
            else -> false
        }
    }
    return when (event.key) {
        Key.DirectionUp, Key.DirectionDown -> {
            onHideChrome(); true
        }
        else -> false
    }
}

private const val CONTROLLER_TIMEOUT_MS = 4_000L
private const val SEEK_HINT_MS = 1_000L
