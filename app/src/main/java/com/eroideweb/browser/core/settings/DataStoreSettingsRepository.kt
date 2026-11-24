package com.eroideweb.browser.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

// Il delegate di DataStore deve essere dichiarato a livello di file.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val dataStore = context.applicationContext.settingsDataStore

    override val settings: Flow<BrowserSettings> = dataStore.data
        // Se il file è corrotto o illeggibile meglio partire dai default che crashare.
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { prefs -> prefs.toSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings() = BrowserSettings(
        toolbarPosition = ToolbarPosition.fromId(this[KEY_TOOLBAR_POSITION]),
        searchEngine = SearchEngine.fromId(this[KEY_SEARCH_ENGINE]),
        adBlockEnabled = this[KEY_ADBLOCK_ENABLED] ?: true,
        adBlockDisabledSites = this[KEY_ADBLOCK_DISABLED_SITES] ?: emptySet(),
        translateNoticeAccepted = this[KEY_TRANSLATE_NOTICE] ?: false,
    )

    private fun MutablePreferences.write(settings: BrowserSettings) {
        this[KEY_TOOLBAR_POSITION] = settings.toolbarPosition.name
        this[KEY_SEARCH_ENGINE] = settings.searchEngine.id
        this[KEY_ADBLOCK_ENABLED] = settings.adBlockEnabled
        this[KEY_ADBLOCK_DISABLED_SITES] = settings.adBlockDisabledSites
        this[KEY_TRANSLATE_NOTICE] = settings.translateNoticeAccepted
    }

    private companion object {
        val KEY_TOOLBAR_POSITION = stringPreferencesKey("toolbar_position")
        val KEY_SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val KEY_ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
        val KEY_ADBLOCK_DISABLED_SITES = stringSetPreferencesKey("adblock_disabled_sites")
        val KEY_TRANSLATE_NOTICE = booleanPreferencesKey("translate_notice_accepted")
    }
}
