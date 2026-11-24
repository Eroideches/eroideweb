package com.eroideweb.browser.core.adblock

import java.io.BufferedReader
import java.io.Writer

/**
 * Cache compatta delle liste di filtri: solo gli host già estratti, uno per
 * riga. Rileggerla è ~7 volte più veloce del parsing delle 134.000 righe ABP,
 * quindi il blocco è attivo prima dopo ogni avvio. Logica pura, testata.
 *
 * Formato:
 * ```
 * eroideweb-adblock v1 <chiave>
 * host bloccato
 * …
 * @@
 * host consentito
 * …
 * ```
 * La chiave cambia a ogni aggiornamento dell'app (liste nuove): cache scartata.
 */
object HostCache {

    private const val HEADER = "eroideweb-adblock v1 "
    private const val ALLOWED_MARKER = "@@"

    fun write(writer: Writer, key: String, rules: BlockRules) {
        writer.write(HEADER)
        writer.write(key)
        writer.write("\n")
        rules.blocked.forEach { writer.write(it); writer.write("\n") }
        writer.write(ALLOWED_MARKER)
        writer.write("\n")
        rules.allowed.forEach { writer.write(it); writer.write("\n") }
    }

    /** `null` se la cache è di un'altra versione, rovinata o vuota. */
    fun read(reader: BufferedReader, key: String): BlockRules? {
        if (reader.readLine() != HEADER + key) return null
        val blocked = HashSet<String>(1 shl 17)
        val allowed = HashSet<String>(1 shl 9)
        var inAllowed = false
        var sawMarker = false
        while (true) {
            val line = reader.readLine() ?: break
            when {
                line == ALLOWED_MARKER -> {
                    inAllowed = true
                    sawMarker = true
                }
                line.isEmpty() -> Unit
                inAllowed -> allowed += line
                else -> blocked += line
            }
        }
        // Senza il separatore il file è troncato (scrittura interrotta): meglio rifare.
        if (!sawMarker || blocked.isEmpty()) return null
        return BlockRules(blocked, allowed)
    }
}
