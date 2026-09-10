package com.jedflix.tv.data.tmdb

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbRepositoryShelvesTest {

    @Test
    fun providerDiscoverUsesCanadaAndSkipsUsWhenCanadaHasTitles() = runTest {
        val api = FakeTmdbApi()
        api.discoverHandler = { call ->
            if (call.watchRegion == TmdbWatchRegions.PREFERRED) {
                page(media(1, "Crave Hit"))
            } else {
                page(media(9, "US Only"))
            }
        }
        val repo = TmdbRepository(api, UnconfinedTestDispatcher(testScheduler))
        val catalog = repo.loadCatalog(CatalogSection.MOVIES)

        val crave = catalog.rows.first { it.id == "crave-movies" }
        assertEquals("Crave Hit", crave.items.single().title)
        val craveCalls = api.discoverCalls.filter { it.watchProviders == TmdbWatchProviders.CRAVE }
        assertEquals(listOf(TmdbWatchRegions.PREFERRED), craveCalls.map { it.watchRegion })
        assertTrue(craveCalls.all { it.minVotes == null })
        assertTrue(craveCalls.all { it.sortBy == "popularity.desc" })
    }

    @Test
    fun providerDiscoverFallsBackToUsWhenCanadaIsEmpty() = runTest {
        val api = FakeTmdbApi()
        api.discoverHandler = { call ->
            if (call.watchRegion == TmdbWatchRegions.PREFERRED) {
                TmdbPagedResponse()
            } else {
                page(media(2, "US Title"))
            }
        }
        val repo = TmdbRepository(api, UnconfinedTestDispatcher(testScheduler))
        val catalog = repo.loadCatalog(CatalogSection.MOVIES)
        val crave = catalog.rows.first { it.id == "crave-movies" }
        assertEquals("US Title", crave.items.single().title)
        val regions = api.discoverCalls
            .filter { it.watchProviders == TmdbWatchProviders.CRAVE }
            .map { it.watchRegion }
        assertEquals(listOf(TmdbWatchRegions.PREFERRED, TmdbWatchRegions.FALLBACK), regions)
    }

    @Test
    fun editorialListLoadsFirstPageOnlyUntilLoadMore() = runTest {
        val api = FakeTmdbApi()
        api.listPages = mapOf(
            (JedsPicksLists.SHOWS to 1) to TmdbListResponse(
                page = 1,
                itemCount = 21,
                items = (1..20).map { media(it, "Show $it", "tv") },
            ),
            (JedsPicksLists.SHOWS to 2) to TmdbListResponse(
                page = 2,
                itemCount = 21,
                items = listOf(media(21, "Family Guy", "tv")),
            ),
        )
        val repo = TmdbRepository(api, UnconfinedTestDispatcher(testScheduler))
        val catalog = repo.loadCatalog(CatalogSection.SHOWS)
        val row = catalog.rows.first { it.id == "jeds-shows" }
        assertEquals(20, row.items.size)
        assertTrue(row.hasMore)
        assertEquals(listOf(1), api.listCalls.filter { it.first == JedsPicksLists.SHOWS }.map { it.second })

        val more = repo.loadMore(CatalogSection.SHOWS, "jeds-shows")
        val full = more!!.rows.first { it.id == "jeds-shows" }
        assertEquals(21, full.items.size)
        assertEquals("Family Guy", full.items.last().title)
        assertFalse(full.hasMore)
        assertEquals(listOf(1, 2), api.listCalls.filter { it.first == JedsPicksLists.SHOWS }.map { it.second })
    }

    @Test
    fun initialCatalogDoesNotFetchSecondTmdbPage() = runTest {
        val api = FakeTmdbApi()
        api.discoverHandler = { call ->
            page(*(1..20).map { media(it + (call.page - 1) * 20, "P${call.page}-$it") }.toTypedArray())
        }
        val repo = TmdbRepository(api, UnconfinedTestDispatcher(testScheduler))
        val catalog = repo.loadCatalog(CatalogSection.MOVIES)
        val crave = catalog.rows.first { it.id == "crave-movies" }
        assertEquals(20, crave.items.size)
        assertTrue(crave.hasMore)
        val cravePages = api.discoverCalls
            .filter { it.watchProviders == TmdbWatchProviders.CRAVE }
            .map { it.page }
        assertEquals(listOf(1), cravePages)
    }

    @Test
    fun loadMoreAppendsSecondPageOnSameWatchRegion() = runTest {
        val api = FakeTmdbApi()
        api.discoverHandler = { call ->
            if (call.watchRegion == TmdbWatchRegions.PREFERRED) {
                val start = (call.page - 1) * 20
                page(*(1..20).map { media(start + it, "CA ${start + it}") }.toTypedArray())
            } else {
                page(media(900, "US Only"))
            }
        }
        val repo = TmdbRepository(api, UnconfinedTestDispatcher(testScheduler))
        repo.loadCatalog(CatalogSection.MOVIES)
        val more = repo.loadMore(CatalogSection.MOVIES, "crave-movies")
        val crave = more!!.rows.first { it.id == "crave-movies" }
        assertEquals(40, crave.items.size)
        assertFalse(crave.hasMore)
        val craveCalls = api.discoverCalls.filter { it.watchProviders == TmdbWatchProviders.CRAVE }
        assertEquals(listOf(1, 2), craveCalls.map { it.page })
        assertTrue(craveCalls.all { it.watchRegion == TmdbWatchRegions.PREFERRED })
        assertEquals(null, repo.loadMore(CatalogSection.MOVIES, "crave-movies"))
        assertEquals(2, api.discoverCalls.count { it.watchProviders == TmdbWatchProviders.CRAVE })
    }

    @Test
    fun sharedShelvesAreFetchedOnceAcrossSections() = runTest {
        val api = FakeTmdbApi()
        api.listPages = mapOf(
            (JedsPicksLists.MOVIES to 1) to TmdbListResponse(
                page = 1,
                itemCount = 1,
                items = listOf(media(11, "Tenet")),
            ),
        )
        val repo = TmdbRepository(api, UnconfinedTestDispatcher(testScheduler))
        repo.loadCatalog(CatalogSection.HOME)
        repo.loadCatalog(CatalogSection.MOVIES)
        val movieListCalls = api.listCalls.filter { it.first == JedsPicksLists.MOVIES }
        assertEquals(1, movieListCalls.size)
    }

    @Test
    fun billboardStaysOnTrendingNotFirstShelf() = runTest {
        val api = FakeTmdbApi()
        api.listPages = mapOf(
            (JedsPicksLists.MOVIES to 1) to TmdbListResponse(
                page = 1,
                itemCount = 1,
                items = listOf(media(11, "Tenet")),
            ),
        )
        api.trendingByType["all"] = listOf(media(99, "Trending Hit", "movie"))
        val repo = TmdbRepository(api, UnconfinedTestDispatcher(testScheduler))
        val catalog = repo.loadCatalog(CatalogSection.HOME)
        assertEquals("jeds-movies", catalog.rows.first().id)
        assertFalse(catalog.rows.first().drivesHero)
        val trending = catalog.rows.first { it.id == CatalogShelves.TRENDING_HOME }
        assertTrue(trending.drivesHero)
        assertEquals("Trending Hit", catalog.featured.single().title)
    }

    @Test
    fun concurrentShelfFetchesStayUnderRateLimit() = runBlocking {
        val api = FakeTmdbApi(delayMs = 40)
        api.discoverHandler = { page(media(1, "Any")) }
        api.trendingByType["all"] = listOf(media(2, "Trend", "movie"))
        val repo = TmdbRepository(api, Dispatchers.Default)
        repo.loadCatalog(CatalogSection.HOME)
        assertTrue(
            "max in-flight was ${api.maxInFlight.get()}",
            api.maxInFlight.get() <= 4,
        )
        assertTrue(api.maxInFlight.get() >= 2)
    }

    @Test
    fun homeLayoutSkipsHiddenShelvesButStillFetchesTrending() = runTest {
        val api = FakeTmdbApi()
        api.trendingByType["all"] = listOf(media(99, "Trending Hit", "movie"))
        api.discoverHandler = { page(media(1, "Any")) }
        api.listPages = mapOf(
            (JedsPicksLists.MOVIES to 1) to TmdbListResponse(
                page = 1,
                itemCount = 1,
                items = listOf(media(11, "Tenet")),
            ),
        )
        val prefs = HomeShelfLayout.resolve(
            HomeShelfConfig(hidden = setOf("jeds-movies", "horror", CatalogShelves.TRENDING_HOME)),
        )
        val repo = TmdbRepository(api, UnconfinedTestDispatcher(testScheduler))
        val catalog = repo.loadCatalog(CatalogSection.HOME, homeShelves = prefs)

        assertTrue(catalog.rows.none { it.id == "jeds-movies" })
        assertTrue(catalog.rows.none { it.id == "horror" })
        assertTrue(catalog.rows.none { it.id == CatalogShelves.TRENDING_HOME })
        assertEquals("Trending Hit", catalog.featured.single().title)
        assertEquals(listOf("all"), api.trendingCalls)
        assertTrue(api.listCalls.none { it.first == JedsPicksLists.MOVIES })
        assertTrue(api.discoverCalls.none { it.genreId == TmdbGenres.MOVIE_HORROR })
    }

    @Test
    fun hidingEveryHomeShelfLeavesFeaturedAndNoCatalogRows() = runTest {
        val api = FakeTmdbApi()
        api.trendingByType["all"] = listOf(media(99, "Trending Hit", "movie"))
        val prefs = HomeShelfLayout.resolve(
            HomeShelfConfig(hidden = HomeShelfLayout.factorySpecs().map { it.id }.toSet()),
        )
        val repo = TmdbRepository(api, UnconfinedTestDispatcher(testScheduler))
        val catalog = repo.loadCatalog(CatalogSection.HOME, homeShelves = prefs)
        assertTrue(catalog.rows.isEmpty())
        assertEquals("Trending Hit", catalog.featured.single().title)
        assertEquals(listOf("all"), api.trendingCalls)
        assertTrue(api.listCalls.isEmpty())
        assertTrue(api.discoverCalls.isEmpty())
    }

    private fun page(vararg items: TmdbMediaDto) = TmdbPagedResponse(page = 1, results = items.toList())

    private fun media(id: Int, title: String, type: String = "movie") = TmdbMediaDto(
        id = id,
        mediaType = type,
        title = title,
        name = title,
        posterPath = "/p$id.jpg",
        backdropPath = "/b$id.jpg",
    )
}

private data class DiscoverCall(
    val mediaType: String,
    val genreId: Int?,
    val sortBy: String,
    val minVotes: Int?,
    val watchProviders: Int?,
    val watchRegion: String?,
    val page: Int,
)

private class FakeTmdbApi(
    private val delayMs: Long = 0,
) : TmdbApi {
    val discoverCalls = CopyOnWriteArrayList<DiscoverCall>()
    val listCalls = CopyOnWriteArrayList<Pair<Int, Int>>()
    val inFlight = AtomicInteger()
    val maxInFlight = AtomicInteger()
    var discoverHandler: (DiscoverCall) -> TmdbPagedResponse = { TmdbPagedResponse() }
    var listPages: Map<Pair<Int, Int>, TmdbListResponse> = emptyMap()
    val trendingByType = mutableMapOf<String, List<TmdbMediaDto>>()
    val trendingCalls = CopyOnWriteArrayList<String>()
    val movieLists = mutableMapOf<String, List<TmdbMediaDto>>()
    val tvLists = mutableMapOf<String, List<TmdbMediaDto>>()

    private suspend fun track(): CloseablePermit {
        val now = inFlight.incrementAndGet()
        maxInFlight.updateAndGet { maxOf(it, now) }
        if (delayMs > 0) delay(delayMs)
        return CloseablePermit { inFlight.decrementAndGet() }
    }

    override suspend fun trending(mediaType: String, page: Int): TmdbPagedResponse {
        trendingCalls += mediaType
        track().use {
            return TmdbPagedResponse(results = if (page == 1) trendingByType[mediaType].orEmpty() else emptyList())
        }
    }

    override suspend fun movieList(list: String, page: Int): TmdbPagedResponse {
        track().use { return TmdbPagedResponse(results = movieLists[list].orEmpty()) }
    }

    override suspend fun tvList(list: String, page: Int): TmdbPagedResponse {
        track().use { return TmdbPagedResponse(results = tvLists[list].orEmpty()) }
    }

    override suspend fun discover(
        mediaType: String,
        genreId: Int?,
        sortBy: String,
        includeAdult: Boolean,
        minVotes: Int?,
        page: Int,
        watchProviders: Int?,
        watchRegion: String?,
    ): TmdbPagedResponse {
        val call = DiscoverCall(mediaType, genreId, sortBy, minVotes, watchProviders, watchRegion, page)
        discoverCalls += call
        track().use { return discoverHandler(call) }
    }

    override suspend fun userList(listId: Int, page: Int): TmdbListResponse {
        listCalls += listId to page
        track().use {
            return listPages[listId to page] ?: TmdbListResponse(page = page)
        }
    }

    override suspend fun details(mediaType: String, id: Int, append: String): TmdbDetailsDto {
        error("unused")
    }

    override suspend fun seasonEpisodes(id: Int, season: Int): TmdbSeasonDto {
        error("unused")
    }

    override suspend fun search(query: String, includeAdult: Boolean, page: Int): TmdbPagedResponse {
        error("unused")
    }
}

private fun interface CloseablePermit : AutoCloseable
