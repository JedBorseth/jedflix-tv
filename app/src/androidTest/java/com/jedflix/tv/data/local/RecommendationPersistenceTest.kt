package com.jedflix.tv.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jedflix.tv.data.library.PlaybackProgress
import com.jedflix.tv.data.library.RoomUserLibraryRepository
import com.jedflix.tv.data.library.TitleFeedback
import com.jedflix.tv.data.settings.SettingsStore
import com.jedflix.tv.data.tmdb.MediaTitle
import com.jedflix.tv.data.tmdb.MediaType
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecommendationPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun additiveMigrationPreservesProfilesResumeListsAndSearches() = runBlocking {
        val name = "migration-${UUID.randomUUID()}.db"
        context.getDatabasePath(name).parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            createVersionOne(db)
            db.execSQL("INSERT INTO profiles VALUES (7, 'Original', 'blue', 100)")
            db.execSQL("INSERT INTO watch_progress VALUES (7, 'movie', 42, 0, 0, 45000, 90000, 200, 'Original Movie', 'Overview', NULL, NULL, '2020', 7.5, 'Drama')")
            db.execSQL("INSERT INTO my_list VALUES (7, 'movie', 43, 201, 'Saved Movie', 'Overview', NULL, NULL, '2021', NULL, 'Comedy')")
            db.execSQL("INSERT INTO recent_searches VALUES (7, 'original search', 202)")
            db.version = 1
        }
        val database = Room.databaseBuilder(context, JedflixDatabase::class.java, name)
            .addMigrations(JedflixDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("Original", database.profileDao().get(7)?.name)
            val old = database.watchProgressDao().get(7, "movie", 42, 0, 0)!!
            assertEquals(45_000L, old.positionMs)
            assertEquals(90_000L, old.durationMs)
            assertEquals(0L, old.watchedMs) // Never pretend historical seek position was viewing time.
            assertEquals("Saved Movie", database.myListDao().get(7, "movie", 43)?.title)
            assertEquals("original search", database.searchQueryDao().getAll(7).single().query)
            database.titleFeedbackDao().upsert(TitleFeedbackEntity(7, "movie", 42, "like", 300))
            database.playbackSessionDao().upsert(PlaybackSessionEntity(7, "session", 1_000, 300))
            database.profileDao().delete(7)
            assertTrue(database.watchProgressDao().recommendationHistory(7, 200).isEmpty())
            assertTrue(database.titleFeedbackDao().recommendationFeedback(7, 200).isEmpty())
            assertNull(database.playbackSessionDao().get(7, "session"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun cumulativeCheckpointsAreIdempotentAndProfilesRemainIndependent() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, JedflixDatabase::class.java).build()
        val preferencesFile = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        val preferencesScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val settings = SettingsStore(context, PreferenceDataStoreFactory.create(
            scope = preferencesScope, produceFile = { preferencesFile },
        ))
        settings.setRealDebridApiKey("test-only-preserved-key")
        val originalKey = settings.realDebridApiKey.first()
        try {
            val profileA = database.profileDao().insert(ProfileEntity(name = "A", avatarKey = "blue", createdAt = 1))
            val profileB = database.profileDao().insert(ProfileEntity(name = "B", avatarKey = "blue", createdAt = 2))
            val library = RoomUserLibraryRepository(database, settings)
            settings.setActiveProfileId(profileA)
            val now = System.currentTimeMillis()
            val snapshot = playback(profileA, "a1", watchedMs = 20_000, positionMs = 90_000, capturedAt = now + 2)
            library.recordPlayback(snapshot)
            library.recordPlayback(snapshot.copy(watchedMs = 10_000, positionMs = 10_000, capturedAt = now + 1))
            library.recordPlayback(snapshot) // Retried final checkpoint.
            assertEquals(20_000L, database.watchProgressDao().get(profileA, "movie", 42, 0, 0)?.watchedMs)
            assertEquals(90_000L, database.watchProgressDao().get(profileA, "movie", 42, 0, 0)?.positionMs)
            library.recordPlayback(snapshot.copy(sessionId = "a2", watchedMs = 5_000, capturedAt = now + 3))
            assertEquals(25_000L, library.recommendationSignals().history.single().watchedMs)
            val title = MediaTitle(42, MediaType.MOVIE, "Movie", "", null, null, "2020", null, emptyList())
            library.setFeedback(title, TitleFeedback.LIKE)
            library.toggleMyList(title)
            assertEquals(TitleFeedback.LIKE, library.observeFeedback(MediaType.MOVIE, 42).first())
            settings.setActiveProfileId(profileB)
            assertNull(library.observeFeedback(MediaType.MOVIE, 42).first())
            assertTrue(library.recommendationSignals().history.isEmpty())
            assertTrue(library.recommendationSignals().myList.isEmpty())
            library.setFeedback(title, TitleFeedback.DISLIKE)
            // A queued checkpoint belongs to the profile captured when playback started.
            library.recordPlayback(snapshot.copy(sessionId = "a3", watchedMs = 2_000, capturedAt = now + 4))
            assertTrue(library.recommendationSignals().history.isEmpty())
            assertEquals("dislike", library.recommendationSignals().feedback.single().value)
            settings.setActiveProfileId(profileA)
            val signals = library.recommendationSignals()
            assertEquals(profileA, signals.profileId)
            assertEquals(27_000L, signals.history.single().watchedMs)
            assertEquals("like", signals.feedback.single().value)
            assertEquals(42, signals.myList.single().tmdbId)
            library.setFeedback(title, null)
            assertTrue(library.recommendationSignals().feedback.isEmpty())
            assertEquals(originalKey, settings.realDebridApiKey.first())
            assertFalse(signals.history.single().watchedMs == signals.history.single().positionMs)
        } finally {
            database.close()
            preferencesScope.cancel()
            preferencesFile.delete()
        }
    }

    private fun playback(profileId: Long, session: String, watchedMs: Long, positionMs: Long, capturedAt: Long) =
        PlaybackProgress(
            mediaType = MediaType.MOVIE, tmdbId = 42, season = null, episode = null,
            positionMs = positionMs, durationMs = 100_000, title = "Movie", overview = "",
            posterUrl = null, backdropUrl = null, year = "2020", rating = null, genres = emptyList(),
            sessionId = session, watchedMs = watchedMs, capturedAt = capturedAt, profileId = profileId,
        )

    private fun createVersionOne(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE profiles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, avatarKey TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("""
            CREATE TABLE watch_progress (
                profileId INTEGER NOT NULL, mediaType TEXT NOT NULL, tmdbId INTEGER NOT NULL,
                season INTEGER NOT NULL, episode INTEGER NOT NULL, positionMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL, lastWatchedAt INTEGER NOT NULL, title TEXT NOT NULL,
                overview TEXT NOT NULL, posterUrl TEXT, backdropUrl TEXT, year TEXT, rating REAL,
                genres TEXT NOT NULL, PRIMARY KEY(profileId, mediaType, tmdbId, season, episode),
                FOREIGN KEY(profileId) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_watch_progress_profileId ON watch_progress(profileId)")
        db.execSQL("CREATE INDEX index_watch_progress_profileId_lastWatchedAt ON watch_progress(profileId, lastWatchedAt)")
        db.execSQL("""
            CREATE TABLE my_list (
                profileId INTEGER NOT NULL, mediaType TEXT NOT NULL, tmdbId INTEGER NOT NULL,
                addedAt INTEGER NOT NULL, title TEXT NOT NULL, overview TEXT NOT NULL,
                posterUrl TEXT, backdropUrl TEXT, year TEXT, rating REAL, genres TEXT NOT NULL,
                PRIMARY KEY(profileId, mediaType, tmdbId),
                FOREIGN KEY(profileId) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_my_list_profileId ON my_list(profileId)")
        db.execSQL("CREATE INDEX index_my_list_profileId_addedAt ON my_list(profileId, addedAt)")
        db.execSQL("""
            CREATE TABLE recent_searches (
                profileId INTEGER NOT NULL, query TEXT NOT NULL, searchedAt INTEGER NOT NULL,
                PRIMARY KEY(profileId, query),
                FOREIGN KEY(profileId) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_recent_searches_profileId ON recent_searches(profileId)")
        db.execSQL("CREATE INDEX index_recent_searches_profileId_searchedAt ON recent_searches(profileId, searchedAt)")
    }
}
