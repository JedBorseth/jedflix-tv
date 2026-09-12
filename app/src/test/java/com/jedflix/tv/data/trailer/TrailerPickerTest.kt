package com.jedflix.tv.data.trailer

import com.jedflix.tv.data.tmdb.TmdbVideoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerPickerTest {

    @Test
    fun prefersOfficialEnglishYoutubeTrailer() {
        val videos = listOf(
            video(key = "teaser", type = "Teaser", official = true, lang = "en"),
            video(key = "french", type = "Trailer", official = true, lang = "fr"),
            video(key = "official", type = "Trailer", official = true, lang = "en"),
            video(key = "other", type = "Trailer", official = false, lang = "en"),
        )
        assertEquals("official", TrailerPicker.youtubeKey(videos))
    }

    @Test
    fun fallsBackToUnofficialEnglishTrailerThenAnyTrailer() {
        assertEquals(
            "en-unofficial",
            TrailerPicker.youtubeKey(
                listOf(
                    video(key = "fr", type = "Trailer", official = true, lang = "fr"),
                    video(key = "en-unofficial", type = "Trailer", official = false, lang = "en"),
                ),
            ),
        )
        assertEquals(
            "any",
            TrailerPicker.youtubeKey(
                listOf(video(key = "any", type = "Trailer", official = false, lang = "ja")),
            ),
        )
    }

    @Test
    fun teasersOnlyWhenNoTrailer() {
        assertEquals(
            "teaser",
            TrailerPicker.youtubeKey(
                listOf(
                    video(key = "clip", type = "Clip", official = true, lang = "en"),
                    video(key = "teaser", type = "Teaser", official = true, lang = "en"),
                ),
            ),
        )
    }

    @Test
    fun ignoresVimeoAndBlankKeys() {
        assertNull(
            TrailerPicker.youtubeKey(
                listOf(
                    TmdbVideoDto(key = "vimeo1", site = "Vimeo", type = "Trailer", official = true, iso6391 = "en"),
                    video(key = "", type = "Trailer", official = true, lang = "en"),
                ),
            ),
        )
    }

    private fun video(
        key: String,
        type: String,
        official: Boolean,
        lang: String,
    ) = TmdbVideoDto(
        name = key,
        key = key,
        site = "YouTube",
        type = type,
        official = official,
        iso6391 = lang,
    )
}
