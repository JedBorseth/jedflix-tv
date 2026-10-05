package com.jedflix.tv.data.playback

import com.jedflix.tv.data.comet.StreamOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoStreamTest {
    private val options = listOf(
        stream("4k-big", "4K", 50L * GIB),
        stream("1080-big", "1080P", 30L * GIB),
        stream("720", "720P", 4L * GIB),
        stream("unknown", "Unknown", null),
    )

    @Test
    fun keepsEveryResolutionAndSizeInCometOrder() {
        assertEquals(options, AutoStream.ranked(options))
        assertEquals(options.first(), AutoStream.pick(options))
    }

    @Test
    fun failureRetainsLargerLowerResolutionAndUnknownCandidates() {
        val ranked = AutoStream.ranked(options)
        assertEquals(options.drop(1), AutoStream.afterFailure(ranked, "4k-big"))
        assertEquals(options.drop(2), AutoStream.afterFailure(ranked, "1080-big"))
        assertEquals(options.drop(3), AutoStream.afterFailure(ranked, "720"))
        assertEquals(emptyList<StreamOption>(), AutoStream.afterFailure(ranked, "unknown"))
    }

    @Test
    fun unlimitedResultsStayAvailableForFallbacks() {
        val many = (1..40).map { stream("stream-$it", "1080P", 2L * GIB) }
        assertEquals(many, AutoStream.ranked(many))
        assertEquals(many.drop(5), AutoStream.afterFailure(many, "stream-5"))
    }

    @Test
    fun unknownFailedIdKeepsFullList() {
        assertEquals(options, AutoStream.afterFailure(options, "missing"))
    }

    @Test
    fun emptyListHasNoSelectionOrFallback() {
        assertNull(AutoStream.pick(emptyList()))
        assertEquals(emptyList<StreamOption>(), AutoStream.afterFailure(emptyList(), "missing"))
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

    private companion object {
        const val GIB = 1_073_741_824L
    }
}
