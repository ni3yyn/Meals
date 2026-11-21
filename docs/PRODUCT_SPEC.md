# Meals — product and implementation brief

## Product direction
A native Android meal planner for Algeria. Set a weekly food budget, choose meals, and get one consolidated shopping list with quantities calculated from the selected recipes.

The first release is local-first. A person opens the app and starts using it immediately. There is no sign-in, phone number, OTP, account creation, cloud profile, or authentication SDK.

## Implemented in the starter build
- Arabic-first interface with right-to-left layout.
- Weekly budget and wilaya stored locally on the device.
- Offline starter recipe catalogue with common Algerian dishes.
- Recipe search and category filters.
- Add/remove recipes from a weekly plan.
- Ingredient aggregation into a deduplicated shopping list.
- Shopping-list check-off state saved locally.
- Explicit demo-price warning so sample costs are not mistaken for live market prices.
- Release R8 minification, obfuscation, and resource shrinking.
- Unit tests for shopping-list aggregation.

## Main user flows

### First launch
1. Launch directly into the home screen.
2. No onboarding wall and no account prompt.
3. Use the default weekly budget and default wilaya, or open settings to change them.
4. Start adding meals immediately.

### Plan meals
1. Open Recipes.
2. Search by dish name or description, or filter by category.
3. Review preparation time, servings, ingredient preview, and the clearly labelled demo cost.
4. Add a recipe to the weekly plan.
5. The home screen updates the selected-meal count and estimated total.

### Shop
1. Open Purchases.
2. The app combines duplicate ingredients from all planned recipes.
3. Quantities are combined only when both the ingredient ID and unit match.
4. Mark items as purchased.
5. Checked items remain checked after the app is closed and reopened.
6. Clear the weekly plan when starting over.

### Settings
1. Edit the weekly budget in DZD.
2. Enter the wilaya manually.
3. Save locally. No location permission is requested.

## Price-data rules
The starter catalogue contains illustrative costs only. They are not claims about current Algerian retail prices and must never be presented as verified local data.

When price providers are integrated, each observation should retain:
- canonical product/ingredient ID;
- amount and normalized unit (kg, litre, piece, pack);
- price in DZD;
- wilaya or geographic coverage;
- source/provider and source URL where available;
- observed timestamp and ingestion timestamp;
- confidence/verification status.

The costing engine must distinguish consumed-ingredient cost from the cash required to buy whole retail packages. If a local observation is missing or stale, show an estimate with its age and uncertainty rather than silently implying a current price.

## Proposed next implementation phases
1. **Recipe and ingredient data layer:** structured recipes, serving scaling, dietary labels, preparation steps, and ingredient aliases in Arabic/French.
2. **Price ingestion:** provider adapters, normalized price observations, wilaya-aware fallback, freshness rules, and user-submitted price reports.
3. **Budget planner:** plan several days, tune servings, compare plan cost with budget, suggest lower-cost substitutions, and explain cost assumptions.
4. **Pantry:** track what is already at home and subtract it from the shopping list.
5. **Shopping experience:** store sections, quantity editing, multiple lists, sharing/export, and receipt capture.
6. **Nutrition and preferences:** optional dietary preferences, allergens, nutrition estimates, and household portions.
7. **Sync (optional):** only add cloud sync when it has a clear user benefit. The app must remain usable without an account.

## Architecture
- Android application, Kotlin, Jetpack Compose, Material 3.
- Single-activity UI with screen-level composables.
- DataStore Preferences for lightweight local user settings and planning state in the starter phase.
- Pure Kotlin shopping-list calculation, separated from UI and persistence.
- Future larger catalogues should move to Room, with migrations and repository interfaces.
- Provider/network code should remain behind repository interfaces; UI must not depend on a specific price API.

## Privacy and account policy
Authentication is not part of the current scope. Do not add Firebase Auth, Supabase Auth, Google Sign-In, phone OTP, or login gating unless the product owner explicitly requests it. Local planning must work offline and without registration.

## Build and release
- Android Gradle Plugin 9.5.0.
- Kotlin 2.4.21 with AGP built-in Kotlin support.
- Jetpack Compose BOM 2026.11.01.
- Java 17 bytecode target.
- minSdk 26, targetSdk 36, compile SDK 37.1.
- Release builds enable minification and resource shrinking.
- Keep R8 rules narrow; test minified release builds when adding reflection, serialization, or native SDKs.
