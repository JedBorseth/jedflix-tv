package com.jedflix.tv.data.live

import com.jedflix.tv.data.tmdb.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class LiveScheduleTest {
    private val shortShow = program("short", 11 * 60_000L)
    private val sitcom = program("sitcom", 22 * 60_000L)
    private val movie = program("movie", 126 * 60_000L)
    private val channel = LiveChannel(
        id = "test",
        name = "Test",
        lineup = listOf(shortShow, sitcom, movie),
    )

    @Test
    fun elevenMinutesSnapsToOneSlot() {
        assertEquals(LiveSchedule.SLOT_MS, LiveSchedule.snappedDurationMs(11 * 60_000L))
    }

    @Test
    fun twentyTwoMinutesSnapsToOneSlot() {
        assertEquals(LiveSchedule.SLOT_MS, LiveSchedule.snappedDurationMs(22 * 60_000L))
    }

    @Test
    fun movieSnapsUpToNextHalfHour() {
        assertEquals(5 * LiveSchedule.SLOT_MS, LiveSchedule.snappedDurationMs(126 * 60_000L))
    }

    @Test
    fun tuneAtStartOfLoopIsFirstProgramFromZero() {
        val tune = LiveSchedule.tune(channel, 0L)
        assertEquals(shortShow.tmdbId, tune.program.tmdbId)
        assertEquals(0, tune.index)
        assertEquals(0L, tune.offsetMs)
        assertEquals(sitcom.tmdbId, tune.next.tmdbId)
    }

    @Test
    fun tuneLateInShortCellClampsToRealDuration() {
        val twentyMinutes = 20L * 60_000L
        val tune = LiveSchedule.tune(channel, twentyMinutes)
        assertEquals(shortShow.tmdbId, tune.program.tmdbId)
        assertEquals(11 * 60_000L - LiveSchedule.SEEK_CLAMP_MS, tune.offsetMs)
    }

    @Test
    fun tuneAtSecondSlotIsNextProgram() {
        val tune = LiveSchedule.tune(channel, LiveSchedule.SLOT_MS)
        assertEquals(sitcom.tmdbId, tune.program.tmdbId)
        assertEquals(0L, tune.offsetMs)
    }

    @Test
    fun wrapAroundReturnsFirstProgram() {
        val loop = LiveSchedule.loopMs(channel)
        val tune = LiveSchedule.tune(channel, loop)
        assertEquals(shortShow.tmdbId, tune.program.tmdbId)
        assertEquals(0L, tune.offsetMs)
    }

    @Test
    fun nextAfterLastWrapsToFirst() {
        assertEquals(shortShow.tmdbId, LiveSchedule.nextProgram(channel, movie).tmdbId)
        assertEquals(sitcom.tmdbId, LiveSchedule.nextProgram(channel, shortShow).tmdbId)
    }

    @Test
    fun epgWindowUsesThirtyMinuteColumns() {
        val window = LiveSchedule.epgWindow(listOf(channel), nowMs = 1L, windowMs = 2 * LiveSchedule.SLOT_MS)
        val cells = window.single().cells
        assertEquals(2, cells.size)
        assertEquals(LiveSchedule.SLOT_MS, cells[0].snappedDurationMs)
        assertEquals(LiveSchedule.SLOT_MS, cells[1].snappedDurationMs)
        assertTrue(cells[0].contains(0L))
        assertTrue(cells[1].contains(LiveSchedule.SLOT_MS))
    }

    @Test
    fun timeslotLabelRoundsToHalfHourClock() {
        val zone = ZoneOffset.UTC
        val start = 8L * 60L * 60_000L + 30L * 60_000L
        val label = LiveSchedule.formatTimeslot(start, start + LiveSchedule.SLOT_MS, zone)
        assertEquals("8:30 – 9:00", label)
    }

    @Test
    fun programsFromRotatesLineup() {
        val rotated = LiveSchedule.programsFrom(channel, sitcom)
        assertEquals(listOf(sitcom.tmdbId, movie.tmdbId, shortShow.tmdbId), rotated.map { it.tmdbId })
    }

    @Test
    fun catalogIncludesRequestedChannels() {
        assertEquals(
            setOf(
                "marvel",
                "90s-cartoons",
                "comedy-central",
                "sitcom",
                "harry-potter",
                "ae",
                "sex-and-the-city",
                "impractical-jokers",
                "discovery",
                "hgtv",
                "food-network",
                "star-wars",
                "seinfeld",
                "disney-xd",
                "brooklyn-nine-nine",
                "office-247",
                "simpsons",
                "cartoon-network",
            ),
            LiveChannels.all.map { it.id }.toSet(),
        )
        assertEquals(18, LiveChannels.all.size)
        assertEquals(LiveChannels.all.size, LiveChannels.all.map { it.id }.distinct().size)
    }

    @Test
    fun joinOffsetClampsToTheRealFileInsideASnappedCell() {
        val start = 10L * LiveSchedule.SLOT_MS
        assertEquals(5L * 60_000L, LiveSchedule.joinOffsetMs(sitcom, start, start + 5L * 60_000L))
        assertEquals(
            22L * 60_000L - LiveSchedule.SEEK_CLAMP_MS,
            LiveSchedule.joinOffsetMs(sitcom, start, start + 28L * 60_000L),
        )
    }

    @Test
    fun aeNowAfterHoardersHourIsStorageWarsNotHoarders() {
        val ae = LiveChannels.require(LiveChannels.AE)
        val now = (4L * 60L + 5L) * 60_000L
        val tune = LiveSchedule.tune(ae, now)
        assertEquals(34971, tune.program.tmdbId)
        assertEquals(2, tune.program.episode)
        val window = LiveSchedule.epgWindow(listOf(ae), now, windowMs = LiveSchedule.WINDOW_MS).single()
        val nowCell = window.cells.first { it.contains(now) }
        assertEquals(tune.program.identity, nowCell.program.identity)
        assertTrue(
            window.cells.none { it.program.tmdbId == 30946 && it.program.episode == 1 },
        )
        assertEquals(
            LiveSchedule.joinOffsetMs(nowCell.program, nowCell.startEpochMs, now),
            tune.offsetMs,
        )
    }

    private fun program(id: String, durationMs: Long): LiveProgram = LiveProgram(
        mediaType = MediaType.MOVIE,
        tmdbId = id.hashCode(),
        displayTitle = id,
        durationMs = durationMs,
    )
}
