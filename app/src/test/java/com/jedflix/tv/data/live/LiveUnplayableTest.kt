package com.jedflix.tv.data.live

import com.jedflix.tv.data.tmdb.MediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiveUnplayableTest {
    private val border = program("border", 22 * 60_000L)
    private val livePd = program("livepd", 60 * 60_000L)
    private val hoarders = program("hoarders", 60 * 60_000L)
    private val channel = LiveChannel(
        id = "ae",
        name = "A&E",
        lineup = listOf(border, livePd, hoarders),
    )

    @Before
    fun reset() {
        LiveUnplayable.clear()
    }

    @After
    fun cleanup() {
        LiveUnplayable.clear()
    }

    @Test
    fun skippedNowProgramLeavesTheGuideAndTunePlaysNext() {
        val now = 5L * 60_000L
        val before = LiveSchedule.epgWindow(listOf(channel), now, windowMs = 4 * LiveSchedule.SLOT_MS)
        assertEquals(border.tmdbId, before.single().cells.first { it.contains(now) }.program.tmdbId)

        LiveUnplayable.mark(border)
        val playable = LiveUnplayable.filter(channel)!!
        val after = LiveSchedule.epgWindow(listOf(playable), now, windowMs = 4 * LiveSchedule.SLOT_MS)
        assertTrue(after.single().cells.none { it.program.sameAs(border) })
        assertEquals(livePd.tmdbId, LiveSchedule.tune(playable, now).program.tmdbId)
        assertEquals(livePd.tmdbId, after.single().cells.first { it.contains(now) }.program.tmdbId)
    }

    @Test
    fun skippingNowAndNextPlaysTheFollowingTitle() {
        val now = 5L * 60_000L
        LiveUnplayable.mark(border)
        LiveUnplayable.mark(livePd)
        val playable = LiveUnplayable.filter(channel)!!
        val window = LiveSchedule.epgWindow(listOf(playable), now, windowMs = 4 * LiveSchedule.SLOT_MS)
        assertTrue(window.single().cells.none { it.program.sameAs(border) || it.program.sameAs(livePd) })
        assertEquals(hoarders.tmdbId, LiveSchedule.tune(playable, now).program.tmdbId)
    }

    @Test
    fun emptyLineupDropsTheChannel() {
        channel.lineup.forEach(LiveUnplayable::mark)
        assertNull(LiveUnplayable.filter(channel))
        assertTrue(LiveUnplayable.filter(listOf(channel)).isEmpty())
    }

    private fun program(id: String, durationMs: Long): LiveProgram = LiveProgram(
        mediaType = MediaType.TV,
        tmdbId = id.hashCode(),
        displayTitle = id,
        durationMs = durationMs,
        season = 1,
        episode = 1,
    )
}
