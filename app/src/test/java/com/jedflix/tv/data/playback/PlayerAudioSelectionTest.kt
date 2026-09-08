package com.jedflix.tv.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerAudioSelectionTest {
    @Test
    fun prefersSupportedEnglishOverUntagged() {
        val picked = PlayerAudioSelection.pickPlayable(
            listOf(
                option(group = 0, language = "und", supported = true, channels = 6),
                option(group = 1, language = "eng", supported = true, channels = 2),
            ),
            preferredLanguage = "en",
        )
        assertEquals(1, picked?.groupIndex)
    }

    @Test
    fun fallsBackToUntaggedWhenPreferredCodecIsUnsupported() {
        val picked = PlayerAudioSelection.pickPlayable(
            listOf(
                option(group = 0, language = "eng", supported = false, selected = true, channels = 8),
                option(group = 1, language = "und", supported = true, channels = 6),
                option(group = 2, language = "jpn", supported = true, channels = 2),
            ),
            preferredLanguage = "en",
        )
        assertEquals(1, picked?.groupIndex)
    }

    @Test
    fun selectsUntaggedMainMixWhenNothingMatchesPreferredLanguage() {
        val picked = PlayerAudioSelection.pickPlayable(
            listOf(
                option(group = 0, language = null, supported = true, isDefault = true, channels = 6),
                option(group = 1, language = "jpn", supported = true, channels = 2),
            ),
            preferredLanguage = "en",
        )
        assertEquals(0, picked?.groupIndex)
    }

    @Test
    fun keepsAlreadySelectedTrackOfEqualLanguageRank() {
        val picked = PlayerAudioSelection.pickPlayable(
            listOf(
                option(group = 0, language = "en", supported = true, selected = true, channels = 2),
                option(group = 1, language = "eng", supported = true, channels = 6),
            ),
            preferredLanguage = "en",
        )
        assertEquals(0, picked?.groupIndex)
    }

    @Test
    fun fallsBackToOtherLanguageWhenOnlyThatIsPlayable() {
        val picked = PlayerAudioSelection.pickPlayable(
            listOf(option(group = 0, language = "jpn", supported = true, channels = 2)),
            preferredLanguage = "en",
        )
        assertEquals(0, picked?.groupIndex)
    }

    @Test
    fun returnsNullWhenNothingIsPlayable() {
        val picked = PlayerAudioSelection.pickPlayable(
            listOf(option(group = 0, language = "eng", supported = false, selected = true)),
            preferredLanguage = "en",
        )
        assertNull(picked)
    }

    @Test
    fun languageRankTreatsBlankAsUndetermined() {
        assertEquals(2, PlayerAudioSelection.languageRank(null, "en"))
        assertEquals(2, PlayerAudioSelection.languageRank("und", "en"))
        assertEquals(3, PlayerAudioSelection.languageRank("eng", "en"))
        assertEquals(1, PlayerAudioSelection.languageRank("jpn", "en"))
    }

    private fun option(
        group: Int,
        language: String?,
        supported: Boolean,
        selected: Boolean = false,
        channels: Int = 2,
        isDefault: Boolean = false,
    ) = AudioTrackOption(
        groupIndex = group,
        trackIndex = 0,
        language = language,
        supported = supported,
        selected = selected,
        channelCount = channels,
        isDefault = isDefault,
    )
}
