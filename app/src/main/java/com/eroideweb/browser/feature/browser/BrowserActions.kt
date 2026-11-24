package com.eroideweb.browser.feature.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import com.eroideweb.browser.R

/**
 * Azioni che passano dal sistema Android (condivisione, informazioni sul
 * pacchetto). Restano fuori dal ViewModel per tenerlo testabile su JVM.
 */
object BrowserActions {

    /** Condivide l'indirizzo della pagina con il foglio di condivisione di sistema. */
    fun share(context: Context, url: String, title: String?) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
        title?.takeIf { it.isNotBlank() }?.let {
            send.putExtra(Intent.EXTRA_SUBJECT, it)
            // Mostrato come anteprima nel foglio di condivisione.
            send.putExtra(Intent.EXTRA_TITLE, it)
        }
        try {
            context.startActivity(Intent.createChooser(send, null))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.share_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /** Versione leggibile dell'app (es. "0.1.0-anteprima"). */
    fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }
}
