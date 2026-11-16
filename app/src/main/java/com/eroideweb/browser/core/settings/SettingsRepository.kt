package com.eroideweb.browser.core.settings

import kotlinx.coroutines.flow.Flow

/**
 * Sorgente delle impostazioni utente. Interfaccia separata per poter testare
 * i ViewModel su JVM senza DataStore.
 */
interface SettingsRepository {
    val settings: Flow<BrowserSettings>

    suspend fun setToolbarPosition(position: ToolbarPosition)

    suspend fun setSearchEngine(engine: SearchEngine)

    suspend fun setAdBlockEnabled(enabled: Boolean)

    suspend fun setAdBlockDisabledForSite(host: String, disabled: Boolean)
}
