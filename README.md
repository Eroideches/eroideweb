# EroideWeb

Browser Android veloce e privato: motore Chromium (Android System WebView), interfaccia
Jetpack Compose Material 3, **zero telemetria**, blocco di tracker e pubblicità integrato.

> Progetto in sviluppo attivo. Gli APK da installare li produce GitHub Actions.

## Stato del progetto

| Fase | Contenuto | Stato |
|------|-----------|-------|
| 0 | Repository, CI, scheletro app, icona | ✅ completata |
| 1 | WebView + barra indirizzi + motore di ricerca | ✅ completata |
| 2 | Schede multiple, switcher, ibernazione, ripristino sessione | ✅ completata |
| 3 | Cronologia e segnalibri (Room) | ✅ completata |
| 4 | Download (schermata dedicata) | ✅ completata |
| 5 | Blocco tracker/pubblicità (EasyList/EasyPrivacy) | ✅ completata |
| 6 | Modalità privata con isolamento | ✅ completata |
| 7 | Barra a due righe, pannello ☰, Impostazioni, Condividi, Trova, Traduci, Desktop | ✅ completata |
| 8 | Tema scuro forzato e modalità lettura | ⏳ prossima |
| 9 | Schede (trascina per chiudere), Preferiti, cronologia migliorata | ⏳ |
| 10 | Elimina dati di navigazione, browser predefinito, privacy | ⏳ |
| 11 | Gestore password | ⏳ |
| 12 | Completezza web: upload, video a schermo intero, menu sui link, permessi | ⏳ |
| 13 | Misura e ottimizzazione prestazioni | ⏳ |
| 14 | Workflow release firmata (keystore nei Secrets) | ⏳ |

## Cosa c'è oggi (Fase 7)

**Barra e navigazione**
- **Barra "liquid glass" a due righe**, in alto o in basso, che occupa spazio reale e non
  copre mai il contenuto: sopra l'indirizzo (lucchetto/avviso, avanzamento, stop/ricarica),
  sotto le azioni **← → + [schede] ☰**. Con la barra in alto la riga azioni resta in
  basso, a portata di pollice; mentre si scrive sparisce per lasciare spazio alla tastiera.
- Input intelligente: dominio → `https://…`, IP/localhost → `http://…`, testo → ricerca
  (Google default, DuckDuckGo, Bing, Startpage, Brave).
- **Indietro/Avanti** con i pulsanti; tocco lungo su ← = pagine precedenti della scheda.
- **Pannello ☰** dal basso: Condividi, Trova, Traduci, Desktop, Segnalibro, blocco
  pubblicità sul sito corrente, Cronologia, Segnalibri, Download, Finestra privata,
  Impostazioni. Sostituisce il vecchio menu ⋮.
- **Trova nella pagina**: la ricerca avviene mentre si scrive, con contatore `n/m` e
  frecce per scorrere i risultati.
- **Traduci** con Google Traduttore web (l'indirizzo della pagina va a Google solo quando
  si tocca Traduci; avviso la prima volta), e "Originale" per tornare indietro.
- **Versione desktop per scheda** (salvata con la sessione): UA desktop e viewport largo,
  così anche i siti responsive mostrano la versione per PC.
- **User-Agent pulito**: come Chrome per Android, senza `; wv` né modello del dispositivo
  (meno fingerprinting, meno siti che rifiutano le "app con WebView").
- **Pinch-to-zoom** su tutte le pagine che lo consentono.

**Impostazioni** (☰ › Impostazioni)
- Generale: motore di ricerca, posizione della barra.
- Privacy e sicurezza: blocco pubblicità e tracker (con il numero di domini nelle liste),
  elenco dei siti esclusi dal blocco con riattivazione.
- Informazioni: versione, liste di filtri, licenze.

**Schede, libreria, privacy** (fasi 2–6)
- **Schede multiple** con selettore a card; ogni scheda ha la sua WebView e la sua
  cronologia; `target=_blank` / `window.open()` aprono una scheda nuova.
- **Ibernazione** oltre 4 WebView vive (`saveState()`), **sessione persistente**,
  **rotazione senza ricarica**.
- **Cronologia** e **Segnalibri** locali (Room) con ricerca; **Download** tramite
  DownloadManager, aperti con l'app adatta (FileProvider).
- **Finestra privata** in un processo separato (`:private`) con directory dati WebView
  dedicata: niente cronologia, segnalibri o sessione; tutto svuotato a ingresso e uscita.
- **Blocco tracker e pubblicità** con EasyList, EasyPrivacy ed EasyList Italy locali
  (~93.000 host, nessun aggiornamento di rete), mai sul documento principale.
- Pagina iniziale senza rete, errori in italiano semplice con "Riprova", link esterni
  (`tel:`, `mailto:`, app) aperti fuori dal browser.
- **Tasto Indietro**: chiude prima pannelli e schermate, poi "Trova", poi la tastiera,
  poi torna indietro nella cronologia, poi esce.

**Qualità**
- **87 test unitari JVM**: URL, host, motori di ricerca, sessione (v1 e v2), User-Agent,
  traduzione, ViewModel del browser e delle impostazioni, liste di filtri, download.

## Come scaricare l'APK

1. Apri la scheda **Actions** del repository.
2. Seleziona l'ultima esecuzione verde del workflow **Build debug APK**.
3. In fondo alla pagina, sezione **Artifacts**: scarica `eroideweb-anteprima-<numero>`
   (consigliata) oppure `eroideweb-debug-<numero>`.
4. Estrai lo ZIP e installa l'APK sul telefono (consenti "installa da origini sconosciute").

Ogni esecuzione produce due APK:

| Artefatto | applicationId | A cosa serve |
|-----------|---------------|--------------|
| `eroideweb-anteprima-<n>` | `com.eroideweb.browser.anteprima` | ottimizzata come la release (R8, non debuggable): **per provare fluidità e velocità** |
| `eroideweb-debug-<n>` | `com.eroideweb.browser.debug` | debuggable, più lenta: per indagare un problema |

Le due convivono tra loro e con la futura release. La CI firma sempre con la stessa
chiave debug (conservata nella cache di Actions), quindi un APK nuovo si installa
**sopra** quello vecchio senza disinstallare. Una sola eccezione: il primo APK firmato
così richiede di disinstallare una volta le build precedenti.

### Una build per fase

Ogni fase ha il suo tag e il suo APK, così un bug si può attribuire alla fase che
l'ha introdotto:

| Tag | APK | Contenuto |
|-----|-----|-----------|
| `fase-3` | `eroideweb-fase-3-debug` | cronologia + segnalibri (Room) |
| `fase-4` | `eroideweb-fase-4-debug` | + download |
| `fase-5` | `eroideweb-fase-5-debug` | + blocco pubblicità/tracker |
| `fase-6` | `eroideweb-fase-6-debug` | + finestra privata isolata |

Per generarne uno (o rigenerarlo) dalla scheda Actions, o da terminale:

```bash
gh workflow run build.yml --ref main -f fase=3 -f rif=fase-3
```

`rif` accetta qualunque tag o SHA: il workflow viene preso da `main`, il codice da `rif`.
Le build per fase non si annullano a vicenda e non toccano le build normali
(`eroideweb-debug-<run>`).

## Stack

- **Kotlin** + **Jetpack Compose** (Material 3), MVVM, un package per funzionalità.
- **Android System WebView** come unico motore di rendering (nessun motore alternativo).
- **Room** per cronologia/segnalibri, **DataStore** per impostazioni e sessione schede.
- `minSdk 26`, `targetSdk 37`, Gradle Kotlin DSL con version catalog.

Versioni fissate in [`gradle/libs.versions.toml`](gradle/libs.versions.toml):
AGP 9.5.1, Gradle 9.6, Kotlin 2.4.21, `compileSdk`/`targetSdk` 37.

Nota tecnica: con AGP 9 il supporto Kotlin è integrato, ma la versione del compilatore
è quella inclusa in AGP. Per poter usare Kotlin 2.4.21 (e il compose compiler
corrispondente) restiamo temporaneamente sul percorso classico con
`android.builtInKotlin=false` e `android.newDsl=false`; la migrazione al built-in Kotlin
è un task a sé, da fare a funzionalità complete.

## Build e test

La compilazione avviene **solo su GitHub Actions** ([`.github/workflows/build.yml`](.github/workflows/build.yml)):

```
push su main / pull request  →  JDK 21 + Android SDK  →  testDebugUnitTest  →  assembleDebug + assembleAnteprima  →  artifact APK
```

In locale non serve Android Studio: con JDK 21 e Android SDK (platform 37, build-tools 36)
basta `./gradlew testDebugUnitTest assembleDebug assembleAnteprima`.

Gli schemi del database Room sono versionati in `app/schemas/` (plugin `androidx.room`):
ogni tabella nuova alza la versione con una AutoMigration, senza perdere i dati di chi aggiorna.

## Liste di filtri

Le liste vivono in `app/src/main/assets/adblock/` con la loro attribuzione
(`NOTICE.md`): EasyList, EasyPrivacy ed EasyList Italy, licenza GPLv3 / CC BY-SA 3.0,
snapshot 16 novembre 2026. Solo regole di rete per host; niente aggiornamenti
automatici.

Nota: l'isolamento su disco richiede Android 9 (API 28) o successivo. Su Android
8.0/8.1 la finestra privata resta separata a livello di dati dell'app ma non ha
una directory WebView dedicata: la pulizia all'ingresso/uscita resta attiva.

## Privacy: regole di casa

- Nessun SDK di analytics, crash reporting o advertising di terze parti.
- Nessuna chiamata di rete che l'utente non abbia richiesto.
- Liste di filtri **locali** (`assets/`), aggiornate solo su richiesta esplicita.
- Cookie di terze parti bloccati di default; nessun backup automatico dei dati dell'app
  (`allowBackup=false`).

Le uniche dipendenze sono AndroidX/Jetpack, compresa `androidx.webkit` per le API WebView
moderne (script a inizio documento, tema scuro, messaggi JS sicuri). Ogni nuova libreria
va motivata in PR.

## Struttura

```
app/src/main/java/com/eroideweb/browser/
  MainActivity.kt              # activity principale
  PrivateActivity.kt           # finestra privata (processo :private)
  EroideWebApp.kt              # Application + container dipendenze
  di/AppContainer.kt           # DI manuale, tutto lazy
  core/settings/               # DataStore: tutte le preferenze (un solo update())
  core/session/                # sessione schede (formato testuale versionato)
  core/url/                    # normalizzazione input della barra (logica pura)
  core/web/                    # User-Agent mobile/desktop (logica pura)
  core/translate/              # URL di Google Traduttore (logica pura)
  core/adblock/                # liste di filtri e motore di blocco
  core/data/ history/ bookmark/ download/   # Room e repository
  feature/browser/             # schermata, ViewModel, barra, trova, start/error page
  feature/browser/menu/        # pannello ☰
  feature/browser/web/         # pool di WebView, client, configurazione
  feature/library/             # cronologia, segnalibri, download
  feature/settings/            # schermata Impostazioni
  ui/common/                   # GlassCapsule (vetro simulato)
  ui/icons/                    # icone Material Symbols generate
  ui/theme/                    # palette, typography, tema Material 3
app/src/test/                  # test unitari JVM
app/schemas/                   # schemi Room versionati
design/                        # sorgente dell'icona + script di generazione
tools/gen_icons.py             # rigenera le icone dell'app dalle sorgenti
tools/gen_symbol_icons.py      # rigenera ui/icons/EroideIcons.kt dai Material Symbols
```

## Licenza

[Apache License 2.0](LICENSE).
