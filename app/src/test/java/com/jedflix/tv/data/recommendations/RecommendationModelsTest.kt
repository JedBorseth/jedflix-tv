package com.jedflix.tv.data.recommendations

import com.jedflix.tv.data.library.RecommendationSignals
import com.jedflix.tv.data.library.RecommendationWatch
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RecommendationModelsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    @Test fun serverNumericYearAndAdditionalMetadataDecodeIntoCatalogTitle() {
        val payload = """{"model":"bge-small","modelRevision":"abc","shelves":[{"id":"for-you","title":"For You","items":[{"id":123,"mediaType":"movie","title":"Movie","posterUrl":"https://image.tmdb.org/t/p/w342/poster.jpg","year":2025,"releaseDate":"2025-02-01"}]}],"eligibleKeys":["movie-123"],"evaluatedKeys":["movie-123"],"completeEligibility":true,"refreshedAt":1790000000000}"""
        val response = json.decodeFromString(RecommendationResponse.serializer(), payload)
        val title = response.shelves.single().toRow()!!.items.single()
        assertEquals("2025", title.year)
        assertEquals("2025-02-01", title.releaseDate)
        assertEquals(1790000000000, response.refreshedAt)
    }

    @Test fun requestIsBoundedDeduplicatedAndDoesNotTransmitProfileIdentity() {
        val titles = (1..350).map { id ->
            MediaTitle(id, MediaType.MOVIE, "Movie $id", "", "poster", null, "2020", null, emptyList())
        }
        val request = RecommendationRequest.from(
            RecommendationSignals(987654321, emptyList(), emptyList(), emptyList()), titles + titles,
        )
        assertEquals(300, request.candidates.size)
        assertEquals(2020, request.candidates.first().year)
        val wire = json.encodeToString(RecommendationRequest.serializer(), request)
        assertFalse(wire.contains("profileId"))
        assertFalse(wire.contains("987654321"))
    }

    @Test fun remoteShelfCannotReplaceUtilityOrTrendingIds() {
        val movie = RecommendationCandidate(123, "movie", "Movie", posterUrl = "poster")
        assertNull(RecommendationShelf("trending-home", "Override", listOf(movie)).toRow())
        assertNull(RecommendationShelf("my-list", "Override", listOf(movie)).toRow())
        assertNotNull(RecommendationShelf("because-movie-123", "Because you watched Movie", listOf(movie)).toRow())
    }

    @Test fun acceptsAllTwentySevenThemeFamiliesAndRejectsUnknownRemoteShelves() {
        val movie = RecommendationCandidate(123, "movie", "Movie", posterUrl = "poster")
        val themes = listOf(
            "friday-movie-night", "saturday-double-feature", "sunday-comfort", "after-long-day",
            "done-before-bed", "late-night-thrillers", "weekend-binge", "halloween", "spooky-not-scary",
            "christmas", "cozy-winter", "new-year", "valentines", "summer-adventure", "thanksgiving",
            "start-new-series", "next-obsession", "one-season-done", "hidden-gems", "change-pace",
            "back-90s", "twists-turns", "worlds", "make-laugh", "true-story",
            "more-director-525", "starring-actor-287",
        )
        assertEquals(27, themes.size)
        themes.forEach { id ->
            assertNotNull(id, RecommendationShelf("dynamic-$id", "Theme", listOf(movie)).toRow())
        }
        listOf("dynamic-trending-home", "dynamic-unknown", "dynamic-more-director-0", "dynamic-starring-actor-name").forEach { id ->
            assertNull(id, RecommendationShelf(id, "Unknown", listOf(movie)).toRow())
        }
    }

    @Test fun latestWatchedEpisodeAndTimezoneReachTheServerWithoutProfileIdentity() {
        val signals = RecommendationSignals(42, listOf(
            RecommendationWatch(123, "tv", 5_000, 96_000, 100_000, 100, season = 2, episode = 8, latestWatchedMs = 3000),
        ), emptyList(), emptyList())
        val request = RecommendationRequest.from(signals, emptyList()).copy(timeZone = "America/Vancouver")
        val wire = json.encodeToString(RecommendationRequest.serializer(), request)
        assertTrue(wire.contains("\"timeZone\":\"America/Vancouver\""))
        assertTrue(wire.contains("\"season\":2"))
        assertTrue(wire.contains("\"episode\":8"))
        assertTrue(wire.contains("\"latestWatchedMs\":3000"))
        assertFalse(wire.contains("profileId"))
    }

    @Test fun dynamicShelvesDoNotDisplaceTasteShelvesAndDuplicatesDoNotConsumeTheBudget() {
        val movie = RecommendationCandidate(123, "movie", "Movie", posterUrl = "poster")
        val themes = listOf("halloween", "spooky-not-scary", "done-before-bed", "hidden-gems", "worlds", "true-story")
        val response = RecommendationResponse(shelves = listOf(
            RecommendationShelf("trending-home", "Cannot override", listOf(movie)),
            RecommendationShelf("dynamic-halloween", "Halloween", emptyList()),
        ) + themes.map { RecommendationShelf("dynamic-$it", it, listOf(movie)) } + listOf(
            RecommendationShelf("dynamic-halloween", "Duplicate", listOf(movie)),
            RecommendationShelf("for-you", "For You", listOf(movie)),
            RecommendationShelf("because-movie-42", "Because 42", listOf(movie)),
            RecommendationShelf("because-tv-123", "Because 123", listOf(movie)),
            RecommendationShelf("because-tv-456", "Extra", listOf(movie)),
        ))
        val rows = response.toRows()
        assertEquals(8, rows.size)
        assertEquals(5, rows.count { it.id.startsWith("dynamic-") })
        assertEquals(listOf("for-you", "because-movie-42", "because-tv-123"), rows.takeLast(3).map { it.id })
        assertEquals("halloween", rows.first().title)
    }
}
