package com.eroideweb.browser.core.download

import java.net.URLDecoder
import java.util.Base64

/** Contenuto di un URL `data:` (immagini incorporate, file generati dalla pagina). */
class DataUrl(val mimeType: String, val bytes: ByteArray) {

    companion object {
        /** Oltre questa dimensione non si salva: la pagina dovrebbe usare un download vero. */
        const val MAX_BYTES = 25 * 1024 * 1024

        /** `data:[tipo][;base64],dati` → contenuto; `null` se non valido o troppo grande. */
        fun parse(url: String): DataUrl? {
            if (!url.startsWith("data:", ignoreCase = true)) return null
            val comma = url.indexOf(',')
            if (comma < 0) return null
            val header = url.substring(5, comma)
            val payload = url.substring(comma + 1)
            val parts = header.split(';')
            val mime = parts.first().trim().ifEmpty { "text/plain" }.lowercase()
            val isBase64 = parts.drop(1).any { it.trim().equals("base64", ignoreCase = true) }
            if (payload.length > MAX_BYTES * 4 / 3 + 4) return null
            val bytes = try {
                if (isBase64) {
                    // Decoder rigoroso: caratteri non validi = URL rifiutato, non ignorati.
                    val clean = (if (payload.contains('%')) URLDecoder.decode(payload.replace("+", "%2B"), Charsets.UTF_8.name()) else payload)
                        .filterNot { it.isWhitespace() }
                    Base64.getDecoder().decode(clean)
                } else {
                    URLDecoder.decode(payload.replace("+", "%2B"), Charsets.UTF_8.name()).toByteArray(Charsets.UTF_8)
                }
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (bytes.size > MAX_BYTES) return null
            return DataUrl(mime, bytes)
        }
    }
}
