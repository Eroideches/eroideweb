package com.eroideweb.browser.core.browsingdata

/** Periodo da cancellare (vale per la cronologia; cookie e cache si cancellano sempre tutti). */
enum class ClearRange(private val millis: Long?) {
    LAST_HOUR(60L * 60 * 1000),
    LAST_DAY(24L * 60 * 60 * 1000),
    LAST_WEEK(7L * 24 * 60 * 60 * 1000),
    LAST_FOUR_WEEKS(28L * 24 * 60 * 60 * 1000),
    ALL(null);

    /** Primo istante da cancellare; 0 = dall'inizio. */
    fun since(now: Long): Long = millis?.let { (now - it).coerceAtLeast(0) } ?: 0L
}

/** Cosa cancellare. I default sono quelli di Chrome: cronologia, cookie e cache. */
data class ClearDataRequest(
    val range: ClearRange = ClearRange.LAST_HOUR,
    val history: Boolean = true,
    val cookiesAndSiteData: Boolean = true,
    val cache: Boolean = true,
    val downloads: Boolean = false,
    val openTabs: Boolean = false,
    val passwords: Boolean = false,
    val sitePermissions: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !history && !cookiesAndSiteData && !cache && !downloads &&
            !openTabs && !passwords && !sitePermissions
}

/** Esegue la cancellazione: implementazione Android in [AndroidBrowsingDataCleaner]. */
interface BrowsingDataCleaner {
    suspend fun clear(request: ClearDataRequest, now: Long = System.currentTimeMillis())
}
