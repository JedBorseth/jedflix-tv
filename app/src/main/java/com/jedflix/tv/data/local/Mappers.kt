package com.jedflix.tv.data.local

import com.jedflix.tv.data.library.LibraryItem
import com.jedflix.tv.data.library.PlaybackProgress
import com.jedflix.tv.data.library.UserProfile
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType

internal fun ProfileEntity.toModel(): UserProfile =
    UserProfile(id = id, name = name, avatarKey = avatarKey, createdAt = createdAt)

internal fun WatchProgressEntity.toLibraryItem(): LibraryItem? {
    val type = MediaType.fromApi(mediaType) ?: return null
    return LibraryItem(
        title = toMediaTitle(type),
        season = season.takeIf { it > 0 },
        episode = episode.takeIf { it > 0 },
        positionMs = positionMs,
        durationMs = durationMs,
        lastWatchedAt = lastWatchedAt,
    )
}

internal fun WatchProgressEntity.toMediaTitle(type: MediaType): MediaTitle =
    MediaTitle(
        id = tmdbId,
        mediaType = type,
        title = title,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        year = year,
        rating = rating,
        genres = genres.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    )

internal fun MyListEntity.toMediaTitle(): MediaTitle? {
    val type = MediaType.fromApi(mediaType) ?: return null
    return MediaTitle(
        id = tmdbId,
        mediaType = type,
        title = title,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        year = year,
        rating = rating,
        genres = genres.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    )
}

internal fun MediaTitle.toMyListEntity(profileId: Long, addedAt: Long): MyListEntity =
    MyListEntity(
        profileId = profileId,
        mediaType = mediaType.apiValue,
        tmdbId = id,
        addedAt = addedAt,
        title = title,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        year = year,
        rating = rating,
        genres = genres.joinToString(","),
    )

internal fun PlaybackProgress.toEntity(profileId: Long, watchedAt: Long): WatchProgressEntity =
    WatchProgressEntity(
        profileId = profileId,
        mediaType = mediaType.apiValue,
        tmdbId = tmdbId,
        season = season ?: 0,
        episode = episode ?: 0,
        positionMs = positionMs,
        durationMs = durationMs,
        lastWatchedAt = watchedAt,
        title = title,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        year = year,
        rating = rating,
        genres = genres.joinToString(","),
    )
