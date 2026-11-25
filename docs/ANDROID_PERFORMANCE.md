# Android performance workflow

Midati uses the Jetpack Compose performance guidance rather than blanket stability annotations. Kotlin 2.4 enables Strong Skipping by default, so keep models and inputs simple and stable; prefer cheaper composables, cached work and deferred animation-state reads over forcing every model to `@Immutable`.

## What is enabled

- Release APKs are already R8-minified and resource-shrunk.
- Compose animation values that only affect position, scale, elevation or drawing are read in graphics/layout/draw lambdas where appropriate.
- Dense catalogue and shopping content uses keyed lazy layouts and cached grouping/lookups.
- Shopping snapshots run their CPU-side mapping and basket calculations on `Dispatchers.Default`; Room suspend queries and transactions continue using Room's own executors.
- Modal glass keeps the shared Haze blur, warm tint and white optical rim. The modal Haze radius and Android blur-behind radius are both 17dp.
- `:baselineprofile` contains startup/profile journeys and Macrobenchmark tests for cold start and the Shopping category sheet.

## Capture a real Baseline Profile

Profile generation requires a connected Android device. Use Android 13 (API 33) or newer to generate without root access. Connect the device, then run:

```bash
./gradlew :app:generateBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
```

The app module is configured with `saveInSrc = true` and `mergeIntoMain = true`. After generation, verify and commit the file created under:

```
app/src/main/generated/baselineProfiles/
```

Do not turn on `automaticGenerationDuringBuild` for the headless GitHub release runner: profile capture needs a connected device. The release build consumes the last generated, committed profile.

## Run Macrobenchmarks

Use a physical Android device (API 33+ is recommended) and run the tests in `baselineprofile/src/main/java/dz/meals/app/baselineprofile/MidatiPerformanceJourneys.kt` from Android Studio, or run:

```bash
./gradlew :baselineprofile:connectedAndroidTest
```

The startup benchmark reports startup timing. The category benchmark records frame timing while navigating into and scrolling the Shopping category sheet. Compare results on the same device, Android version, build variant and thermal state; do not treat a debug build or a different device as an apples-to-apples comparison.

## Device coverage

No code can guarantee identical frame rates on every Android GPU, OEM compositor or Android release. The goal is to keep CPU work off the UI thread, virtualize long lists, reduce per-frame composition, retain the branded blur at a lower radius and measure the actual critical journeys on representative low-, mid- and high-tier devices.
