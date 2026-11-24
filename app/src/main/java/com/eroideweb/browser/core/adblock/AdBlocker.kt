package com.eroideweb.browser.core.adblock

import android.content.Context
import android.os.SystemClock
import com.eroideweb.browser.core.perf.PerfStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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
            val start = SystemClock.elapsedRealtime()
            val key = cacheKey()
            val cached = readCache(key)
            val rules = cached ?: parseAssets().also { writeCache(key, it) }
            PerfStats.adBlockLoadMs = SystemClock.elapsedRealtime() - start
            PerfStats.adBlockFromCache = cached != null
            PerfStats.adBlockHosts = rules.size
            AdBlockEngine(rules).also { engine = it }
        }
    }

    private fun parseAssets(): BlockRules {
        val blocked = HashSet<String>(1 shl 17)
        val allowed = HashSet<String>(1 shl 10)
        ASSETS.forEach { name ->
            context.assets.open("adblock/$name").bufferedReader().useLines { lines ->
                val rules = FilterListParser.parse(lines)
                blocked += rules.blocked
                allowed += rules.allowed
            }
        }
        return BlockRules(blocked, allowed)
    }

    /** Cambia a ogni installazione o aggiornamento dell'app (liste nuove). */
    private fun cacheKey(): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.lastUpdateTime}-${ASSETS.joinToString("+")}"
    } catch (_: Exception) {
        "sconosciuta"
    }

    private fun cacheFile() = File(context.noBackupFilesDir, CACHE_FILE)

    private fun readCache(key: String): BlockRules? = try {
        cacheFile().takeIf { it.exists() }?.bufferedReader()?.use { HostCache.read(it, key) }
    } catch (_: IOException) {
        null
    }

    /** Scrittura atomica: un file a metà non viene mai letto come buono. */
    private fun writeCache(key: String, rules: BlockRules) {
        val target = cacheFile()
        val temp = File(target.parentFile, "$CACHE_FILE.tmp")
        try {
            temp.bufferedWriter().use { HostCache.write(it, key, rules) }
            if (!temp.renameTo(target)) temp.delete()
        } catch (_: IOException) {
            temp.delete()
        }
    }

    /** Lista corrente, solo se già caricata. */
    override fun engineOrNull(): AdBlockEngine? = engine

    private companion object {
        val ASSETS = listOf("easylist.txt", "easyprivacy.txt", "easylist-italy.txt")
        const val CACHE_FILE = "adblock-hosts.txt"
    }
}
