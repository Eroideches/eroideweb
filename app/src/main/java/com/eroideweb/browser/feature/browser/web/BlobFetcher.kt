package com.eroideweb.browser.feature.browser.web

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eroideweb.browser.core.download.DataUrl
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/**
 * Download di URL `blob:` (file creati dalla pagina, es. "Esporta CSV"): il
 * DownloadManager non li vede, quindi la pagina stessa li legge e ce li passa.
 *
 * Ogni richiesta ha un codice casuale: messaggi senza una richiesta in
 * corso vengono ignorati, così una pagina non può "spingere" file a caso.
 */
internal class BlobFetcher {

    private val pending = mutableMapOf<String, (DataUrl?) -> Unit>()

    /** Da chiamare alla creazione della WebView, prima del caricamento. */
    fun install(view: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(view, OBJECT_NAME, setOf("*")) { _, message, _, _, _ ->
            val json = try {
                JSONObject(message.data ?: return@addWebMessageListener)
            } catch (_: JSONException) {
                return@addWebMessageListener
            }
            val callback = pending.remove(json.optString("token")) ?: return@addWebMessageListener
            callback(if (json.has("data")) DataUrl.parse(json.optString("data")) else null)
        }
    }

    fun fetch(view: WebView, url: String, onResult: (DataUrl?) -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            onResult(null)
            return
        }
        val token = UUID.randomUUID().toString()
        pending[token] = onResult
        val script = SCRIPT
            .replace("__URL__", JSONObject.quote(url))
            .replace("__TOKEN__", JSONObject.quote(token))
            .replace("__MAX__", DataUrl.MAX_BYTES.toString())
        view.evaluateJavascript(script, null)
    }

    private companion object {
        const val OBJECT_NAME = "EroideDownloads"

        const val SCRIPT = """
(function (url, token) {
  var bridge = window.EroideDownloads;
  if (!bridge) return;
  function fail() { bridge.postMessage(JSON.stringify({ token: token })); }
  fetch(url).then(function (response) { return response.blob(); }).then(function (blob) {
    if (blob.size > __MAX__) { fail(); return; }
    var reader = new FileReader();
    reader.onload = function () { bridge.postMessage(JSON.stringify({ token: token, data: reader.result })); };
    reader.onerror = fail;
    reader.readAsDataURL(blob);
  }).catch(fail);
})(__URL__, __TOKEN__);
"""
    }
}
