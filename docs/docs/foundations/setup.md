# 1. Setup & project layout 🟢

## What you need

| Tool | Why |
|---|---|
| **JDK 17+** | Gradle and the Kotlin compiler run on the JVM. Android Studio bundles one (the *JBR*). |
| **Android SDK** | Platform libraries (`compileSdk 37`), build tools and `adb`. |
| **Android Studio** or the command line | Studio gives previews and a debugger; `./gradlew` is all CI needs. |

```bash
./gradlew testDebugUnitTest   # run every unit test on the JVM (seconds, no device)
./gradlew installDebug        # build and install on a connected phone
./gradlew recordRoborazziDebug  # re-render every image in this guide
```

## How an Android project is laid out

```text
ttac/
├─ settings.gradle.kts        # which modules exist (:app, :baselineprofile)
├─ gradle/libs.versions.toml  # one place for every dependency version
├─ app/
│  ├─ build.gradle.kts        # how to build the app module
│  └─ src/
│     ├─ main/java/io/gh/kdbrian/ttac/   # the game
│     ├─ main/res/                        # launcher icon (a vector), fonts, strings
│     └─ test/                            # JVM unit + screenshot tests
├─ baselineprofile/           # macrobenchmarks & profile generator (runs on a device)
└─ docs/                      # this guide (MkDocs) + Dokka config
```

### The version catalog

Instead of sprinkling version numbers across build files, `gradle/libs.versions.toml` declares them once:

```toml
[versions]
agp = "9.4.1"          # Android Gradle Plugin
kotlin = "2.4.10"
androidxComposeBom = "2026.06.01"

[libraries]
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }  # version from the BOM
```

The **Compose BOM** (bill of materials) pins every Compose library to versions that are tested together, so
individual Compose artifacts don't need a version at all.

## The source packages

| Package | Responsibility | Android-free? |
|---|---|---|
| `game` | Rules, AI, Blocks engine, Word Hive | ✅ pure Kotlin |
| `data` | Records, medals, unlocks, storage | reducers ✅, storage ❌ |
| `net` | LAN protocol & sessions | protocol ✅, sockets ❌ |
| `app` | ViewModel, `Match` state machine | mostly ✅ |
| `fx` | Synth & haptics | ❌ |
| `ui` | Theme, drawing, components, screens | ❌ |

!!! note "Why keep `game` Android-free?"
    Pure Kotlin runs in plain JVM unit tests in milliseconds. TTac's test suite plays hundreds of AI games and lets a
    bot clear lines in Blocks — none of which would be practical if the rules needed an emulator.

## Going further

- The app module uses **AGP 9's built-in Kotlin support**, so there's no separate `kotlin-android` plugin.
- Release builds enable **R8** (`isMinifyEnabled`, `isShrinkResources`) — see [Performance](../shipping/performance.md).
- Signing is read from environment variables so CI can sign without secrets in the repo — see [CI/CD](../shipping/ci-cd.md).
