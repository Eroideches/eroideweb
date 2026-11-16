package com.eroideweb.browser.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Il delegate di DataStore deve essere dichiarato a livello di file.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val dataStore = context.applicationContext.settingsDataStore

    override val settings: Flow<BrowserSettings> = dataStore.data
        // Se il file è corrotto o illeggibile meglio partire dai default che crashare.
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs ->
            BrowserSettings(
                toolbarPosition = ToolbarPosition.fromId(prefs[KEY_TOOLBAR_POSITION]),
                searchEngine = SearchEngine.fromId(prefs[KEY_SEARCH_ENGINE]),
                adBlockEnabled = prefs[KEY_ADBLOCK_ENABLED] ?: true,
                adBlockDisabledSites = prefs[KEY_ADBLOCK_DISABLED_SITES] ?: emptySet(),
            )
        }

    override suspend fun setToolbarPosition(position: ToolbarPosition) {
        dataStore.edit { prefs -> prefs[KEY_TOOLBAR_POSITION] = position.name }
    }

    override suspend fun setSearchEngine(engine: SearchEngine) {
        dataStore.edit { prefs -> prefs[KEY_SEARCH_ENGINE] = engine.id }
    }

    override suspend fun setAdBlockEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ADBLOCK_ENABLED] = enabled }
    }

    override suspend fun setAdBlockDisabledForSite(host: String, disabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_ADBLOCK_DISABLED_SITES] ?: emptySet()
            prefs[KEY_ADBLOCK_DISABLED_SITES] = if (disabled) current + host else current - host
        }
    }

    private companion object {
        val KEY_TOOLBAR_POSITION = stringPreferencesKey("toolbar_position")
        val KEY_SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val KEY_ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
        val KEY_ADBLOCK_DISABLED_SITES = stringSetPreferencesKey("adblock_disabled_sites")
    }
}
