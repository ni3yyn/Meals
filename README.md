# مائدتي (Meals)

Native Kotlin Android meal planner for Algeria.

## No-account policy
The app opens directly into the planner. No authentication, email, phone number, OTP, cloud account or network connection is required for the implemented features.

## Implemented
- Arabic-first Jetpack Compose interface with right-to-left layout.
- Room-backed local ingredient and recipe catalogue, weekly meal slots, pantry and shopping-list state.
- Seven-day planner with breakfast, lunch and dinner slots and deterministic local plan generation.
- Household serving scaling, configurable breakfast/lunch/dinner slots and previous/next week navigation.
- Vegetarian/vegan hard filters plus soft preferences for disliked ingredients and preferred cooking time.
- Configurable budget safety buffer and per-week plan generator/price-snapshot metadata.
- Locally saved favourite recipes.
- Configurable allergy exclusions applied as hard recipe filters, including checks against locked meals during plan generation.
- User price observations expire from basket calculations after 30 days; old reports remain visible as history but are not treated as current prices.
- Arabic/French recipe and ingredient-alias search.
- Searchable pantry ingredient picker, editable stock records, exact deductions and approximate-stock reminders.
- Optional pantry expiry dates and storage notes, stored locally.
- Consolidated shopping requirements and whole-pack rounding.
- Consumed-ingredient value separated from cash needed to buy packages.
- Week-specific shopping lists with category grouping, persistent checks, editable generated purchase amounts, shop notes, unavailable flags, actual-spend entry and optional prices for custom items.
- Local price observations with pack size, date, shop context and wilaya; reported values remain unverified and device-local.
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
