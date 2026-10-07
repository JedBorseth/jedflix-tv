package com.jedflix.tv.data.recommendations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class RecommendationRepositoryTest {
    @get:Rule val directory = TemporaryFolder()
    private fun request(watchedMs: Long = 1000) = RecommendationRequest(
        listOf(WatchSignal(123, "movie", watchedMs, 1000, 10000, 100)), emptyList(), emptyList(), emptyList(),
    )
    private fun response(name: String) = RecommendationResponse(model = name, eligibleKeys = listOf("movie-123"))

    @Test fun cacheSurvivesRestartAndIsScopedToProfile() = runTest {
        val first = RecommendationRepository(directory.root, { response("first") }, { 1000 })
        first.refresh(1, request())
        first.refresh(2, request(2000))
        val restarted = RecommendationRepository(directory.root, { error("offline") }, { 2000 })
        assertEquals(1L, restarted.cached(1)?.profileId)
        assertEquals(2L, restarted.cached(2)?.profileId)
        assertNull(restarted.cached(3))
        assertEquals("first", restarted.refresh(1, request(5000))?.response?.model)
    }

    @Test fun unchangedSignalsReuseCacheAndChangedViewingRefreshes() = runTest {
        var requests = 0
        val repo = RecommendationRepository(directory.root, { response("request-${++requests}") }, { 1000 })
        repo.refresh(1, request())
        repo.refresh(1, request())
        assertEquals(1, requests)
        repo.refresh(1, request(5000))
        assertEquals(2, requests)
    }

    @Test fun expiredCacheRefreshesButOfflineFailureRetainsPreviousShelves() = runTest {
        var clock = 1000L
        var online = true
        val repo = RecommendationRepository(directory.root, {
            if (!online) error("offline")
            response("cached")
        }, { clock })
        repo.refresh(1, request())
        clock += 7 * 60 * 60 * 1000
        online = false
        assertEquals("cached", repo.refresh(1, request())?.response?.model)
        assertNotNull(repo.cached(1))
    }

    @Test fun oldOrFutureDatedEvidenceCannotBypassLocalReleaseHoldback() {
        val cached = CachedRecommendations(1, 1000, 0, response("cached"))
        assertEquals(setOf("movie-123"), cached.verifiedKeys(1001))
        assertTrue(cached.verifiedKeys(1000 + CachedRecommendations.EVIDENCE_TTL_MS + 1).isEmpty())
        assertTrue(cached.verifiedKeys(999).isEmpty())
    }

    @Test fun corruptedOrWrongProfileCacheIsIgnored() = runTest {
        File(directory.root, "profile-1.json").writeText("broken")
        val repo = RecommendationRepository(directory.root, { response("live") }, { 1000 })
        assertNull(repo.cached(1))
        repo.refresh(2, request())
        File(directory.root, "profile-2.json").copyTo(File(directory.root, "profile-1.json"), overwrite = true)
        assertNull(repo.cached(1))
    }

    @Test fun cancellationIsNotSwallowedOrSaved() = runTest {
        val repo = RecommendationRepository(directory.root, { throw CancellationException("leave Home") })
        try {
            repo.refresh(1, request())
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertNull(repo.cached(1))
    }

    @Test fun deletedProfilesLoseTheirCacheAndCannotRecreateItFromAnOldRefresh() = runTest {
        val repo = RecommendationRepository(directory.root, { response("cached") }, { 1000 })
        repo.refresh(1, request())
        repo.refresh(2, request())
        repo.retainProfiles(setOf(2))
        assertNull(repo.cached(1))
        assertNotNull(repo.cached(2))
        assertNull(repo.refresh(1, request(5000)))
        assertNull(repo.cached(1))
    }

    @Test fun halloweenCacheCannotReturnInNovemberEvenWhenOffline() = runTest {
        var clock = Instant.parse("2026-11-01T06:59:00Z").toEpochMilli() // Oct31 23:59 Vancouver.
        val midnight = Instant.parse("2026-11-01T07:00:00Z").toEpochMilli()
        var online = true
        val movie = RecommendationCandidate(123, "movie", "Movie", posterUrl = "poster")
        val repo = RecommendationRepository(directory.root, {
            if (!online) error("offline")
            RecommendationResponse(shelves = listOf(
                RecommendationShelf("dynamic-halloween", "Halloween Movie Night", listOf(movie)),
                RecommendationShelf("for-you", "For You", listOf(movie)),
            ), validUntil = midnight)
        }, { clock })
        val payload = request().copy(timeZone = "America/Vancouver")
        repo.refresh(1, payload)
        assertEquals(2, repo.cached(1, payload.timeZone)?.response?.shelves?.size)
        clock = midnight
        online = false
        assertEquals(listOf("for-you"), repo.cached(1, payload.timeZone)?.response?.shelves?.map { it.id })
        assertEquals(listOf("for-you"), repo.refresh(1, payload)?.response?.shelves?.map { it.id })
    }

    @Test fun daypartAndTimezoneChangesRefreshUnchangedTaste() = runTest {
        var clock = Instant.parse("2026-10-09T23:59:00Z").toEpochMilli() // Friday 16:59 Vancouver.
        var requests = 0
        val repo = RecommendationRepository(directory.root, { response("${++requests}") }, { clock })
        val payload = request().copy(timeZone = "America/Vancouver")
        repo.refresh(1, payload)
        repo.refresh(1, payload)
        assertEquals(1, requests)
        clock += 60_000 // Movie night becomes eligible at 17:00.
        repo.refresh(1, payload)
        assertEquals(2, requests)
        repo.refresh(1, payload.copy(timeZone = "America/Toronto"))
        assertEquals(3, requests)
    }

    @Test fun incompleteCatalogRefreshesAfterOneMinuteAndReadyCachePersists() = runTest {
        var clock = 1000L
        var requests = 0
        val repo = RecommendationRepository(directory.root, {
            response("${++requests}").copy(catalogReady = requests > 1, modelVersion = "qwen3-v1")
        }, { clock })
        repo.refresh(1, request())
        clock += 59_999
        repo.refresh(1, request())
        assertEquals(1, requests)
        clock++
        assertEquals(true, repo.refresh(1, request())?.response?.catalogReady)
        assertEquals(2, requests)
        val restarted = RecommendationRepository(directory.root, { error("must reuse cache") }, { clock + 1 })
        assertEquals("qwen3-v1", restarted.refresh(1, request())?.response?.modelVersion)
    }

    @Test fun serverEligibilityBoundaryExpiresWithinTheSameHour() = runTest {
        var clock = Instant.parse("2026-10-10T02:10:00Z").toEpochMilli()
        var requests = 0
        val deadline = clock + 5 * 60_000
        val repo = RecommendationRepository(directory.root, {
            response("${++requests}").copy(validUntil = deadline)
        }, { clock })
        repo.refresh(1, request())
        clock = deadline - 1
        repo.refresh(1, request())
        assertEquals(1, requests)
        clock++
        repo.refresh(1, request())
        assertEquals(2, requests)
    }

    @Test fun responseArrivingAfterItsEligibilityDeadlineCannotDisplayAnExpiredTheme() = runTest {
        var clock = Instant.parse("2026-11-01T06:59:59Z").toEpochMilli()
        val midnight = clock + 1000
        val movie = RecommendationCandidate(123, "movie", "Movie", posterUrl = "poster")
        val repo = RecommendationRepository(directory.root, {
            clock = midnight
            RecommendationResponse(validUntil = midnight, shelves = listOf(
                RecommendationShelf("dynamic-halloween", "Halloween Movie Night", listOf(movie)),
            ))
        }, { clock })
        assertTrue(repo.refresh(1, request().copy(timeZone = "America/Vancouver"))!!.response.shelves.isEmpty())
    }

    @Test fun oldBgeServerCacheRefreshesImmediatelyAfterQwenDeploymentWithinTheSameHour() = runTest {
        var requests = 0
        val movie = RecommendationCandidate(123, "movie", "Movie", posterUrl = "poster")
        val repo = RecommendationRepository(directory.root, {
            requests++
            RecommendationResponse(model = if (requests == 1) "BAAI/bge-small-en-v1.5" else "Qwen/Qwen3-Embedding-0.6B",
                shelves = listOf(RecommendationShelf("for-you", "For You", listOf(movie))))
        }, { 1000 })
        repo.refresh(1, request())
        assertEquals("BAAI/bge-small-en-v1.5", repo.cached(1)?.response?.model)
        assertEquals("for-you", repo.cached(1)?.response?.shelves?.single()?.id)
        assertEquals("Qwen/Qwen3-Embedding-0.6B", repo.refresh(1, request())?.response?.model)
        assertEquals(2, requests)
        repo.refresh(1, request())
        assertEquals(2, requests)
    }

    @Test fun refreshTimerTargetsDaypartAndMidnightBoundariesWithBoundedOfflineRetries() {
        val zone = "America/Vancouver"
        val friday = Instant.parse("2026-10-09T23:59:00Z").toEpochMilli()
        val cache = CachedRecommendations(1, friday, 0, response("Qwen/Qwen3-Embedding-0.6B"),
            RecommendationContext.key(friday, zone))
        assertEquals(60_000L, cache.nextRefreshDelay(friday, zone)) // Local17:00.
        val halloween = Instant.parse("2026-11-01T06:59:00Z").toEpochMilli()
        val midnight = cache.copy(receivedAt = halloween, contextKey = RecommendationContext.key(halloween, zone))
        assertEquals(60_000L, midnight.nextRefreshDelay(halloween, zone))
        assertEquals(1000L, midnight.nextRefreshDelay(halloween + 59_500, zone))
        assertEquals(60_000L, midnight.nextRefreshDelay(halloween + 60_000, zone)) // Expired/offline cache.
        assertEquals(60_000L, cache.copy(response = cache.response.copy(catalogReady = false))
            .nextRefreshDelay(friday, zone))
        assertEquals(20_000L, cache.copy(response = cache.response.copy(validUntil = friday + 20_000))
            .nextRefreshDelay(friday, zone))
    }

    @Test fun refreshTimerHandlesRepeatedDaylightSavingHour() {
        val zone = "America/Vancouver"
        val beforeFallback = Instant.parse("2026-11-01T08:59:00Z").toEpochMilli()
        val cache = CachedRecommendations(1, beforeFallback, 0, response("qwen"),
            RecommendationContext.key(beforeFallback, zone))
        assertEquals(60_000L, cache.nextRefreshDelay(beforeFallback, zone))
        assertFalse(cache.contextMatches(beforeFallback + 60_000, zone))
    }
}
