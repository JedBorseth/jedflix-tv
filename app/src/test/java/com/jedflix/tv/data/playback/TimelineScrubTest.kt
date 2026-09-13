package com.jedflix.tv.data.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineScrubTest {
    @Test
    fun stepsForwardAndBackByTenSeconds() {
        assertEquals(40_000L, TimelineScrub.step(30_000, 120_000, TimelineScrub.STEP_MS))
        assertEquals(20_000L, TimelineScrub.step(30_000, 120_000, -TimelineScrub.STEP_MS))
    }

    @Test
    fun clampsToStartAndEnd() {
        assertEquals(0L, TimelineScrub.step(3_000, 120_000, -TimelineScrub.STEP_MS))
        assertEquals(120_000L, TimelineScrub.step(115_000, 120_000, TimelineScrub.STEP_MS))
    }

    @Test
    fun unknownDurationStaysAtZero() {
        assertEquals(0L, TimelineScrub.step(8_000, 0, TimelineScrub.STEP_MS))
        assertEquals(0L, TimelineScrub.step(8_000, -1, TimelineScrub.STEP_MS))
    }
}
