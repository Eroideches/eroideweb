package com.eroideweb.browser

import android.app.Application
import com.eroideweb.browser.di.AppContainer

class EroideWebApp : Application() {

    /** Dipendenze condivise, create pigramente al primo uso (avvio a freddo). */
    val container: AppContainer by lazy { AppContainer(this) }
}
