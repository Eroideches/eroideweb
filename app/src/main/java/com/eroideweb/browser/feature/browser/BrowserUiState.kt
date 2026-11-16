package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.ToolbarPosition

/** Errore di caricamento mostrato al posto della pagina. */
data class PageError(
    val code: Int,
    val description: String,
    val failingUrl: String?,
)

data class BrowserUiState(
    /** Testo attualmente nella barra degli indirizzi. */
    val addressText: String = "",
    val isAddressFocused: Boolean = false,
    /** URL caricato; `null` significa "pagina iniziale". */
    val currentUrl: String? = null,
    val pageTitle: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = false,
    val error: PageError? = null,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.BOTTOM,
) {
    val isStartPage: Boolean get() = currentUrl == null
}
