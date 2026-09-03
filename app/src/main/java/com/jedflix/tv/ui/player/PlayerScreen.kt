package com.jedflix.tv.ui.player

import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jedflix.tv.R
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.playback.PlaybackSession
import com.jedflix.tv.ui.theme.WarmWhite
import com.jedflix.tv.ui.theme.Zinc300
import com.jedflix.tv.ui.theme.Zinc400
import com.jedflix.tv.ui.theme.Zinc950

@Composable
fun PlayerScreen(
    playbackSession: PlaybackSession,
    library: UserLibraryRepository,
    onExit: () -> Unit,
) {
    val item = remember { playbackSession.current }
    if (item == null) {
        // Nothing staged (e.g. process death restored this route); bounce back to the picker.
        LaunchedEffect(Unit) { onExit() }
        return
    }

    val context = LocalContext.current
    val viewModel: PlayerViewModel = viewModel(
        key = "player-${item.streamUrl.hashCode()}",
        factory = PlayerViewModel.Factory(context, item, library),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val keyFocus = remember { FocusRequester() }

    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose { viewModel.onBackground() }
    }

    // Compose owns focus for the whole screen; PlayerView only renders. This keeps remote keys
    // working after the controller auto-hides (its buttons would otherwise take and drop focus).
    LaunchedEffect(state.error) {
        if (!state.error) runCatching { keyFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player")
            .focusRequester(keyFocus)
            .onKeyEvent { event ->
                if (state.error) false else handlePlayerKey(event, viewModel.player, playerView, onExit)
            }
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.player
                    useController = true
                    controllerAutoShow = true
                    controllerHideOnTouch = false
                    controllerShowTimeoutMs = CONTROLLER_TIMEOUT_MS
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowSubtitleButton(false)
                    keepScreenOn = true
                    isFocusable = false
                    isClickable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == View.VISIBLE
                        },
                    )
                    playerView = this
                }
            },
            update = { view ->
                if (view.player !== viewModel.player) view.player = viewModel.player
            },
            onRelease = { view ->
                view.player = null
                playerView = null
            },
        )

        AnimatedVisibility(
            visible = controlsVisible && !state.error,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            TitleOverlay(title = state.item.title, subtitle = state.item.subtitle)
        }

        if (state.error) {
            PlayerError(onBack = onExit)
        }
    }
}

/** Maps TV remote keys onto the player. Returns true when consumed. */
private fun handlePlayerKey(
    event: KeyEvent,
    player: Player,
    view: PlayerView?,
    onExit: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    fun reveal() = view?.showController()
    return when (event.key) {
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
            if (player.playWhenReady) player.pause() else player.play()
            reveal()
            true
        }
        Key.MediaPlay -> {
            player.play(); reveal(); true
        }
        Key.MediaPause -> {
            player.pause(); reveal(); true
        }
        Key.DirectionLeft, Key.MediaRewind -> {
            player.seekBack(); reveal(); true
        }
        Key.DirectionRight, Key.MediaFastForward -> {
            player.seekForward(); reveal(); true
        }
        Key.DirectionUp, Key.DirectionDown -> {
            if (view?.isControllerFullyVisible == true) view.hideController() else reveal()
            true
        }
        Key.MediaStop -> {
            onExit(); true
        }
        else -> false
    }
}

@Composable
private fun TitleOverlay(title: String, subtitle: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Zinc950.copy(alpha = 0.85f),
                    1f to Color.Transparent,
                ),
            )
            .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 56.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = WarmWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = Zinc300,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerError(onBack: () -> Unit) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950.copy(alpha = 0.9f))
            .testTag("player-error"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.player_error_title),
                style = MaterialTheme.typography.headlineMedium,
                color = WarmWhite,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.player_error),
                style = MaterialTheme.typography.bodyLarge,
                color = Zinc400,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.focusRequester(backFocus).testTag("player-back"),
                colors = ButtonDefaults.colors(
                    containerColor = WarmWhite,
                    contentColor = Zinc950,
                    focusedContainerColor = WarmWhite,
                    focusedContentColor = Zinc950,
                ),
            ) {
                Text(stringResource(R.string.action_back), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private const val CONTROLLER_TIMEOUT_MS = 4_000
