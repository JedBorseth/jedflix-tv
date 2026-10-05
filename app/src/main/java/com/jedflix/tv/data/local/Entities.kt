package com.jedflix.tv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatarKey: String,
    val createdAt: Long,
)

@Entity(
    tableName = "watch_progress",
    primaryKeys = ["profileId", "mediaType", "tmdbId", "season", "episode"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profileId"),
        Index(value = ["profileId", "lastWatchedAt"]),
    ],
)
data class WatchProgressEntity(
    val profileId: Long,
    val mediaType: String,
    val tmdbId: Int,
    val season: Int,
    val episode: Int,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: String?,
    val rating: Double?,
    val genres: String,
    @ColumnInfo(defaultValue = "0") val watchedMs: Long = 0L,
)

@Entity(
    tableName = "my_list",
    primaryKeys = ["profileId", "mediaType", "tmdbId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profileId"),
        Index(value = ["profileId", "addedAt"]),
    ],
)
data class MyListEntity(
    val profileId: Long,
    val mediaType: String,
    val tmdbId: Int,
    val addedAt: Long,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: String?,
    val rating: Double?,
    val genres: String,
)

@Entity(
    tableName = "recent_searches",
    primaryKeys = ["profileId", "query"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profileId"),
        Index(value = ["profileId", "searchedAt"]),
    ],
)
data class SearchQueryEntity(
    val profileId: Long,
    val query: String,
    val searchedAt: Long,
)

/** One watermark per viewing session makes retries/out-of-order saves idempotent. */
@Entity(
    tableName = "playback_sessions",
    primaryKeys = ["profileId", "sessionId"],
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("profileId")],
)
data class PlaybackSessionEntity(
    val profileId: Long,
    val sessionId: String,
    val watchedMs: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "title_feedback",
    primaryKeys = ["profileId", "mediaType", "tmdbId"],
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("profileId")],
)
data class TitleFeedbackEntity(
    val profileId: Long,
    val mediaType: String,
    val tmdbId: Int,
    val value: String,
    val updatedAt: Long,
)
