package com.jedflix.tv.data.library

import com.jedflix.tv.data.local.JedflixDatabase
import com.jedflix.tv.data.local.ProfileEntity
import com.jedflix.tv.data.local.SearchQueryEntity
import com.jedflix.tv.data.local.toEntity
import com.jedflix.tv.data.local.toLibraryItem
import com.jedflix.tv.data.local.toMediaTitle
import com.jedflix.tv.data.local.toModel
import com.jedflix.tv.data.local.toMyListEntity
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine

@OptIn(ExperimentalCoroutinesApi::class)
class RoomUserLibraryRepository(
    database: JedflixDatabase,
    private val settingsStore: SettingsStore,
) : UserLibraryRepository {

    private val profiles = database.profileDao()
    private val progress = database.watchProgressDao()
    private val myList = database.myListDao()
    private val searches = database.searchQueryDao()

    private val activeProfileId: Flow<Long> = combine(
        settingsStore.activeProfileId,
        profiles.observeAll(),
    ) { stored, rows ->
        when {
            rows.isEmpty() -> NO_PROFILE
            stored != null && rows.any { it.id == stored } -> stored
            else -> rows.first().id
        }
    }.distinctUntilChanged()

    override fun observeProfiles(): Flow<List<UserProfile>> =
        profiles.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeActiveProfile(): Flow<UserProfile?> =
        combine(activeProfileId, profiles.observeAll()) { id, rows ->
            rows.firstOrNull { it.id == id }?.toModel()
        }.distinctUntilChanged()

    override suspend fun switchProfile(id: Long) {
        val profile = profiles.get(id) ?: throw IllegalArgumentException("Profile not found")
        settingsStore.setActiveProfileId(profile.id)
    }

    override suspend fun createProfile(name: String, avatarKey: String): UserProfile {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Name is required")
        if (profiles.count() >= UserLibraryRepository.MAX_PROFILES) {
            throw IllegalStateException("Maximum of ${UserLibraryRepository.MAX_PROFILES} profiles")
        }
        val key = if (ProfileAvatars.all.any { it.key == avatarKey }) avatarKey else ProfileAvatars.defaultKey
        val id = profiles.insert(
            ProfileEntity(
                name = trimmed.take(MAX_NAME_LENGTH),
                avatarKey = key,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return profiles.get(id)?.toModel()
            ?: UserProfile(id, trimmed.take(MAX_NAME_LENGTH), key, System.currentTimeMillis())
    }

    override suspend fun updateProfile(id: Long, name: String, avatarKey: String) {
        val existing = profiles.get(id) ?: throw IllegalArgumentException("Profile not found")
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Name is required")
        val key = if (ProfileAvatars.all.any { it.key == avatarKey }) avatarKey else existing.avatarKey
        profiles.update(existing.copy(name = trimmed.take(MAX_NAME_LENGTH), avatarKey = key))
    }

    override suspend fun deleteProfile(id: Long) {
        if (profiles.count() <= 1) {
            throw IllegalStateException("At least one profile is required")
        }
        val remaining = profiles.getAll().filter { it.id != id }
        if (remaining.isEmpty()) {
            throw IllegalStateException("At least one profile is required")
        }
        profiles.delete(id)
        val active = settingsStore.activeProfileId.first()
        if (active == null || active == id) {
            settingsStore.setActiveProfileId(remaining.first().id)
        }
    }

    override fun observeContinueWatching(mediaType: MediaType?): Flow<List<LibraryItem>> =
        observeProgressRows(mediaType).map { rows ->
            rows.filter { isPlaybackInProgress(it.positionMs, it.durationMs) }
                .distinctBy { "${it.mediaType}-${it.tmdbId}" }
                .mapNotNull { it.toLibraryItem() }
        }

    override fun observeWatchHistory(mediaType: MediaType?): Flow<List<LibraryItem>> =
        observeProgressRows(mediaType).map { rows ->
            rows.distinctBy { "${it.mediaType}-${it.tmdbId}" }
                .mapNotNull { it.toLibraryItem() }
        }

    override fun observeMyList(mediaType: MediaType?): Flow<List<MediaTitle>> =
        activeProfileId.flatMapLatest { profileId ->
            if (profileId == NO_PROFILE) return@flatMapLatest flowOf(emptyList())
            myList.observeAll(profileId).map { rows ->
                rows.mapNotNull { it.toMediaTitle() }
                    .filter { mediaType == null || it.mediaType == mediaType }
            }
        }

    override fun observeRecentSearches(): Flow<List<String>> =
        activeProfileId.flatMapLatest { profileId ->
            if (profileId == NO_PROFILE) return@flatMapLatest flowOf(emptyList())
            searches.observe(profileId, UserLibraryRepository.MAX_RECENT_SEARCHES)
                .map { rows -> rows.map { it.query } }
        }

    override fun observeInMyList(mediaType: MediaType, tmdbId: Int): Flow<Boolean> =
        activeProfileId.flatMapLatest { profileId ->
            if (profileId == NO_PROFILE) return@flatMapLatest flowOf(false)
            myList.observeOne(profileId, mediaType.apiValue, tmdbId).map { it != null }
        }

    override fun observeTitleProgress(mediaType: MediaType, tmdbId: Int): Flow<LibraryItem?> =
        activeProfileId.flatMapLatest { profileId ->
            if (profileId == NO_PROFILE) return@flatMapLatest flowOf(null)
            progress.observeLatestForTitle(profileId, mediaType.apiValue, tmdbId)
                .map { entity -> entity?.toLibraryItem()?.takeIf { it.isInProgress } }
        }

    override suspend fun toggleMyList(title: MediaTitle) {
        val profileId = requireProfileId()
        val existing = myList.get(profileId, title.mediaType.apiValue, title.id)
        if (existing == null) {
            myList.upsert(title.toMyListEntity(profileId, System.currentTimeMillis()))
        } else {
            myList.delete(profileId, title.mediaType.apiValue, title.id)
        }
    }

    override suspend fun recordPlayback(progress: PlaybackProgress) {
        val profileId = requireProfileId()
        this.progress.upsert(progress.toEntity(profileId, System.currentTimeMillis()))
    }

    override suspend fun playbackPosition(
        mediaType: MediaType,
        tmdbId: Int,
        season: Int?,
        episode: Int?,
    ): Long {
        val profileId = requireProfileId()
        val row = progress.get(
            profileId,
            mediaType.apiValue,
            tmdbId,
            season ?: 0,
            episode ?: 0,
        ) ?: return 0L
        return if (isPlaybackInProgress(row.positionMs, row.durationMs)) row.positionMs else 0L
    }

    override suspend fun saveSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val profileId = requireProfileId()
        searches.upsert(
            SearchQueryEntity(
                profileId = profileId,
                query = trimmed,
                searchedAt = System.currentTimeMillis(),
            ),
        )
        val extras = searches.getAll(profileId).drop(UserLibraryRepository.MAX_RECENT_SEARCHES)
        extras.forEach { searches.delete(profileId, it.query) }
    }

    private fun observeProgressRows(mediaType: MediaType?) =
        activeProfileId.flatMapLatest { profileId ->
            if (profileId == NO_PROFILE) return@flatMapLatest flowOf(emptyList())
            progress.observeAll(profileId).map { rows ->
                if (mediaType == null) rows else rows.filter { it.mediaType == mediaType.apiValue }
            }
        }

    private suspend fun requireProfileId(): Long {
        val id = activeProfileId.first()
        if (id == NO_PROFILE) throw IllegalStateException("No profile available")
        return id
    }

    private companion object {
        const val NO_PROFILE = -1L
        const val MAX_NAME_LENGTH = 20
    }
}
