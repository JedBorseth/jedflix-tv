package com.jedflix.tv.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerPreviewStateTest {

    @Test
    fun opensOnlyAfterHoldAndPlayerReady() {
        var state = TrailerPreviewState().reduce(TrailerPreviewEvent.Focused("movie-1"))
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        assertEquals(TrailerPreviewPhase.Preparing, state.phase)
        assertFalse(state.visible)

        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        assertEquals(TrailerPreviewPhase.Opening, state.phase)
        assertTrue(state.visible)
    }

    @Test
    fun holdFirstThenReadyStillOpens() {
        var state = TrailerPreviewState().reduce(TrailerPreviewEvent.Focused("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        assertEquals(TrailerPreviewPhase.Preparing, state.phase)
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        assertEquals(TrailerPreviewPhase.Opening, state.phase)
    }

    @Test
    fun clipUrlAfterPlayerReadyStillOpens() {
        var state = TrailerPreviewState().reduce(TrailerPreviewEvent.Focused("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        assertEquals(TrailerPreviewPhase.Preparing, state.phase)
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        assertEquals(TrailerPreviewPhase.Opening, state.phase)
    }

    @Test
    fun ignoresStaleEventsFromPreviousTitle() {
        var state = TrailerPreviewState().reduce(TrailerPreviewEvent.Focused("movie-1"))
        state = state.reduce(TrailerPreviewEvent.Focused("movie-2"))
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        assertEquals(TrailerPreviewPhase.Preparing, state.phase)
        assertEquals("movie-2", state.titleKey)
        assertFalse(state.playerReady)
    }

    @Test
    fun sameTitleWhilePreparingIsIgnored() {
        val preparing = TrailerPreviewState().reduce(TrailerPreviewEvent.Focused("movie-1"))
        val again = preparing.reduce(TrailerPreviewEvent.Focused("movie-1"))
        assertEquals(preparing, again)
    }

    @Test
    fun graceTimeoutHidesWhenStillPreparing() {
        var state = TrailerPreviewState().reduce(TrailerPreviewEvent.Focused("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        state = state.reduce(TrailerPreviewEvent.HoldExpiredUnready)
        assertEquals(TrailerPreviewPhase.Hidden, state.phase)
        assertTrue(state.failed)
    }

    @Test
    fun graceTimeoutDoesNotCancelOpenPreview() {
        var state = TrailerPreviewState().reduce(TrailerPreviewEvent.Focused("movie-1"))
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        state = state.reduce(TrailerPreviewEvent.HoldExpiredUnready)
        assertEquals(TrailerPreviewPhase.Opening, state.phase)
    }

    @Test
    fun morphFinishedStartsPlaybackPhase() {
        var state = TrailerPreviewState().reduce(TrailerPreviewEvent.Focused("movie-1"))
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        state = state.reduce(TrailerPreviewEvent.MorphFinished)
        assertEquals(TrailerPreviewPhase.Playing, state.phase)
    }

    @Test
    fun clipEndResetsSoTheSamePosterDoesNotReplay() {
        var state = TrailerPreviewState().reduce(TrailerPreviewEvent.Focused("movie-1"))
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        state = state.reduce(TrailerPreviewEvent.MorphFinished)
        state = state.reduce(TrailerPreviewEvent.ClipEnded)
        assertEquals(TrailerPreviewState(), state)
        state = TrailerPreviewState().reduce(TrailerPreviewEvent.Focused("movie-1"))
        state = state.reduce(TrailerPreviewEvent.ClipEnded)
        assertEquals(TrailerPreviewPhase.Preparing, state.phase)
    }

    @Test
    fun playerFailureHidesWithoutOpening() {
        var state = TrailerPreviewState().reduce(TrailerPreviewEvent.Focused("movie-1"))
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        state = state.reduce(TrailerPreviewEvent.PlayerFailed)
        assertEquals(TrailerPreviewPhase.Hidden, state.phase)
        assertTrue(state.failed)
    }

    private companion object {
        const val CLIP = "https://clips.example.com/dQw4w9WgXcQ.mp4"
    }
}
