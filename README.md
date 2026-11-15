# EroideWeb

Browser Android veloce e privato: motore Chromium (Android System WebView), interfaccia
Jetpack Compose Material 3, **zero telemetria**, blocco di tracker e pubblicità integrato.

> Progetto in sviluppo attivo. Nessuna build locale: ogni APK è prodotto da GitHub Actions.

## Stato del progetto

| Fase | Contenuto | Stato |
|------|-----------|-------|
| 0 | Repository, CI, scheletro app, icona | ✅ completata |
| 1 | WebView + barra indirizzi + motore di ricerca | ⏳ prossima |
| 2 | Schede (normali e private) | ⏳ |
| 3 | Cronologia e segnalibri (Room) | ⏳ |
| 4 | Download (schermata dedicata) | ⏳ |
| 5 | Blocco tracker/pubblicità (EasyList/EasyPrivacy) | ⏳ |
| 6 | Modalità privata con isolamento | ⏳ |
| 7 | Tema scuro forzato e modalità lettura | ⏳ |
| 8 | Impostazioni complete | ⏳ |
| 9 | Misura e ottimizzazione prestazioni | ⏳ |
| 10 | Workflow release firmata (keystore nei Secrets) | ⏳ |

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
- `minSdk 26`, `targetSdk 36`, Gradle Kotlin DSL con version catalog.

Versioni fissate in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
Nota: al momento usiamo AGP 8.13.x (supporta `compileSdk 36`); la migrazione ad AGP 9
(built-in Kotlin) è un task separato, da fare quando la base funzionale è stabile.

## Build

La compilazione avviene **solo su GitHub Actions** ([`.github/workflows/build.yml`](.github/workflows/build.yml)):

```
push su main / pull request  →  JDK 21 + Android SDK  →  testDebugUnitTest  →  assembleDebug  →  artifact APK
```

In locale non serve Android Studio: al massimo `./gradlew` per lint e test, se hai un JDK 21.

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
  MainActivity.kt          # unica activity, ospiterà il browser
  ui/theme/                # palette, typography, tema Material 3
app/src/main/res/          # icone (generate), stringhe, tema XML
design/                    # sorgente dell'icona + script di generazione
tools/gen_icons.py         # rigenera tutte le icone dalle sorgenti
```

## Licenza

[Apache License 2.0](LICENSE).
