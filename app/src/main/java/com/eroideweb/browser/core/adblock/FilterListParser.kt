package com.eroideweb.browser.core.adblock

/**
 * Insiemi di host ricavati dalle liste di filtri (EasyList, EasyPrivacy,
 * EasyList Italy). Sono solo regole di rete per host: le regole cosmetiche
 * (`##`) e quelle basate sul percorso non vengono applicate.
 */
class BlockRules(
    val blocked: Set<String>,
    val allowed: Set<String>,
) {
    val size: Int get() = blocked.size
}

object FilterListParser {

    /** `||host^` oppure `@@||host^`, con eventuali opzioni dopo `^`. */
    private val HOST_RULE = Regex("""^(@@)?\|\|([a-zA-Z0-9.-]+)\^""")

    /**
     * Estrae gli host dalle righe ABP. Scelte conservative:
     * - regole con `$domain=` ignorate (applicarle globalmente bloccherebbe CDN condivisi);
     * - regole `$document` ignorate (bloccherebbero interi siti);
     * - eccezioni `@@` rispettate;
     * - host senza punto o malformati scartati.
     */
    fun parse(lines: Sequence<String>): BlockRules {
        val blocked = HashSet<String>(1 shl 15)
        val allowed = HashSet<String>(1 shl 10)

        lines.forEach { raw ->
            val rule = raw.trim()
            if (!rule.startsWith("||") && !rule.startsWith("@@||")) return@forEach

            val match = HOST_RULE.find(rule) ?: return@forEach
            val host = match.groupValues[2].lowercase()
            if (!isValidHost(host)) return@forEach

            val options = rule.substringAfter("^", "")
            if (options.startsWith("$")) {
                if (options.contains("domain=")) return@forEach
                if (options.contains("document")) return@forEach
            }

            if (match.groupValues[1].isNotEmpty()) {
                allowed += host
            } else {
                blocked += host
            }
        }

        return BlockRules(blocked = blocked, allowed = allowed)
    }

    private fun isValidHost(host: String): Boolean =
        host.contains('.') &&
            !host.startsWith('.') &&
            !host.endsWith('.') &&
            !host.contains("..")
}
