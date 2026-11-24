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
