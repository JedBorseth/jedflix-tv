package com.jedflix.tv.data.recommendations

import com.jedflix.tv.data.library.LibraryRows
import com.jedflix.tv.data.tmdb.Catalog
import com.jedflix.tv.data.tmdb.CatalogRow
import com.jedflix.tv.data.tmdb.CatalogShelves
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DiscoveryPolicyTest {
    private val today = LocalDate.parse("2026-10-05")
    private fun movie(id: Int, date: String? = "2020-01-01", year: String? = "2020") =
        MediaTitle(id, MediaType.MOVIE, "Movie $id", "", "poster", null, year, null, emptyList(), date)

    @Test fun futurePrimaryReleaseNeedsVerifiedEarlierPublicAvailability() {
        val upcoming = movie(1, "2026-11-05")
        assertFalse(DiscoveryPolicy.eligible(upcoming, today))
        assertTrue(DiscoveryPolicy.eligible(upcoming, today, setOf(upcoming.key)))
    }

    @Test fun freshTheatricalReleaseNeedsEvidenceButThirtyDayOldMovieDoesNot() {
        val fresh = movie(1, "2026-10-01")
        assertFalse(DiscoveryPolicy.eligible(fresh, today))
        assertTrue(DiscoveryPolicy.eligible(fresh, today, setOf(fresh.key)))
        assertTrue(DiscoveryPolicy.eligible(movie(2, "2026-09-05"), today))
    }

    @Test fun unknownDatesDoNotAdmitCurrentYearUpcomingMovies() {
        assertFalse(DiscoveryPolicy.eligible(movie(1, null, "2026"), today))
        assertFalse(DiscoveryPolicy.eligible(movie(2, "bad-date", "2027"), today))
        assertTrue(DiscoveryPolicy.eligible(movie(3, null, "2020"), today))
    }

    @Test fun dedupPreservesUtilityOverlapAndCustomDiscoveryOrder() {
        val one = movie(1)
        val two = movie(2)
        val three = movie(3)
        val rows = listOf(
            CatalogRow(CatalogShelves.TRENDING_HOME, "Trending Now", listOf(one)),
            CatalogRow(LibraryRows.CONTINUE_WATCHING, "Continue Watching", listOf(one, two)),
            CatalogRow("for-you", "For You", listOf(one, two)),
            CatalogRow("custom-second", "Second", listOf(two, three)),
        )
        val result = DiscoveryPolicy.apply(Catalog(listOf(one), rows), today)
        assertEquals(rows.map { it.id }, result.rows.map { it.id })
        assertEquals(listOf(one, two), result.rows[1].items)
        assertEquals(listOf(two), result.rows[2].items)
        assertEquals(listOf(three), result.rows[3].items)
    }

    @Test fun filteredBillboardNeverFallsBackToProviderOrPersonalizedTitle() {
        val future = movie(1, "2026-11-05")
        val older = movie(2)
        val result = DiscoveryPolicy.apply(Catalog(listOf(future), listOf(
            CatalogRow(CatalogShelves.TRENDING_HOME, "Trending Now", listOf(future)),
            CatalogRow("for-you", "For You", listOf(older)),
        )), today)
        assertTrue(result.featured.isEmpty())
        assertEquals(listOf(older), result.rows.single().items)
    }

    @Test fun billboardRefillsFromEligibleTrendingWhenOriginalFeaturedTitlesAreRemoved() {
        val future = movie(1, "2026-11-05")
        val trending = movie(2)
        val provider = movie(3)
        val result = DiscoveryPolicy.apply(Catalog(listOf(future), listOf(
            CatalogRow(CatalogShelves.TRENDING_HOME, "Trending", listOf(future, trending)),
            CatalogRow("provider", "Provider", listOf(provider)),
        )), today)
        assertEquals(listOf(trending), result.featured)
    }

    @Test fun billboardUsesCurrentTrendingMetadataInsteadOfStaleFeaturedObject() {
        val older = movie(1)
        val updated = older.copy(title = "Updated title")
        val result = DiscoveryPolicy.apply(Catalog(listOf(older), listOf(
            CatalogRow(CatalogShelves.TRENDING_HOME, "Trending", listOf(updated)),
        )), today)
        assertEquals("Updated title", result.featured.single().title)
    }

    @Test fun dislikeDoesNotEraseHistoryOrMyList() {
        val title = movie(1)
        val result = DiscoveryPolicy.apply(Catalog(listOf(title), listOf(
            CatalogRow(CatalogShelves.TRENDING_HOME, "Trending", listOf(title)),
            CatalogRow(LibraryRows.MY_LIST, "My List", listOf(title)),
        )), today, dislikedKeys = setOf(title.key))
        assertEquals(LibraryRows.MY_LIST, result.rows.single().id)
        assertTrue(result.featured.isEmpty())
    }

    @Test fun movieAndShowIdsAreDifferentIdentities() {
        val movie = movie(1)
        val show = movie.copy(mediaType = MediaType.TV, releaseDate = null)
        val result = DiscoveryPolicy.apply(Catalog(listOf(movie), listOf(
            CatalogRow(CatalogShelves.TRENDING_HOME, "Trending", listOf(movie, show)),
        )), today)
        assertEquals(2, result.rows.single().items.size)
    }

    @Test fun backgroundRefillKeepsCurrentlyFocusedDuplicateInItsExistingShelf() {
        val title = movie(1)
        val result = DiscoveryPolicy.apply(Catalog(emptyList(), listOf(
            CatalogRow("earlier", "Earlier", listOf(title)),
            CatalogRow("focused", "Focused", listOf(title)),
        )), today, focusedRowId = "focused", focusedItemKey = title.key)
        assertEquals("focused", result.rows.single().id)
        assertEquals(listOf(title), result.rows.single().items)
    }
}
