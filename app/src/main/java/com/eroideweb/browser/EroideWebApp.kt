package com.eroideweb.browser

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import android.webkit.WebView
import com.eroideweb.browser.di.AppContainer
import com.eroideweb.browser.feature.browser.web.PasswordScripts
import com.eroideweb.browser.feature.browser.web.ReaderScripts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EroideWebApp : Application() {

    @Volatile
    private var privateProcess = false

    /** Dipendenze condivise, create pigramente al primo uso (avvio a freddo). */
    val container: AppContainer by lazy { AppContainer(this, privateMode = privateProcess) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        privateProcess = resolvePrivateProcess()
        if (privateProcess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Profilo WebView separato: cookie, cache e storage della finestra
            // privata non toccano quelli della navigazione normale. Va chiamato
            // prima di qualsiasi WebView nel processo.
            WebView.setDataDirectorySuffix("private")
        }

        // Le liste di filtri si leggono in background: la prima pagina non
        // deve aspettare il disco. Finché non è pronta, nessun blocco.
        appScope.launch { container.adBlocker.load() }
        // Anche gli script della modalità lettura (~95 KB) si leggono fuori dal
        // thread principale, così il primo controllo di una pagina non attende.
        appScope.launch { runCatching { ReaderScripts.preload(this@EroideWebApp) } }
        if (!privateProcess) {
            appScope.launch { runCatching { PasswordScripts.preload(this@EroideWebApp) } }
        }
    }

    private fun resolvePrivateProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            getSystemService(ActivityManager::class.java)
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
        }
        return name?.endsWith(PRIVATE_PROCESS_SUFFIX) == true
    }

    private companion object {
        const val PRIVATE_PROCESS_SUFFIX = ":private"
    }
}
