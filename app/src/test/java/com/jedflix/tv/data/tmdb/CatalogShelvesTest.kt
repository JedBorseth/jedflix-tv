package com.jedflix.tv.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogShelvesTest {

    @Test
    fun homePrependsEditorialAndProviderShelvesBeforeTrending() {
        val ids = CatalogShelves.forSection(CatalogSection.HOME).map { it.id }
        assertEquals(
            listOf(
                "jeds-movies",
                "jeds-shows",
                "crave-movies",
                "crave-shows",
                "apple-tv-movies",
                "apple-tv-shows",
                "paramount-movies",
                "paramount-shows",
                "disney-movies",
                "disney-shows",
                CatalogShelves.TRENDING_HOME,
            ),
            ids.take(11),
        )
        assertTrue(ids.contains("popular-movies"))
    }

    @Test
    fun moviesSectionGetsMovieShelvesOnly() {
        val titles = CatalogShelves.forSection(CatalogSection.MOVIES).map { it.title }
        assertEquals(
            listOf(
                "Jed's Movies",
                "Crave Movies",
                "Apple TV Movies",
                "Paramount+ Movies",
                "Disney+ Movies",
                "Trending Movies",
            ),
            titles.take(6),
        )
        assertTrue(titles.none { it.contains("Shows") })
    }

    @Test
    fun showsSectionGetsShowShelvesOnly() {
        val titles = CatalogShelves.forSection(CatalogSection.SHOWS).map { it.title }
        assertEquals(
            listOf(
                "Jed's Shows",
                "Crave Shows",
                "Apple TV Shows",
                "Paramount+ Shows",
                "Disney+ Shows",
                "Trending Shows",
            ),
            titles.take(6),
        )
        assertTrue(titles.none { it.contains("Movies") })
    }

    @Test
    fun appleTvUsesSubscriptionProviderNotStore() {
        assertEquals(TmdbWatchProviders.APPLE_TV, CatalogShelves.appleTvMovies.providerId)
        assertEquals(350, TmdbWatchProviders.APPLE_TV)
    }
}
