package com.eroideweb.browser.core.reader

import com.eroideweb.browser.core.settings.ReaderTheme

/** Articolo estratto da Readability. [contentHtml] è già ripulito da script e stili. */
data class ReaderArticle(
    val url: String,
    val title: String,
    val byline: String?,
    val siteName: String?,
    val contentHtml: String,
    val language: String?,
    /** `rtl` per arabo/ebraico, altrimenti `ltr`. */
    val direction: String?,
)

/** Colori e carattere della vista di lettura (la dimensione passa da textZoom). */
data class ReaderStyle(
    val theme: ReaderTheme = ReaderTheme.AUTO,
    val serif: Boolean = true,
) {
    /** Tema concreto: AUTO segue il tema dell'app. */
    fun resolvedTheme(appDark: Boolean): ReaderTheme = when (theme) {
        ReaderTheme.AUTO -> if (appDark) ReaderTheme.DARK else ReaderTheme.LIGHT
        else -> theme
    }
}

/** Colori di una palette di lettura, in esadecimale CSS. */
data class ReaderPalette(
    val background: String,
    val text: String,
    val secondary: String,
    val link: String,
) {
    companion object {
        fun of(theme: ReaderTheme): ReaderPalette = when (theme) {
            ReaderTheme.DARK -> ReaderPalette("#121418", "#E3E6EA", "#9AA3AE", "#8AC5FF")
            ReaderTheme.SEPIA -> ReaderPalette("#F4ECD8", "#3B2F23", "#7A6A58", "#8A4B12")
            ReaderTheme.LIGHT, ReaderTheme.AUTO -> ReaderPalette("#FFFFFF", "#1B1C1F", "#5F6368", "#0A5BD3")
        }
    }
}

/**
 * Costruisce la pagina della modalità lettura. Logica pura, testata su JVM.
 *
 * Sicurezza: la pagina viene mostrata in una WebView con JavaScript spento e
 * la CSP qui sotto vieta comunque script, oggetti e frame. Titolo e autore
 * sono testo e vengono sempre sottoposti a escape.
 */
object ReaderDocument {

    const val CONTENT_SECURITY_POLICY =
        "default-src 'none'; img-src http: https: data:; media-src http: https:; " +
            "style-src 'unsafe-inline'; font-src 'none'; script-src 'none'; frame-src 'none'; object-src 'none'"

    fun build(article: ReaderArticle, theme: ReaderTheme, serif: Boolean): String {
        val palette = ReaderPalette.of(theme)
        val fontFamily = if (serif) {
            "Georgia, 'Noto Serif', 'Times New Roman', serif"
        } else {
            "'Roboto', 'Noto Sans', system-ui, sans-serif"
        }
        val direction = if (article.direction.equals("rtl", ignoreCase = true)) "rtl" else "ltr"
        val language = article.language?.takeIf { LANGUAGE_TAG.matches(it) } ?: ""
        val meta = listOfNotNull(
            article.byline?.takeIf { it.isNotBlank() },
            article.siteName?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

        return buildString {
            append("<!DOCTYPE html><html")
            if (language.isNotEmpty()) append(" lang=\"").append(language).append('"')
            append(" dir=\"").append(direction).append("\"><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<meta http-equiv=\"Content-Security-Policy\" content=\"")
            append(CONTENT_SECURITY_POLICY)
            append("\"><title>").append(escapeHtml(article.title)).append("</title><style>")
            append(css(palette, fontFamily))
            append("</style></head><body><article><h1 class=\"title\">")
            append(escapeHtml(article.title))
            append("</h1>")
            if (meta.isNotEmpty()) {
                append("<p class=\"meta\">").append(escapeHtml(meta)).append("</p>")
            }
            append("<div class=\"content\">").append(article.contentHtml).append("</div>")
            append("</article></body></html>")
        }
    }

    fun escapeHtml(text: String): String = buildString(text.length) {
        text.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }

    private val LANGUAGE_TAG = Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$")

    private fun css(palette: ReaderPalette, fontFamily: String) = """
        html { background: ${palette.background}; }
        body { margin: 0; padding: 20px 20px 48px; background: ${palette.background};
               color: ${palette.text}; font-family: $fontFamily; font-size: 19px;
               line-height: 1.65; word-wrap: break-word; overflow-wrap: anywhere;
               -webkit-text-size-adjust: none; }
        article { max-width: 680px; margin: 0 auto; }
        h1.title { font-size: 1.6em; line-height: 1.25; margin: 0.4em 0 0.3em; }
        p.meta { color: ${palette.secondary}; font-size: 0.85em; margin: 0 0 1.6em; }
        a { color: ${palette.link}; }
        img, video, figure, picture, svg { max-width: 100%; height: auto; }
        figure { margin: 1.2em 0; }
        figcaption { color: ${palette.secondary}; font-size: 0.85em; }
        pre, code { font-family: 'Roboto Mono', monospace; font-size: 0.85em; }
        pre { overflow-x: auto; padding: 12px; background: rgba(127,127,127,0.12); border-radius: 8px; }
        blockquote { margin: 1em 0; padding-left: 1em; border-left: 3px solid ${palette.secondary};
                     color: ${palette.secondary}; }
        table { border-collapse: collapse; max-width: 100%; display: block; overflow-x: auto; }
        td, th { border: 1px solid rgba(127,127,127,0.3); padding: 4px 8px; }
        hr { border: 0; border-top: 1px solid rgba(127,127,127,0.3); }
    """.trimIndent()
}
