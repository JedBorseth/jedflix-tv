package com.jedflix.tv.data.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowPlayTest {
    @Test
    fun playWithoutHistoryStartsSeason1Episode1() {
        assertEquals(1 to 1, ShowPlay.play(null, null))
        assertEquals(1 to 1, ShowPlay.play(2, null))
        assertEquals(1 to 1, ShowPlay.play(null, 4))
    }

    @Test
    fun playWithHistoryResumesThatEpisode() {
        assertEquals(2 to 5, ShowPlay.play(2, 5))
        assertEquals(1 to 3, ShowPlay.play(1, 3))
    }

    @Test
    fun browsePrefersSeason1OverSpecials() {
        assertEquals(1, ShowPlay.browseSeason(listOf(0, 1, 2)))
        assertEquals(1, ShowPlay.browseSeason(listOf(1)))
    }

    @Test
    fun browseFallsBackToFirstRegularSeason() {
        assertEquals(2, ShowPlay.browseSeason(listOf(0, 2, 3)))
        assertEquals(1, ShowPlay.browseSeason(emptyList()))
    }
}
