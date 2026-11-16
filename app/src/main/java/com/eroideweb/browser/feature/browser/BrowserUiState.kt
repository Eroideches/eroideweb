package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.ToolbarPosition

/** Categoria dell'errore: l'utente vede un messaggio semplice, non un codice. */
enum class PageErrorKind {
    NO_CONNECTION,
    INSECURE,
    GENERIC,
}

data class PageError(
    val kind: PageErrorKind,
    /** Codice WebView (ERROR_*), utile solo per debug/log. */
    val code: Int = 0,
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
