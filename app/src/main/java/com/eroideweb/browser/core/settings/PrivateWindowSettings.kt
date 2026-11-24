package com.eroideweb.browser.core.settings

/**
 * Impostazioni passate alla finestra privata (processo separato, che non può
 * leggere il DataStore della finestra normale). Formato chiave → testo,
 * pensato per gli extra di un Intent. Logica pura, testata.
 */
object PrivateWindowSettings {

    private const val PREFIX = "com.eroideweb.browser.settings."
    private const val SITE_SEPARATOR = "\n"

    fun toExtras(settings: BrowserSettings): Map<String, String> = mapOf(
        key("toolbar") to settings.toolbarPosition.name,
        key("engine") to settings.searchEngine.id,
        key("adblock") to settings.adBlockEnabled.toString(),
        key("adblockSites") to settings.adBlockDisabledSites.sorted().joinToString(SITE_SEPARATOR),
        key("translateNotice") to settings.translateNoticeAccepted.toString(),
        key("theme") to settings.appTheme.name,
        key("darken") to settings.darkenWebPages.toString(),
        key("textZoom") to settings.textZoom.toString(),
        key("readerTextZoom") to settings.readerTextZoom.toString(),
        key("readerTheme") to settings.readerTheme.name,
        key("readerSerif") to settings.readerSerif.toString(),
        key("thirdPartyCookies") to settings.allowThirdPartyCookies.toString(),
    )

    /** Valori mancanti o rovinati ricadono sui default: la finestra si apre comunque. */
    fun fromExtras(extras: (String) -> String?): BrowserSettings {
        val defaults = BrowserSettings()
        return BrowserSettings(
            toolbarPosition = ToolbarPosition.fromId(extras(key("toolbar"))),
            searchEngine = SearchEngine.fromId(extras(key("engine"))),
            adBlockEnabled = extras(key("adblock"))?.toBooleanStrictOrNull() ?: defaults.adBlockEnabled,
            adBlockDisabledSites = extras(key("adblockSites"))
                ?.split(SITE_SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet(),
            translateNoticeAccepted = extras(key("translateNotice"))?.toBooleanStrictOrNull() ?: false,
            appTheme = AppTheme.fromId(extras(key("theme"))),
            darkenWebPages = extras(key("darken"))?.toBooleanStrictOrNull() ?: defaults.darkenWebPages,
            textZoom = clampTextZoom(extras(key("textZoom"))?.toIntOrNull() ?: defaults.textZoom),
            readerTextZoom = clampTextZoom(extras(key("readerTextZoom"))?.toIntOrNull() ?: defaults.readerTextZoom),
            readerTheme = ReaderTheme.fromId(extras(key("readerTheme"))),
            readerSerif = extras(key("readerSerif"))?.toBooleanStrictOrNull() ?: defaults.readerSerif,
            allowThirdPartyCookies = extras(key("thirdPartyCookies"))?.toBooleanStrictOrNull()
                ?: defaults.allowThirdPartyCookies,
            // La finestra privata non ripristina mai schede.
            restoreTabs = false,
        )
    }

    /** `true` se gli extra contengono impostazioni (finestra aperta dal browser). */
    fun hasExtras(extras: (String) -> String?): Boolean = extras(key("engine")) != null

    private fun key(name: String) = PREFIX + name
}
