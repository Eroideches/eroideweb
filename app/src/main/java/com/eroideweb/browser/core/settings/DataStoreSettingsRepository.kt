package com.eroideweb.browser.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
        appTheme = AppTheme.fromId(this[KEY_APP_THEME]),
        darkenWebPages = this[KEY_DARKEN_WEB_PAGES] ?: false,
        textZoom = clampTextZoom(this[KEY_TEXT_ZOOM] ?: BrowserSettings.DEFAULT_TEXT_ZOOM),
        readerTextZoom = clampTextZoom(this[KEY_READER_TEXT_ZOOM] ?: BrowserSettings.DEFAULT_TEXT_ZOOM),
        readerTheme = ReaderTheme.fromId(this[KEY_READER_THEME]),
        readerSerif = this[KEY_READER_SERIF] ?: true,
    )

    private fun MutablePreferences.write(settings: BrowserSettings) {
        this[KEY_TOOLBAR_POSITION] = settings.toolbarPosition.name
        this[KEY_SEARCH_ENGINE] = settings.searchEngine.id
        this[KEY_ADBLOCK_ENABLED] = settings.adBlockEnabled
        this[KEY_ADBLOCK_DISABLED_SITES] = settings.adBlockDisabledSites
        this[KEY_TRANSLATE_NOTICE] = settings.translateNoticeAccepted
        this[KEY_APP_THEME] = settings.appTheme.name
        this[KEY_DARKEN_WEB_PAGES] = settings.darkenWebPages
        this[KEY_TEXT_ZOOM] = settings.textZoom
        this[KEY_READER_TEXT_ZOOM] = settings.readerTextZoom
        this[KEY_READER_THEME] = settings.readerTheme.name
        this[KEY_READER_SERIF] = settings.readerSerif
    }

    private companion object {
        val KEY_TOOLBAR_POSITION = stringPreferencesKey("toolbar_position")
        val KEY_SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val KEY_ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
        val KEY_ADBLOCK_DISABLED_SITES = stringSetPreferencesKey("adblock_disabled_sites")
        val KEY_TRANSLATE_NOTICE = booleanPreferencesKey("translate_notice_accepted")
        val KEY_APP_THEME = stringPreferencesKey("app_theme")
        val KEY_DARKEN_WEB_PAGES = booleanPreferencesKey("darken_web_pages")
        val KEY_TEXT_ZOOM = intPreferencesKey("text_zoom")
        val KEY_READER_TEXT_ZOOM = intPreferencesKey("reader_text_zoom")
        val KEY_READER_THEME = stringPreferencesKey("reader_theme")
        val KEY_READER_SERIF = booleanPreferencesKey("reader_serif")
    }
}
