package com.eroideweb.browser.core.adblock

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Carica le liste dai `assets` una volta sola, fuori dal thread principale.
 * Nessun aggiornamento di rete automatico: le liste cambiano solo con una
 * nuova versione dell'app (regola di privacy del progetto).
 */
/** Sorgente dell'engine: interfaccia separata per i test su JVM. */
interface AdBlockProvider {
    fun engineOrNull(): AdBlockEngine?
}

class AdBlocker(private val context: Context) : AdBlockProvider {

    @Volatile
    var engine: AdBlockEngine? = null
        private set

    private val mutex = Mutex()

    suspend fun load(): AdBlockEngine = withContext(Dispatchers.IO) {
        engine?.let { return@withContext it }
        mutex.withLock {
            engine?.let { return@withLock it }

            val blocked = HashSet<String>(1 shl 15)
            val allowed = HashSet<String>(1 shl 10)
            ASSETS.forEach { name ->
                context.assets.open("adblock/$name").bufferedReader().useLines { lines ->
                    val rules = FilterListParser.parse(lines)
                    blocked += rules.blocked
                    allowed += rules.allowed
                }
            }
            AdBlockEngine(BlockRules(blocked, allowed)).also { engine = it }
        }
    }

    /** Lista corrente, solo se già caricata. */
    override fun engineOrNull(): AdBlockEngine? = engine

    private companion object {
        val ASSETS = listOf("easylist.txt", "easyprivacy.txt", "easylist-italy.txt")
    }
}
