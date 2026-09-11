package com.jedflix.tv.data.comet

import com.jedflix.tv.data.playback.EpisodeRef
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneEpisodeTest {

    @Test
    fun officeDinnerPartyUsesSceneEpisodeThirteen() {
        val search = SceneEpisode.searchRef(
            tmdbSeason = 4,
            tmdbEpisode = 9,
            tmdbTitle = "Dinner Party",
            videos = officeSeason4,
        )
        assertEquals(EpisodeRef(4, 13), search)
    }

    @Test
    fun officeLocalAdUsesSceneEpisodeNine() {
        val search = SceneEpisode.searchRef(
            tmdbSeason = 4,
            tmdbEpisode = 5,
            tmdbTitle = "Local Ad",
            videos = officeSeason4,
        )
        assertEquals(EpisodeRef(4, 9), search)
    }

    @Test
    fun combinedTwoParterUsesFirstScenePart() {
        val search = SceneEpisode.searchRef(
            tmdbSeason = 4,
            tmdbEpisode = 1,
            tmdbTitle = "Fun Run",
            videos = officeSeason4,
        )
        assertEquals(EpisodeRef(4, 1), search)
    }

    @Test
    fun optionalThePrefixStillMatches() {
        val search = SceneEpisode.searchRef(
            tmdbSeason = 4,
            tmdbEpisode = 10,
            tmdbTitle = "Chair Model",
            videos = officeSeason4,
        )
        assertEquals(EpisodeRef(4, 14), search)
    }

    @Test
    fun laterSeasonShiftMapsMafiaPastSplitNiagara() {
        val search = SceneEpisode.searchRef(
            tmdbSeason = 6,
            tmdbEpisode = 5,
            tmdbTitle = "Mafia",
            videos = officeSeason6,
        )
        assertEquals(EpisodeRef(6, 6), search)
    }

    @Test
    fun unmatchedTitleKeepsTmdbNumbers() {
        val search = SceneEpisode.searchRef(
            tmdbSeason = 1,
            tmdbEpisode = 3,
            tmdbTitle = "Pilot",
            videos = officeSeason4,
        )
        assertEquals(EpisodeRef(1, 3), search)
    }

    @Test
    fun dropsWrongEpisodeFileWhenSearchingDinnerParty() {
        val options = listOf(
            stream("The.Office.US.S04E09.Local.Ad.1080p.mkv"),
            stream("The.Office.US.S04E13.Dinner.Party.1080p.mkv"),
            stream("The.Office.US.S04.Complete.1080p.mkv"),
        )
        val kept = SceneEpisode.filterStreams(options, season = 4, episode = 13)
            .map { it.filename }
        assertEquals(
            listOf(
                "The.Office.US.S04E13.Dinner.Party.1080p.mkv",
                "The.Office.US.S04.Complete.1080p.mkv",
            ),
            kept,
        )
    }

    @Test
    fun keepsMultiEpisodeFileThatContainsTarget() {
        val options = listOf(stream("The.Office.US.S04E13E14.1080p.mkv"))
        val kept = SceneEpisode.filterStreams(options, season = 4, episode = 13)
        assertEquals(1, kept.size)
    }

    @Test
    fun unlabeledFileIsKeptAsSeasonPack() {
        val options = listOf(stream("The.Office.US.S04.Complete.1080p.mkv"))
        assertEquals(1, SceneEpisode.filterStreams(options, season = 4, episode = 13).size)
    }

    @Test
    fun filterFallsBackWhenEveryFileWouldBeDropped() {
        val options = listOf(stream("The.Office.US.S04E09.Local.Ad.1080p.mkv"))
        val kept = SceneEpisode.filterStreams(options, season = 4, episode = 13)
        assertEquals(options, kept)
    }

    private fun stream(filename: String) = StreamOption(
        id = filename,
        resolution = "1080P",
        filename = filename,
        details = emptyList(),
        sizeBytes = 1L,
        cached = true,
        playbackUrl = "https://comet.example/playback/$filename",
    )

    companion object {
        private val officeSeason4 = listOf(
            CinemetaEpisode(4, 1, "Fun Run (1)"),
            CinemetaEpisode(4, 2, "Fun Run (2)"),
            CinemetaEpisode(4, 3, "Dunder Mifflin Infinity (1)"),
            CinemetaEpisode(4, 4, "Dunder Mifflin Infinity (2)"),
            CinemetaEpisode(4, 5, "Launch Party (1)"),
            CinemetaEpisode(4, 6, "Launch Party (2)"),
            CinemetaEpisode(4, 7, "Money (1)"),
            CinemetaEpisode(4, 8, "Money (2)"),
            CinemetaEpisode(4, 9, "Local Ad"),
            CinemetaEpisode(4, 10, "Branch Wars"),
            CinemetaEpisode(4, 11, "Survivor Man"),
            CinemetaEpisode(4, 12, "The Deposition"),
            CinemetaEpisode(4, 13, "Dinner Party"),
            CinemetaEpisode(4, 14, "The Chair Model"),
            CinemetaEpisode(4, 15, "Night Out"),
            CinemetaEpisode(4, 16, "Did I Stutter?"),
            CinemetaEpisode(4, 17, "Job Fair"),
            CinemetaEpisode(4, 18, "Goodbye, Toby (1)"),
            CinemetaEpisode(4, 19, "Goodbye, Toby (2)"),
        )

        private val officeSeason6 = listOf(
            CinemetaEpisode(6, 4, "Niagara (1)"),
            CinemetaEpisode(6, 5, "Niagara (2)"),
            CinemetaEpisode(6, 6, "Mafia"),
        )
    }
}
