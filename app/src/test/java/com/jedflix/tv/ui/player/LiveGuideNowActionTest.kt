package com.jedflix.tv.ui.player

import com.jedflix.tv.data.live.LiveEpgCell
import com.jedflix.tv.data.live.LiveProgram
import com.jedflix.tv.data.live.LiveSchedule
import com.jedflix.tv.data.playback.PlaybackItem
import com.jedflix.tv.data.tmdb.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveGuideNowActionTest {
    private val storageWars = LiveProgram(
        mediaType = MediaType.TV,
        tmdbId = 34971,
        displayTitle = "Storage Wars  •  Melee in the Maze",
        durationMs = 22L * 60_000L,
        season = 1,
        episode = 3,
    )
    private val hoarders = LiveProgram(
        mediaType = MediaType.TV,
        tmdbId = 30946,
        displayTitle = "Hoarders  •  Jennifer & Ron/Jill",
        durationMs = 60L * 60_000L,
        season = 1,
        episode = 1,
    )
    private val now = LiveSchedule.SLOT_MS + 5L * 60_000L
    private val storageNow = LiveEpgCell(
        channelId = "ae",
        program = storageWars,
        startEpochMs = LiveSchedule.SLOT_MS,
        endEpochMs = 2L * LiveSchedule.SLOT_MS,
    )

    @Test
    fun nowOnADifferentProgramRetunesEvenOnTheSameChannel() {
        val action = liveGuideNowAction(
            cell = storageNow,
            playing = playing(hoarders, channelId = "ae"),
            nowMs = now,
        )
        assertEquals(LiveGuideNowAction.Retune, action)
    }

    @Test
    fun nowOnThePlayingProgramClosesTheGuide() {
        val action = liveGuideNowAction(
            cell = storageNow,
            playing = playing(storageWars, channelId = "ae"),
            nowMs = now,
        )
        assertEquals(LiveGuideNowAction.Close, action)
    }

    @Test
    fun futureCellsDoNothing() {
        val action = liveGuideNowAction(
            cell = storageNow,
            playing = playing(hoarders, channelId = "cartoon-network"),
            nowMs = 0L,
        )
        assertEquals(LiveGuideNowAction.Ignore, action)
    }

    private fun playing(program: LiveProgram, channelId: String) = PlaybackItem(
        streamUrl = "https://example.invalid/stream",
        title = program.displayTitle,
        subtitle = null,
        mediaType = program.mediaType,
        tmdbId = program.tmdbId,
        season = program.season,
        episode = program.episode,
        liveChannelId = channelId,
    )
}
