package com.eroideweb.browser.feature.browser.web

import android.content.Context
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eroideweb.browser.core.password.CredentialOrigin
import org.json.JSONException
import org.json.JSONObject

/**
 * Collega le pagine al gestore password (`assets/password/password-bridge.js`).
 *
 * Sicurezza:
 * - l'origine di ogni messaggio la dà la WebView (`sourceOrigin`), mai la pagina;
 * - le credenziali vanno solo al frame che ha segnalato il campo, e solo se
 *   la sua origine è quella delle credenziali;
 * - dove possibile lo script gira in un mondo JavaScript isolato.
 */
internal class PasswordBridge(private val context: Context) {

    interface Listener {
        fun onLoginFieldFocused(tabId: String, origin: String)
        fun onCredentialsSubmitted(tabId: String, origin: String, username: String, password: String)
    }

    private class FillTarget(val origin: String, val proxy: JavaScriptReplyProxy)

    /** Ultimo frame (per scheda) in cui l'utente ha toccato un campo di accesso. */
    private val targets = mutableMapOf<String, FillTarget>()

    /** @return `false` se la WebView è troppo vecchia per il ponte (gestore spento). */
    fun install(tabId: String, view: WebView, listener: Listener): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        val messages = WebViewCompat.WebMessageListener { _, message, sourceOrigin, _, replyProxy ->
            val origin = CredentialOrigin.from(sourceOrigin.toString()) ?: return@WebMessageListener
            val json = try {
                JSONObject(message.data ?: return@WebMessageListener)
            } catch (_: JSONException) {
                return@WebMessageListener
            }
            when (json.optString("type")) {
                "focus" -> {
                    targets[tabId] = FillTarget(origin, replyProxy)
                    listener.onLoginFieldFocused(tabId, origin)
                }
                "submit" -> {
                    val password = json.optString("password")
                    if (password.isNotEmpty()) {
                        listener.onCredentialsSubmitted(tabId, origin, json.optString("username"), password)
                    }
                }
            }
        }
        val script = PasswordScripts.bridge(context)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)) {
            val world = WebViewCompat.getExecutionWorld(view, WORLD_NAME)
            WebViewCompat.addWebMessageListener(view, OBJECT_NAME, ALL_ORIGINS, world, messages)
            WebViewCompat.addJavaScriptOnEvent(
                view,
                script,
                WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                ALL_ORIGINS,
                world,
            )
            return true
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
        WebViewCompat.addWebMessageListener(view, OBJECT_NAME, ALL_ORIGINS, messages)
        WebViewCompat.addDocumentStartJavaScript(view, script, ALL_ORIGINS)
        return true
    }

    /** Compila il campo toccato, solo se il frame appartiene a [origin]. */
    fun fill(tabId: String, origin: String, username: String, password: String): Boolean {
        val target = targets[tabId] ?: return false
        if (target.origin != origin) return false
        val message = JSONObject()
            .put("type", "fill")
            .put("username", username)
            .put("password", password)
            .toString()
        return try {
            target.proxy.postMessage(message)
            true
        } catch (_: IllegalStateException) {
            // Il frame non esiste più (pagina cambiata).
            targets.remove(tabId)
            false
        }
    }

    fun forget(tabId: String) {
        targets.remove(tabId)
    }

    private companion object {
        const val OBJECT_NAME = "EroidePasswords"
        const val WORLD_NAME = "eroideweb-passwords"
        val ALL_ORIGINS = setOf("*")
    }
}

/** Script del ponte, letto dagli asset una volta sola. */
internal object PasswordScripts {

    @Volatile
    private var bridge: String? = null

    fun preload(context: Context) {
        bridge(context)
    }

    fun bridge(context: Context): String =
        bridge ?: context.assets.open("password/password-bridge.js").bufferedReader()
            .use { it.readText() }
            .also { bridge = it }
}
