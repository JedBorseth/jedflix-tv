package com.jedflix.tv.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKeysTest {
    @Test
    fun leftAndRightOpenChromeWhenHidden() {
        val seen = mutableListOf<String>()
        val handled = dispatch(
            key = Key.DirectionLeft,
            controlsVisible = false,
            onShowChrome = { seen += "show" },
            onSeekBack = { seen += "seek" },
        )
        assertTrue(handled)
        assertEquals(listOf("show"), seen)

        seen.clear()
        dispatch(
            key = Key.DirectionRight,
            controlsVisible = false,
            onShowChrome = { seen += "show" },
            onSeekForward = { seen += "seek" },
        )
        assertEquals(listOf("show"), seen)
    }

    @Test
    fun upStillOpensChromeWhenHidden() {
        var shown = false
        assertTrue(dispatch(Key.DirectionUp, controlsVisible = false, onShowChrome = { shown = true }))
        assertTrue(shown)
    }

    @Test
    fun okSkipsWhenSkipIsVisibleAndChromeIsHidden() {
        val seen = mutableListOf<String>()
        val handled = dispatch(
            key = Key.DirectionCenter,
            controlsVisible = false,
            skipVisible = true,
            onSkip = { seen += "skip" },
            onTogglePlay = { seen += "play" },
        )
        assertTrue(handled)
        assertEquals(listOf("skip"), seen)
    }

    @Test
    fun okTogglesPlayWhenChromeAndSkipAreHidden() {
        var toggled = false
        assertTrue(
            dispatch(
                key = Key.Enter,
                controlsVisible = false,
                onTogglePlay = { toggled = true },
            ),
        )
        assertTrue(toggled)
    }

    @Test
    fun mediaRewindStillSeeks() {
        var seek = false
        assertTrue(dispatch(Key.MediaRewind, controlsVisible = false, onSeekBack = { seek = true }))
        assertTrue(seek)
    }

    @Test
    fun ignoresKeysWhileMenuOpen() {
        assertFalse(dispatch(Key.DirectionLeft, controlsVisible = false, menuOpen = true, onShowChrome = {}))
    }

    @Test
    fun upAndDownDoNotStealFocusWhileChromeIsOpen() {
        assertFalse(dispatch(key = Key.DirectionDown, controlsVisible = true))
        assertFalse(dispatch(key = Key.DirectionUp, controlsVisible = true))
    }

    @Test
    fun okOnTimelineIsConsumedSoItCannotPause() {
        assertEquals(TimelineKeyAction.Consume, handleTimelineKey(Key.DirectionCenter))
        assertEquals(TimelineKeyAction.Consume, handleTimelineKey(Key.Enter))
        assertEquals(TimelineKeyAction.ScrubBack, handleTimelineKey(Key.DirectionLeft))
        assertEquals(TimelineKeyAction.ScrubForward, handleTimelineKey(Key.DirectionRight))
        assertEquals(TimelineKeyAction.Ignore, handleTimelineKey(Key.MediaPlayPause))
    }

    private fun dispatch(
        key: Key,
        controlsVisible: Boolean,
        menuOpen: Boolean = false,
        skipVisible: Boolean = false,
        onShowChrome: () -> Unit = {},
        onTogglePlay: () -> Unit = {},
        onPlay: () -> Unit = {},
        onPause: () -> Unit = {},
        onSeekBack: () -> Unit = {},
        onSeekForward: () -> Unit = {},
        onSkip: () -> Unit = {},
        onStop: () -> Unit = {},
    ): Boolean = handlePlayerKey(
        key = key,
        type = KeyEventType.KeyDown,
        controlsVisible = controlsVisible,
        menuOpen = menuOpen,
        upNextOpen = false,
        error = false,
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
}
