package com.jedflix.tv.data.recommendations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
}
