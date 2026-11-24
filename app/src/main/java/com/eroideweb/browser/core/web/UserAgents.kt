package com.eroideweb.browser.core.web

/**
 * User-Agent inviati ai siti. Logica pura, testata su JVM.
 *
 * Partiamo sempre dallo UA di default della WebView per leggere la versione
 * reale di Chromium, poi presentiamo il browser come Chrome:
 * - niente token `; wv` né `Version/4.0`: alcuni siti (es. l'accesso Google)
 *   rifiutano le "app con WebView" e mostrano "browser non sicuro";
 * - modello del dispositivo e versione minore ridotti come fa Chrome dal 2023
 *   (`Android 10; K`, `Chrome/<major>.0.0.0`): meno dati per il fingerprinting.
 */
object UserAgents {

    private val CHROME_MAJOR = Regex("""Chrome/(\d+)""")

    /** Versione di ripiego se lo UA di default non contiene Chrome (non dovrebbe succedere). */
    private const val FALLBACK_MAJOR = 140

    /** UA mobile pulito, equivalente a Chrome per Android. */
    fun mobile(defaultUserAgent: String): String {
        val major = chromeMajor(defaultUserAgent) ?: return defaultUserAgent
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36"
    }

    /** UA desktop: Chrome su Linux, la piattaforma desktop più neutra. */
    fun desktop(defaultUserAgent: String): String {
        val major = chromeMajor(defaultUserAgent) ?: FALLBACK_MAJOR
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    }

    fun chromeMajor(userAgent: String): Int? =
        CHROME_MAJOR.find(userAgent)?.groupValues?.get(1)?.toIntOrNull()
}
