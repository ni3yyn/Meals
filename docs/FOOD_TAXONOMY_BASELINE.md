# Canonical food taxonomy — stage 1 and 2 baseline

This document governs the canonical food identity and category data contract. It records the initial state before classification improvements, Room normalization and screen integration.

## Frozen starting point

Captured from main commit `6476f9e510e7a39826236044ec07ab427632f5f2` on 2026-11-24.

| Invariant | Baseline |
|---|---:|
| Generic food identities | 4,649 |
| Existing food UUIDs | 4,649 |
| Legacy Room ingredient IDs mapped | 1,261 |
| Recipe ingredient rows mapped | 8,623 |
| Recipe-used food identities | 836 |
| Main categories | 22 |
| Subcategories | 82 |
| Taxonomy locales | Arabic, English, French |

`data/products/catalogue-baseline.json` is the machine-readable record. In addition to the counts above, it contains the complete starting set of food UUIDs, every legacy Room-ID-to-food-UUID mapping, every existing `(recipeId, ingredientIndex)` mapping, the ordered category/subcategory ID hierarchy, and source Git blob SHAs.

The baseline deliberately protects food identity and recipe compatibility while allowing future additions. Existing UUIDs cannot silently disappear or be replaced. Existing legacy mappings and recipe ingredient rows cannot silently change. Any intentional identity merge or legacy mapping change requires a reviewed baseline migration.

The baseline does not freeze localized food names, aliases, accepted units, category assignments or optional metadata. Those are allowed to improve while the canonical UUID remains stable. In particular, stage 3 may correct category assignments without creating new food identities.

## Canonical taxonomy

`data/products/categories.json` is the sole public taxonomy source. It defines exactly:

- 22 main categories;
- 82 subcategories;
- Arabic (`ar`), English (`en`) and French (`fr`) display labels.

Each category uses a stable `foodcat-*` ID. Each subcategory uses a stable `foodsub-*` ID nested under exactly one parent category. IDs are not derived from names and must never change when a label is edited or translated.

Localized category labels are presentation data, not identifiers. Food identity records reference the taxonomy through `categoryId` and `subcategoryId`. They must not duplicate category labels or use translated strings as grouping keys.

External taxonomy identifiers (for example Open Food Facts or FoodOn classes) are source evidence only. They are not the app's public category IDs.

## Validation and change policy

`data/products/taxonomy.schema.json` documents the JSON structure. `tools/validate_food_taxonomy.py` is the authoritative standard-library validator and checks:

- exact category/subcategory counts and locale contract;
- valid, unique, stable IDs and unambiguous parentage;
- complete Arabic, English and French labels, including Arabic-script checks;
- stable category/subcategory ordering and parent relationships against the baseline;
- valid category references on every food identity;
- absence of duplicated category-name fields in generated food records;
- UUIDv4 validity, persistent registry coverage and English-name uniqueness;
- preservation of all baseline food IDs, legacy mappings and recipe rows;
- consistency between the catalogue, crosswalk and generated build audit.

Run it from the repository root:

`python tools/validate_food_taxonomy.py`

Any taxonomy restructuring, ID retirement, category addition/removal or parent reassignment must be treated as a versioned data migration. Do not silently repurpose IDs. Label corrections can be made without changing IDs, but all three translations must remain present and reviewed.

## Application integration contract — Room v17

The canonical taxonomy is now used by the Android application and shared Compose UI.

### Normalized persistence

Room schema version 17 stores taxonomy definitions once:

| Table | Responsibility |
|---|---|
| `food_categories` | The 22 localized main categories and their stable IDs |
| `food_subcategories` | The 82 localized subcategories, parent IDs and sort order |
| `food_items` | The 4,649 generic food identities and their category/subcategory IDs |
| `ingredients` | Recipe/application ingredient masters with category/subcategory IDs, never localized category strings |

Both `food_items` and `ingredients` reference the composite `(categoryId, subcategoryId)` pair in `food_subcategories`. A foreign-key relationship prevents an ingredient from using a subcategory belonging to a different category. Category labels are resolved from taxonomy rows at the presentation boundary.

Recipe metadata such as a recipe's own category (for example, a meal type or editorial classification) is deliberately separate from food taxonomy. It must not be replaced by an ingredient's category.

The v16-to-v17 migration rebuilds the ingredient master table transactionally, verifies the copied row count and checks foreign-key integrity. The existing v13-to-v17 migration chain is exercised by the Robolectric Room migration workflow. Pantry, shopping, price, recipe, cooking and plan records remain attached to their canonical ingredient UUIDs.

### Repository and identity contract

Recipe imports resolve their legacy ingredient keys through the persistent UUID crosswalk before writing recipe ingredients. The generated crosswalk contains 1,261 legacy IDs and 8,623 recipe ingredient rows mapping to 836 canonical recipe-used food identities. Imported recipe ingredients obtain category IDs directly from their canonical food records. Unknown/custom entries use the explicit `foodcat-other` / `foodsub-other-general` fallback until a reviewed canonical identity is available.

The repository exposes taxonomy definitions and ID-bearing ingredient, pantry, shopping and price-summary models. Basket grouping, shopping grouping, and dietary exclusions operate on IDs; translated category labels are not business keys.

### Search and app browsing contract

The Pantry screen browses the full hierarchy (22 categories → their 82 subcategories → the actual catalogue foods) and shows counts including zero-count subcategories. Search scans the complete available food master list rather than a fixed-size preview. The search index combines Arabic, English and French names, stored aliases, and the associated category/subcategory labels.

Search normalization is presentation-only: it folds Arabic diacritics, tatweel and common hamza/alef/ya forms; Latin case and common French accents; punctuation and repeated whitespace. It does not rewrite stored names, identity UUIDs, taxonomy IDs or user data. Every query token must be found in the combined searchable fields.

Shopping groups generated ingredients by category ID and then subcategory ID, using canonical taxonomy sort order and the active locale for labels. Shared shopping-list text uses the same hierarchy. No translated label is used as a grouping key.

### Release acceptance

Before a release is considered complete, CI must pass:

- catalogue parser/matching unit tests, reviewed category-override regression tests, catalogue generation and taxonomy validator;
- Room migration and application unit tests, including fresh and upgrade-path schema checks;
- signed minified release build, release unit tests and Android lint.

The release APK is built from the generated catalogue commit on `main`; a successful build of an earlier source revision is not evidence that the latest generated taxonomy data is in the APK.

## Final generated catalogue snapshot

The verified generated snapshot on `main` contains:

- 4,649 stable UUIDv4 generic food identities;
- 22 main categories and 82 subcategories, with complete Arabic, English and French taxonomy labels;
- 0 missing Arabic, English or French food names;
- 0 duplicate food UUIDs and 0 duplicate normalized English food names;
- 1,261 legacy Room ingredient mappings and 8,623 recipe ingredient rows mapped to 836 recipe-used food identities;
- 0 missing recipe rows, 0 legacy ID conflicts and 0 registry split reassignments.

The recipe-linked red-cabbage identity keeps its existing UUID and is displayed as “Shredded red cabbage” so it remains distinguishable from the generic “Red cabbage” identity without breaking its recipe crosswalk. Search for “red cabbage” matches both through token-based search.

This is a generic food-identity catalogue, not yet a retail SKU catalogue: product barcodes, package variants, product photography and nutrition records are not supplied for these 4,649 generic identities. Those belong to a later market-product enrichment layer and must not be represented by fabricated placeholder data.

