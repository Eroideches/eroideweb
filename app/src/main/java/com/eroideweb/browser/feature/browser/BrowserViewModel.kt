package com.eroideweb.browser.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.SettingsRepository
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.core.url.UrlNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stato della schermata browser. Le operazioni imperative sulla WebView
 * (loadUrl, reload, back) passano dal WebViewController, non da qui:
 * il ViewModel resta testabile su JVM.
 */
class BrowserViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update {
                    it.copy(
                        searchEngine = settings.searchEngine,
                        toolbarPosition = settings.toolbarPosition,
                    )
                }
            }
        }
    }

    fun onAddressBarTextChange(text: String) {
        _state.update { it.copy(addressText = text) }
    }

    fun onAddressBarFocusChange(focused: Boolean) {
        _state.update { current ->
            current.copy(
                isAddressFocused = focused,
                // Uscendo dalla barra senza aver navigato, torna a mostrare l'URL reale.
                addressText = if (!focused) current.currentUrl ?: current.addressText else current.addressText,
            )
        }
    }

    /**
     * Normalizza il testo della barra e prepara il caricamento.
     * @return l'URL da passare alla WebView, oppure `null` se non c'è nulla da caricare.
     */
    fun submitAddressBar(): String? {
        val current = _state.value
        val url = UrlNormalizer.toLoadableUrl(current.addressText, current.searchEngine) ?: return null
        _state.update {
            it.copy(
                addressText = url,
                currentUrl = url,
                isLoading = true,
                progress = 0,
                error = null,
                isAddressFocused = false,
                canGoBack = false,
                canGoForward = false,
                isSecure = url.startsWith("https://"),
            )
        }
        return url
    }

    fun onPageStarted(url: String) {
        _state.update { current ->
            current.copy(
                currentUrl = url,
                addressText = if (current.isAddressFocused) current.addressText else url,
                isLoading = true,
                progress = if (current.progress == 0) 5 else current.progress,
                error = null,
                isSecure = url.startsWith("https://"),
            )
        }
    }

    fun onPageFinished(url: String, title: String?) {
        _state.update { current ->
            current.copy(
                currentUrl = url,
                addressText = if (current.isAddressFocused) current.addressText else url,
                pageTitle = title ?: current.pageTitle,
                isLoading = false,
                progress = 100,
                isSecure = url.startsWith("https://"),
            )
        }
    }

    fun onProgressChanged(progress: Int) {
        val clamped = progress.coerceIn(0, 100)
        _state.update { it.copy(progress = clamped, isLoading = clamped < 100) }
    }

    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean, url: String?) {
        _state.update { current ->
            val navigated = url != null && url != current.currentUrl
            current.copy(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                currentUrl = url ?: current.currentUrl,
                addressText = if (navigated && !current.isAddressFocused) url else current.addressText,
                isSecure = url?.startsWith("https://") ?: current.isSecure,
            )
        }
    }

    fun onPageError(error: PageError) {
        _state.update { it.copy(isLoading = false, error = error) }
    }

    fun clearError() {
        _state.update { it.copy(error = null, isLoading = true) }
    }

    fun setToolbarPosition(position: ToolbarPosition) {
        // Aggiornamento ottimistico: la UI non deve aspettare il disco.
        _state.update { it.copy(toolbarPosition = position) }
        viewModelScope.launch { settingsRepository.setToolbarPosition(position) }
    }

    fun setSearchEngine(engine: SearchEngine) {
        _state.update { it.copy(searchEngine = engine) }
        viewModelScope.launch { settingsRepository.setSearchEngine(engine) }
    }
}
