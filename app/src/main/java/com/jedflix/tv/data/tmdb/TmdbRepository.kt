package com.jedflix.tv.data.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class TmdbRepository(private val api: TmdbApi) {

    // Session cache so returning to a section is instant; process death clears it.
    private val cache = ConcurrentHashMap<CatalogSection, Catalog>()
    private val detailsCache = ConcurrentHashMap<String, TitleDetails>()

    fun peek(section: CatalogSection): Catalog? = cache[section]

    suspend fun loadCatalog(section: CatalogSection, force: Boolean = false): Catalog {
        if (!force) cache[section]?.let { return it }
        val catalog = withContext(Dispatchers.IO) { fetch(section) }
        cache[section] = catalog
        return catalog
    }

    suspend fun loadDetails(type: MediaType, id: Int, force: Boolean = false): TitleDetails {
        val key = "${type.apiValue}-$id"
        if (!force) detailsCache[key]?.let { return it }
        val details = withContext(Dispatchers.IO) {
            val append = if (type == MediaType.MOVIE) {
                "credits,recommendations,external_ids"
            } else {
                "aggregate_credits,recommendations,external_ids"
            }
            val dto = api.details(type.apiValue, id, append)
            dto.toTitleDetails(type) ?: throw IllegalStateException("Title not found")
        }
        detailsCache[key] = details
        return details
    }

    suspend fun loadSeasonEpisodes(showId: Int, seasonNumber: Int): List<TvEpisode> =
        withContext(Dispatchers.IO) {
            api.seasonEpisodes(showId, seasonNumber).episodes.map { it.toTvEpisode() }
        }

    suspend fun search(query: String): List<MediaTitle> = withContext(Dispatchers.IO) {
        api.search(query.trim())
            .results
            .asSequence()
            .filter { it.mediaType == MediaType.MOVIE.apiValue || it.mediaType == MediaType.TV.apiValue }
            .sortedByDescending { it.popularity ?: 0.0 }
            .mapNotNull { it.toMediaTitle(null) }
            .distinctBy { it.key }
            .take(SEARCH_LIMIT)
            .toList()
    }

    private suspend fun fetch(section: CatalogSection): Catalog = coroutineScope {
        val specs = CatalogShelves.forSection(section)
        val deferred = specs.map { spec -> async { spec to runCatching { fetchShelf(spec) } } }
        val results = deferred.map { it.await() }

        val rows = results.mapNotNull { (spec, result) ->
            result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { CatalogRow(spec.id, spec.title, it) }
        }
        if (rows.isEmpty()) {
            val cause = results.firstNotNullOfOrNull { it.second.exceptionOrNull() }
            throw cause ?: IllegalStateException("TMDB returned no titles")
        }
        val featured = rows.first().items.filter { it.backdropUrl != null }.take(FEATURED_LIMIT)
        Catalog(featured = featured, rows = rows)
    }

    private suspend fun fetchShelf(spec: ShelfSpec): List<MediaTitle> {
        val (response, fallback) = when (spec) {
            is ShelfSpec.Trending -> api.trending(spec.mediaType) to MediaType.fromApi(spec.mediaType)
            is ShelfSpec.MovieList -> api.movieList(spec.list) to MediaType.MOVIE
            is ShelfSpec.TvList -> api.tvList(spec.list) to MediaType.TV
            is ShelfSpec.Discover -> api.discover(spec.mediaType.apiValue, spec.genreId) to spec.mediaType
        }
        return response.results
            .mapNotNull { it.toMediaTitle(fallback) }
            .distinctBy { it.key }
            .take(ROW_LIMIT)
    }

    private companion object {
        const val ROW_LIMIT = 20
        const val FEATURED_LIMIT = 20
        const val SEARCH_LIMIT = 30
    }
}
