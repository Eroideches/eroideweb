package com.eroideweb.browser.core.favicon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Icone dei siti (favicon), ricevute dalla WebView mentre si naviga: nessuna
 * richiesta di rete in più. Restano in memoria e, fuori dalla finestra
 * privata, anche su disco ([directory] `null` = solo memoria).
 *
 * Le icone sono ridotte a [SIZE] px: bastano per schede, preferiti e
 * suggerimenti e pesano pochi KB.
 */
class FaviconStore(private val directory: File?) {

    private val memory = LruCache<String, ImageBitmap>(MEMORY_ENTRIES)
    private val missing = mutableSetOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _version = MutableStateFlow(0)

    /** Cresce a ogni icona nuova: la UI lo osserva per ridisegnare. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Icona già in memoria, senza attese. */
    fun cached(host: String): ImageBitmap? = memory.get(host)

    /** Icona dalla memoria o dal disco; `null` se non l'abbiamo mai vista. */
    suspend fun load(host: String): ImageBitmap? {
        memory.get(host)?.let { return it }
        val dir = directory ?: return null
        synchronized(missing) { if (host in missing) return null }
        return withContext(Dispatchers.IO) {
            val file = fileFor(dir, host)
            val bitmap = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
            if (bitmap == null) {
                synchronized(missing) { missing += host }
                null
            } else {
                bitmap.asImageBitmap().also { memory.put(host, it) }
            }
        }
    }

    /** Nuova icona per [host]: in memoria subito, su disco in background. */
    fun put(host: String, icon: Bitmap) {
        val scaled = if (icon.width > SIZE || icon.height > SIZE) {
            Bitmap.createScaledBitmap(icon, SIZE, SIZE, true)
        } else {
            icon.copy(Bitmap.Config.ARGB_8888, false) ?: return
        }
        memory.put(host, scaled.asImageBitmap())
        synchronized(missing) { missing -= host }
        _version.update { it + 1 }
        val dir = directory ?: return
        scope.launch {
            runCatching {
                dir.mkdirs()
                fileFor(dir, host).outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }
    }

    /** Cancella tutte le icone (Elimina dati di navigazione). */
    fun clear() {
        memory.evictAll()
        synchronized(missing) { missing.clear() }
        _version.update { it + 1 }
        val dir = directory ?: return
        scope.launch { runCatching { dir.listFiles()?.forEach { it.delete() } } }
    }

    /** Il nome del file non rivela il sito: hash dell'host. */
    private fun fileFor(dir: File, host: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(host.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }.take(32)
        return File(dir, "$name.png")
    }

    private companion object {
        const val SIZE = 64
        const val MEMORY_ENTRIES = 128
    }
}
