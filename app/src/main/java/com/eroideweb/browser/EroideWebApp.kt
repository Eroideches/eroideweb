package com.eroideweb.browser

import android.app.Application
import com.eroideweb.browser.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EroideWebApp : Application() {

    /** Dipendenze condivise, create pigramente al primo uso (avvio a freddo). */
    val container: AppContainer by lazy { AppContainer(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Le liste di filtri si leggono in background: la prima pagina non
        // deve aspettare il disco. Finché non è pronta, nessun blocco.
        appScope.launch { container.adBlocker.load() }
    }
}
