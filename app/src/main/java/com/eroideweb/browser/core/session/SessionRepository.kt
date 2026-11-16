package com.eroideweb.browser.core.session

import kotlinx.coroutines.flow.Flow

/** Persistenza della sessione di navigazione (elenco schede + scheda attiva). */
interface SessionRepository {
    suspend fun load(): SessionSnapshot?

    suspend fun save(snapshot: SessionSnapshot)
}

/** Finestra privata: la sessione non viene mai salvata né ripristinata. */
object NoOpSessionRepository : SessionRepository {
    override suspend fun load(): SessionSnapshot? = null

    override suspend fun save(snapshot: SessionSnapshot) = Unit
}
