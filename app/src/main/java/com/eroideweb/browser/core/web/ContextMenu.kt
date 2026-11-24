package com.eroideweb.browser.core.web

/** Elemento toccato a lungo nella pagina. */
data class ContextTarget(
    val linkUrl: String? = null,
    val imageUrl: String? = null,
    /** Testo o titolo del link, se la WebView lo conosce. */
    val title: String? = null,
)

/** Voci del menu del tocco lungo. */
enum class ContextAction {
    OPEN_LINK_NEW_TAB,
    OPEN_LINK_BACKGROUND,
    OPEN_LINK_PRIVATE,
    COPY_LINK,
    SHARE_LINK,
    DOWNLOAD_LINK,
    OPEN_IMAGE_NEW_TAB,
    DOWNLOAD_IMAGE,
    COPY_IMAGE_ADDRESS,
    SHARE_IMAGE,
}

/** Quali voci mostrare per un elemento. Logica pura, testata. */
object ContextMenu {

    fun actionsFor(target: ContextTarget, isPrivate: Boolean): List<ContextAction> = buildList {
        val link = target.linkUrl?.takeIf { isWeb(it) }
        val image = target.imageUrl?.takeIf { isWeb(it) || it.startsWith("data:image/", ignoreCase = true) }
        if (link != null) {
            add(ContextAction.OPEN_LINK_NEW_TAB)
            add(ContextAction.OPEN_LINK_BACKGROUND)
            if (!isPrivate) add(ContextAction.OPEN_LINK_PRIVATE)
            add(ContextAction.COPY_LINK)
            add(ContextAction.SHARE_LINK)
            add(ContextAction.DOWNLOAD_LINK)
        }
        if (image != null) {
            if (isWeb(image)) add(ContextAction.OPEN_IMAGE_NEW_TAB)
            add(ContextAction.DOWNLOAD_IMAGE)
            if (isWeb(image)) {
                add(ContextAction.COPY_IMAGE_ADDRESS)
                add(ContextAction.SHARE_IMAGE)
            }
        }
    }

    private fun isWeb(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}
