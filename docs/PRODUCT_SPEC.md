# Meals — implementation status against the product specification

## Product and privacy policy
This is a native Android app for Algeria. It deliberately starts directly in local guest mode. No authentication, sign-in, phone number, OTP, cloud profile or auth SDK is required for the current feature set. Planning works without a network connection after installation.

## Implemented milestone
- Kotlin, Jetpack Compose and Material 3; Arabic RTL interface.
- Room persistence for canonical ingredients, recipes, recipe ingredients, bilingual preparation steps, week slots, pantry items, price observations and shopping items.
- DataStore for household budget, wilaya, household servings and allergy exclusions.
- Ingredient catalogue with stable IDs, Arabic/French labels, common Arabic/French/Darija aliases and base units.
- Seed-catalogue validation checks unique IDs, ingredient existence, normalized units, positive amounts and allergen metadata coverage.
- Search by Arabic/French recipe name, description and ingredient aliases.
- Seven-day planner with lunch and dinner slots, manual recipe assignment, serving adjustments and meal locking.
- Deterministic local week generator. It preserves locked slots and excludes recipes intersecting configured allergy IDs. If a previously locked meal conflicts with a newly selected allergy exclusion, generation is blocked with a prompt to unlock or replace it; the app never silently bypasses the exclusion.
- Pantry exact-quantity records and approximate reminders, with optional user-entered expiry dates and storage notes. Only numeric exact stock can reduce basket requirements.
- Whole-week ingredient aggregation by canonical ingredient ID and unit.
- Separate consumed ingredient value and purchase cash outlay, with whole retail pack rounding when pack size is known.
- Persistent shopping checkboxes, category-grouped rows, editable user-added shopping items and saved recipe favourites.
- Local price-report capture with pack quantity, DZD price, wilaya, observation timestamp and shop context. Exact-wilaya observations take precedence over national and demo values.
- Unit tests for serving scaling, exact/approximate pantry handling, package rounding and catalogue integrity.
- Saved favourite recipes stored in Room, with a versioned v1-to-v2 database migration.
- Previous/next week navigation; the planner anchors weeks to Monday.
- Locked meals that conflict with a newly selected allergy exclusion block regeneration until the user unlocks or replaces them.
- Non-demo user price observations older than 30 days (or with future/invalid timestamps) are excluded from live basket estimates.
- Release R8 minification, obfuscation and resource shrinking.

## Current user flows

### First launch
1. Launch directly into the weekly plan.
2. Seed the local recipe and ingredient catalogue into Room on first run.
3. Set budget, wilaya, household portions and allergy exclusions.
4. No account, location permission or network access is required.

### Weekly plan
1. View seven days with lunch and dinner slots.
2. Generate a week using household servings, budget and strict allergy filtering.
3. Lock meals that must remain unchanged.
4. Replace or clear a slot, or change portion counts.
5. The shopping basket recalculates from the whole plan.

### Recipe discovery
1. Search Arabic, French and catalogue aliases.
2. Filter by curated categories.
3. Read recipe ingredients and preparation instructions.
4. Add a recipe to the next available meal slot.

### Pantry
1. Select a canonical ingredient.
2. Enter an exact quantity in its base unit, or mark the stock as approximate.
3. Exact stock reduces the amount to buy.
4. Approximate reminders are visible but never treated as exact quantities.

### Price reports
1. Select an ingredient and enter the observed pack size in its canonical base unit.
2. Enter the price, shop context and the currently selected wilaya.
3. The app timestamps the observation and stores it locally as an unverified user report.
4. The basket prefers a report from the selected wilaya, then a national observation, then a clearly labelled demo value.
5. Reports are not uploaded, moderated or shared with other devices in this version.

### Shopping
1. Aggregate repeated ingredients across planned meals.
2. Display recipe requirement, pantry deduction and purchase quantity separately.
3. Round purchase quantity up to complete packages when pack-size data exists.
4. Preserve checked state, group ingredients by category and allow manual additions or edits.

## Cost and price integrity
The app distinguishes consumed ingredient value from cash required to buy full packages. A recipe requiring 120 g can have a 120 g consumed value while requiring a 500 g package purchase. Remaining package contents can be used later.

Price observations store canonical ingredient ID, pack size, normalized unit, DZD price, source, geographic label, observation timestamp, price kind and confidence. Non-demo observations older than 30 days are excluded from current basket estimates; the sample/demo fallback remains explicitly labelled. Current sample observations are tagged as demo, have no observation timestamp or geographic coverage, and are labelled accordingly. They must not be described as live or verified prices. Missing prices remain unknown rather than being silently treated as zero.

## Safety and limitations
- Allergen filtering is only as complete as the curated recipe and ingredient metadata. Empty allergen metadata does not prove that a recipe is safe.
- The week generator is a deterministic heuristic, not a proof of globally optimal cost. Its output is an initial proposal for user review.
- Approximate pantry quantities are not deducted.
- Data is device-local. Clearing app storage or uninstalling removes the local plan and pantry.

## Remaining work from the full specification
1. Expand and editorially review the recipe catalogue, regional coverage and bilingual instructions.
2. Build real price-provider adapters, source/licence review, wilaya/commune and store observations, freshness handling and user-submitted price moderation.
3. Add price entry and receipt capture with reviewed suggestions.
4. Add ingredient substitution and budget optimization with transparent scoring.
5. Add week history, meal-prep/cook mode, favourites, cookbooks and personal recipe creation/import.
6. Add dietary profiles, nutrition calculations and more allergen controls after curating the data.
7. Add data export/import, local backup, reminders and expiry tracking.
8. Cloud sync, accounts, household invitations and server-side roles are deferred. They must remain optional and must never gate local use.

## Architecture
- Single-activity Compose UI.
- data/local: Room entities, DAO and database.
- data: seed catalogue, DataStore preferences and repository.
- domain: cost and serving calculations.
- Room is the source of truth for structured local data; DataStore contains lightweight settings.
- Future external price/network services belong behind repository interfaces. No privileged key should ship inside the APK.

## Build and release
- Android Gradle Plugin 9.5.0, Kotlin 2.4.21, KSP 2.3.12.
- Room 2.8.5 and Jetpack Compose BOM 2026.11.01.
- JDK 17, Gradle 9.6.0, Android SDK 37.1, minSdk 26, targetSdk 36.
- Release builds enable R8 minification, obfuscation and resource shrinking.
