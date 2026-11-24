package com.eroideweb.browser.core.suggest

import com.eroideweb.browser.core.url.urlHost

/** Da dove arriva un suggerimento: cambia l'icona e un po' il peso. */
enum class SuggestionSource {
    FAVORITE,
    BOOKMARK,
    HISTORY,
}

/** Un candidato da classificare (preferito, segnalibro o pagina visitata). */
data class SuggestionCandidate(
    val url: String,
    val title: String?,
    val source: SuggestionSource,
    /** Visite in cronologia (0 per preferiti e segnalibri). */
    val visitCount: Int = 0,
)

data class Suggestion(
    val url: String,
    val title: String?,
    val source: SuggestionSource,
)

/**
 * Ordina i suggerimenti della barra degli indirizzi. Logica pura, testata.
 *
 * Vince chi inizia come ciò che si sta scrivendo ("rep" → repubblica.it prima
 * di una pagina che contiene "rep" a metà), poi preferiti e segnalibri, poi la
 * frequenza delle visite. Ogni URL compare una sola volta.
 */
object SuggestionRanker {

    fun rank(query: String, candidates: List<SuggestionCandidate>, limit: Int = 6): List<Suggestion> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val best = LinkedHashMap<String, Pair<Int, SuggestionCandidate>>()
        candidates.forEach { candidate ->
            val score = score(q, candidate)
            if (score <= 0) return@forEach
            val previous = best[candidate.url]
            if (previous == null || score > previous.first) best[candidate.url] = score to candidate
        }
        return best.values
            .sortedWith(compareByDescending<Pair<Int, SuggestionCandidate>> { it.first }.thenByDescending { it.second.visitCount })
            .take(limit)
            .map { (_, candidate) -> Suggestion(candidate.url, candidate.title, candidate.source) }
    }

    private fun score(q: String, candidate: SuggestionCandidate): Int {
        val host = urlHost(candidate.url)?.removePrefix("www.").orEmpty()
        val bareUrl = candidate.url.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
        val title = candidate.title?.lowercase().orEmpty()

        val match = when {
            host.startsWith(q) -> 100
            bareUrl.startsWith(q) -> 85
            title.startsWith(q) -> 70
            title.split(' ').any { it.startsWith(q) } -> 60
            host.contains(q) -> 50
            title.contains(q) -> 40
            bareUrl.contains(q) -> 30
            else -> return 0
        }
        val sourceBonus = when (candidate.source) {
            SuggestionSource.FAVORITE -> 15
            SuggestionSource.BOOKMARK -> 10
            SuggestionSource.HISTORY -> minOf(candidate.visitCount, 10)
        }
        return match + sourceBonus
    }
}
