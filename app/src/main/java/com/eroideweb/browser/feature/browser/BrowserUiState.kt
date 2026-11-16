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

/** Stato di una singola scheda. La WebView vive nel layer UI, non qui. */
data class TabUi(
    val id: String,
    /** `null` = nuova scheda sulla pagina iniziale. */
    val url: String? = null,
    val title: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = false,
    val error: PageError? = null,
)

data class BrowserUiState(
    val tabs: List<TabUi>,
    val activeTabId: String,
    /** Testo attualmente nella barra degli indirizzi (solo scheda attiva). */
    val addressText: String = "",
    val isAddressFocused: Boolean = false,
    val isTabSwitcherVisible: Boolean = false,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.BOTTOM,
) {
    val activeTab: TabUi
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    val isStartPage: Boolean get() = activeTab.url == null
}
