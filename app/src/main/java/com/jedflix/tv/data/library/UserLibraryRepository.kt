package com.jedflix.tv.data.library

import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import kotlinx.coroutines.flow.Flow

/**
 * Profile-scoped local library. Room is the only backend today; keep this
 * interface so a remote source can sit behind the same calls later.
 */
interface UserLibraryRepository {
    fun observeProfiles(): Flow<List<UserProfile>>
    fun observeActiveProfile(): Flow<UserProfile?>
    suspend fun switchProfile(id: Long)
    suspend fun createProfile(name: String, avatarKey: String): UserProfile
    suspend fun updateProfile(id: Long, name: String, avatarKey: String)
    suspend fun deleteProfile(id: Long)

    fun observeContinueWatching(mediaType: MediaType? = null): Flow<List<LibraryItem>>
    fun observeWatchHistory(mediaType: MediaType? = null): Flow<List<LibraryItem>>
    fun observeMyList(mediaType: MediaType? = null): Flow<List<MediaTitle>>
    fun observeRecentSearches(): Flow<List<String>>
    fun observeInMyList(mediaType: MediaType, tmdbId: Int): Flow<Boolean>
    fun observeTitleProgress(mediaType: MediaType, tmdbId: Int): Flow<LibraryItem?>

    suspend fun toggleMyList(title: MediaTitle)
    suspend fun recordPlayback(progress: PlaybackProgress)
    suspend fun playbackPosition(mediaType: MediaType, tmdbId: Int, season: Int?, episode: Int?): Long
    suspend fun saveSearchQuery(query: String)

    companion object {
        const val MAX_PROFILES = 5
        const val MAX_RECENT_SEARCHES = 10
        const val DEFAULT_PROFILE_NAME = "User 1"
    }
}
