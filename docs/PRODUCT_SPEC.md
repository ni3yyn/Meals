# Meals — implementation status against the product specification

## Product and privacy policy
This is a native Android app for Algeria. It deliberately starts directly in local guest mode. No authentication, sign-in, phone number, OTP, cloud profile or auth SDK is required for the current feature set. Planning works without a network connection after installation.

## Implemented milestone
- Kotlin, Jetpack Compose and Material 3; Arabic RTL interface.
- Room persistence for canonical ingredients, recipes, recipe ingredients, bilingual preparation steps, week slots, pantry items, price observations and shopping items.
- DataStore for budget, wilaya, household servings, selected meal slots, allergies, vegetarian/vegan pattern, soft disliked-ingredient preferences, preferred maximum cook time, budget buffer and local first-run setup completion.
- Ingredient catalogue with stable IDs, Arabic/French labels, common Arabic/French/Darija aliases and base units.
- Seed-catalogue validation checks unique IDs, ingredient existence, normalized units, positive amounts and allergen metadata coverage.
- Search by Arabic/French recipe name, description and ingredient aliases.
- Seven-day planner with configurable breakfast/lunch/dinner slots, manual recipe assignment, serving adjustments and meal locking.
- Regeneration preserves locked, cooked and manually selected/cleared slots by default; an explicit “fill empty slots only” option preserves all already-populated slots.
- Deterministic local week generator. It preserves locked slots and excludes recipes intersecting configured allergy IDs. If a previously locked meal conflicts with a newly selected allergy exclusion, generation is blocked with a prompt to unlock or replace it; the app never silently bypasses the exclusion.
- Pantry exact-quantity records and approximate reminders, with optional user-entered expiry dates and storage notes. Only numeric exact stock can reduce basket requirements.
- Whole-week ingredient aggregation by canonical ingredient ID and unit.
- Separate consumed ingredient value and purchase cash outlay, with whole retail pack rounding when pack size is known.
- Plan-run metadata saved per week: generator version, generation timestamp, price snapshot timestamp, budget, wilaya and selected meal slots.
- Week-scoped shopping lists; generated and manually entered items remain associated with the plan week.
- Planned meals can be marked cooked after confirmation; exact pantry deductions are recorded and can be undone. A cooked meal is read-only until its deduction is undone.
- Manual plan edits are persisted separately from generated suggestions so regeneration cannot silently replace user decisions. Approximate stock is never deducted.
- Shopping item details for manual purchase-quantity override, store notes, availability and user-entered actual spend.
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
1. Seed the local recipe and ingredient catalogue into Room on first run.
2. Show a first-run setup for budget, wilaya, household portions, meal slots, allergies and dietary preferences.
3. Save setup completion locally, then open the weekly plan.
4. The setup can be dismissed and revisited from Settings. No account, phone number, location permission or network access is required.

### Weekly plan
1. View seven days with the selected breakfast, lunch and/or dinner slots.
2. Generate a week using household servings, budget and strict allergy filtering.
3. Lock meals that must remain unchanged.
4. Replace or clear a slot, or change portion counts.
5. Plan generation treats allergies and vegetarian/vegan patterns as hard constraints. Disliked ingredients and preferred cook time are soft ranking inputs.
6. A configurable budget buffer is applied to the planner target.
7. Each generated week records its engine version and price snapshot.
8. The shopping basket recalculates from the whole plan.
9. Marking a meal cooked asks for confirmation, deducts only exact pantry quantities and records a reversible deduction.

### Recipe discovery
1. Search Arabic, French and catalogue aliases.
2. Filter by curated categories.
3. Read recipe ingredients and preparation instructions.
4. Add a recipe to the next available meal slot.

### Pantry
1. Search ingredients by Arabic, French or stored aliases, then select a canonical ingredient.
2. Enter an exact quantity in its base unit, or mark the stock as approximate. Existing records can be edited.
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
4. Preserve checked state within that week, group ingredients by category and allow manual additions or edits.
5. Edit a generated item's intended purchase amount, add a shop note, mark it unavailable or enter actual spend. Recipe requirements remain unchanged.

## Cost and price integrity
The app distinguishes consumed ingredient value from cash required to buy full packages. A recipe requiring 120 g can have a 120 g consumed value while requiring a 500 g package purchase. Remaining package contents can be used later.

Price observations store canonical ingredient ID, pack size, normalized unit, DZD price, source, geographic label, observation timestamp, price kind and confidence. Non-demo observations older than 30 days are excluded from current basket estimates; the sample/demo fallback remains explicitly labelled. Current sample observations are tagged as demo, have no observation timestamp or geographic coverage, and are labelled accordingly. They must not be described as live or verified prices. Missing prices remain unknown rather than being silently treated as zero.

## Safety and limitations
- Allergen filtering is only as complete as the curated recipe and ingredient metadata. Empty allergen metadata does not prove that a recipe is safe.
- The week generator is a deterministic heuristic, not a proof of globally optimal cost. Its output is an initial proposal for user review.
- Approximate pantry quantities are not deducted.
- Data is device-local. Clearing app storage or uninstalling removes the local plan and pantry.

## Remaining work from the full specification
1. Complete the first-run welcome/onboarding flow, Arabic/French UI localization, commune and shopping-channel preferences.
2. Expand and editorially review the recipe catalogue, regional coverage, recipe versioning, attribution/licensing and bilingual instructions.
3. Build real price-provider adapters, source/licence register, wilaya/commune/store observations, configurable freshness/confidence and moderation.
4. Add receipt/photo price capture with explicit consent, redaction and user review.
5. Add recipe import (Share sheet, URL, image and manual entry) with private draft review, plus editable personal recipes and cookbooks.
6. Add optional pantry/fridge scan with user confirmation, expiry reminders and cooked-meal stock deduction with undo.
7. Add transparent bounded-swap plan optimization, ingredient substitutions and more complete budget feasibility explanations.
8. Add data export/import, local backup, notifications and accessibility/performance/device tests.
9. Cloud sync, accounts, household invitations/roles, subscriptions, purchase verification, admin console and server operations are deferred. They must remain optional and must never gate local use.

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
