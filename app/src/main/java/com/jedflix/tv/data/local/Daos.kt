package com.jedflix.tv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Insert
    suspend fun insert(entity: ProfileEntity): Long

    @Update
    suspend fun update(entity: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId ORDER BY lastWatchedAt DESC")
    fun observeAll(profileId: Long): Flow<List<WatchProgressEntity>>

    @Query(
        """
        SELECT * FROM watch_progress
        WHERE profileId = :profileId AND mediaType = :mediaType AND tmdbId = :tmdbId
        ORDER BY lastWatchedAt DESC
        LIMIT 1
        """,
    )
    fun observeLatestForTitle(profileId: Long, mediaType: String, tmdbId: Int): Flow<WatchProgressEntity?>

    @Query(
        """
        SELECT * FROM watch_progress
        WHERE profileId = :profileId AND mediaType = :mediaType AND tmdbId = :tmdbId
          AND season = :season AND episode = :episode
        """,
    )
    suspend fun get(
        profileId: Long,
        mediaType: String,
        tmdbId: Int,
        season: Int,
        episode: Int,
    ): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchProgressEntity)
}

@Dao
interface MyListDao {
    @Query("SELECT * FROM my_list WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun observeAll(profileId: Long): Flow<List<MyListEntity>>

    @Query(
        """
        SELECT * FROM my_list
        WHERE profileId = :profileId AND mediaType = :mediaType AND tmdbId = :tmdbId
        LIMIT 1
        """,
    )
    fun observeOne(profileId: Long, mediaType: String, tmdbId: Int): Flow<MyListEntity?>

    @Query(
        """
        SELECT * FROM my_list
        WHERE profileId = :profileId AND mediaType = :mediaType AND tmdbId = :tmdbId
        LIMIT 1
        """,
    )
    suspend fun get(profileId: Long, mediaType: String, tmdbId: Int): MyListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MyListEntity)

    @Query(
        "DELETE FROM my_list WHERE profileId = :profileId AND mediaType = :mediaType AND tmdbId = :tmdbId",
    )
    suspend fun delete(profileId: Long, mediaType: String, tmdbId: Int)
}

@Dao
interface SearchQueryDao {
    @Query(
        """
        SELECT * FROM recent_searches
        WHERE profileId = :profileId
        ORDER BY searchedAt DESC
        LIMIT :limit
        """,
    )
    fun observe(profileId: Long, limit: Int): Flow<List<SearchQueryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchQueryEntity)

    @Query("SELECT * FROM recent_searches WHERE profileId = :profileId ORDER BY searchedAt DESC")
    suspend fun getAll(profileId: Long): List<SearchQueryEntity>

    @Query("DELETE FROM recent_searches WHERE profileId = :profileId AND query = :query")
    suspend fun delete(profileId: Long, query: String)
}
