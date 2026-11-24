package com.eroideweb.browser.core.password

/** Cosa proporre dopo che un sito ha ricevuto utente e password. */
sealed interface SaveDecision {
    /** Niente da chiedere (già salvata, sito escluso, password vuota). */
    data object Ignore : SaveDecision

    /** Credenziali nuove: "Salvare la password?". */
    data object Save : SaveDecision

    /** Stesso utente, password diversa: "Aggiornare la password?". */
    data class Update(val id: Long) : SaveDecision
}

/** Una credenziale già salvata, con la password in chiaro solo per il confronto. */
data class StoredLogin(
    val id: Long,
    val username: String,
    val password: String?,
)

/** Regola di salvataggio. Logica pura, testata. */
object SavePolicy {

    /** Oltre questa lunghezza non è una password vera (testo incollato per errore). */
    private const val MAX_PASSWORD_LENGTH = 1024

    fun decide(
        stored: List<StoredLogin>,
        username: String,
        password: String,
        neverForSite: Boolean,
    ): SaveDecision {
        if (neverForSite || password.isEmpty() || password.length > MAX_PASSWORD_LENGTH) return SaveDecision.Ignore
        val sameUser = stored.firstOrNull { it.username == username.trim() } ?: return SaveDecision.Save
        return if (sameUser.password == password) SaveDecision.Ignore else SaveDecision.Update(sameUser.id)
    }
}
