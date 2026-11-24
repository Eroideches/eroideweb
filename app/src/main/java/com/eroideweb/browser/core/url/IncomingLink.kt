package com.eroideweb.browser.core.url

/**
 * Cosa aprire quando un'altra app ci passa qualcosa (link, condivisione,
 * ricerca, testo selezionato). Logica pura: le activity estraggono i campi
 * dall'Intent e chiamano [from].
 */
sealed interface IncomingLink {
    /** Indirizzo web da aprire così com'è. */
    data class Url(val url: String) : IncomingLink

    /** Testo da cercare (o da normalizzare come nella barra). */
    data class Text(val text: String) : IncomingLink

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_WEB_SEARCH = "android.intent.action.WEB_SEARCH"
        const val ACTION_PROCESS_TEXT = "android.intent.action.PROCESS_TEXT"

        private val URL_IN_TEXT = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

        /** Una lunghezza oltre cui il testo non è più una ricerca sensata. */
        private const val MAX_TEXT = 2000

        fun from(
            action: String?,
            data: String?,
            sharedText: String? = null,
            searchQuery: String? = null,
            processText: String? = null,
        ): IncomingLink? = when (action) {
            ACTION_VIEW -> data?.trim()?.takeIf { isWebUrl(it) }?.let(::Url)

            // Le app condividono spesso "Titolo https://…": se c'è un link, quello.
            ACTION_SEND -> sharedText?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
                URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ')', ']', ';')?.let(::Url)
                    ?: Text(text.take(MAX_TEXT))
            }

            ACTION_WEB_SEARCH -> searchQuery?.trim()?.takeIf { it.isNotEmpty() }?.let { Text(it.take(MAX_TEXT)) }

            ACTION_PROCESS_TEXT -> processText?.trim()?.takeIf { it.isNotEmpty() }?.let { Text(it.take(MAX_TEXT)) }

            else -> null
        }

        private fun isWebUrl(value: String): Boolean {
            val lower = value.lowercase()
            return (lower.startsWith("http://") || lower.startsWith("https://")) && urlHost(value) != null
        }
    }
}
