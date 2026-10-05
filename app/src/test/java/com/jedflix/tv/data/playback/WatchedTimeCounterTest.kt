package com.jedflix.tv.data.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchedTimeCounterTest {
    @Test
    fun countsOnlyActiveIntervalsAndCheckpointsAreCumulative() {
        var time = 0L
        val counter = WatchedTimeCounter({ time })
        counter.setPlaying(true)
        time = 10_000
        assertEquals(10_000L, counter.snapshotMs())
        assertEquals(10_000L, counter.snapshotMs())
        counter.setPlaying(false)
        time = 80_000 // Paused/buffering time.
        assertEquals(10_000L, counter.snapshotMs())
        counter.setPlaying(true)
        time = 85_000
        counter.setPlaying(false)
        assertEquals(15_000L, counter.snapshotMs())
    }

    @Test
    fun duplicatePlayingEventsDoNotRestartTheInterval() {
        var time = 5_000L
        val counter = WatchedTimeCounter({ time })
        counter.setPlaying(true)
        time = 8_000
        counter.setPlaying(true)
        time = 10_000
        counter.setPlaying(false)
        counter.setPlaying(false)
        assertEquals(5_000L, counter.snapshotMs())
    }

    @Test
    fun seekingDoesNotCreditTheJumpAndNextEpisodeStartsFresh() {
        var time = 0L
        val counter = WatchedTimeCounter({ time })
        counter.setPlaying(true)
        time = 2_000
        counter.setPlaying(false) // A seek starts buffering.
        time = 2_500 // Player position may jump to 90 minutes: counter ignores it.
        counter.setPlaying(true)
        time = 3_500
        counter.setPlaying(false)
        assertEquals(3_000L, counter.snapshotMs())
        counter.reset(eligible = true)
        assertEquals(0L, counter.snapshotMs())
        counter.setPlaying(true)
        time = 4_000
        assertEquals(500L, counter.snapshotMs())
    }

    @Test
    fun liveAndPreviewSessionsCanBeExcludedWithoutAccumulatingTime() {
        var time = 0L
        val counter = WatchedTimeCounter({ time }, eligible = false)
        counter.setPlaying(true)
        time = 100_000
        counter.setPlaying(false)
        assertEquals(0L, counter.snapshotMs())
        counter.reset(eligible = true)
        counter.setPlaying(true)
        time = 101_000
        assertEquals(1_000L, counter.snapshotMs())
    }
}
