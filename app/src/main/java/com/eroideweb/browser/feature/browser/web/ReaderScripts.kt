package com.eroideweb.browser.feature.browser.web

import android.content.Context
import com.eroideweb.browser.core.reader.ReaderArticle
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Script della modalità lettura, costruiti attorno a Readability di Mozilla
 * (`assets/reader/`, senza modifiche).
 *
 * I file vengono eseguiti dentro una funzione: le loro dichiarazioni restano
 * locali e la variabile `module` è oscurata, così la pagina non vede
 * nessuna variabile globale nuova e un eventuale `module` della pagina non
 * viene toccato.
 */
internal object ReaderScripts {

    @Volatile
    private var readerable: String? = null

    @Volatile
    private var extractor: String? = null

    /** Lettura degli asset fuori dal thread principale, all'avvio dell'app. */
    fun preload(context: Context) {
        readerableScript(context)
        extractorScript(context)
    }

    /** Restituisce `true` se la pagina sembra un articolo. */
    fun readerableScript(context: Context): String =
        readerable ?: wrap(
            source = asset(context, "reader/Readability-readerable.js"),
            body = """
                try { return isProbablyReaderable(document); } catch (e) { return false; }
            """,
        ).also { readerable = it }

    /** Restituisce l'articolo in JSON, oppure `null`. */
    fun extractorScript(context: Context): String =
        extractor ?: wrap(
            source = asset(context, "reader/Readability.js"),
            body = """
                try {
                  var article = new Readability(document.cloneNode(true), { charThreshold: 500 }).parse();
                  if (!article || !article.content) return null;
                  return JSON.stringify({
                    title: article.title || document.title || '',
                    byline: article.byline || null,
                    siteName: article.siteName || null,
                    content: article.content,
                    lang: article.lang || document.documentElement.lang || null,
                    dir: article.dir || null
                  });
                } catch (e) { return null; }
            """,
        ).also { extractor = it }

    /**
     * Converte il risultato di `evaluateJavascript` (una stringa JSON che
     * contiene a sua volta JSON) in un articolo.
     */
    fun parseArticle(raw: String?, url: String): ReaderArticle? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            val json = JSONTokener(raw).nextValue() as? String ?: return null
            val obj = JSONObject(json)
            val content = obj.optStringOrNull("content")?.takeIf { it.isNotBlank() } ?: return null
            ReaderArticle(
                url = url,
                title = obj.optStringOrNull("title")?.takeIf { it.isNotBlank() } ?: url,
                byline = obj.optStringOrNull("byline"),
                siteName = obj.optStringOrNull("siteName"),
                contentHtml = content,
                language = obj.optStringOrNull("lang"),
                direction = obj.optStringOrNull("dir"),
            )
        } catch (_: JSONException) {
            null
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name)

    private fun wrap(source: String, body: String): String =
        "(function () {\nvar module;\n$source\n$body\n})()"

    private fun asset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }
}
