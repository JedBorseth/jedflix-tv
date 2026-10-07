package com.jedflix.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.jedflix.tv.data.library.LibraryItem
import com.jedflix.tv.data.library.LibraryRows
import com.jedflix.tv.data.library.UserLibraryRepository
import com.jedflix.tv.data.recommendations.CachedRecommendations
import com.jedflix.tv.data.recommendations.DiscoveryPolicy
import com.jedflix.tv.data.recommendations.RecommendationRepository
import com.jedflix.tv.data.recommendations.RecommendationRequest
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.Catalog
import com.jedflix.tv.data.tmdb.CatalogRow
import com.jedflix.tv.data.tmdb.CatalogSection
import com.jedflix.tv.data.tmdb.HomeShelfConfig
import com.jedflix.tv.data.tmdb.HomeShelfLayout
import com.jedflix.tv.data.tmdb.HomeShelfPref
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import com.jedflix.tv.data.tmdb.MissingTmdbKeyException
import com.jedflix.tv.data.tmdb.ShelfPaging
import com.jedflix.tv.data.tmdb.TmdbRepository
import com.jedflix.tv.ui.focus.RailRestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.ZoneId

class CatalogViewModel(
    private val section: CatalogSection,
    private val repository: TmdbRepository,
    private val library: UserLibraryRepository,
    private val settingsStore: SettingsStore,
    private val recommendations: RecommendationRepository? = null,
) : ViewModel() {

    private val tmdb = MutableStateFlow<CatalogUiState>(
        repository.peek(section)?.let { CatalogUiState.Ready(it) } ?: CatalogUiState.Loading,
    )
    private val _state = MutableStateFlow(
        tmdb.value.let { initial ->
            if (section == CatalogSection.HOME && initial is CatalogUiState.Ready) {
                initial.copy(catalog = DiscoveryPolicy.apply(initial.catalog))
            } else initial
        },
    )
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()
    private data class DiscoveryState(
        val profileId: Long,
        val cached: CachedRecommendations? = null,
        val dislikedKeys: Set<String> = emptySet(),
    )
    private val discovery = MutableStateFlow<DiscoveryState?>(null)
    private var activeProfileId: Long? = null
    private var lastRefreshProfile: Long? = null
    private var recommendationJob: Job? = null
    private var refillJob: Job? = null
    private var pendingDiscovery: DiscoveryState? = null

    private val mediaFilter: MediaType? = when (section) {
        CatalogSection.HOME -> null
        CatalogSection.MOVIES -> MediaType.MOVIE
        CatalogSection.SHOWS -> MediaType.TV
    }

    private data class FocusIdentity(val rowId: String?, val itemKey: String?)
    @Volatile private var focusIdentity = FocusIdentity(null, null)
    val focusRowId: String? get() = focusIdentity.rowId
    val focusItemKey: String? get() = focusIdentity.itemKey
    var profileStateKey: String = "profile"
        private set

    private var homeShelves: List<HomeShelfPref>? =
        if (section == CatalogSection.HOME) HomeShelfLayout.resolve(HomeShelfConfig()) else null

    init {
        if (section == CatalogSection.HOME && recommendations != null) {
            viewModelScope.launch {
                library.observeProfiles().collect { profiles ->
                    if (profiles.isNotEmpty()) recommendations.retainProfiles(profiles.map { it.id }.toSet())
                }
            }
        }
        viewModelScope.launch {
            var previousProfile: Long? = null
            library.observeActiveProfile().collect { profile ->
                val id = profile?.id
                if (previousProfile != null && previousProfile != id) {
                    clearFocusMemory()
                }
                previousProfile = id
                profileStateKey = id?.toString() ?: "profile"
                if (activeProfileId != id) {
                    activeProfileId = id
                    discovery.value = null
                    pendingDiscovery = null
                    recommendationJob?.cancel()
                    lastRefreshProfile = null
                    refreshRecommendations()
                }
            }
        }
        viewModelScope.launch {
            if (section == CatalogSection.HOME) {
                combine(
                    combine(tmdb, discovery) { catalog, personalized -> catalog to personalized },
                    library.observeContinueWatching(mediaFilter),
                    library.observeMyList(mediaFilter),
                    library.observeWatchHistory(mediaFilter),
                    settingsStore.homeShelfConfig,
                ) { catalogAndDiscovery, continueWatching, myList, history, config ->
                    val (tmdbState, personalized) = catalogAndDiscovery
                    when (tmdbState) {
                        is CatalogUiState.Ready -> {
                            val merged = mergePersonalRows(
                                tmdbState.catalog.copy(
                                    rows = HomeShelfLayout.arrangeRows(
                                        tmdbState.catalog.rows,
                                        HomeShelfLayout.resolve(config),
                                        personalized?.cached?.response?.toRows().orEmpty(),
                                    ),
                                ),
                                continueWatching,
                                myList,
                                history,
                            )
                            val now = System.currentTimeMillis()
                            val focused = focusIdentity
                            CatalogUiState.Ready(
                                catalog = DiscoveryPolicy.apply(
                                    merged,
                                    verifiedKeys = personalized?.cached?.verifiedKeys(now).orEmpty(),
                                    dislikedKeys = personalized?.dislikedKeys.orEmpty() +
                                        personalized?.cached?.ineligibleKeys(now).orEmpty(),
                                    focusedRowId = focused.rowId,
                                    focusedItemKey = focused.itemKey,
                                ),
                                myListKeys = myList.map { it.key }.toSet(),
                                continueWatching = continueWatching,
                            )
                        }
                        else -> tmdbState
                    }
                }.flowOn(Dispatchers.Default).collect { _state.value = it }
            } else {
                combine(
                    tmdb,
                    library.observeContinueWatching(mediaFilter),
                    library.observeMyList(mediaFilter),
                    library.observeWatchHistory(mediaFilter),
                ) { tmdbState, continueWatching, myList, history ->
                    when (tmdbState) {
                        is CatalogUiState.Ready -> CatalogUiState.Ready(
                            catalog = mergePersonalRows(tmdbState.catalog, continueWatching, myList, history),
                            myListKeys = myList.map { it.key }.toSet(),
                            continueWatching = continueWatching,
                        )
                        else -> tmdbState
                    }
                }.collect { _state.value = it }
            }
        }
        viewModelScope.launch {
            if (section == CatalogSection.HOME) {
                settingsStore.homeShelfConfig.collect { config ->
                    homeShelves = HomeShelfLayout.resolve(config)
                    load(force = false)
                }
            } else if (tmdb.value is CatalogUiState.Loading) {
                load(force = false)
            }
        }
    }

    fun retry() = load(force = true)

    fun onHomeVisible() {
        // Returning after a calendar/daypart boundary starts from an eligible snapshot.
        // Network updates during an active browse still wait until Billboard focus.
        val current = discovery.value
        val cached = current?.cached
        if (cached != null) {
            val display = cached.forDisplay(System.currentTimeMillis(), ZoneId.systemDefault().id)
            if (display !== cached) {
                if (focusRowId?.startsWith("dynamic-") == true) clearFocusMemory()
                discovery.value = current.copy(cached = display)
                pendingDiscovery = null
            }
        }
        refreshRecommendations(force = true)
    }

    fun onBillboardPlayFocused() {
        focusIdentity = FocusIdentity(null, RailRestore.BILLBOARD_PLAY)
        pendingDiscovery?.let {
            discovery.value = it.copy(cached = it.cached?.forDisplay(System.currentTimeMillis(), ZoneId.systemDefault().id))
        }
        pendingDiscovery = null
    }

    fun onTitleFocused(rowId: String, itemKey: String) {
        focusIdentity = FocusIdentity(rowId, itemKey)
    }

    fun clearFocusMemory() {
        focusIdentity = FocusIdentity(null, null)
    }

    fun onShelfItemFocused(rowId: String, index: Int, itemCount: Int, hasMore: Boolean) {
        if (!ShelfPaging.shouldPrefetch(index, itemCount, hasMore)) return
        viewModelScope.launch {
            val updated = runCatching { repository.loadMore(section, rowId) }.getOrNull() ?: return@launch
            val current = tmdb.value
            if (current is CatalogUiState.Ready) {
                tmdb.value = CatalogUiState.Ready(updated)
            }
        }
    }

    fun toggleMyList(title: MediaTitle) {
        viewModelScope.launch { library.toggleMyList(title) }
    }

    private fun load(force: Boolean) {
        viewModelScope.launch {
            if (tmdb.value !is CatalogUiState.Ready) {
                tmdb.value = CatalogUiState.Loading
            }
            try {
                tmdb.value = CatalogUiState.Ready(repository.loadCatalog(section, force, homeShelves))
                refreshRecommendations(force)
                refillDiscoveryShelves()
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingTmdbKeyException) {
                tmdb.value = CatalogUiState.Error(ErrorKind.MISSING_KEY)
            } catch (e: Exception) {
                tmdb.value = CatalogUiState.Error(ErrorKind.NETWORK)
            }
        }
    }

    /** Once per Home/profile load, never from focus or pagination callbacks. */
    private fun refreshRecommendations(force: Boolean = false) {
        if (section != CatalogSection.HOME) return
        val client = recommendations ?: return
        val profileId = activeProfileId ?: return
        val catalog = (tmdb.value as? CatalogUiState.Ready)?.catalog ?: return
        if (!force && lastRefreshProfile == profileId && recommendationJob?.isActive == true) return
        if (!force && lastRefreshProfile == profileId) return
        lastRefreshProfile = profileId
        recommendationJob?.cancel()
        recommendationJob = viewModelScope.launch {
            val cached = client.cached(profileId)
            if (activeProfileId != profileId) return@launch
            publishDiscovery(DiscoveryState(profileId, cached))
            val signals = try {
                library.recommendationSignals()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            }
            if (signals.profileId != profileId || activeProfileId != profileId) return@launch
            val dislikes = signals.feedback.filter { it.value == "dislike" }
                .map { "${it.mediaType}-${it.tmdbId}" }.toSet()
            publishDiscovery(DiscoveryState(profileId, cached, dislikes))
            val request = RecommendationRequest.from(signals, catalog.rows.flatMap { it.items })
            var refreshed = client.refresh(profileId, request)
            if (activeProfileId == profileId) publishDiscovery(DiscoveryState(profileId, refreshed, dislikes))
            // Refresh independently of D-pad input at each local calendar/daypart boundary.
            // Background catalog warmup and network failures retry at most once a minute.
            while (activeProfileId == profileId) {
                val zone = ZoneId.systemDefault().id
                delay(refreshed?.nextRefreshDelay(System.currentTimeMillis(), zone) ?: 60_000L)
                refreshed = client.refresh(profileId, request.copy(timeZone = ZoneId.systemDefault().id))
                if (activeProfileId == profileId) publishDiscovery(DiscoveryState(profileId, refreshed, dislikes))
            }
        }
    }

    private fun publishDiscovery(updated: DiscoveryState) {
        if (focusRowId != null) pendingDiscovery = updated else discovery.value = updated
    }

    /** Refill the first thin discovery shelves in a batch, outside D-pad handling. */
    private fun refillDiscoveryShelves() {
        if (section != CatalogSection.HOME || refillJob?.isActive == true) return
        val catalog = (tmdb.value as? CatalogUiState.Ready)?.catalog ?: return
        refillJob = viewModelScope.launch(Dispatchers.Default) {
            val filtered = DiscoveryPolicy.apply(catalog)
            val counts = filtered.rows.associate { it.id to it.items.size }
            val thin = catalog.rows.filter { it.hasMore && (counts[it.id] ?: 0) < 12 }.take(6)
            var updated: Catalog? = null
            for (row in thin) {
                try {
                    repository.loadMore(section, row.id)?.let { updated = it }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) { /* Keep the first page when a refill fails. */ }
            }
            updated?.let { tmdb.value = CatalogUiState.Ready(it) }
        }
    }

    class Factory(
        private val section: CatalogSection,
        private val repository: TmdbRepository,
        private val library: UserLibraryRepository,
        private val settingsStore: SettingsStore,
        private val recommendations: RecommendationRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            CatalogViewModel(section, repository, library, settingsStore, recommendations) as T
    }
}

private fun mergePersonalRows(
    catalog: Catalog,
    continueWatching: List<LibraryItem>,
    myList: List<MediaTitle>,
    history: List<LibraryItem>,
): Catalog {
    val tmdbRows = catalog.rows
    val personal = buildList {
        if (continueWatching.isNotEmpty()) {
            add(
                CatalogRow(
                    id = LibraryRows.CONTINUE_WATCHING,
                    title = "Continue Watching",
                    items = continueWatching.map { it.title },
                    showProgress = true,
                ),
            )
        }
        if (myList.isNotEmpty()) {
            add(
                CatalogRow(
                    id = LibraryRows.MY_LIST,
                    title = "My List",
                    items = myList,
                ),
            )
        }
        if (history.isNotEmpty()) {
            add(
                CatalogRow(
                    id = LibraryRows.WATCH_HISTORY,
                    title = "Watch History",
                    items = history.map { it.title },
                ),
            )
        }
    }
    return catalog.copy(rows = HomeShelfLayout.pinTrending(personal + tmdbRows))
}
