package com.jedflix.tv.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jedflix.tv.data.library.ProfileAvatars
import com.jedflix.tv.data.library.UserLibraryRepository

@Database(
    entities = [
        ProfileEntity::class,
        WatchProgressEntity::class,
        MyListEntity::class,
        SearchQueryEntity::class,
        PlaybackSessionEntity::class,
        TitleFeedbackEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class JedflixDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun myListDao(): MyListDao
    abstract fun searchQueryDao(): SearchQueryDao

    abstract fun playbackSessionDao(): PlaybackSessionDao
    abstract fun titleFeedbackDao(): TitleFeedbackDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_progress ADD COLUMN watchedMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playback_sessions (
                        profileId INTEGER NOT NULL, sessionId TEXT NOT NULL,
                        watchedMs INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(profileId, sessionId),
                        FOREIGN KEY(profileId) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_sessions_profileId ON playback_sessions(profileId)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS title_feedback (
                        profileId INTEGER NOT NULL, mediaType TEXT NOT NULL, tmdbId INTEGER NOT NULL,
                        value TEXT NOT NULL, updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(profileId, mediaType, tmdbId),
                        FOREIGN KEY(profileId) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_title_feedback_profileId ON title_feedback(profileId)")
            }
        }

        fun create(context: Context): JedflixDatabase =
            Room.databaseBuilder(context.applicationContext, JedflixDatabase::class.java, "jedflix.db")
                .addCallback(SeedCallback())
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

private class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val values = ContentValues().apply {
            put("name", UserLibraryRepository.DEFAULT_PROFILE_NAME)
            put("avatarKey", ProfileAvatars.defaultKey)
            put("createdAt", System.currentTimeMillis())
        }
        db.insert("profiles", SQLiteDatabase.CONFLICT_ABORT, values)
    }
}
