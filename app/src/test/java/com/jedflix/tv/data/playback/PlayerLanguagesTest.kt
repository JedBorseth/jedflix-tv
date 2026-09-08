package com.jedflix.tv.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLanguagesTest {
    @Test
    fun englishAliasesMatch() {
        assertTrue(PlayerLanguages.matches("eng", "en"))
        assertTrue(PlayerLanguages.matches("en-US", "eng"))
        assertTrue(PlayerLanguages.matches("en", "en"))
    }

    @Test
    fun unknownDoesNotMatchEnglish() {
        assertFalse(PlayerLanguages.matches("und", "en"))
        assertFalse(PlayerLanguages.matches(null, "en"))
        assertFalse(PlayerLanguages.matches("ja", "en"))
    }

    @Test
    fun normalizeMapsIso3() {
        assertEquals("en", PlayerLanguages.normalize("ENG"))
        assertEquals("ja", PlayerLanguages.normalize("jpn"))
        assertEquals("und", PlayerLanguages.normalize("und"))
        assertNull(PlayerLanguages.normalize("  "))
    }

    @Test
    fun displayNameUsesEnglishLocale() {
        assertEquals("English", PlayerLanguages.displayName("eng"))
        assertEquals("Japanese", PlayerLanguages.displayName("jpn"))
        assertEquals("Unknown", PlayerLanguages.displayName(null))
        assertEquals("Unknown", PlayerLanguages.displayName("und"))
    }
}

class AudioFormatLabelTest {
    @Test
    fun languagePlusCodecAndChannels() {
        assertEquals(
            "English  ·  DTS 5.1",
            AudioFormatLabel.format("eng", "dtsc", null, 6),
        )
        assertEquals(
            "Japanese  ·  AAC Stereo",
            AudioFormatLabel.format("jpn", "mp4a.40.2", "audio/mp4a-latm", 2),
        )
    }

    @Test
    fun atmosBeatsEac3() {
        assertEquals("Atmos", AudioFormatLabel.codecLabel("ec+3", null))
        assertEquals("DD+", AudioFormatLabel.codecLabel("eac3", null))
    }

    @Test
    fun unknownLanguageWithoutFormatStaysUnknown() {
        assertEquals("Unknown", AudioFormatLabel.format(null, null, null, FormatNoChannels))
    }

    private companion object {
        const val FormatNoChannels = -1
    }
}

class TextTrackLabelTest {
    @Test
    fun includesSdhHint() {
        assertEquals("English  ·  SDH", TextTrackLabel.format("en", "SDH"))
        assertEquals("English", TextTrackLabel.format("en", "English"))
    }
}

class PlaybackClockTest {
    @Test
    fun formatsHoursAndMinutes() {
        assertEquals("0:00", PlaybackClock.formatMs(0))
        assertEquals("1:05", PlaybackClock.formatMs(65_000))
        assertEquals("1:02:03", PlaybackClock.formatMs(3_723_000))
    }
}
