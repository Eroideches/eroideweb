package com.eroideweb.browser.core.settings

import kotlinx.coroutines.flow.Flow

/**
 * Sorgente delle impostazioni utente. Interfaccia separata per poter testare
 * i ViewModel su JVM senza DataStore.
 *
 * Un solo punto di scrittura ([update]): ogni impostazione nuova è un campo di
 * [BrowserSettings] e non richiede metodi nuovi nelle implementazioni.
 */
interface SettingsRepository {
    val settings: Flow<BrowserSettings>

    /** Applica [transform] alle impostazioni correnti e salva il risultato. */
    suspend fun update(transform: (BrowserSettings) -> BrowserSettings)
}

suspend fun SettingsRepository.setToolbarPosition(position: ToolbarPosition) =
    update { it.copy(toolbarPosition = position) }

suspend fun SettingsRepository.setSearchEngine(engine: SearchEngine) =
    update { it.copy(searchEngine = engine) }

suspend fun SettingsRepository.setAdBlockEnabled(enabled: Boolean) =
    update { it.copy(adBlockEnabled = enabled) }

suspend fun SettingsRepository.setAdBlockDisabledForSite(host: String, disabled: Boolean) =
    update { current ->
        current.copy(
            adBlockDisabledSites = if (disabled) {
                current.adBlockDisabledSites + host
            } else {
                current.adBlockDisabledSites - host
            },
        )
    }

suspend fun SettingsRepository.setTranslateNoticeAccepted(accepted: Boolean) =
    update { it.copy(translateNoticeAccepted = accepted) }

suspend fun SettingsRepository.setAppTheme(theme: AppTheme) =
    update { it.copy(appTheme = theme) }

suspend fun SettingsRepository.setDarkenWebPages(enabled: Boolean) =
    update { it.copy(darkenWebPages = enabled) }

suspend fun SettingsRepository.setTextZoom(percent: Int) =
    update { it.copy(textZoom = clampTextZoom(percent)) }

suspend fun SettingsRepository.setReaderTextZoom(percent: Int) =
    update { it.copy(readerTextZoom = clampTextZoom(percent)) }

suspend fun SettingsRepository.setReaderTheme(theme: ReaderTheme) =
    update { it.copy(readerTheme = theme) }

suspend fun SettingsRepository.setReaderSerif(serif: Boolean) =
    update { it.copy(readerSerif = serif) }

/** Le dimensioni del testo restano tra 50% e 200%. */
fun clampTextZoom(percent: Int): Int =
    percent.coerceIn(BrowserSettings.MIN_TEXT_ZOOM, BrowserSettings.MAX_TEXT_ZOOM)

suspend fun SettingsRepository.setAllowThirdPartyCookies(allow: Boolean) =
    update { it.copy(allowThirdPartyCookies = allow) }

suspend fun SettingsRepository.setRestoreTabs(enabled: Boolean) =
    update { it.copy(restoreTabs = enabled) }
