package com.jedflix.tv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jedflix_settings",
)

data class CachedRelease(
    val lastCheckAtMs: Long = 0L,
    val tag: String = "",
    val notes: String = "",
    val apkUrl: String = "",
    val apkName: String = "",
    val apkSize: Long = 0L,
    val dismissedTag: String = "",
)

/**
 * Device-local preferences. The Real-Debrid key stays on this client and is not synced.
 */
class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val realDebridApiKey: Flow<String> = dataStore.data.map { prefs ->
        prefs[REAL_DEBRID_API_KEY].orEmpty()
    }

    suspend fun setRealDebridApiKey(value: String) {
        dataStore.edit { prefs ->
            val trimmed = value.trim()
            if (trimmed.isEmpty()) {
                prefs.remove(REAL_DEBRID_API_KEY)
            } else {
                prefs[REAL_DEBRID_API_KEY] = trimmed
            }
        }
    }

    suspend fun loadCachedRelease(): CachedRelease {
        val prefs = dataStore.data.first()
        return CachedRelease(
            lastCheckAtMs = prefs[UPDATE_LAST_CHECK_AT] ?: 0L,
            tag = prefs[UPDATE_TAG].orEmpty(),
            notes = prefs[UPDATE_NOTES].orEmpty(),
            apkUrl = prefs[UPDATE_APK_URL].orEmpty(),
            apkName = prefs[UPDATE_APK_NAME].orEmpty(),
            apkSize = prefs[UPDATE_APK_SIZE] ?: 0L,
            dismissedTag = prefs[UPDATE_DISMISSED_TAG].orEmpty(),
        )
    }

    suspend fun setLastUpdateCheckAt(epochMs: Long) {
        dataStore.edit { prefs ->
            prefs[UPDATE_LAST_CHECK_AT] = epochMs
        }
    }

    suspend fun setCachedRelease(
        tag: String,
        notes: String,
        apkUrl: String,
        apkName: String,
        apkSize: Long,
    ) {
        dataStore.edit { prefs ->
            prefs[UPDATE_TAG] = tag
            prefs[UPDATE_NOTES] = notes
            prefs[UPDATE_APK_URL] = apkUrl
            prefs[UPDATE_APK_NAME] = apkName
            prefs[UPDATE_APK_SIZE] = apkSize
        }
    }

    suspend fun clearCachedRelease() {
        dataStore.edit { prefs ->
            prefs.remove(UPDATE_TAG)
            prefs.remove(UPDATE_NOTES)
            prefs.remove(UPDATE_APK_URL)
            prefs.remove(UPDATE_APK_NAME)
            prefs.remove(UPDATE_APK_SIZE)
        }
    }

    suspend fun setDismissedUpdateTag(tag: String) {
        dataStore.edit { prefs ->
            prefs[UPDATE_DISMISSED_TAG] = tag
        }
    }

    private companion object {
        val REAL_DEBRID_API_KEY = stringPreferencesKey("real_debrid_api_key")
        val UPDATE_LAST_CHECK_AT = longPreferencesKey("update_last_check_at")
        val UPDATE_TAG = stringPreferencesKey("update_tag")
        val UPDATE_NOTES = stringPreferencesKey("update_notes")
        val UPDATE_APK_URL = stringPreferencesKey("update_apk_url")
        val UPDATE_APK_NAME = stringPreferencesKey("update_apk_name")
        val UPDATE_APK_SIZE = longPreferencesKey("update_apk_size")
        val UPDATE_DISMISSED_TAG = stringPreferencesKey("update_dismissed_tag")
    }
}
