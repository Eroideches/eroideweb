package com.eroideweb.browser.core.adblock

import com.eroideweb.browser.core.url.urlHost

/**
 * Decide se una richiesta va bloccata. Pura e thread-safe: viene interrogata
 * dal thread di rete della WebView, non da quello principale.
 */
class AdBlockEngine(private val rules: BlockRules) {

    val ruleCount: Int get() = rules.size

    fun isBlocked(requestUrl: String, pageUrl: String?): Boolean {
        val host = urlHost(requestUrl) ?: return false
        // Eccezione esplicita della lista: passa sempre.
        if (matches(host, rules.allowed)) return false
        // La prima parte (stesso host della pagina) non si blocca mai: romperebbe il sito.
        if (host == urlHost(pageUrl)) return false
        return matches(host, rules.blocked)
    }

    /** `ads.example.com` corrisponde anche a `example.com` (dominio e sottodomini). */
    private fun matches(host: String, rules: Set<String>): Boolean {
        var candidate = host
        while (true) {
            if (candidate in rules) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }
}
