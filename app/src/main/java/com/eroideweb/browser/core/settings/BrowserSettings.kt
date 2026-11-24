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
)
