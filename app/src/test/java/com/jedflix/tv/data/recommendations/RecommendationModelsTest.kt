package com.jedflix.tv.data.recommendations

import com.jedflix.tv.data.library.RecommendationSignals
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RecommendationModelsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

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
}
