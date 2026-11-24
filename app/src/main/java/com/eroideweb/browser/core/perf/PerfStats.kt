package com.eroideweb.browser.core.perf

/** Fotografia dei numeri di prestazione, per la schermata Diagnostica. */
data class PerfSnapshot(
    /** Avvio a freddo: dall'avvio del processo al primo fotogramma. */
    val firstFrameMs: Long?,
    val adBlockLoadMs: Long?,
    /** `true` se le liste sono state lette dalla cache compatta. */
    val adBlockFromCache: Boolean?,
    val adBlockHosts: Int?,
    /** Preparazione del motore WebView (in background). */
    val webViewStartupMs: Long?,
    /** Parte di quella preparazione che ha occupato il thread principale. */
    val webViewUiThreadMs: Long?,
    val lastPageLoadMs: Long?,
    val averagePageLoadMs: Long?,
    val pageLoads: Int,
    val liveWebViews: Int,
)

/**
 * Misure raccolte durante l'uso, solo in memoria e solo per l'utente
 * (Impostazioni › Informazioni › Diagnostica). Nessun invio: zero telemetria.
 */
object PerfStats {

    private const val PAGE_SAMPLES = 20

    @Volatile var firstFrameMs: Long? = null
    @Volatile var adBlockLoadMs: Long? = null
    @Volatile var adBlockFromCache: Boolean? = null
    @Volatile var adBlockHosts: Int? = null
    @Volatile var webViewStartupMs: Long? = null
    @Volatile var webViewUiThreadMs: Long? = null
    @Volatile var liveWebViews: Int = 0

    private val pageLoads = ArrayDeque<Long>(PAGE_SAMPLES)

    /** Prima volta che si disegna: le ricomposizioni successive non contano. */
    fun recordFirstFrame(ms: Long) {
        if (firstFrameMs == null) firstFrameMs = ms
    }

    fun recordPageLoad(ms: Long) {
        if (ms < 0) return
        synchronized(pageLoads) {
            if (pageLoads.size == PAGE_SAMPLES) pageLoads.removeFirst()
            pageLoads.addLast(ms)
        }
    }

    fun snapshot(): PerfSnapshot {
        val loads = synchronized(pageLoads) { pageLoads.toList() }
        return PerfSnapshot(
            firstFrameMs = firstFrameMs,
            adBlockLoadMs = adBlockLoadMs,
            adBlockFromCache = adBlockFromCache,
            adBlockHosts = adBlockHosts,
            webViewStartupMs = webViewStartupMs,
            webViewUiThreadMs = webViewUiThreadMs,
            lastPageLoadMs = loads.lastOrNull(),
            averagePageLoadMs = if (loads.isEmpty()) null else loads.average().toLong(),
            pageLoads = loads.size,
            liveWebViews = liveWebViews,
        )
    }

    /** Solo per i test. */
    internal fun reset() {
        firstFrameMs = null
        adBlockLoadMs = null
        adBlockFromCache = null
        adBlockHosts = null
        webViewStartupMs = null
        webViewUiThreadMs = null
        liveWebViews = 0
        synchronized(pageLoads) { pageLoads.clear() }
    }
}
