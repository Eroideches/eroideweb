package com.eroideweb.browser.core.password

import java.net.URI
import java.net.URISyntaxException

/**
 * Origine a cui appartiene una password: schema + host (+ porta se non
 * standard). È la chiave con cui le credenziali vengono salvate e proposte,
 * e arriva sempre dal browser (mai dalla pagina). Logica pura, testata.
 *
 * Solo siti sicuri: `https`, oppure `http` su questo dispositivo (localhost).
 * Una password non viene mai proposta a una pagina in chiaro su Internet.
 */
object CredentialOrigin {

    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "::1")

    fun from(value: String?): String? {
        if (value.isNullOrBlank() || value == "null") return null
        val uri = try {
            URI(value.trim())
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() } ?: return null
        val secure = scheme == "https" || (scheme == "http" && host in LOCAL_HOSTS)
        if (!secure) return null
        val defaultPort = if (scheme == "https") 443 else 80
        val port = uri.port.takeIf { it != -1 && it != defaultPort }
        return buildString {
            append(scheme).append("://").append(host)
            if (port != null) append(':').append(port)
        }
    }

    /** Nome da mostrare: "accounts.example.com" → "accounts.example.com", senza "www.". */
    fun displayName(origin: String): String =
        origin.substringAfter("://").removePrefix("www.")
}
