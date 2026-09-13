package com.jedflix.tv.data.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterboxdLogTest {
    @Test
    fun logUrlUsesTitleName() {
        assertEquals(
            "letterboxd://x-callback-url/log?name=Interstellar",
            LetterboxdLog.url("Interstellar"),
        )
    }

    @Test
    fun logUrlEncodesSpacesAndPunctuation() {
        assertEquals(
            "letterboxd://x-callback-url/log?name=Spider-Man%3A%20Across%20the%20Spider-Verse",
            LetterboxdLog.url("Spider-Man: Across the Spider-Verse"),
        )
    }
}
