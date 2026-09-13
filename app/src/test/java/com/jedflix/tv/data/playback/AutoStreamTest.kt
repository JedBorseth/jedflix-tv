package com.jedflix.tv.data.playback

import com.jedflix.tv.data.comet.StreamOption
import com.jedflix.tv.data.settings.QualityProfile
import com.jedflix.tv.data.tmdb.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStreamTest {
    private val movieList = listOf(
        stream("4k-big", "4K", sizeGiB(50)),
        stream("1080-ok", "1080P", sizeGiB(18)),
        stream("4k-ok", "4K", sizeGiB(22)),
        stream("720-only", "720P", sizeGiB(4)),
        stream("1080-huge", "1080P", sizeGiB(30)),
    )

    private val showList = listOf(
        stream("4k-show", "4K", 900L * 1_048_576L),
        stream("1080-over", "1080P", sizeGiB(2)),
        stream("1080-ok", "1080P", 800L * 1_048_576L),
        stream("720-show", "720P", 400L * 1_048_576L),
    )

    @Test
    fun maxKeepsCometOrder() {
        val ranked = AutoStream.ranked(movieList, QualityProfile.Max, MediaType.MOVIE)
        assertEquals(movieList.map { it.id }, ranked.map { it.id })
        assertEquals("4k-big", AutoStream.pick(movieList, QualityProfile.Max, MediaType.MOVIE)?.id)
    }

    @Test
    fun mediumMovieKeepsFirstEligible1080pOrBetterUnder30Gb() {
        val ranked = AutoStream.ranked(movieList, QualityProfile.Medium, MediaType.MOVIE)
        assertEquals(listOf("1080-ok", "4k-ok"), ranked.map { it.id })
        assertEquals("1080-ok", AutoStream.pick(movieList, QualityProfile.Medium, MediaType.MOVIE)?.id)
    }

    @Test
    fun mediumShowRequires1080pOrBetterUnder1Gb() {
        val ranked = AutoStream.ranked(showList, QualityProfile.Medium, MediaType.TV)
        assertEquals(listOf("4k-show", "1080-ok"), ranked.map { it.id })
        assertEquals("4k-show", AutoStream.pick(showList, QualityProfile.Medium, MediaType.TV)?.id)
    }

    @Test
    fun lowKeepsOnly720pInCometOrder() {
        val ranked = AutoStream.ranked(movieList, QualityProfile.Low, MediaType.MOVIE)
        assertEquals(listOf("720-only"), ranked.map { it.id })
        val two720 = listOf(
            stream("1080", "1080P", sizeGiB(8)),
            stream("720-a", "720P", sizeGiB(3)),
            stream("720-b", "720p", sizeGiB(2)),
        )
        assertEquals(listOf("720-a", "720-b"), AutoStream.ranked(two720, QualityProfile.Low, MediaType.MOVIE).map { it.id })
    }

    @Test
    fun mediumDropsUnknownResolutionAndUnknownSize() {
        val options = listOf(
            stream("unknown", "Unknown", sizeGiB(10)),
            stream("nosize", "1080P", null),
            stream("ok", "1080P", sizeGiB(10)),
        )
        assertEquals(listOf("ok"), AutoStream.ranked(options, QualityProfile.Medium, MediaType.MOVIE).map { it.id })
    }

    @Test
    fun emptyProfileFilterFallsBackToCometOrder() {
        val onlyHuge = listOf(stream("remux", "4K", sizeGiB(50)))
        assertEquals(listOf("remux"), AutoStream.ranked(onlyHuge, QualityProfile.Medium, MediaType.MOVIE).map { it.id })
        val only1080 = listOf(stream("1080", "1080P", sizeGiB(8)))
        assertEquals(listOf("1080"), AutoStream.ranked(only1080, QualityProfile.Low, MediaType.MOVIE).map { it.id })
        assertTrue(AutoStream.ranked(emptyList(), QualityProfile.Max, MediaType.MOVIE).isEmpty())
    }

    @Test
    fun afterFailureWalksDownTheRankedList() {
        val ranked = AutoStream.ranked(movieList, QualityProfile.Medium, MediaType.MOVIE)
        assertEquals(listOf("4k-ok"), AutoStream.afterFailure(ranked, "1080-ok").map { it.id })
        assertEquals(emptyList<String>(), AutoStream.afterFailure(ranked, "4k-ok").map { it.id })
        assertEquals(ranked.map { it.id }, AutoStream.afterFailure(ranked, "missing").map { it.id })
    }

    private fun stream(id: String, resolution: String, sizeBytes: Long?) = StreamOption(
        id = id,
        resolution = resolution,
        filename = "$id.mkv",
        details = emptyList(),
        sizeBytes = sizeBytes,
        cached = true,
        playbackUrl = "https://comet.example/playback/$id",
    )

    private fun sizeGiB(gib: Int): Long = gib * 1_073_741_824L
}
