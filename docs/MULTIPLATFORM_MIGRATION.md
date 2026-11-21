# Kotlin Multiplatform migration

## Current foundation

Meals now has a dedicated `:shared` Kotlin Multiplatform library. It builds Android and Apple targets:

- `android` (Android KMP library target)
- `iosArm64` (physical iPhones/iPads)
- `iosSimulatorArm64` (Apple Silicon simulators)
- `iosX64` (Intel simulator compatibility)

The shared module produces a static `MealsShared` framework for Xcode integration.

## Moved into `commonMain`

The following production sources are now compiled as common Kotlin:

- Domain/data models in `dz.meals.app.data`
- The bundled ingredient and recipe catalogue
- The shared Compose component system
- Mida color tokens and Compose theme

The Android app consumes these sources from `:shared`, so Android and future iOS screens use the same component implementations and tokens.

## Still Android-specific

The existing `:app` module remains the Android application and owns platform services that must not leak into common code:

- `MainActivity`, Android manifest, launcher resources and APK configuration
- Room database initialization and Android Room persistence adapter
- DataStore preferences adapter
- GitHub APK update checking and Android download intents
- Android application composition root

The repository and settings adapters currently depend on Android `Context`. They remain in `:app` until the persistence boundary is migrated deliberately.

## Remaining migration steps

1. Extract repository contracts and platform-neutral use cases from the current Android repository.
2. Move date/time and price calculation code to multiplatform-safe Kotlin APIs; keep Room entities out of common domain code.
3. Migrate persistence to a KMP-compatible database implementation with Android and iOS drivers, preserving the existing schema and local user data.
4. Move the Compose screens and application state holder to `commonMain`, replacing Android-only APIs with injected platform services.
5. Add the iOS host project and call the exported `MealsShared` framework from SwiftUI.
6. Run iOS simulator/device builds and persistence migration tests on a macOS runner with Xcode before treating iOS as release-ready.

## Platform constraints

A Mac with Xcode is required to build, run, sign and distribute the iOS app. The current Linux Android CI can validate the Android target and the common sources consumed by Android; it cannot replace an Xcode/iOS build.

Do not move Android APIs, `java.time`, Android Room entities, `Context`, `Intent`, or Android DataStore into `commonMain`. Use common interfaces with platform implementations or a multiplatform library.
