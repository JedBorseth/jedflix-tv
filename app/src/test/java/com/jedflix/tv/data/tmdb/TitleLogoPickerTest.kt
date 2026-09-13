package com.jedflix.tv.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleLogoPickerTest {

    @Test
    fun prefersEnglishPngOverOtherLanguages() {
        assertEquals(
            "/en.png",
            TitleLogoPicker.path(
                listOf(
                    logo("/fr.png", "fr", 9.0),
                    logo("/en.png", "en", 1.0),
                    logo("/en.svg", "en", 10.0),
                ),
            ),
        )
    }

    @Test
    fun skipsSvgAndJpeg() {
        assertEquals(
            "/mark.webp",
            TitleLogoPicker.path(
                listOf(
                    logo("/mark.svg", "en", 9.0),
                    logo("/mark.jpg", "en", 8.0),
                    logo("/mark.webp", "en", 1.0),
                ),
            ),
        )
    }

    @Test
    fun fallsBackToTextlessThenAnyRaster() {
        assertEquals(
            "/plain.png",
            TitleLogoPicker.path(
                listOf(
                    logo("/ja.png", "ja", 2.0),
                    logo("/plain.png", null, 1.0),
                ),
            ),
        )
        assertEquals(
            "/ja.png",
            TitleLogoPicker.path(listOf(logo("/ja.png", "ja", 2.0))),
        )
    }

    @Test
    fun emptyWhenNoRasterLogo() {
        assertNull(TitleLogoPicker.path(emptyList()))
        assertNull(TitleLogoPicker.path(listOf(logo("/x.svg", "en", 9.0))))
    }

    private fun logo(path: String, lang: String?, vote: Double) = TmdbImageDto(
        filePath = path,
        iso6391 = lang,
        voteAverage = vote,
    )
}
