package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.core.url.urlHost

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
    val isLibraryVisible: Boolean = false,
    /** URL presenti tra i segnalibri: serve per lo stato del pulsante. */
    val bookmarkedUrls: Set<String> = emptySet(),
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.BOTTOM,
    val adBlockEnabled: Boolean = true,
    val adBlockDisabledSites: Set<String> = emptySet(),
    /** True nella finestra privata: niente cronologia, segnalibri o sessione. */
    val isPrivate: Boolean = false,
) {
    val activeTab: TabUi
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    val isStartPage: Boolean get() = activeTab.url == null

    val isActiveBookmarked: Boolean
        get() = activeTab.url?.let { it in bookmarkedUrls } == true

    /** Sito su cui il blocco può essere attivato/disattivato (una pagina vera). */
    val canToggleAdBlockForSite: Boolean
        get() = urlHost(activeTab.url) != null

    val isAdBlockActiveForSite: Boolean
        get() {
            val host = urlHost(activeTab.url) ?: return adBlockEnabled
            return adBlockEnabled && host !in adBlockDisabledSites
        }
}
