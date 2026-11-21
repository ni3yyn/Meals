# مائدتي (Meals)

Native Kotlin Android app for planning meals and shopping on a budget in Algeria.

## Product principles
- **No authentication:** launch directly into the app. No email, phone number, OTP, account, or login gate.
- **Offline-first:** planning settings and shopping progress are stored on the device.
- **Algerian-first:** Arabic RTL UI and DZD budgeting.
- **Honest prices:** starter values are demo estimates, not live market prices.

## First implemented slice
- Weekly budget and wilaya settings
- Searchable recipe catalogue with categories
- Weekly meal selection
- Consolidated shopping list with quantities
- Persistent check-off state
- R8 minification and resource shrinking on release

## Open the project
Open this repository in a recent Android Studio version with JDK 17 and Android SDK Platform 37 / extension 1 installed. The build uses Gradle 9.6.0 and Android Gradle Plugin 9.5.0.

## Build
With Gradle 9.6.0 installed:
```bash
gradle testDebugUnitTest assembleDebug
gradle assembleRelease
```

The debug application ID is `dz.meals.app.debug`; the release application ID is `dz.meals.app`.

See [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) for user flows, local-data rules, price-data requirements, and the roadmap.
