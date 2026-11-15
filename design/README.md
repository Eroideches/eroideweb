# design/

Materiale di partenza dell'identità visiva.

- `icon-source.jpg` — immagine originale dell'icona (1024×1024, gradiente blu→ciano con glifo "E.").
- `icon-preview.png` — anteprima generata, utile per controllare il risultato con la maschera adattiva.

Le icone Android in `app/src/main/res/mipmap-*` **non si modificano a mano**: si rigenerano con

```bash
python3 tools/gen_icons.py
```

Lo script estrae il glifo dall'immagine, ricava i due colori del gradiente, e scrive
adaptive icon, monochrome layer e icone legacy per tutte le densità.
Serve solo Pillow (`pip install pillow`), nessun Android SDK.
