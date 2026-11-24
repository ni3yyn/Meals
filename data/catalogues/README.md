# Midati generic food catalogue

This directory holds the generated generic food catalogue. It is not the
retailer/market-product catalogue and does not contain brands, barcodes, pack
quantities or prices.

## Build
Run the GitHub Actions workflow named "Build generic food catalogue". It downloads
pinned versions of Open Food Facts and FoodOn, verifies Git-blob SHA-1 hashes,
reads the complete recipe catalogue, matches exact normalized names and aliases,
adds recipe-only concepts when needed, and validates the complete recipe mapping.
Downloaded datasets are temporary workflow inputs and are not committed here.

Update source-lock.json deliberately when refreshing an upstream dataset version.

## Generated files
- food-items.json: generic food identity, trilingual names where available, aliases,
  accepted units, category/subcategory IDs, optional image/origin/nutrition fields,
  and FoodOn external class IDs.
- categories.json: Midati-owned category hierarchy.
- recipe-ingredient-map.json: recipe ID and ingredient index to canonical UUID.
- food-catalogue-report.json: coverage, matches, skips and category counts.
- food-item-id-registry.json: persistent UUID registry. Do not delete casually.
- curated-food-items.json: reviewed Algerian vocabulary additions, including Lben
  and Raib where global labels are incomplete.

## Schema rules
A generic food record contains an opaque UUID, names in ar/en/fr, acceptedUnits,
imageUrl, categoryId, subcategoryId, aliases in ar/en/fr, countryOfOrigin,
nutrition, and externalIds.foodOn. Missing translations or unavailable metadata
remain null; the build does not invent them. Quantity and pack size belong to the
future market_products table, not the generic food record.

Accepted units are gathered from existing recipe usage. Catalogue-only entries
receive conservative category/name defaults. Recipe quantities, units, and
preparation notes remain attached to recipe ingredient rows.

## Duplicate and matching policy
Only exact normalized labels and aliases are auto-matched. Ambiguous aliases
are not auto-merged. Every existing recipe ingredient row must map to one
canonical food UUID; unmatched or ambiguous recipe terms receive a dedicated
recipe-derived entry rather than being dropped. Canned, frozen, dried and
fermented foods remain distinguishable when they are named as different foods.

## Attribution and licensing
Open Food Facts ingredients taxonomy: Open Food Facts. The database is under
ODbL 1.0 and individual contents under DbCL 1.0. Review share-alike and
attribution obligations before redistribution:
https://openfoodfacts.github.io/openfoodfacts-server/api/tutorials/license-be-on-the-legal-side/

FoodOn ontology and synonym export: FoodOn, CC BY 4.0:
https://github.com/FoodOntology/foodon

Recipe-derived names retain provenance/licensing in data/recipes/ar/recipes.json.
The combined generated catalogue is not legally cleared for closed redistribution
without reviewing all source licence obligations and their interaction.
