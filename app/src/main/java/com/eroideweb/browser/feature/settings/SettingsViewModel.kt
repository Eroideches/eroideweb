package com.eroideweb.browser.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eroideweb.browser.core.adblock.AdBlockProvider
import com.eroideweb.browser.core.browsingdata.BrowsingDataCleaner
import com.eroideweb.browser.core.browsingdata.ClearDataRequest
import com.eroideweb.browser.core.settings.AppTheme
import com.eroideweb.browser.core.settings.BrowserSettings
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.SettingsRepository
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.core.settings.clampTextZoom
import com.eroideweb.browser.core.settings.setAdBlockDisabledForSite
import com.eroideweb.browser.core.settings.setAdBlockEnabled
import com.eroideweb.browser.core.settings.setAllowThirdPartyCookies
import com.eroideweb.browser.core.settings.setRestoreTabs
import com.eroideweb.browser.core.settings.setAppTheme
import com.eroideweb.browser.core.settings.setDarkenWebPages
import com.eroideweb.browser.core.settings.setSearchEngine
import com.eroideweb.browser.core.settings.setTextZoom
import com.eroideweb.browser.core.settings.setToolbarPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pagine della schermata Impostazioni. */
enum class SettingsPage {
    ROOT,
    SEARCH_ENGINE,
    TEXT_SIZE,
    ADBLOCK_SITES,
    CLEAR_DATA,
    ABOUT,
    DIAGNOSTICS,
}

/** Cancellazione appena terminata: il browser chiude le schede se richiesto. */
data class ClearDataResult(
    val id: Long,
    val request: ClearDataRequest,
)

data class SettingsUiState(
    val settings: BrowserSettings = BrowserSettings(),
    val page: SettingsPage = SettingsPage.ROOT,
    /** Host bloccati dalle liste; `null` finché le liste non sono caricate. */
    val adBlockRuleCount: Int? = null,
    /** Nella finestra privata le modifiche valgono solo per quella finestra. */
    val isPrivate: Boolean = false,
    /** Scelte della pagina "Elimina dati di navigazione". */
    val clearRequest: ClearDataRequest = ClearDataRequest(),
    val isClearing: Boolean = false,
    /** Ultima cancellazione completata, `null` se non ce n'è una da notificare. */
    val clearResult: ClearDataResult? = null,
)

/**
 * Impostazioni del browser. Ogni modifica è ottimistica (la UI non aspetta il
 * disco) e poi salvata nel repository, da cui la legge anche il browser.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val adBlocker: AdBlockProvider,
    privateMode: Boolean = false,
    /** `null` nella finestra privata: lì i dati spariscono alla chiusura. */
    private val cleaner: BrowsingDataCleaner? = null,
) : ViewModel() {

    private var clearCounter = 0L

    private val _state = MutableStateFlow(SettingsUiState(isPrivate = privateMode))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
    }

    /** Chiamato all'apertura della schermata (di norma dalla pagina principale). */
    fun onOpen(page: SettingsPage = SettingsPage.ROOT) {
        _state.update {
            it.copy(page = page, adBlockRuleCount = adBlocker.engineOrNull()?.ruleCount)
        }
    }

    fun openPage(page: SettingsPage) {
        _state.update { it.copy(page = page) }
    }

    /** @return `false` se si è già sulla pagina principale (la schermata va chiusa). */
    fun navigateUp(): Boolean {
        if (_state.value.page == SettingsPage.ROOT) return false
        _state.update { it.copy(page = SettingsPage.ROOT) }
        return true
    }

    fun setToolbarPosition(position: ToolbarPosition) {
        updateLocal { it.copy(toolbarPosition = position) }
        viewModelScope.launch { repository.setToolbarPosition(position) }
    }

    fun setSearchEngine(engine: SearchEngine) {
        updateLocal { it.copy(searchEngine = engine) }
        viewModelScope.launch { repository.setSearchEngine(engine) }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        updateLocal { it.copy(adBlockEnabled = enabled) }
        viewModelScope.launch { repository.setAdBlockEnabled(enabled) }
    }

    fun setAppTheme(theme: AppTheme) {
        updateLocal { it.copy(appTheme = theme) }
        viewModelScope.launch { repository.setAppTheme(theme) }
    }

    fun setDarkenWebPages(enabled: Boolean) {
        updateLocal { it.copy(darkenWebPages = enabled) }
        viewModelScope.launch { repository.setDarkenWebPages(enabled) }
    }

    fun setTextZoom(percent: Int) {
        val value = clampTextZoom(percent)
        if (value == _state.value.settings.textZoom) return
        updateLocal { it.copy(textZoom = value) }
        viewModelScope.launch { repository.setTextZoom(value) }
    }

    fun setAllowThirdPartyCookies(allow: Boolean) {
        updateLocal { it.copy(allowThirdPartyCookies = allow) }
        viewModelScope.launch { repository.setAllowThirdPartyCookies(allow) }
    }

    fun setRestoreTabs(enabled: Boolean) {
        updateLocal { it.copy(restoreTabs = enabled) }
        viewModelScope.launch { repository.setRestoreTabs(enabled) }
    }

    // ------------------------------------------- elimina dati di navigazione

    val canClearData: Boolean get() = cleaner != null

    fun updateClearRequest(transform: (ClearDataRequest) -> ClearDataRequest) {
        _state.update { it.copy(clearRequest = transform(it.clearRequest)) }
    }

    fun clearData() {
        val worker = cleaner ?: return
        val request = _state.value.clearRequest
        if (request.isEmpty || _state.value.isClearing) return
        _state.update { it.copy(isClearing = true) }
        viewModelScope.launch {
            try {
                worker.clear(request)
            } finally {
                _state.update {
                    it.copy(isClearing = false, clearResult = ClearDataResult(++clearCounter, request))
                }
            }
        }
    }

    /** Il browser ha gestito l'esito (avviso, chiusura schede). */
    fun onClearResultHandled(id: Long) {
        _state.update { if (it.clearResult?.id == id) it.copy(clearResult = null) else it }
    }

    /** Riattiva il blocco su un sito escluso in precedenza. */
    fun removeAdBlockException(host: String) {
        updateLocal { it.copy(adBlockDisabledSites = it.adBlockDisabledSites - host) }
        viewModelScope.launch { repository.setAdBlockDisabledForSite(host, disabled = false) }
    }

    private fun updateLocal(transform: (BrowserSettings) -> BrowserSettings) {
        _state.update { it.copy(settings = transform(it.settings)) }
    }
}
