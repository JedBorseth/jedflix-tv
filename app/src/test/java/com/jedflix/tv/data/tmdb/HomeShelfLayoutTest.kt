package com.jedflix.tv.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeShelfLayoutTest {

    private val factory = listOf(
        spec("jeds-movies", "Jed's Movies"),
        spec("crave-movies", "Crave Movies"),
        spec(CatalogShelves.TRENDING_HOME, "Trending Now"),
        spec("horror", "Horror Movies"),
    )

    @Test
    fun defaultConfigUsesFactoryOrderWithoutTrending() {
        val prefs = HomeShelfLayout.resolve(HomeShelfConfig(), factory)
        assertEquals(listOf("jeds-movies", "crave-movies", "horror"), prefs.map { it.id })
        assertTrue(prefs.all { it.visible })
    }

    @Test
    fun savedOrderWinsAndStaleIdsAreIgnored() {
        val prefs = HomeShelfLayout.resolve(
            HomeShelfConfig(order = listOf("horror", "gone", "jeds-movies")),
            factory,
        )
        assertEquals(listOf("horror", "jeds-movies", "crave-movies"), prefs.map { it.id })
    }

    @Test
    fun newFactoryShelvesAppendVisibleAtEnd() {
        val prefs = HomeShelfLayout.resolve(
            HomeShelfConfig(order = listOf("jeds-movies", "crave-movies")),
            factory,
        )
        assertEquals(listOf("jeds-movies", "crave-movies", "horror"), prefs.map { it.id })
        assertTrue(prefs.all { it.visible })
    }

    @Test
    fun hiddenKeepsSlotAndUnhideRestoresPlace() {
        val hidden = HomeShelfLayout.resolve(
            HomeShelfConfig(
                order = listOf("jeds-movies", "crave-movies", CatalogShelves.TRENDING_HOME, "horror"),
                hidden = setOf("crave-movies"),
            ),
            factory,
        )
        assertEquals("crave-movies", hidden[1].id)
        assertFalse(hidden[1].visible)
        val arranged = HomeShelfLayout.arrangeRows(
            rows = hidden.map { CatalogRow(it.id, it.title, items = emptyList()) } +
                CatalogRow(CatalogShelves.TRENDING_HOME, "Trending Now", items = emptyList()),
            prefs = hidden,
        )
        assertEquals(
            listOf(CatalogShelves.TRENDING_HOME, "jeds-movies", "horror"),
            arranged.map { it.id },
        )
    }

    @Test
    fun fetchIdsAlwaysIncludeTrendingEvenWhenHiddenInSavedConfig() {
        val prefs = HomeShelfLayout.resolve(
            HomeShelfConfig(hidden = setOf(CatalogShelves.TRENDING_HOME, "horror")),
            factory,
        )
        val fetch = HomeShelfLayout.fetchIds(prefs)
        assertTrue(fetch.contains(CatalogShelves.TRENDING_HOME))
        assertFalse(fetch.contains("horror"))
        assertTrue(fetch.contains("jeds-movies"))
        assertTrue(prefs.none { it.id == CatalogShelves.TRENDING_HOME })
    }

    @Test
    fun moveDoesNothingAtEnds() {
        val prefs = HomeShelfLayout.resolve(HomeShelfConfig(), factory)
        val first = prefs.first().id
        val last = prefs.last().id
        assertEquals(prefs, HomeShelfLayout.move(prefs, first, -1))
        assertEquals(prefs, HomeShelfLayout.move(prefs, last, 1))
        val swapped = HomeShelfLayout.move(prefs, first, 1)
        assertEquals(listOf("crave-movies", "jeds-movies", "horror"), swapped.map { it.id })
    }

    @Test
    fun toggleAndMoveIgnoreTrending() {
        val prefs = HomeShelfLayout.resolve(HomeShelfConfig(), factory)
        assertEquals(prefs, HomeShelfLayout.toggleVisible(prefs, CatalogShelves.TRENDING_HOME))
        assertEquals(prefs, HomeShelfLayout.move(prefs, CatalogShelves.TRENDING_HOME, 1))
    }

    @Test
    fun toggleVisibleAndToConfigRoundTrip() {
        val prefs = HomeShelfLayout.toggleVisible(
            HomeShelfLayout.resolve(HomeShelfConfig(), factory),
            "horror",
        )
        val config = HomeShelfLayout.toConfig(prefs)
        assertEquals(setOf("horror"), config.hidden)
        assertEquals(listOf("jeds-movies", "crave-movies", "horror"), config.order)
        assertEquals(prefs, HomeShelfLayout.resolve(config, factory))
    }

    @Test
    fun factoryHomeOmitsPinnedTrending() {
        val ids = HomeShelfLayout.factorySpecs().map { it.id }
        assertEquals(18, ids.size)
        assertFalse(ids.contains(CatalogShelves.TRENDING_HOME))
    }

    @Test
    fun settingsPreviewNeverListsTrending() {
        val many = factory + spec("comedy", "Comedy") + spec("scifi", "Sci-Fi")
        val prefs = HomeShelfLayout.resolve(HomeShelfConfig(), many)
        assertEquals(
            listOf("jeds-movies", "crave-movies", "horror", "comedy"),
            HomeShelfLayout.settingsList(prefs, expanded = false).map { it.id },
        )
        assertEquals(prefs.map { it.id }, HomeShelfLayout.settingsList(prefs, expanded = true).map { it.id })
        assertTrue(prefs.none { it.id == CatalogShelves.TRENDING_HOME })
        assertTrue(HomeShelfLayout.settingsNeedsShowAll(prefs))
    }

    @Test
    fun settingsPreviewShowsAllWhenAtMostFour() {
        val shortFactory = factory.take(3)
        val prefs = HomeShelfLayout.resolve(HomeShelfConfig(), shortFactory)
        assertEquals(prefs, HomeShelfLayout.settingsList(prefs, expanded = false))
        assertFalse(HomeShelfLayout.settingsNeedsShowAll(prefs))
    }

    @Test
    fun pinTrendingMovesRowAbovePersonalShelves() {
        val rows = listOf(
            CatalogRow("continue-watching", "Continue Watching", items = emptyList()),
            CatalogRow("jeds-movies", "Jed's Movies", items = emptyList()),
            CatalogRow(CatalogShelves.TRENDING_HOME, "Trending Now", items = emptyList()),
        )
        assertEquals(
            listOf(CatalogShelves.TRENDING_HOME, "continue-watching", "jeds-movies"),
            HomeShelfLayout.pinTrending(rows).map { it.id },
        )
    }

    private fun spec(id: String, title: String) = ShelfSpec.MovieList(id, title, "popular")
}
