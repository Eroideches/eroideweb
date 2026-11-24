package com.eroideweb.browser.core.settings

import java.net.URLEncoder

/** Dove sta la barra degli indirizzi. */
enum class ToolbarPosition {
    TOP,
    BOTTOM;

    companion object {
        fun fromId(id: String?): ToolbarPosition =
            entries.firstOrNull { it.name == id } ?: BOTTOM
    }
}

/** Tema dell'interfaccia (e preferenza chiaro/scuro comunicata ai siti). */
enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK;

    /** Tema effettivo, dato quello del sistema. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.name == id } ?: SYSTEM
    }
}

/** Colori della modalità lettura. AUTO segue il tema dell'app. */
enum class ReaderTheme {
    AUTO,
    LIGHT,
    SEPIA,
    DARK;

    companion object {
        fun fromId(id: String?): ReaderTheme = entries.firstOrNull { it.name == id } ?: AUTO
    }
}

/** Motori di ricerca supportati. Google è il default; si sceglie nelle Impostazioni. */
enum class SearchEngine(
    val id: String,
    val displayName: String,
    private val queryTemplate: String,
) {
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BING("bing", "Bing", "https://www.bing.com/search?q=%s"),
    STARTPAGE("startpage", "Startpage", "https://www.startpage.com/sp/search?query=%s"),
    BRAVE("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
    ;

    /** URL di ricerca per [query], già codificata per l'uso in query string. */
    fun searchUrl(query: String): String = queryTemplate.format(encode(query))

    companion object {
        fun fromId(id: String?): SearchEngine =
            entries.firstOrNull { it.id == id } ?: GOOGLE

        private fun encode(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}

data class BrowserSettings(
    val toolbarPosition: ToolbarPosition = ToolbarPosition.BOTTOM,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    /** Blocco tracker/pubblicità attivo per impostazione predefinita. */
    val adBlockEnabled: Boolean = true,
    /** Siti su cui l'utente ha disattivato il blocco. */
    val adBlockDisabledSites: Set<String> = emptySet(),
    /** L'utente ha letto che la traduzione passa da Google Traduttore. */
    val translateNoticeAccepted: Boolean = false,
    val appTheme: AppTheme = AppTheme.SYSTEM,
    /** Tema scuro forzato su tutti i siti, anche quelli che non lo prevedono. */
    val darkenWebPages: Boolean = false,
    /** Dimensione del testo delle pagine, in percentuale. */
    val textZoom: Int = DEFAULT_TEXT_ZOOM,
    /** Dimensione del testo in modalità lettura, in percentuale. */
    val readerTextZoom: Int = DEFAULT_TEXT_ZOOM,
    val readerTheme: ReaderTheme = ReaderTheme.AUTO,
    /** Carattere con grazie (più leggibile su testi lunghi). */
    val readerSerif: Boolean = true,
    /** Cookie di terze parti: bloccati di default (tracciamento tra siti). */
    val allowThirdPartyCookies: Boolean = false,
    /** Riapre le schede della volta precedente all'avvio. */
    val restoreTabs: Boolean = true,
) {
    companion object {
        const val DEFAULT_TEXT_ZOOM = 100
        const val MIN_TEXT_ZOOM = 50
        const val MAX_TEXT_ZOOM = 200
    }
}
