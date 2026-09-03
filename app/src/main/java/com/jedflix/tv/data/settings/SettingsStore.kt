package com.jedflix.tv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jedflix_settings",
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

    private companion object {
        val REAL_DEBRID_API_KEY = stringPreferencesKey("real_debrid_api_key")
    }
}
