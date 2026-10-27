# Step Analyzer

Android app (Kotlin + Jetpack Compose) for recording phone motion sensors and testing
step-counting algorithms on the recordings. It is the Android successor of the Python
"STRIDELAB" web tool: the same five classic algorithms and seven step-length models, ported
to Kotlin with bit-for-bit parity, plus five new algorithms for comparison.

* package `com.vislupus.stepanalyzer`, portrait only, designed for 1080 × 2340 (Xiaomi Redmi Note 8 Pro)
* English UI with Bulgarian available in Settings, light / dark theme, six accent colours, four font sizes
* every file the app writes (sensor CSV, GPX tracks, SQLite database) stays in the app sandbox (`filesDir`)
* icons from [Lucide](https://lucide.dev), Roboto (the Android system font)

## Project layout

```
StepAnalyzer/
├─ core/            pure Kotlin/JVM: signal processing, 10 step algorithms, 7 step-length models,
│                   CSV + GPX parsing, analysis pipeline, unit tests, desktop CLI
├─ app/             Android application (Compose UI, SQLite, foreground recording service, GPS)
│  └─ src/main/java/com/vislupus/stepanalyzer/
│     ├─ config/AppConfig.kt   <- every UI/behaviour knob of the app
│     ├─ config/Palette.kt     <- colours (accents, light/dark neutrals, chart series)
│     ├─ data/                 SQLite (plain SQLiteOpenHelper), repositories, sandbox files
│     ├─ recording/            foreground service writing sensors.csv + track.gpx
│     └─ ui/                   screens: Record, Sessions, Analysis, Settings, Info
├─ tools/           gen_icons.py (Lucide -> VectorDrawable), gen_strings.py + strings_src.py (EN/BG texts)
├─ dev.sh / dev.bat one-command build / run / test helpers
└─ gradle/libs.versions.toml   all dependency versions
```

## Where to change things

| What | Where |
|---|---|
| Layout sizes, chart heights, animation speed, defaults of the user settings, recording rates, feature switches | `app/src/main/java/com/vislupus/stepanalyzer/config/AppConfig.kt` |
| Colours (accent presets, light/dark palettes, chart series colours) | `app/src/main/java/com/vislupus/stepanalyzer/config/Palette.kt` |
| Algorithm maths: every default, slider range and threshold | `core/src/main/kotlin/com/vislupus/stepanalyzer/core/config/CoreDefaults.kt` |
| All texts (English + Bulgarian) | `tools/strings_src.py`, then `python3 tools/gen_strings.py` |
| Icons | `tools/gen_icons.py` (add the Lucide primitives, run the script) |
| Dependency versions | `gradle/libs.versions.toml` |

The user-visible settings (theme, accent, font size, language, height, sex, recording rate,
GPS, band-pass, resample rate, units, enabled algorithms, saved algorithm parameters) are
stored in the `settings` table of the SQLite database; `AppConfig.Defaults` only provides the
initial values.

## Build & run

Requirements: Android Studio (Ladybug or newer) with the Android SDK (API 35) and a JDK 17+.
Enable USB debugging on the phone.

```bash
./dev.sh run        # debug build -> install on the connected phone -> launch
./dev.sh build      # release APK: app/build/outputs/apk/release/app-release.apk (signed with the debug key)
./dev.sh test       # algorithm unit tests (no Android SDK needed)
./dev.sh analyze sensors.csv track.gpx --height 178     # analyse a file on your PC
./dev.sh logs       # logcat of the app
```

On Windows use `dev.bat` with the same commands. Without the scripts, the plain Gradle tasks
are `./gradlew :app:installDebug`, `./gradlew :app:assembleRelease`, `./gradlew -PcoreOnly :core:test`.

**Hot reload.** Open the folder in Android Studio and run the app once (Shift+F10). After
that, edits inside `@Composable` functions are pushed to the phone automatically by *Live
Edit* (Settings → Editor → Live Edit, "Push manually" or "Automatically"), and *Apply Changes*
(Ctrl+F10 / Ctrl+Shift+F10) applies everything else without a full reinstall. Every screen
also works with Compose `@Preview` in the Studio design pane.

## Data formats

The recorder writes the same long CSV format the old tool consumed, so old files can be
imported directly (Sessions → Import CSV, then attach a GPX from the ⋮ menu):

```
timestamp_ms,sensor,x,y,z
1782372767801,accel,-0.226,4.005,9.411
1782372767806,gyro,0.010,-0.020,0.000
```

Recognised sensors: `accel` (with gravity), `uaccel`/`linaccel` (linear), `gyro`, `mag`.
Wide CSV files (`timestamp,acc_x,acc_y,acc_z,gyro_x,…`) are also accepted, units (g vs
m/s², deg/s vs rad/s) are detected automatically. GPX 1.1 tracks with timestamps are
cleaned (segments faster than the configurable maximum speed are dropped) and used to
calibrate the step-length models.

Files on the phone: `/data/data/com.vislupus.stepanalyzer/files/sessions/<id>/sensors.csv`,
`.../track.gpx` and `/data/data/com.vislupus.stepanalyzer/files/stepanalyzer.db`.

## Algorithms

| id | family | idea |
|---|---|---|
| `peak_magnitude` | peak | local maxima of the band-passed magnitude with distance + prominence constraints |
| `adaptive_peak_valley` | peak | peak–valley pairs above an adaptive (local std) threshold |
| `zero_crossing` | zero crossing | positive-going zero crossings validated by a following peak |
| `autocorrelation` | frequency | windowed autocorrelation period inside the step band |
| `fft_cadence` | frequency | windowed FFT dominant frequency with a stride → step guard |
| `dynamic_threshold` | threshold | classic hardware pedometer: max/min midpoint threshold per window |
| `pan_tompkins` | threshold | derivative → rectify → square → integrate → adaptive SPKI/NPKI threshold |
| `hysteresis` | threshold | Schmitt trigger with ±T hysteresis |
| `gyro_peak` | peak | peaks on the band-passed gyroscope magnitude (needs gyro) |
| `vertical_accel` | peak | gravity projection → vertical acceleration → peaks (needs raw accel) |

Each algorithm declares its parameters with slider ranges; the Analysis screen re-runs the
algorithm live while you drag a slider, shows the result next to all other algorithms and the
phone's own step counter, and can store the parameters as the new defaults.

Step length: GPX average, Weinberg, Kim, Scarlett (K calibrated to the GPS distance when a
track exists), height × factor, cadence linear, height × cadence.

## Verification

`core` is covered by unit tests that compare the Kotlin implementations of the Butterworth
design, `filtfilt`, `find_peaks`, FFT and statistics against values produced by SciPy/NumPy,
plus end-to-end tests on a synthetic walk. On the two real recordings shipped with the old
project the Kotlin pipeline reproduces the Python results exactly (1162/1182/1168/1179/1190
and 808/822/804/810/811 steps for the five classic algorithms, identical confidences and GPX
distances).

The Android module was written against AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.12.01
(see `gradle/libs.versions.toml`). Gradle sync in Android Studio downloads everything.

---

## Бърз старт (BG)

1. Отворете папката в Android Studio и изчакайте Gradle sync.
2. Включете USB debugging на телефона и го свържете.
3. `./dev.sh run` (или `dev.bat run` на Windows) – компилира, инсталира и стартира приложението.
4. `./dev.sh build` – готов APK в `app/build/outputs/apk/release/app-release.apk`.
5. Езикът се сменя от Настройки → Език → Български. Темата, цветът и размерът на шрифта са там също.
6. Всичко, което контролира вида и поведението на приложението, е в
   `app/src/main/java/com/vislupus/stepanalyzer/config/AppConfig.kt`; математиката на
   алгоритмите е в `core/.../config/CoreDefaults.kt`; текстовете – в `tools/strings_src.py`.
