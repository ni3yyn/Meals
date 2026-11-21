# مائدتي (Meals)

Native Kotlin Android meal planner for Algeria.

## No-account policy
The app opens directly into the planner. No authentication, email, phone number, OTP, cloud account or network connection is required for the implemented features.

## Implemented
- Arabic-first Jetpack Compose interface with right-to-left layout.
- Room-backed local ingredient and recipe catalogue, weekly meal slots, pantry and shopping-list state.
- Seven-day planner with deterministic local plan generation.
- Household serving scaling and lockable meal slots.
- Configurable allergy exclusions applied as hard recipe filters.
- Arabic/French recipe and ingredient-alias search.
- Exact pantry deductions; approximate stock is a reminder and is never deducted.
- Optional pantry expiry dates and storage notes, stored locally.
- Consolidated shopping requirements and whole-pack rounding.
- Consumed-ingredient value separated from cash needed to buy packages.
- Manual shopping items with in-place editing and persistent check-off state, grouped by ingredient category.
- Local price observations with pack size, date, shop context and wilaya; reported values remain unverified and device-local.
- Weekly budget, wilaya and household preferences stored locally.
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
