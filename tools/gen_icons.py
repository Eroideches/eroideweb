#!/usr/bin/env python3
"""Genera le icone Android di EroideWeb a partire da design/icon-source.jpg.

Cosa produce:
  app/src/main/res/drawable/ic_launcher_background.xml   (gradiente vettoriale)
  app/src/main/res/mipmap-*/ic_launcher_foreground.png   (glifo "E." su trasparente)
  app/src/main/res/mipmap-*/ic_launcher.png              (icona legacy)
  app/src/main/res/mipmap-*/ic_launcher_round.png        (icona legacy tonda)
  app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml    (adaptive icon)
  design/icon-preview.png                                (anteprima con maschera)

Uso:  python3 tools/gen_icons.py
Richiede solo Pillow (nessuna dipendenza da Android SDK).
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "design" / "icon-source.jpg"
RES = ROOT / "app" / "src" / "main" / "res"

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# Percentuale del lato icona occupata dal glifo (misurata sull'immagine sorgente).
GLYPH_RATIO = 0.40
# Raggio degli angoli dell'icona legacy, in frazione del lato.
LEGACY_RADIUS = 0.22
SUPERSAMPLE = 4

FG_LAYER_DP = 108.0  # lato del layer adattivo (Android definisce 108dp)
LEGACY_DP = 48.0


def find_squircle(img: Image.Image) -> tuple[int, int, int, int]:
    white = Image.new("RGB", img.size, (255, 255, 255))
    diff = ImageChops.difference(img, white).convert("L")
    bbox = diff.point(lambda v: 255 if v > 6 else 0).getbbox()
    if bbox is None:
        raise SystemExit("Impossibile trovare il quadrato arrotondato nell'immagine.")
    return bbox


def extract_glyph(img: Image.Image, box: tuple[int, int, int, int]) -> Image.Image:
    """Il glifo è bianco su blu: il canale minimo RGB è già una maschera alpha pulita."""
    x0, y0, x1, y1 = box
    # Regione centrale: evita gli angoli bianchi fuori dal quadrato arrotondato.
    inset_x = int((x1 - x0) * 0.18)
    inset_y = int((y1 - y0) * 0.18)
    crop = img.crop((x0 + inset_x, y0 + inset_y, x1 - inset_x, y1 - inset_y))
    r, g, b = crop.split()
    alpha = ImageChops.darker(ImageChops.darker(r, g), b)
    alpha = alpha.point(lambda v: 0 if v < 12 else min(255, int((v - 12) * 255 / 235)))
    # Il bbox va calcolato sul glifo "pieno", altrimenti il rumore JPEG
    # allarga il ritaglio fino ai bordi della crop.
    solid = alpha.point(lambda v: 255 if v >= 100 else 0).getbbox()
    if solid is None:
        raise SystemExit("Impossibile trovare il glifo.")
    pad = 6
    bbox = (
        max(0, solid[0] - pad),
        max(0, solid[1] - pad),
        min(alpha.width, solid[2] + pad),
        min(alpha.height, solid[3] + pad),
    )
    alpha = alpha.crop(bbox)
    glyph = Image.new("RGBA", alpha.size, (255, 255, 255, 0))
    glyph.putalpha(alpha)
    return glyph


def gradient_mask(size: int) -> Image.Image:
    n = 256
    data = [(x + y) * 255 // (2 * (n - 1)) for y in range(n) for x in range(n)]
    mask = Image.new("L", (n, n))
    mask.putdata(data)
    return mask.resize((size, size), Image.LANCZOS)


def diagonal_gradient(size: int, start: tuple[int, int, int], end: tuple[int, int, int]) -> Image.Image:
    mask = gradient_mask(size)
    return Image.composite(Image.new("RGB", (size, size), end), Image.new("RGB", (size, size), start), mask)


def sample_gradient(img: Image.Image, box: tuple[int, int, int, int]) -> tuple[tuple[int, int, int], tuple[int, int, int]]:
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    start = img.getpixel((x0 + int(w * 0.27), y0 + int(h * 0.27)))
    end = img.getpixel((x0 + int(w * 0.87), y0 + int(h * 0.87)))
    return start, end


def hex_color(rgb: tuple[int, int, int]) -> str:
    return "#FF%02X%02X%02X" % rgb


def rounded_mask(size: int, radius_ratio: float | None) -> Image.Image:
    """Maschera alpha: quadrato arrotondato (radius_ratio) o cerchio (None)."""
    big = size * SUPERSAMPLE
    mask = Image.new("L", (big, big), 0)
    draw = ImageDraw.Draw(mask)
    if radius_ratio is None:
        draw.ellipse((0, 0, big - 1, big - 1), fill=255)
    else:
        draw.rounded_rectangle(
            (0, 0, big - 1, big - 1), radius=int(big * radius_ratio), fill=255
        )
    return mask.resize((size, size), Image.LANCZOS)


def glyph_at(glyph: Image.Image, width: int) -> Image.Image:
    height = max(1, round(glyph.height * width / glyph.width))
    return glyph.resize((width, height), Image.LANCZOS)


def write_png(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "PNG", optimize=True)
    print(f"  {path.relative_to(ROOT)}")


def main() -> None:
    if not SRC.exists():
        raise SystemExit(f"Sorgente mancante: {SRC}")

    src = Image.open(SRC).convert("RGB")
    squircle = find_squircle(src)
    glyph = extract_glyph(src, squircle)
    start, end = sample_gradient(src, squircle)
    print(f"quadrato arrotondato: {squircle}")
    print(f"glifo: {glyph.size[0]}x{glyph.size[1]}")
    print(f"gradiente: {hex_color(start)} -> {hex_color(end)}")

    # --- background vettoriale (adaptive icon) --------------------------------
    bg_xml = RES / "drawable" / "ic_launcher_background.xml"
    bg_xml.parent.mkdir(parents=True, exist_ok=True)
    bg_xml.write_text(
        f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108"
                android:startColor="{hex_color(start)}"
                android:endColor="{hex_color(end)}" />
        </aapt:attr>
    </path>
</vector>
""",
        encoding="utf-8",
    )
    print(f"  {bg_xml.relative_to(ROOT)}")

    # --- adaptive foreground (glifo su trasparente, layer 108dp) --------------
    for name, scale in DENSITIES.items():
        layer = round(FG_LAYER_DP * scale)
        fg = Image.new("RGBA", (layer, layer), (0, 0, 0, 0))
        gw = round(layer * GLYPH_RATIO)
        g = glyph_at(glyph, gw)
        fg.paste(g, ((layer - g.width) // 2, (layer - g.height) // 2), g)
        write_png(fg, RES / f"mipmap-{name}" / "ic_launcher_foreground.png")

    # --- legacy icons ---------------------------------------------------------
    for name, scale in DENSITIES.items():
        size = round(LEGACY_DP * scale)
        base = diagonal_gradient(size, start, end).convert("RGBA")
        g = glyph_at(glyph, round(size * GLYPH_RATIO))
        base.paste(g, ((size - g.width) // 2, (size - g.height) // 2), g)
        for filename, radius in (("ic_launcher.png", LEGACY_RADIUS), ("ic_launcher_round.png", None)):
            icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            icon.paste(base, (0, 0), rounded_mask(size, radius))
            write_png(icon, RES / f"mipmap-{name}" / filename)

    # --- adaptive icon XML ----------------------------------------------------
    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    adaptive = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""
    for filename in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (anydpi / filename).write_text(adaptive, encoding="utf-8")
        print(f"  {(anydpi / filename).relative_to(ROOT)}")

    # --- anteprima con maschera reale (per revisione a occhio) ----------------
    preview_size = 192
    layer = 108 * 4
    bg = diagonal_gradient(layer, start, end).convert("RGBA")
    fg = Image.new("RGBA", (layer, layer), (0, 0, 0, 0))
    g = glyph_at(glyph, round(layer * GLYPH_RATIO))
    fg.paste(g, ((layer - g.width) // 2, (layer - g.height) // 2), g)
    composed = Image.alpha_composite(bg, fg)
    # la maschera visibile è ~72/108 del layer
    visible = round(layer * 72 / 108)
    offset = (layer - visible) // 2
    composed = composed.crop((offset, offset, offset + visible, offset + visible))
    composed = composed.resize((preview_size, preview_size), Image.LANCZOS)
    squircle_mask = rounded_mask(preview_size, 0.26)
    preview = Image.new("RGBA", (preview_size * 3 + 80, preview_size + 40), (247, 249, 252, 255))
    for i, size in enumerate((preview_size, 96, 48)):
        icon = composed.resize((size, size), Image.LANCZOS)
        mask = squircle_mask.resize((size, size), Image.LANCZOS)
        x = 20 + i * (preview_size + 20) if i == 0 else 20 + preview_size + 20 + (i - 1) * 116
        y = 20 + (preview_size - size) // 2
        preview.paste(icon, (x, y), mask)
    write_png(preview, ROOT / "design" / "icon-preview.png")

    # Icona "monochrome" deve essere visibile anche su fondo chiaro/scuro: ok, è bianca.

    print("Fatto.")


if __name__ == "__main__":
    main()
