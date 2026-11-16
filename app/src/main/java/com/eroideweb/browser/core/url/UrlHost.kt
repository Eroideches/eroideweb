package com.eroideweb.browser.core.url

import java.net.URI
import java.net.URISyntaxException

/**
 * Host di un URL, minuscolo, senza punto finale. `null` se non c'è
 * (about:, data:, javascript:) o se l'URL non è interpretabile.
 *
 * Non usa `android.net.Uri` per restare testabile su JVM.
 */
fun urlHost(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val trimmed = url.trim()
    return try {
        URI(trimmed).host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() }
    } catch (_: URISyntaxException) {
        manualHost(trimmed)
    }
}

private fun manualHost(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return null
    var rest = url.substring(schemeEnd + 3)
    rest = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    rest = rest.substringAfterLast('@')
    val host = rest.substringBefore(':')
    return host.lowercase().trimEnd('.').takeIf { it.isNotBlank() }
}

/** "https://www.example.com/a" -> "example.com" (dominio registrabile approssimato). */
fun siteHost(url: String?): String? = urlHost(url)
