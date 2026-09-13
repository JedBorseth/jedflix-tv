package com.jedflix.tv.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerPreviewStateTest {

    @Test
    fun opensOnlyAfterHoldAndPlayerReady() {
        var state = TrailerPreviewState().reduce(focus("movie-1"))
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
        var state = TrailerPreviewState().reduce(focus("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        assertEquals(TrailerPreviewPhase.Preparing, state.phase)
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        assertEquals(TrailerPreviewPhase.Opening, state.phase)
    }

    @Test
    fun clipUrlAfterPlayerReadyStillOpens() {
        var state = TrailerPreviewState().reduce(focus("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        assertEquals(TrailerPreviewPhase.Preparing, state.phase)
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        assertEquals(TrailerPreviewPhase.Opening, state.phase)
    }

    @Test
    fun ignoresStaleEventsFromPreviousTitle() {
        var state = TrailerPreviewState().reduce(focus("movie-1"))
        state = state.reduce(focus("movie-2"))
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        assertEquals(TrailerPreviewPhase.Preparing, state.phase)
        assertEquals("movie-2", state.titleKey)
        assertFalse(state.playerReady)
    }

    @Test
    fun sameTitleWhilePreparingIsIgnored() {
        val preparing = TrailerPreviewState().reduce(focus("movie-1"))
        val again = preparing.reduce(focus("movie-1"))
        assertEquals(preparing, again)
    }

    @Test
    fun sameTitleOnAnotherShelfRestartsOnThatPoster() {
        var state = TrailerPreviewState().reduce(focus("movie-1", "my-list"))
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        assertTrue(state.appliesTo("my-list", "movie-1"))
        assertFalse(state.appliesTo("jeds-movies", "movie-1"))

        val moved = state.reduce(focus("movie-1", "jeds-movies"))
        assertEquals(TrailerPreviewPhase.Preparing, moved.phase)
        assertEquals("jeds-movies", moved.rowId)
        assertTrue(moved.appliesTo("jeds-movies", "movie-1"))
        assertFalse(moved.appliesTo("my-list", "movie-1"))
    }

    @Test
    fun graceTimeoutHidesWhenStillPreparing() {
        var state = TrailerPreviewState().reduce(focus("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        state = state.reduce(TrailerPreviewEvent.HoldExpiredUnready)
        assertEquals(TrailerPreviewPhase.Hidden, state.phase)
        assertTrue(state.failed)
    }

    @Test
    fun graceTimeoutDoesNotCancelOpenPreview() {
        var state = TrailerPreviewState().reduce(focus("movie-1"))
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        state = state.reduce(TrailerPreviewEvent.HoldExpiredUnready)
        assertEquals(TrailerPreviewPhase.Opening, state.phase)
    }

    @Test
    fun morphFinishedStartsPlaybackPhase() {
        var state = TrailerPreviewState().reduce(focus("movie-1"))
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        state = state.reduce(TrailerPreviewEvent.MorphFinished)
        assertEquals(TrailerPreviewPhase.Playing, state.phase)
    }

    @Test
    fun clipEndKeepsWideStillUntilFocusLeaves() {
        var state = TrailerPreviewState().reduce(focus("movie-1"))
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        state = state.reduce(TrailerPreviewEvent.PlayerReady("movie-1"))
        state = state.reduce(TrailerPreviewEvent.HoldElapsed)
        state = state.reduce(TrailerPreviewEvent.MorphFinished)
        state = state.reduce(TrailerPreviewEvent.ClipEnded)
        assertEquals(TrailerPreviewPhase.Ended, state.phase)
        assertEquals("movie-1", state.titleKey)
        assertTrue(state.visible)
        val stayed = state.reduce(focus("movie-1"))
        assertEquals(TrailerPreviewPhase.Ended, stayed.phase)
        val next = state.reduce(focus("movie-2"))
        assertEquals(TrailerPreviewPhase.Preparing, next.phase)
        assertEquals("movie-2", next.titleKey)
    }

    @Test
    fun playerSurfaceAttachesOnlyWhileOpeningOrPlaying() {
        assertFalse(TrailerPreviewPhase.Hidden.attachesPlayer)
        assertFalse(TrailerPreviewPhase.Preparing.attachesPlayer)
        assertTrue(TrailerPreviewPhase.Opening.attachesPlayer)
        assertTrue(TrailerPreviewPhase.Playing.attachesPlayer)
        assertFalse(TrailerPreviewPhase.Ended.attachesPlayer)
    }

    @Test
    fun prepareWaitsOneSecondThenHoldAtFive() {
        assertEquals(1_000L, TRAILER_PREVIEW_PREPARE_DEBOUNCE_MS)
        assertEquals(5_000L, TRAILER_PREVIEW_HOLD_MS)
        assertTrue(TRAILER_PREVIEW_PREPARE_DEBOUNCE_MS < TRAILER_PREVIEW_HOLD_MS)
    }

    @Test
    fun playerFailureHidesWithoutOpening() {
        var state = TrailerPreviewState().reduce(focus("movie-1"))
        state = state.reduce(TrailerPreviewEvent.ClipReady("movie-1", CLIP))
        state = state.reduce(TrailerPreviewEvent.PlayerFailed)
        assertEquals(TrailerPreviewPhase.Hidden, state.phase)
        assertTrue(state.failed)
    }

    private fun focus(titleKey: String, rowId: String = "shelf") =
        TrailerPreviewEvent.Focused(titleKey, rowId)

    private companion object {
        const val CLIP = "https://clips.example.com/dQw4w9WgXcQ.mp4"
    }
}
