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
| 8 | Tema scuro forzato e modalità lettura | ✅ completata |
| 9 | Schede (trascina per chiudere), Preferiti, cronologia migliorata | ✅ completata |
| 10 | Elimina dati di navigazione, browser predefinito, privacy | ✅ completata |
| 11 | Gestore password | ✅ completata |
| 12 | Completezza web: upload, video a schermo intero, menu sui link, permessi | ✅ completata |
| 13 | Misura e ottimizzazione prestazioni | ⏳ prossima |
| 14 | Workflow release firmata (keystore nei Secrets) | ⏳ |

## Cosa c'è oggi (Fase 12)

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

**Completezza web**
- **Upload di file** (`<input type="file">`, anche multipli) con il selettore di Android.
- **Video a schermo intero** con barre di sistema nascoste e schermo sempre acceso;
  Indietro esce dallo schermo intero.
- **Rotazione e cambio tema senza ricreare l'activity**: le pagine non si ricaricano e un
  video a schermo intero continua girando il telefono.
- **Tocco lungo su link e immagini**: apri in nuova scheda, in background o in finestra
  privata, copia, condividi, scarica link o immagine. Sul testo resta la selezione normale.
- **Permessi dei siti** (posizione, fotocamera, microfono, contenuti protetti): domanda
  "Consenti / Blocca" e permesso Android solo se serve; la scelta vale fino alla chiusura
  dell'app e si cancella anche da Elimina dati.
- **Stampa / Salva come PDF** (☰ › Stampa).
- **Accesso HTTP** (finestra di login del server) con utente e password.
- **Download di file creati dalla pagina** (`blob:` e `data:`, es. "Esporta CSV"), salvati
  in Download fino a 25 MB.
- **Crash del renderer gestito**: se Android chiude il processo di una pagina per memoria,
  l'app non si chiude più; la scheda si ricarica da sola, e se ricapita subito compare
  "Questa pagina ha smesso di funzionare" con "Riprova".

**Gestore password**
- Dopo un accesso: "Salvare la password per sito?" (Salva / Non ora / Mai per questo sito),
  oppure "Aggiornare la password?" se è cambiata.
- Toccando un campo di accesso di un sito con password salvate compare **"Accedi come …"**
  sopra la tastiera: si compila **solo al tocco**, mai da solo; funziona anche con i siti
  React/Vue e con gli accessi in due passi (prima l'email).
- Password **cifrate con AES-256-GCM** e chiave nell'Android Keystore (non esce dal
  telefono); legate all'origine (`https://sito`) data dal browser, mai dalla pagina; mai
  proposte a pagine `http` su Internet. Dove la WebView lo consente lo script gira in un
  **mondo JavaScript isolato**, invisibile alla pagina.
- Schermata **Password** (☰ o Impostazioni): elenco per sito con ricerca, dettaglio,
  modifica, eliminazione, siti esclusi. **Vedere o copiare** una password richiede lo
  **sblocco del telefono** (impronta, volto o PIN), valido un minuto; la schermata è
  esclusa da screenshot e app recenti; gli appunti sono marcati come sensibili.
- Niente password nella finestra privata; voce "Password salvate" in Elimina dati.
  L'autofill di sistema (Google, Bitwarden…) continua a funzionare.

**Privacy e sistema**
- **Elimina dati di navigazione** (Impostazioni › Privacy o ☰): intervallo (ultima ora,
  24 ore, 7 giorni, 4 settimane, da sempre) e caselle per cronologia, cookie e dati dei
  siti, cache (con le favicon), download, schede aperte.
- **Browser predefinito**: EroideWeb può aprire i link delle altre app (ruolo "Browser"
  da Android 10, impostazioni di sistema prima), riceve le condivisioni di testo e
  compare come "Cerca con EroideWeb" sul testo selezionato. Una scheda aperta da
  un'altra app, a inizio cronologia, con Indietro si chiude e riporta all'app di partenza.
  Un link arrivato mentre l'app si avvia si aggiunge alle schede salvate, senza perderle.
- **Cookie di terze parti** bloccati di default, con interruttore nelle Impostazioni.
- **Riapri le schede all'avvio** disattivabile.
- La **finestra privata eredita le impostazioni** della finestra normale (motore, tema,
  blocco, testo…): prima partiva sempre dai default.

**Schede, preferiti, cronologia**
- **Trascina una scheda di lato per chiuderla** nel selettore, con avviso "Scheda chiusa ·
  Annulla" che la riapre esattamente dov'era (stato della WebView compreso).
  "Chiudi tutte" (annullabile), tocco lungo per duplicare; le card si ricompattano
  con un'animazione. Tutto il gesto lavora nella fase di disegno: niente scatti.
- **Preferiti** nella pagina iniziale: griglia di riquadri con l'icona del sito o
  l'iniziale colorata; ☰ › Preferito aggiunge la pagina, "+" aggiunge a mano, tocco
  lungo per modificare, spostare o rimuovere.
- **Favicon** raccolte durante la navigazione (nessuna richiesta in più), salvate su
  disco con nome anonimo; in privato solo in memoria.
- **Cronologia per giorno** (Oggi, Ieri, date) con ora della visita, icone dei siti e
  trascinamento per eliminare.
- **Suggerimenti nella barra** mentre si scrive: ricerca sul motore scelto, preferiti,
  segnalibri e pagine più visitate, ordinati per pertinenza. Solo dati locali.

**Tema e lettura**
- **Tema dell'app** Sistema / Chiaro / Scuro, con barre di sistema coerenti e nessun lampo
  di colore all'avvio. Le pagine ricevono la stessa preferenza (`prefers-color-scheme`).
- **Scurisci tutti i siti**: tema scuro forzato anche dove il sito non lo prevede
  (scurimento automatico della WebView; filtro CSS su Android 8–9).
- **Dimensione del testo** delle pagine dal 50% al 200%, applicata subito a tutte le schede.
- **Modalità lettura** (icona libro nella barra o ☰ › Lettura) con Readability di Mozilla:
  solo il testo dell'articolo, in una WebView con JavaScript spento e CSP restrittiva;
  dimensione A−/A+, temi Automatico/Chiaro/Seppia/Scuro, carattere con o senza grazie.

**Impostazioni** (☰ › Impostazioni)
- Generale: motore di ricerca, posizione della barra.
- Aspetto: tema, scurisci tutti i siti, dimensione del testo.
- Privacy e sicurezza: blocco pubblicità e tracker (con il numero di domini nelle liste),
  elenco dei siti esclusi dal blocco con riattivazione.
- Informazioni: versione, liste di filtri, licenze.

**Schede, libreria, privacy** (fasi 2–6)
- **Schede multiple** con selettore a card; ogni scheda ha la sua WebView e la sua
  cronologia; `target=_blank` / `window.open()` aprono una scheda nuova.
- **Ibernazione** oltre 4 WebView vive (`saveState()`) e **sessione persistente**.
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
- **165 test unitari JVM**: URL, host, motori di ricerca, sessione (v1 e v2), User-Agent,
  traduzione, documento di lettura, impostazioni, suggerimenti, cronologia per giorno,
  chiusura/Annulla delle schede, preferiti, link in arrivo, elimina dati, impostazioni
  della finestra privata, origine e regole delle password, cifratura AES-GCM (anche con
  dati manomessi), menu del tocco lungo, permessi dei siti, URL `data:`, crash del
  renderer, ViewModel, liste di filtri, download.
- Database alla versione 3 con AutoMigration generate da Room: chi aggiorna conserva
  cronologia, segnalibri e download.
- Gli script iniettati nelle pagine (lettura, scuro forzato, viewport desktop, ponte
  password, lettura dei `blob:`) sono verificati anche in Chromium headless.

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

## Modalità lettura

`app/src/main/assets/reader/` contiene `Readability.js` e `Readability-readerable.js` di
Mozilla 0.6.0 (Apache 2.0) senza modifiche, con `NOTICE.md` e hash di integrità npm.
Vengono eseguiti solo su richiesta, dentro una funzione isolata: la pagina non vede
variabili globali nuove.

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
- Cookie di terze parti bloccati di default (interruttore nelle Impostazioni); nessun
  backup automatico dei dati dell'app (`allowBackup=false`).

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
  core/reader/                 # pagina della modalità lettura (logica pura)
  core/adblock/                # liste di filtri e motore di blocco
  core/data/ history/ bookmark/ download/ favorites/   # Room e repository
  core/favicon/                # icone dei siti (memoria + disco)
  core/suggest/                # classifica dei suggerimenti (logica pura)
  core/browsingdata/           # elimina dati di navigazione
  core/password/               # origine, regole, cifratura e archivio delle password
  feature/browser/             # schermata, ViewModel, barra, trova, start/error page
  feature/browser/menu/        # pannello ☰
  feature/browser/web/         # pool di WebView, client, configurazione
  feature/library/             # cronologia, segnalibri, download
  feature/settings/            # schermata Impostazioni
  feature/reader/              # vista della modalità lettura
  feature/passwords/           # schermata Password e sblocco del telefono
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
