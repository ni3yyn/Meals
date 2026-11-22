# Midati

Kotlin Multiplatform meal planner for Algeria, built with Jetpack Compose and Compose Multiplatform. Android remains the current application host; reusable UI, theme tokens, domain models and the recipe catalogue are being shared through the `:shared` module. Android and iOS targets are configured, while iOS persistence and the complete iOS app host remain in the migration roadmap.

## No-auth policy
The app opens directly into local first-run setup and then the planner. No authentication, email, phone number, OTP, cloud account or user-account system is included. The core app works offline.

Kitchen-equipment awareness and recipe filtering based on equipment are intentionally excluded.

## Implemented
- Arabic-first Compose interface with right-to-left layout.
- Full-screen first-run onboarding (`shared/.../ui/onboarding`) with Compose-drawn vector illustrations and refraction-free Liquid Glass bubbles.
- KMP `:shared` module targeting Android, iOS devices and iOS simulators.
- Shared Compose component library, Midati theme and color tokens in `shared/src/commonMain`.
- Shared Kotlin data models and bundled Algerian ingredient/recipe catalogue in `commonMain`.
- Room-backed local ingredient and recipe catalogue, weekly meal slots, pantry and shopping-list state.
- Seven-day planner with breakfast, lunch and dinner slots and deterministic local plan generation.
- Household serving scaling, configurable breakfast/lunch/dinner slots and previous/next week navigation.
- Vegetarian/vegan hard filters plus soft preferences for disliked ingredients and preferred cooking time.
- Configurable budget safety buffer and per-week plan generator/price-snapshot metadata; each generated week keeps its own copy of the prices it used.
- Locally saved favourite recipes.
- Configurable allergy exclusions applied as hard recipe filters, including checks against locked meals during plan generation.
- User price observations expire from basket calculations after 30 days; old reports remain visible as history but are not treated as current prices.
- Arabic/French recipe and ingredient-alias search.
- Searchable pantry ingredient picker, editable stock records, exact deductions and approximate-stock reminders.
- Optional pantry expiry dates and storage notes, stored locally.
- Consolidated shopping requirements and whole-pack rounding.
- Consumed-ingredient value separated from cash needed to buy packages.
- Week-specific shopping lists with category grouping, persistent checks, editable generated purchase amounts, shop notes, unavailable flags, actual-spend entry and optional prices for custom items.
- Local price observations with pack size, date, optional commune, shopping channel, shop context and wilaya; reported values remain unverified and device-local.
- Share the current shopping list as plain text using Android's native share chooser.
- Weekly budget, wilaya, optional commune, shopping channel and preferred shop stored locally.
- R8 minification, obfuscation and resource shrinking in release builds.

**Price warning:** bundled observations are explicitly tagged as demo values, not current Algerian market prices. No live retailer or government price feed is connected yet.

## Build
JDK 17, Gradle 9.6.0 and Android SDK Platform 37.1:

```bash
./gradlew testDebugUnitTest assembleDebug
./gradlew assembleRelease
```

Debug ID: `dz.meals.app.debug`  
Release ID: `dz.meals.app`

See [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) for status and the remaining roadmap.


## Signed releases and in-app updates

Pushing a `v*` version tag or manually dispatching the signed release workflow publishes an APK. The workflow builds the R8-minified Android release variant, verifies the APK signature and package ID, uploads a 90-day Actions artifact, then publishes `meals.apk` as a GitHub Release asset. The app's update button checks the latest stable release and opens that APK through Android's browser/download flow.

The release workflow requires these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: the Base64-encoded, persistent Midati release keystore.
- `ANDROID_KEYSTORE_PASSWORD`: that keystore's password.
- `ANDROID_KEY_ALIAS`: the key alias.
- `ANDROID_KEY_PASSWORD`: the key password.

Create the keystore once with `keytool -genkeypair -v -keystore meals-release.jks -alias meals -keyalg RSA -keysize 4096 -validity 10000`, encode it with `base64 -w0 meals-release.jks`, and save the result and passwords in **Settings → Secrets and variables → Actions** for this repository. Keep a secure offline backup of the keystore. Never commit it, and do not replace or lose it: every update to an installed release must use the same signing key.

The normal Android CI workflow intentionally uploads only the debug APK. It no longer publishes an unsigned release APK that Android cannot install.


## Kotlin Multiplatform migration

See [docs/MULTIPLATFORM_MIGRATION.md](docs/MULTIPLATFORM_MIGRATION.md) for the shared source-set layout, platform boundaries and remaining work before iOS is release-ready.
