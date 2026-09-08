package com.jedflix.tv.data.playback

import com.jedflix.tv.data.comet.StreamOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextStreamTest {
    @Test
    fun prefersFirstCachedAtSameResolution() {
        val options = listOf(
            stream("1080P", cached = false),
            stream("4K", cached = true),
            stream("1080P", cached = true, id = "match-a"),
            stream("1080P", cached = true, id = "match-b"),
        )
        assertEquals("match-a", NextStream.pickCachedAtResolution(options, "1080P")?.id)
    }

    @Test
    fun resolutionMatchIsCaseInsensitive() {
        val options = listOf(stream("1080p", cached = true, id = "lower"))
        assertEquals("lower", NextStream.pickCachedAtResolution(options, "1080P")?.id)
    }

    @Test
    fun uncachedOrWrongResolutionIsNotPicked() {
        val options = listOf(
            stream("1080P", cached = false),
            stream("4K", cached = true),
        )
        assertNull(NextStream.pickCachedAtResolution(options, "1080P"))
        assertNull(NextStream.pickCachedAtResolution(options, ""))
    }

    private fun stream(resolution: String, cached: Boolean, id: String = "$resolution-$cached") = StreamOption(
        id = id,
        resolution = resolution,
        filename = "file.mkv",
        details = emptyList(),
        sizeBytes = 1L,
        cached = cached,
        playbackUrl = "https://comet.example/playback/$id",
    )
}
