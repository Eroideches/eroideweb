package com.eroideweb.browser.core.session

import java.net.URLDecoder
import java.net.URLEncoder

data class SessionTab(
    val id: String,
    val url: String?,
    val title: String?,
    /** La scheda chiede la versione desktop dei siti. */
    val desktopMode: Boolean = false,
)

data class SessionSnapshot(
    val tabs: List<SessionTab>,
    val activeTabId: String,
)

/**
 * Serializzazione della sessione in un formato testuale semplice.
 * Un campo per riga, valori codificati con URLEncoder (gestisce tab, newline,
 * percentuali e caratteri accentati). Niente JSON, niente dipendenze.
 *
 * - `v1`: `tab=id\turl\ttitolo`
 * - `v2`: come v1 più il flag desktop (`1`/`0`).
 *
 * Si scrive sempre l'ultima versione; si leggono tutte, così l'aggiornamento
 * dell'app non perde le schede aperte.
 */
object SessionCodec {

    private const val VERSION_1 = "v1"
    private const val VERSION_2 = "v2"
    private const val ACTIVE_PREFIX = "active="
    private const val TAB_PREFIX = "tab="
    private const val SEPARATOR = '\t'

    fun encode(snapshot: SessionSnapshot): String = buildString {
        appendLine(VERSION_2)
        appendLine(ACTIVE_PREFIX + enc(snapshot.activeTabId))
        snapshot.tabs.forEach { tab ->
            append(TAB_PREFIX)
            append(enc(tab.id))
            append(SEPARATOR)
            append(enc(tab.url.orEmpty()))
            append(SEPARATOR)
            append(enc(tab.title.orEmpty()))
            append(SEPARATOR)
            append(if (tab.desktopMode) "1" else "0")
            appendLine()
        }
    }

    fun decode(text: String): SessionSnapshot? {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return null
        val fieldCount = when (lines.first()) {
            VERSION_1 -> 3
            VERSION_2 -> 4
            else -> return null
        }

        var activeId: String? = null
        val tabs = mutableListOf<SessionTab>()

        lines.drop(1).forEach { line ->
            when {
                line.startsWith(ACTIVE_PREFIX) ->
                    activeId = dec(line.removePrefix(ACTIVE_PREFIX))

                line.startsWith(TAB_PREFIX) -> {
                    val parts = line.removePrefix(TAB_PREFIX).split(SEPARATOR)
                    if (parts.size == fieldCount) {
                        tabs += SessionTab(
                            id = dec(parts[0]),
                            url = dec(parts[1]).ifBlank { null },
                            title = dec(parts[2]).ifBlank { null },
                            desktopMode = fieldCount >= 4 && parts[3] == "1",
                        )
                    }
                }
            }
        }

        if (tabs.isEmpty()) return null
        val active = activeId?.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id
        return SessionSnapshot(tabs = tabs, activeTabId = active)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun dec(value: String): String = try {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        value
    }
}
