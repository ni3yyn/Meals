# Midati food catalogue

This directory contains the generic, brand-independent food identity catalogue.

- catalogue.json: canonical food items, multilingual names, aliases, category/subcategory, observed recipe units and source references.
- recipe_ingredient_map.json: mapping from each source recipe ingredient row to a canonical food item UUID.
- legacy_id_map.json: mapping from current seed ingredient IDs to canonical UUIDs for a future safe migration.
- identity_registry.json: durable registry mapping internal source identity keys to random UUIDs. Do not regenerate or delete this file.
- categories.json: editable category labels in Arabic, English and French.
- build_report.json: coverage, matching, duplicate and missing-field report.

## Identity rules

Every food item receives a random UUIDv4. UUIDs are allocated once and persisted in identity_registry.json; they are not generated from the English name, Arabic name, French name, category, or slug. Keep the registry in version control. If two concepts are later reviewed as duplicates, explicitly migrate references to one surviving UUID and keep the retired ID as a redirect/mapping; never recycle an ID.

## Scope rules

This is the generic food identity layer. Chickpeas, canned chickpeas, milk, raib, mayonnaise, tomato paste and similar foods are generic food items. Brand names, barcodes, retailer SKUs, pack quantities, pack sizes, retailer availability and local prices belong to the separate market-product and pricing systems. Recipe quantity belongs to the recipe-ingredient relationship and is not copied into the food item.

acceptedUnits lists measurement units actually observed in the recipe corpus. baseUnit is the most-used observed unit. An empty list or null base unit means there is not enough source evidence yet; do not guess a conversion.

Image URLs, country of origin and nutritional values are null unless the source actually supplies verified data. The current OFF ingredient taxonomy and FoodOn synonym export are not product-image, production-origin or full nutrient databases. Where OFF provides CIQUAL/USDA reference codes, those are preserved in sourceRefs for a later enrichment step; a reference code is not itself a nutrition value.

## Sources and licences

The build workflow downloads immutable snapshots:

- Open Food Facts ingredient taxonomy: openfoodfacts/openfoodfacts-server/taxonomies/food/ingredients.txt (ODbL 1.0 / DbCL 1.0; attribution to Open Food Facts).
- FoodOn synonym export: FoodOntology/foodon/foodon-synonyms.tsv (CC BY 4.0; attribution to FoodOn).

The combined result carries source provenance per item and a source manifest. Review the ODbL/DbCL share-alike and attribution obligations, together with FoodOn CC BY 4.0, before redistribution outside the project. This is a technical data-source note, not legal advice.

## Rebuild

The GitHub Actions workflow downloads pinned taxonomy snapshots, verifies their Git blob SHA-1 values, runs unit tests, builds the catalogue from the complete recipe corpus and commits generated outputs. Refreshing external taxonomy snapshots requires an explicit reviewed update to the pinned commit and blob SHA constants in tools/build_food_catalogue.py and the workflow.
