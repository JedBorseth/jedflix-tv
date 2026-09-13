package com.jedflix.tv.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerClipUrlsTest {

    @Test
    fun joinsBaseAndYoutubeKey() {
        assertEquals(
            "https://clips.example.com/dQw4w9WgXcQ.mp4",
            TrailerClipUrls.url("https://clips.example.com/", "dQw4w9WgXcQ"),
        )
    }

    @Test
    fun blankBaseUsesStandInMp4() {
        assertEquals(TrailerClipUrls.STAND_IN_MP4, TrailerClipUrls.url("", "dQw4w9WgXcQ"))
        assertEquals(true, TrailerClipUrls.usesStandIn(""))
        assertEquals(false, TrailerClipUrls.usesStandIn("https://clips.example.com"))
    }

    @Test
    fun fullMp4UrlIsUsedAsIs() {
        assertEquals(
            TrailerClipUrls.STAND_IN_MP4,
            TrailerClipUrls.url(TrailerClipUrls.STAND_IN_MP4, "dQw4w9WgXcQ"),
        )
        assertEquals(true, TrailerClipUrls.usesStandIn(TrailerClipUrls.STAND_IN_MP4))
    }

    @Test
    fun rejectsInvalidKeyWhenHosted() {
        assertNull(TrailerClipUrls.url("https://clips.example.com", ""))
        assertNull(TrailerClipUrls.url("https://clips.example.com", "../secret"))
        assertNull(TrailerClipUrls.url("https://clips.example.com", "not a key"))
    }
}
