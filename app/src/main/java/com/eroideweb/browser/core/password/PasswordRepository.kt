package com.eroideweb.browser.core.password

import android.database.sqlite.SQLiteConstraintException
import com.eroideweb.browser.core.data.CredentialDao
import com.eroideweb.browser.core.data.CredentialEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Credenziale come la vede l'interfaccia: la password resta cifrata finché non serve. */
data class SavedLogin(
    val id: Long,
    val origin: String,
    val username: String,
    val updatedAt: Long,
    val lastUsedAt: Long?,
)

/** Gestore password locale. In modalità privata non esiste ([NoOpPasswordRepository]). */
interface PasswordRepository {
    fun observeAll(): Flow<List<SavedLogin>>

    suspend fun loginsFor(origin: String): List<SavedLogin>

    /** Password in chiaro, `null` se non leggibile. */
    suspend fun password(id: Long): String?

    /** Cosa proporre dopo un accesso: salvare, aggiornare o niente. */
    suspend fun decide(origin: String, username: String, password: String, neverForSite: Boolean): SaveDecision

    /** Salva o aggiorna (stessa origine e utente = stessa voce). */
    suspend fun save(origin: String, username: String, password: String)

    /** @return `false` se esiste già un'altra voce con quell'utente sullo stesso sito. */
    suspend fun update(id: Long, username: String, password: String): Boolean

    suspend fun markUsed(id: Long)

    suspend fun delete(id: Long)

    suspend fun deleteAll()
}

class RoomPasswordRepository(
    private val dao: CredentialDao,
    private val cipher: SecretCipher,
    private val now: () -> Long = System::currentTimeMillis,
    /** Keystore e cifratura non vanno mai sul thread principale. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PasswordRepository {

    override fun observeAll(): Flow<List<SavedLogin>> =
        dao.observeAll().map { entries -> entries.map { it.toLogin() } }

    override suspend fun loginsFor(origin: String): List<SavedLogin> =
        withContext(io) { dao.byOrigin(origin).map { it.toLogin() } }

    override suspend fun password(id: Long): String? = withContext(io) {
        dao.byId(id)?.let { cipher.decrypt(it.secret) }
    }

    override suspend fun decide(
        origin: String,
        username: String,
        password: String,
        neverForSite: Boolean,
    ): SaveDecision = withContext(io) {
        val stored = dao.byOrigin(origin).map { StoredLogin(it.id, it.username, cipher.decrypt(it.secret)) }
        SavePolicy.decide(stored, username, password, neverForSite)
    }

    override suspend fun save(origin: String, username: String, password: String) = withContext(io) {
        val user = username.trim()
        val secret = cipher.encrypt(password)
        val time = now()
        val existing = dao.find(origin, user)
        if (existing != null) {
            dao.update(existing.id, user, secret, time)
        } else {
            dao.insert(
                CredentialEntry(origin = origin, username = user, secret = secret, createdAt = time, updatedAt = time),
            )
        }
        Unit
    }

    override suspend fun update(id: Long, username: String, password: String): Boolean = withContext(io) {
        try {
            dao.update(id, username.trim(), cipher.encrypt(password), now())
            true
        } catch (_: SQLiteConstraintException) {
            false
        }
    }

    override suspend fun markUsed(id: Long) = withContext(io) { dao.markUsed(id, now()) }

    override suspend fun delete(id: Long) = withContext(io) { dao.deleteById(id) }

    override suspend fun deleteAll() = withContext(io) { dao.deleteAll() }

    private fun CredentialEntry.toLogin() = SavedLogin(id, origin, username, updatedAt, lastUsedAt)
}

object NoOpPasswordRepository : PasswordRepository {
    override fun observeAll(): Flow<List<SavedLogin>> = flowOf(emptyList())

    override suspend fun loginsFor(origin: String): List<SavedLogin> = emptyList()

    override suspend fun password(id: Long): String? = null

    override suspend fun decide(origin: String, username: String, password: String, neverForSite: Boolean) =
        SaveDecision.Ignore

    override suspend fun save(origin: String, username: String, password: String) = Unit

    override suspend fun update(id: Long, username: String, password: String): Boolean = false

    override suspend fun markUsed(id: Long) = Unit

    override suspend fun delete(id: Long) = Unit

    override suspend fun deleteAll() = Unit
}
