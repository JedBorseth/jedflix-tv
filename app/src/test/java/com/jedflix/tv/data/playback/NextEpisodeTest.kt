package com.jedflix.tv.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {
    @Test
    fun nextEpisodeInSameSeason() {
        assertEquals(3, NextEpisode.nextInSeason(listOf(1, 2, 3, 4), 2))
        assertNull(NextEpisode.nextInSeason(listOf(1, 2, 3), 3))
        assertEquals(5, NextEpisode.nextInSeason(listOf(1, 2, 5), 2))
    }

    @Test
    fun nextSeasonSkipsGaps() {
        assertEquals(3, NextEpisode.nextSeasonNumber(listOf(1, 3, 4), 1))
        assertNull(NextEpisode.nextSeasonNumber(listOf(1, 2), 2))
    }

    @Test
    fun firstEpisodeIgnoresZeros() {
        assertEquals(1, NextEpisode.firstEpisode(listOf(0, 1, 2)))
        assertNull(NextEpisode.firstEpisode(emptyList()))
    }

    @Test
    fun episodeSubtitleIncludesTitle() {
        assertEquals("S2 E4  •  The One", NextEpisode.episodeSubtitle(2, 4, "The One"))
        assertEquals("S1 E1", NextEpisode.episodeSubtitle(1, 1, null))
    }
}
