package com.eroideweb.browser.core.url

import com.eroideweb.browser.core.settings.SearchEngine

/**
 * Decide cosa fare con quello che l'utente scrive nella barra degli indirizzi:
 * navigare o cercare. Logica pura (nessuna dipendenza Android) per essere
 * testabile su JVM.
 */
object UrlNormalizer {

    private val IPv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}(:\d{1,5})?(/.*)?$""")

    /** Schema host valido: etichette alfanumeriche + TLD alfabetico. */
    private val HostLike = Regex(
        pattern = """^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+(:\d{1,5})?(/.*)?$""",
    )

    /**
     * Restituisce l'URL da caricare, oppure `null` se l'input è vuoto.
     * Tutto ciò che non sembra un indirizzo viene trasformato in una ricerca
     * sul motore scelto.
     */
    fun toLoadableUrl(rawInput: String, searchEngine: SearchEngine): String? {
        val input = rawInput.trim()
        if (input.isEmpty()) return null

        // Schema esplicito: passano solo i protocolli che il WebView sa gestire.
        val schemeSeparator = input.indexOf("://")
        if (schemeSeparator > 0) {
            val scheme = input.substring(0, schemeSeparator).lowercase()
            return if (scheme == "http" || scheme == "https") {
                input
            } else {
                searchEngine.searchUrl(input)
            }
        }

        if (input.startsWith("about:") || input.startsWith("data:")) return input

        if (input.any { it.isWhitespace() }) return searchEngine.searchUrl(input)

        // "localhost" e indirizzi IP sono quasi sempre servizi locali in http.
        if (input.startsWith("localhost") || IPv4.matches(input)) return "http://$input"

        if (isHostLike(input)) return "https://$input"

        return searchEngine.searchUrl(input)
    }

    /** Un host plausibile contiene almeno un punto e un TLD alfabetico. */
    private fun isHostLike(input: String): Boolean {
        if (input.contains('@')) return false
        val hostPart = input.substringBefore('/').substringBefore('?').substringBefore('#')
        if (!hostPart.contains('.')) return false
        val tld = hostPart.substringAfterLast('.')
        if (tld.isEmpty() || tld.length < 2 || tld.length > 24) return false
        if (tld.any { !it.isLetter() }) return false
        return HostLike.matches(input)
    }
}
