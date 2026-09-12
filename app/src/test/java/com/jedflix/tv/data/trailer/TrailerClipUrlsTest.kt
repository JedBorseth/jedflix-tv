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
    fun rejectsBlankBaseOrInvalidKey() {
        assertNull(TrailerClipUrls.url("", "dQw4w9WgXcQ"))
        assertNull(TrailerClipUrls.url("https://clips.example.com", ""))
        assertNull(TrailerClipUrls.url("https://clips.example.com", "../secret"))
        assertNull(TrailerClipUrls.url("https://clips.example.com", "not a key"))
    }
}
