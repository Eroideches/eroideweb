# EroideWeb

Browser Android veloce e privato: motore Chromium (Android System WebView), interfaccia
Jetpack Compose Material 3, **zero telemetria**, blocco di tracker e pubblicità integrato.

> Progetto in sviluppo attivo. Nessuna build locale: ogni APK è prodotto da GitHub Actions.

## Stato del progetto

| Fase | Contenuto | Stato |
|------|-----------|-------|
| 0 | Repository, CI, scheletro app, icona | ✅ completata |
| 1 | WebView + barra indirizzi + motore di ricerca | ✅ completata |
| 2 | Schede (normali e private) | ⏳ prossima |
| 3 | Cronologia e segnalibri (Room) | ⏳ |
| 4 | Download (schermata dedicata) | ⏳ |
| 5 | Blocco tracker/pubblicità (EasyList/EasyPrivacy) | ⏳ |
| 6 | Modalità privata con isolamento | ⏳ |
| 7 | Tema scuro forzato e modalità lettura | ⏳ |
| 8 | Impostazioni complete | ⏳ |
| 9 | Misura e ottimizzazione prestazioni | ⏳ |
| 10 | Workflow release firmata (keystore nei Secrets) | ⏳ |

## Cosa c'è oggi (Fase 1)

- **WebView** configurata da browser: JS e storage attivi, mixed content mai ammesso,
  popup automatici disattivati, nessun accesso a file locali, SSL non valido = blocco.
- **Barra degli indirizzi "liquid glass"**, in alto o in basso (scelta persistita in
  DataStore), che occupa spazio reale e non copre mai il contenuto delle pagine; con:
  - input intelligente: dominio → `https://…`, IP/localhost → `http://…`, testo → ricerca;
  - icona di stato (lucchetto / avviso / lente), progresso di caricamento, stop/ricarica;
  - menu rapido provvisorio per posizione barra e motore di ricerca (Google default,
    DuckDuckGo, Bing, Startpage, Brave).
- **Pagina iniziale** senza rete, **schermata di errore** in italiano semplice
  ("Nessuna connessione a Internet") con "Riprova".
- **Tasto back**: prima chiude la tastiera, poi torna indietro nella cronologia, poi esce.
- Link esterni (`tel:`, `mailto:`, app link) aperti fuori dal browser.
- **21 test unitari JVM** su normalizzazione URL, motori di ricerca e ViewModel.

## Come scaricare l'APK

1. Apri la scheda **Actions** del repository.
2. Seleziona l'ultima esecuzione verde del workflow **Build debug APK**.
3. In fondo alla pagina, sezione **Artifacts**: scarica `eroideweb-debug-<numero>`.
4. Estrai lo ZIP e installa `app-debug.apk` sul telefono (consenti "installa da origini sconosciute").

L'APK ha `applicationId` `com.eroideweb.browser.debug`, quindi può convivere con una
futura versione release.

## Stack

- **Kotlin** + **Jetpack Compose** (Material 3), MVVM, un package per funzionalità.
- **Android System WebView** come unico motore di rendering (nessun motore alternativo).
- **Room** per cronologia/segnalibri, **DataStore** per le impostazioni.
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
push su main / pull request  →  JDK 21 + Android SDK  →  testDebugUnitTest  →  assembleDebug  →  artifact APK
```

In locale non serve Android Studio: al massimo `./gradlew testDebugUnitTest lint`, se hai un JDK 21.

## Privacy: regole di casa

- Nessun SDK di analytics, crash reporting o advertising di terze parti.
- Nessuna chiamata di rete che l'utente non abbia richiesto.
- Liste di filtri **locali** (`assets/`), aggiornate solo su richiesta esplicita.
- Cookie di terze parti bloccati di default; nessun backup automatico dei dati dell'app
  (`allowBackup=false`).

Le uniche dipendenze previste sono AndroidX/Jetpack e, quando servirà, `androidx.webkit`
per le API WebView moderne. Ogni nuova libreria va motivata in PR.

## Struttura

```
app/src/main/java/com/eroideweb/browser/
  MainActivity.kt              # unica activity
  EroideWebApp.kt              # Application + container dipendenze
  di/AppContainer.kt           # DI manuale, tutto lazy
  core/settings/               # DataStore: posizione barra, motore di ricerca
  core/url/                    # normalizzazione input della barra (logica pura)
  feature/browser/             # schermata, ViewModel, barra indirizzi, start/error page
  feature/browser/web/         # WebView + controller imperativo
  ui/common/                   # GlassCapsule (vetro simulato)
  ui/theme/                    # palette, typography, tema Material 3
app/src/test/                  # test unitari JVM (URL, motori, ViewModel)
design/                        # sorgente dell'icona + script di generazione
tools/gen_icons.py             # rigenera tutte le icone dalle sorgenti
```

## Licenza

[Apache License 2.0](LICENSE).
