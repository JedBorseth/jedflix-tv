package com.jedflix.tv.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jedflix.tv.data.library.ProfileAvatars
import com.jedflix.tv.data.library.UserLibraryRepository

@Database(
    entities = [
        ProfileEntity::class,
        WatchProgressEntity::class,
        MyListEntity::class,
        SearchQueryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class JedflixDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun myListDao(): MyListDao
    abstract fun searchQueryDao(): SearchQueryDao

    companion object {
        fun create(context: Context): JedflixDatabase =
            Room.databaseBuilder(context.applicationContext, JedflixDatabase::class.java, "jedflix.db")
                .addCallback(SeedCallback())
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
