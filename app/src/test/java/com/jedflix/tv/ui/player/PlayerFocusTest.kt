package com.jedflix.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerFocusTest {
    @Test
    fun openingChromeFocusesPlayEvenIfSkipIsVisible() {
        assertEquals(
            PlayerFocusRequest.Play,
            playerFocusWhenChromeChanges(
                error = false,
                upNextOpen = false,
                menuOpen = false,
                controlsVisible = true,
            ),
        )
    }

    @Test
    fun skipDoesNotStealFocusWhileChromeIsOpen() {
        assertEquals(
            PlayerFocusRequest.Unchanged,
            playerFocusWhenSkipChanges(
                error = false,
                upNextOpen = false,
                menuOpen = false,
                controlsVisible = true,
                skipVisible = true,
            ),
        )
    }

    @Test
    fun skipTakesFocusWhenChromeIsHidden() {
        assertEquals(
            PlayerFocusRequest.Skip,
            playerFocusWhenSkipChanges(
                error = false,
                upNextOpen = false,
                menuOpen = false,
                controlsVisible = false,
                skipVisible = true,
            ),
        )
    }

    @Test
    fun hidingChromeReturnsTransportWhenSkipIsGone() {
        assertEquals(
            PlayerFocusRequest.Transport,
            playerFocusWhenChromeChanges(
                error = false,
                upNextOpen = false,
                menuOpen = false,
                controlsVisible = false,
            ),
        )
        assertEquals(
            PlayerFocusRequest.Unchanged,
            playerFocusWhenSkipChanges(
                error = false,
                upNextOpen = false,
                menuOpen = false,
                controlsVisible = false,
                skipVisible = false,
            ),
        )
    }
}
