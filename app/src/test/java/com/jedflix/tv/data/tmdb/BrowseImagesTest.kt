package com.jedflix.tv.data.tmdb

import com.jedflix.tv.data.settings.QualityProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseImagesTest {

    @Test
    fun posterSizeFollowsQualityProfile() {
        assertEquals("w185", tmdbBrowsePosterSize(QualityProfile.Low))
        assertEquals("w342", tmdbBrowsePosterSize(QualityProfile.Medium))
        assertEquals("w500", tmdbBrowsePosterSize(QualityProfile.Max))
    }

    @Test
    fun backdropSizeFollowsQualityProfile() {
        assertEquals("w780", tmdbBrowseBackdropSize(QualityProfile.Low))
        assertEquals("w780", tmdbBrowseBackdropSize(QualityProfile.Medium))
        assertEquals("w1280", tmdbBrowseBackdropSize(QualityProfile.Max))
    }

    @Test
    fun rewritesTmdbSizeToken() {
        val stored = "https://image.tmdb.org/t/p/w500/poster.jpg"
        assertEquals(
            "https://image.tmdb.org/t/p/w185/poster.jpg",
            tmdbImageUrlAtSize(stored, "w185"),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w780/poster.jpg",
            tmdbImageUrlAtSize(stored, "w780"),
        )
    }

    @Test
    fun leavesNonTmdbUrlsAlone() {
        val other = "https://cdn.example.com/art.jpg"
        assertEquals(other, tmdbImageUrlAtSize(other, "w185"))
    }

    @Test
    fun blankUrlIsNull() {
        assertNull(tmdbImageUrlAtSize(null, "w185"))
        assertNull(tmdbImageUrlAtSize("  ", "w185"))
    }
}
