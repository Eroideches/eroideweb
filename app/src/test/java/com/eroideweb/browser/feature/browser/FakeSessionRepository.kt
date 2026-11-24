package com.eroideweb.browser.feature.browser

import com.eroideweb.browser.core.session.SessionRepository
import com.eroideweb.browser.core.session.SessionSnapshot

/** Implementazione in memoria: i test non toccano DataStore né Android. */
class FakeSessionRepository(
    initial: SessionSnapshot? = null,
) : SessionRepository {

    private var snapshot: SessionSnapshot? = initial

    /** Ultimo stato scritto: `null` se non è mai stato salvato. */
    var lastSaved: SessionSnapshot? = null
        private set

    override suspend fun load(): SessionSnapshot? = snapshot

    override suspend fun save(snapshot: SessionSnapshot) {
        this.snapshot = snapshot
        lastSaved = snapshot
    }
}

/** Sessione che arriva "in ritardo": serve a simulare un link aperto durante l'avvio. */
class SlowSessionRepository(private val snapshot: SessionSnapshot) : SessionRepository {

    private val gate = kotlinx.coroutines.CompletableDeferred<Unit>()

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun load(): SessionSnapshot {
        gate.await()
        return snapshot
    }

    override suspend fun save(snapshot: SessionSnapshot) = Unit
}
