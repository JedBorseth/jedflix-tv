package com.jedflix.tv.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

internal fun handlePlayerKey(
    event: KeyEvent,
    controlsVisible: Boolean,
    menuOpen: Boolean,
    upNextOpen: Boolean,
    error: Boolean,
    skipVisible: Boolean,
    onShowChrome: () -> Unit,
    onTogglePlay: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
): Boolean = handlePlayerKey(
    key = event.key,
    type = event.type,
    controlsVisible = controlsVisible,
    menuOpen = menuOpen,
    upNextOpen = upNextOpen,
    error = error,
    skipVisible = skipVisible,
    onShowChrome = onShowChrome,
    onTogglePlay = onTogglePlay,
    onPlay = onPlay,
    onPause = onPause,
    onSeekBack = onSeekBack,
    onSeekForward = onSeekForward,
    onSkip = onSkip,
    onStop = onStop,
)

internal fun handlePlayerKey(
    key: Key,
    type: KeyEventType,
    controlsVisible: Boolean,
    menuOpen: Boolean,
    upNextOpen: Boolean,
    error: Boolean,
    skipVisible: Boolean,
    onShowChrome: () -> Unit,
    onTogglePlay: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
): Boolean {
    if (error || type != KeyEventType.KeyDown) return false
    if (menuOpen || upNextOpen) return false
    val media = when (key) {
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
        return when (key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (skipVisible) onSkip() else onTogglePlay()
                true
            }
            Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                onShowChrome(); true
            }
            else -> false
        }
    }
    return false
}

internal enum class TimelineKeyAction {
    ScrubBack,
    ScrubForward,
    Consume,
    Ignore,
}

/** Left/Right scrub; OK is swallowed so it cannot pause while the timeline is focused. */
internal fun handleTimelineKey(key: Key): TimelineKeyAction = when (key) {
    Key.DirectionLeft -> TimelineKeyAction.ScrubBack
    Key.DirectionRight -> TimelineKeyAction.ScrubForward
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> TimelineKeyAction.Consume
    else -> TimelineKeyAction.Ignore
}
