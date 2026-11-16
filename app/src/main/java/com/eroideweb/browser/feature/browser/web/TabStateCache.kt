package com.eroideweb.browser.feature.browser.web

import android.os.Bundle

/**
 * Stati WebView conservati tra due composizioni (es. rotazione).
 *
 * Vive nel ViewModel perché deve sopravvivere alla ricreazione dell'activity:
 * la WebView viene distrutta con la vecchia composizione e ripristinata dalla
 * nuova senza ricaricare la pagina. Ogni stato viene consumato una sola volta.
 */
class TabStateCache {

    private val states = mutableMapOf<String, Bundle>()

    fun save(tabId: String, state: Bundle) {
        states[tabId] = state
    }

    fun consume(tabId: String): Bundle? = states.remove(tabId)

    fun remove(tabId: String) {
        states.remove(tabId)
    }

    fun clear() {
        states.clear()
    }
}
