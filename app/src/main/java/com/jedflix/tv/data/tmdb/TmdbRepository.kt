package com.jedflix.tv.data.tmdb

import com.jedflix.tv.data.trailer.TrailerPicker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class TmdbRepository(
    private val api: TmdbApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // Session cache so returning to a section is instant; process death clears it.
    private val cache = ConcurrentHashMap<CatalogSection, Catalog>()
    private val detailsCache = ConcurrentHashMap<String, TitleDetails>()
    private val trailerCache = ConcurrentHashMap<String, String>()
    private val shelfCache = ConcurrentHashMap<String, CachedShelf>()
    private val inFlight = ConcurrentHashMap<String, Mutex>()
    private val requestPermits = Semaphore(MAX_CONCURRENT_REQUESTS)

    fun peek(section: CatalogSection): Catalog? = cache[section]

    suspend fun loadCatalog(
        section: CatalogSection,
        force: Boolean = false,
        homeShelves: List<HomeShelfPref>? = null,
    ): Catalog {
        val layout = homeShelves.takeIf { section == CatalogSection.HOME }
        if (!force && layout == null) cache[section]?.let { return it }
        if (force) {
            CatalogShelves.forSection(section).forEach { shelfCache.remove(it.id) }
        }
        val catalog = withContext(ioDispatcher) { fetch(section, force, layout) }
        cache[section] = catalog
        return catalog
    }

    /** Fetches the next TMDB page for a Shelf. No-op when page 2 is already loaded. */
    suspend fun loadMore(section: CatalogSection, shelfId: String): Catalog? =
        withContext(ioDispatcher) {
            val lock = inFlight.getOrPut(shelfId) { Mutex() }
            lock.withLock {
                val cached = shelfCache[shelfId] ?: return@withLock cache[section]
                val page = cached.nextPage ?: return@withLock null
                val fetched = fetchShelfPage(cached.spec, page, cached.watchRegion)
                val merged = (cached.items + fetched.items).distinctBy { it.key }
                val next = ShelfPaging.nextPage(
                    currentPage = page,
                    pageSize = fetched.rawCount,
                    knownTotal = fetched.knownTotal,
                    loadedCount = merged.size,
                )
                shelfCache[shelfId] = cached.copy(
                    items = merged,
                    nextPage = next,
                    watchRegion = fetched.watchRegion ?: cached.watchRegion,
                )
                patchShelfInCatalogs(shelfId)
                cache[section]
            }
        }

    suspend fun loadDetails(type: MediaType, id: Int, force: Boolean = false): TitleDetails {
        val key = "${type.apiValue}-$id"
        if (!force) detailsCache[key]?.let { return it }
        val details = withContext(ioDispatcher) {
            val append = if (type == MediaType.MOVIE) {
                "credits,recommendations,external_ids"
            } else {
                "aggregate_credits,recommendations,external_ids"
            }
            val dto = throttled { api.details(type.apiValue, id, append) }
            dto.toTitleDetails(type) ?: throw IllegalStateException("Title not found")
        }
        detailsCache[key] = details
        return details
    }

    suspend fun loadSeasonEpisodes(showId: Int, seasonNumber: Int): List<TvEpisode> =
        withContext(ioDispatcher) {
            throttled { api.seasonEpisodes(showId, seasonNumber) }.episodes.map { it.toTvEpisode() }
        }

    /**
     * YouTube key for the Title's preferred trailer, or null when TMDB has none.
     * Empty-string cache entries remember a confirmed miss.
     */
    suspend fun loadTrailerYoutubeKey(type: MediaType, id: Int): String? {
        val key = "${type.apiValue}-$id"
        trailerCache[key]?.let { return it.ifEmpty { null } }
        return withContext(ioDispatcher) {
            try {
                val dto = throttled { api.videos(type.apiValue, id) }
                val picked = TrailerPicker.youtubeKey(dto.results)
                trailerCache[key] = picked.orEmpty()
                picked
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
    }

    suspend fun search(query: String): List<MediaTitle> = withContext(ioDispatcher) {
        throttled { api.search(query.trim()) }
            .results
            .asSequence()
            .filter { it.mediaType == MediaType.MOVIE.apiValue || it.mediaType == MediaType.TV.apiValue }
            .sortedByDescending { it.popularity ?: 0.0 }
            .mapNotNull { it.toMediaTitle(null) }
            .distinctBy { it.key }
            .take(SEARCH_LIMIT)
            .toList()
    }

    private suspend fun fetch(
        section: CatalogSection,
        force: Boolean,
        homeShelves: List<HomeShelfPref>?,
    ): Catalog = coroutineScope {
        val allSpecs = CatalogShelves.forSection(section)
        val billboardId = CatalogShelves.billboardShelfId(section)
        val fetchIds = homeShelves?.let { HomeShelfLayout.fetchIds(it, billboardId) }
        val specs = if (fetchIds == null) allSpecs else allSpecs.filter { it.id in fetchIds }
        val deferred = specs.map { spec -> async { spec to runCatching { itemsFor(spec, force) } } }
        val results = deferred.map { it.await() }
        val fetched = results.associate { (spec, result) -> spec.id to result }

        val rowSpecs = if (homeShelves == null) {
            specs
        } else {
            homeShelves.filter { it.visible }.mapNotNull { pref -> allSpecs.find { it.id == pref.id } }
        }
        val rows = rowSpecs.mapNotNull { spec ->
            fetched[spec.id]?.getOrNull()?.takeIf { it.items.isNotEmpty() }?.let { toRow(it, billboardId) }
        }
        val hidEverything = homeShelves != null && homeShelves.none { it.visible }
        if (rows.isEmpty() && !hidEverything) {
            val cause = results.firstNotNullOfOrNull { it.second.exceptionOrNull() }
            throw cause ?: IllegalStateException("TMDB returned no titles")
        }
        val billboardItems = fetched[billboardId]?.getOrNull()?.items.orEmpty()
        val featured = billboardItems
            .filter { it.backdropUrl != null }
            .take(FEATURED_LIMIT)
            .ifEmpty { billboardItems.take(FEATURED_LIMIT) }
            .ifEmpty {
                rows.firstOrNull()?.items?.filter { it.backdropUrl != null }?.take(FEATURED_LIMIT).orEmpty()
            }
        Catalog(featured = featured, rows = rows)
    }

    private suspend fun itemsFor(spec: ShelfSpec, force: Boolean): CachedShelf {
        if (!force) shelfCache[spec.id]?.let { return it }
        val lock = inFlight.getOrPut(spec.id) { Mutex() }
        lock.withLock {
            if (!force) shelfCache[spec.id]?.let { return it }
            val fetched = fetchShelfPage(spec, page = 1, watchRegion = null)
            val next = ShelfPaging.nextPage(
                currentPage = 1,
                pageSize = fetched.rawCount,
                knownTotal = fetched.knownTotal,
                loadedCount = fetched.items.size,
            )
            val cached = CachedShelf(
                spec = spec,
                items = fetched.items,
                nextPage = next,
                watchRegion = fetched.watchRegion,
            )
            shelfCache[spec.id] = cached
            return cached
        }
    }

    private suspend fun fetchShelfPage(
        spec: ShelfSpec,
        page: Int,
        watchRegion: String?,
    ): FetchedPage = when (spec) {
        is ShelfSpec.Trending -> {
            val response = throttled { api.trending(spec.mediaType, page) }
            FetchedPage(mapPage(response.results, MediaType.fromApi(spec.mediaType)), response.results.size)
        }
        is ShelfSpec.MovieList -> {
            val response = throttled { api.movieList(spec.list, page) }
            FetchedPage(mapPage(response.results, MediaType.MOVIE), response.results.size)
        }
        is ShelfSpec.TvList -> {
            val response = throttled { api.tvList(spec.list, page) }
            FetchedPage(mapPage(response.results, MediaType.TV), response.results.size)
        }
        is ShelfSpec.Discover -> {
            val response = throttled {
                api.discover(spec.mediaType.apiValue, spec.genreId, page = page)
            }
            FetchedPage(mapPage(response.results, spec.mediaType), response.results.size)
        }
        is ShelfSpec.WatchProvider -> fetchWatchProviderPage(spec, page, watchRegion)
        is ShelfSpec.EditorialList -> {
            val response = throttled { api.userList(spec.listId, page) }
            val items = mapPage(response.items, spec.mediaType)
            FetchedPage(
                items = items,
                rawCount = response.items.size,
                knownTotal = response.itemCount.takeIf { it > 0 },
            )
        }
    }

    private suspend fun fetchWatchProviderPage(
        spec: ShelfSpec.WatchProvider,
        page: Int,
        watchRegion: String?,
    ): FetchedPage {
        if (watchRegion != null) {
            return discoverProvider(spec, page, watchRegion)
        }
        val preferred = discoverProvider(spec, page = 1, TmdbWatchRegions.PREFERRED)
        if (preferred.rawCount > 0) return preferred
        return discoverProvider(spec, page = 1, TmdbWatchRegions.FALLBACK)
    }

    private suspend fun discoverProvider(
        spec: ShelfSpec.WatchProvider,
        page: Int,
        region: String,
    ): FetchedPage {
        val response = throttled {
            api.discover(
                mediaType = spec.mediaType.apiValue,
                genreId = null,
                minVotes = null,
                page = page,
                watchProviders = spec.providerId,
                watchRegion = region,
            )
        }
        return FetchedPage(
            items = mapPage(response.results, spec.mediaType),
            rawCount = response.results.size,
            watchRegion = region,
        )
    }

    private fun toRow(cached: CachedShelf, billboardId: String) = CatalogRow(
        id = cached.spec.id,
        title = cached.spec.title,
        items = cached.items,
        drivesHero = cached.spec.id == billboardId,
        hasMore = cached.nextPage != null,
    )

    private fun patchShelfInCatalogs(shelfId: String) {
        val cached = shelfCache[shelfId] ?: return
        cache.replaceAll { section, catalog ->
            val billboardId = CatalogShelves.billboardShelfId(section)
            catalog.copy(
                rows = catalog.rows.map { row ->
                    if (row.id == shelfId) {
                        toRow(cached, billboardId).copy(
                            drivesHero = row.drivesHero,
                            showProgress = row.showProgress,
                        )
                    } else {
                        row
                    }
                },
            )
        }
    }

    private fun mapPage(results: List<TmdbMediaDto>, fallback: MediaType?): List<MediaTitle> =
        results.mapNotNull { it.toMediaTitle(fallback) }.distinctBy { it.key }

    private suspend fun <T> throttled(block: suspend () -> T): T =
        requestPermits.withPermit { block() }

    private data class CachedShelf(
        val spec: ShelfSpec,
        val items: List<MediaTitle>,
        val nextPage: Int?,
        val watchRegion: String?,
    )

    private data class FetchedPage(
        val items: List<MediaTitle>,
        val rawCount: Int,
        val knownTotal: Int? = null,
        val watchRegion: String? = null,
    )

    private companion object {
        const val FEATURED_LIMIT = 20
        const val SEARCH_LIMIT = 30
        const val MAX_CONCURRENT_REQUESTS = 4
    }
}
