package com.eroideweb.browser.core.translate

import com.eroideweb.browser.core.url.urlHost
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/**
 * Traduzione delle pagine con Google Traduttore web: l'unico modo di tradurre
 * una pagina intera senza SDK di terze parti. L'indirizzo della pagina viene
 * inviato a Google solo quando l'utente tocca "Traduci".
 *
 * Logica pura: costruisce gli URL, non fa richieste.
 */
object TranslateUrl {

    private const val PROXY_SUFFIX = ".translate.goog"

    /** URL che apre [pageUrl] tradotta in [targetLanguage], oppure `null` se non traducibile. */
    fun forPage(pageUrl: String, targetLanguage: String): String? {
        if (!canTranslate(pageUrl)) return null
        val lang = targetLanguage.ifBlank { "it" }
        return "https://translate.google.com/translate?sl=auto&tl=$lang&hl=$lang&u=" +
            URLEncoder.encode(pageUrl, Charsets.UTF_8.name())
    }

    /** Solo pagine web vere, non già tradotte. */
    fun canTranslate(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val host = urlHost(url) ?: return false
        return !isTranslated(url) && host != "translate.google.com"
    }

    /** Pagina servita dal proxy di Google Traduttore (`*.translate.goog`). */
    fun isTranslated(url: String?): Boolean =
        urlHost(url)?.endsWith(PROXY_SUFFIX) == true

    /**
     * Ricostruisce l'indirizzo originale da una pagina `*.translate.goog`:
     * nell'host i `.` diventano `-` e i `-` diventano `--`; i parametri
     * `_x_tr_*` sono del traduttore e vanno tolti.
     */
    fun originalUrl(translatedUrl: String): String? {
        if (!isTranslated(translatedUrl)) return null
        val uri = try {
            URI(translatedUrl)
        } catch (_: URISyntaxException) {
            return null
        }
        val encodedHost = uri.host?.lowercase()?.removeSuffix(PROXY_SUFFIX) ?: return null
        if (encodedHost.isEmpty()) return null
        val host = encodedHost
            .replace("--", "\u0000")
            .replace('-', '.')
            .replace('\u0000', '-')
        val query = uri.rawQuery
            ?.split('&')
            ?.filterNot { it.startsWith("_x_tr_") }
            ?.joinToString("&")
            ?.takeIf { it.isNotEmpty() }
        return buildString {
            append("https://")
            append(host)
            if (uri.port != -1) append(':').append(uri.port)
            append(uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/")
            if (query != null) append('?').append(query)
            uri.rawFragment?.let { append('#').append(it) }
        }
    }

    /**
     * Codice lingua per Google Traduttore a partire da quello di sistema.
     * Java usa ancora alcuni codici storici (`in`, `iw`, `ji`) e il cinese
     * richiede la variante.
     */
    fun languageCode(language: String, country: String = ""): String {
        val lang = language.lowercase()
        return when (lang) {
            "" -> "it"
            "in" -> "id"
            "iw" -> "he"
            "ji" -> "yi"
            "zh" -> if (country.uppercase() in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
            else -> lang
        }
    }
}
