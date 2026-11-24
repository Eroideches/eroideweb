#!/usr/bin/env python3
"""
Genera app/src/main/java/com/eroideweb/browser/ui/icons/EroideIcons.kt dai path
Material Symbols (stile Outlined, peso 400, Apache License 2.0).

Uso (dalla radice del repository):
    (mkdir -p /tmp/symbols && cd /tmp/symbols && npm pack @material-symbols/svg-400@0.49.0 \
        && tar -xzf material-symbols-svg-400-0.49.0.tgz)
    python3 tools/gen_symbol_icons.py /tmp/symbols/package/outlined \
        app/src/main/java/com/eroideweb/browser/ui/icons/EroideIcons.kt

Per aggiungere un'icona: una riga in ICONS e rigenerare.
"""
import re, sys
src = sys.argv[1]
out = sys.argv[2]
# (nome Kotlin, file svg Material Symbols outlined, descrizione)
ICONS = [
    ("Translate", "translate", "Traduzione"),
    ("Desktop", "desktop_windows", "Versione desktop"),
    ("History", "history", "Cronologia"),
    ("Download", "download", "Download"),
    ("Bookmark", "bookmark", "Segnalibro (vuoto)"),
    ("BookmarkFilled", "bookmark-fill", "Segnalibro (pieno)"),
    ("Bookmarks", "bookmarks", "Elenco segnalibri"),
    ("Private", "domino_mask", "Navigazione privata"),
    ("DarkMode", "dark_mode", "Tema scuro"),
    ("Reader", "menu_book", "Modalità lettura"),
    ("Print", "print", "Stampa"),
    ("Key", "key", "Password"),
    ("ContentCopy", "content_copy", "Copia"),
    ("Visibility", "visibility", "Mostra"),
    ("VisibilityOff", "visibility_off", "Nascondi"),
    ("OpenInNew", "open_in_new", "Apri in nuova scheda"),
    ("Image", "image", "Immagine"),
    ("Shield", "shield", "Blocco tracker"),
    ("Public", "public", "Sito web"),
    ("Tab", "tab", "Scheda"),
    ("FindInPage", "find_in_page", "Trova nella pagina"),
    ("Share", "share", "Condividi"),
    ("Settings", "settings", "Impostazioni"),
    ("Favorite", "favorite", "Preferito (vuoto)"),
    ("FavoriteFilled", "favorite-fill", "Preferito (pieno)"),
    ("Delete", "delete", "Elimina"),
    ("Edit", "edit", "Modifica"),
    ("Info", "info", "Informazioni"),
    ("Cookie", "cookie", "Cookie"),
    ("TextFields", "text_fields", "Dimensione testo"),
    ("Contrast", "contrast", "Tema"),
    ("Undo", "undo", "Annulla"),
    ("Language", "language", "Lingua"),
    ("Policy", "policy", "Privacy"),
]
lines = []
for name, file, desc in ICONS:
    svg = open(f"{src}/{file}.svg").read()
    assert 'viewBox="0 -960 960 960"' in svg, file
    paths = re.findall(r'<path d="([^"]+)"', svg)
    assert len(paths) == 1, (file, len(paths))
    lines.append((name, file, desc, paths[0]))

kt = []
kt.append("""package com.eroideweb.browser.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icone Material Symbols (stile Outlined, peso 400) che material-icons-core non
 * include. Generate da `@material-symbols/svg-400` 0.49.0 (Apache License 2.0,
 * Google): solo i path, nessuna dipendenza in più. Create pigramente al primo uso.
 *
 * Non modificare a mano: si rigenera con `tools/gen_symbol_icons.py`.
 */
object EroideIcons {
""")
for name, file, desc, d in lines:
    kt.append(f"""    /** {desc} (`{file}`). */
    val {name}: ImageVector by lazy {{ symbol("{name}", "{d}") }}
""")
kt.append("""
    /** Le icone Symbols hanno viewBox `0 -960 960 960`: il gruppo trasla di 960 in verticale. */
    private fun symbol(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        )
            .addGroup(translationY = 960f)
            .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
}
""")
open(out, "w").write("".join(kt))
print("icons:", len(lines))
