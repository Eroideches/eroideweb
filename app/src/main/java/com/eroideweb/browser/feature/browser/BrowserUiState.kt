package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.reader.ReaderArticle
import com.eroideweb.browser.core.settings.SearchEngine
import com.eroideweb.browser.core.settings.ToolbarPosition
import com.eroideweb.browser.core.translate.TranslateUrl
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
    /** La scheda chiede ai siti la versione desktop. */
    val desktopMode: Boolean = false,
    /** La pagina sembra un articolo: si può aprire in modalità lettura. */
    val readerAvailable: Boolean = false,
)

/**
 * Schermate e pannelli sopra il browser. Uno solo alla volta: il tasto
 * Indietro chiude quello visibile e l'ordine resta prevedibile.
 */
enum class BrowserOverlay {
    NONE,
    MENU,
    TABS,
    LIBRARY,
    SETTINGS,
    READER,
}

/** Barra "Trova nella pagina" (al posto della barra degli indirizzi). */
data class FindInPageState(
    val query: String = "",
    /** Risultato evidenziato, da 1; 0 se non ce ne sono. */
    val activeMatch: Int = 0,
    val matchCount: Int = 0,
)

data class BrowserUiState(
    val tabs: List<TabUi>,
    val activeTabId: String,
    /** Testo attualmente nella barra degli indirizzi (solo scheda attiva). */
    val addressText: String = "",
    val isAddressFocused: Boolean = false,
    val overlay: BrowserOverlay = BrowserOverlay.NONE,
    /** `null` = barra "Trova nella pagina" chiusa. */
    val findInPage: FindInPageState? = null,
    /** Articolo mostrato in modalità lettura (con overlay READER). */
    val readerArticle: ReaderArticle? = null,
    /** URL presenti tra i segnalibri: serve per lo stato del pulsante. */
    val bookmarkedUrls: Set<String> = emptySet(),
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.BOTTOM,
    val adBlockEnabled: Boolean = true,
    val adBlockDisabledSites: Set<String> = emptySet(),
    /** L'avviso sulla traduzione tramite Google è già stato accettato. */
    val translateNoticeAccepted: Boolean = false,
    /** True nella finestra privata: niente cronologia, segnalibri o sessione. */
    val isPrivate: Boolean = false,
) {
    val activeTab: TabUi
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    val isStartPage: Boolean get() = activeTab.url == null

    val isTabSwitcherVisible: Boolean get() = overlay == BrowserOverlay.TABS

    val isLibraryVisible: Boolean get() = overlay == BrowserOverlay.LIBRARY

    val isMenuVisible: Boolean get() = overlay == BrowserOverlay.MENU

    val isSettingsVisible: Boolean get() = overlay == BrowserOverlay.SETTINGS

    val isReaderVisible: Boolean get() = overlay == BrowserOverlay.READER && readerArticle != null

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

    /** La pagina attiva è una pagina web vera (azioni di condivisione, ricerca, ecc.). */
    val hasWebPage: Boolean
        get() = activeTab.url?.let { it.startsWith("http://") || it.startsWith("https://") } == true

    val canTranslate: Boolean
        get() = activeTab.url?.let(TranslateUrl::canTranslate) == true

    /** La pagina attiva è già una traduzione: l'azione diventa "Mostra originale". */
    val isActiveTranslated: Boolean
        get() = TranslateUrl.isTranslated(activeTab.url)
}
