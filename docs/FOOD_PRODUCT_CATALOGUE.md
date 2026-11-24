# Midati generic food-item catalogue

This pipeline builds Midati's brand-independent food identity catalogue for recipes and grocery planning. It is **not** the supermarket-product catalogue: brands, barcodes, retailer SKUs, pack sizes and retailer-specific prices belong to a separate market-product layer linked to a generic food item.

## Generated files

- \`data/products/catalogue.json\`: generic food items with an opaque UUIDv4, Arabic/English/French names where sourced, aliases, accepted units, categories and source references.
- \`data/products/categories.json\`: Midati-owned multilingual category hierarchy.
- \`data/products/recipe-ingredient-map.json\`: both the legacy Room ingredient-ID map and one mapping row for every recipe ingredient occurrence.
- \`data/products/build-audit.json\`: matching coverage, missing translations/optional data, ambiguous matches, category conflicts and duplicate checks.
- \`data/products/identity_registry.json\`: persistent source-key-to-UUID registry and retired-ID redirects. **Do not delete or regenerate this file.**
- \`data/products/translations.json\`: UUID-keyed machine translations with original source text and resumable batch checkpoints.

The GitHub Actions workflow rebuilds this catalogue when the recipe corpus, categories, builder, tests or workflow change.

## Identity and translation model

A food identity has a stable, opaque UUIDv4. Its ID is not derived from Arabic, English, French, a translated name, a category or a slug. The persistent registry reuses the same UUID when a pinned source term or recipe identity is seen in a later build. Adding a translation therefore does not change the ID.

Each item stores:

- \`names.ar/en/fr\`: preferred names; unavailable translations remain null.
- \`aliases.ar/en/fr\`: exact alternate spellings and sourced synonyms.
- \`categoryId\` and \`subcategoryId\`: IDs from Midati's own category file, not source taxonomy IDs.
- \`acceptedUnits\`: observed recipe units plus conservative standard grocery units.
- \`sourceMappings\`: external OFF term/code and FoodOn class references where an exact mapping exists.
- \`imageUrl\`, \`countryOfOrigin\` and \`nutrition\`: optional fields; absent evidence stays null.

Algerian-curated vocabulary is kept separate when the global taxonomy collapses different local foods. In particular, milk, Lben (لبن) and Raib (رايب) are separate identities. Curated terms are matched only against preferred labels, not broad synonyms, to avoid accidental merging.

## Recipe compatibility and migration

The builder mirrors the current Android \`ArabicRecipeCatalogueInstaller.kt\` logic to recreate the exact legacy Room IDs:

- UniTools: \`orc-ing-unitools-\` plus the source ingredient ID, without slugification.
- Focus: the existing SHA-256 name/unit algorithm, including Android's exact unit normalization.

The generated \`recipe-ingredient-map.json\` contains:

- \`legacyRoomIngredientIdToFoodItemId\`: old Room ID to canonical UUID.
- \`recipeRows\`: one row per recipe ingredient with recipe ID, ingredient index, old Room ID and canonical UUID.

The build fails if a recipe ingredient row is unmapped, a legacy ID maps to more than one food identity, a mapping points to a missing product, or duplicate canonical IDs/English names remain. A retired canonical UUID is kept as a redirect in the registry when a reviewed merge is made.

The app now bundles the generated food catalogue and category hierarchy as verified build inputs, then installs all 4,649 generic food identities into the `food_items` Room table on first initialization. The existing `ingredients` table remains the compatibility layer for recipe, pantry, shopping and price references; the new table is the canonical multilingual metadata source keyed by the same stable UUIDs. Existing v14 databases receive the new table through a non-destructive v14-to-v15 migration. Recipe imports resolve canonical food UUIDs against this table and use the catalogue's category labels instead of heuristic name classification whenever an exact identity mapping exists.

The generated recipe crosswalk remains the compatibility bridge for legacy IDs. This phase does not create branded market products, barcodes, retailer SKUs, pack variants or real market prices.

## Categories

The categories are owned by Midati and translated in Arabic, English and French. The hierarchy includes fruits, vegetables, dairy, cheese/butter, meat and offal, poultry, seafood, eggs, legumes, grains/cereals, pasta/bakery, nuts/seeds, oils/fats, spices, herbs, sauces/condiments, sweeteners, baking/dessert ingredients, beverages, frozen foods, mushrooms/fungi and other foods, with subcategories such as fish/shellfish, chicken/turkey, offal, chickpeas/lentils, cheeses, herbs and ground/whole spices.

External taxonomy categories are evidence for mapping, not Midati's public category IDs. New source releases therefore cannot silently rename or renumber Midati categories.

## Food-name translation workflow

Food-item display names use the repository's existing no-card Google Apps Script bridge in `.github/apps-script/recipe_translation_api.gs`. The food translation runner reuses the same `GOOGLE_APPS_SCRIPT_URL` and `GOOGLE_APPS_SCRIPT_TOKEN` GitHub Actions secrets as the recipe translation workflow. It does not add a second provider, paid API, API key or Cloud billing setup.

The **Translate generic food catalogue names** Actions workflow fills only missing `ar`, `en` and `fr` names. Source-backed Open Food Facts, FoodOn and Midati-curated names are never overwritten. It groups requests by source/target language and sends up to 20 food names in one Apps Script LanguageApp call. The runner checkpoints each successful batch to `data/products/translations.json`, so a quota limit or interrupted run can resume without retranslating completed names.

Translations are keyed by stable food-item UUID and retain both their source language and exact source label. The catalogue builder applies a translation only if the current source label still matches the saved source text. A taxonomy update that changes the source label makes the translation stale instead of silently attaching it to a changed concept. Translation text never participates in UUID generation or the legacy Room crosswalk.

Category labels remain the reviewed Midati-owned Arabic/English/French values in `categories.json`; the food-name workflow does not machine-translate or replace the category hierarchy. Existing aliases remain source-backed and are not synthesized from machine translations.

The Apps Script source is versioned in this repository, but its deployed Web App lives in your Google account. After updating the `.gs` file, paste its complete contents into the existing Apps Script project's `Code.gs` and create a new Web App deployment version. Keep using the existing `/exec` URL and token secret. Updating this repository file alone does not update Google's deployed script.

Machine-generated culinary names should be reviewed before being treated as editorially approved. Existing Algerian-curated identities such as Lben and Raib keep their reviewed names and are not replaced by generated translations.

## Sources, reproducibility and rights

The build downloads exact immutable source commits and verifies Git blob SHA-1 values before parsing them:

- Open Food Facts ingredient taxonomy: \`openfoodfacts/openfoodfacts-server\`, \`taxonomies/food/ingredients.txt\`, commit \`f5abb0a0ca8ae5702f3e192219511adf211b55ee\`. It contributes multilingual labels, aliases, taxonomy parents and selected external codes.
- FoodOn: \`FoodOntology/foodon\`, \`foodon-base.owl\`, commit \`8a27493346a122e86ac1748aac24199d5af69166\`. It contributes semantic food classes, language-tagged labels/synonyms and subclass ancestry.

Open Food Facts uses ODbL 1.0 for the database and DbCL 1.0 for individual contents. FoodOn is CC BY 4.0. The combined output requires attribution and a review of ODbL share-alike obligations before redistribution or closed/commercial bundling; this documentation is not a legal clearance.

## Scope limits

Neither ingredient taxonomy is a dependable source of generic product photographs or complete nutrient panels. Recipe photos are not ingredient photos. Recipe text is not a verified nutrition database. The builder intentionally leaves unsupported images and nutrient data empty rather than fabricating them.

Country of origin must be supported by an explicit, item-specific origin field. A default regional cultivation assumption is not proof that a food item originated there.

A future market-product record will reference the generic food UUID and add brand, barcode, package quantity/unit, country-of-origin evidence, retailer SKU, price and product-specific imagery/nutrition where licensed and available.

## Canonical taxonomy contract and identity baseline

`data/products/categories.json` is the sole source of public food-category and subcategory IDs and their Arabic, English and French display labels. It currently defines exactly 22 main categories and 82 subcategories. Category and subcategory IDs are stable identifiers; localized labels are display values and must never be used as keys, foreign keys, or identity inputs.

`data/products/taxonomy.schema.json` describes the file shape for editors and tooling. `tools/validate_food_taxonomy.py` enforces the full contract with the Python standard library: exact taxonomy counts, locale completeness, Arabic-script labels, unique IDs and labels, stable ordering and parentage, and valid category references from every catalogue item. Food catalogue records may contain `categoryId` and `subcategoryId` only; translated category labels belong to the taxonomy source.

`data/products/catalogue-baseline.json` is the reviewed stage-one compatibility baseline. It captures all existing food UUIDs, all legacy Room ingredient-ID mappings, all 8,623 existing recipe ingredient rows, the initial category/subcategory IDs and their ordering, and the source Git blob SHAs used for the snapshot. Future catalogue builds may add new food identities and recipe rows, but must not silently remove or re-key an existing identity or recipe reference. Classification corrections may change a food item's category assignment without changing its food UUID.

The baseline is intentionally not a copy of the generated catalogue. It records identity and compatibility invariants, not mutable names, aliases, accepted units or category assignments. Update it only as part of a reviewed identity migration or an explicitly approved taxonomy version change. Do not regenerate it as a side effect of the catalogue build.

Run the canonical data guard from the repository root with:

`python tools/validate_food_taxonomy.py`

The food catalogue build workflow runs this guard after generating the catalogue, crosswalk and audit. It is not an Android build or a device test; it validates the data contract and compatibility invariants directly.

## Reviewed category classification

The category builder classifies one preferred food label at a time. It does not concatenate arbitrary aliases, synonyms and ancestor labels into one keyword string. The precedence is:

1. Preferred OFF/recipe name using specific whole-token food rules.
2. Explicit source taxonomy parent when the preferred label is not specific enough.
3. Supporting synonyms only when every recognized synonym agrees on the same category and subcategory.
4. A conservative unclassified result when evidence is absent or conflicting.

Every generated food identity records an internal classification method, confidence, matched label and rule. The build audit publishes per-method and per-confidence totals, per-category/subcategory counts, classification conflicts, and unclassified examples. Internal decision fields are removed from the shipped food-item records.

`data/products/category-overrides.json` contains reviewed, identity-specific corrections keyed by existing UUIDv4. It is deliberately separate from source taxonomy matching: these records repair known bad source classifications without renaming or recreating a food identity. Every override includes a category ID, a subcategory ID belonging to that category, and a human-readable reason. The catalogue build rejects duplicate, unknown, or invalid override references; the validator confirms that every reviewed override was applied exactly.

The stage-three override set currently contains 239 reviewed UUID-specific corrections. It corrects high-confidence source mistakes such as vegetable oil/fat appearing under vegetables, cocoa and chocolate appearing under unrelated food groups, and cardamom appearing under nuts or legumes. The preferred-label classifier is expanded with regression-covered food vocabulary for common recipe ingredients (including fruit, vegetables, dairy, seafood, bakery, grains and spices); it is not necessary to add an identity override for a food family that can be classified reliably from its preferred name. Salt, ice and non-food smoking accessories remain in the catch-all group where the current taxonomy has no more precise category; the builder does not force every record into an inappropriate category merely to reduce the catch-all count.

The classification audit is intended for iterative review. A low-confidence or conflicting record is visible in the audit rather than silently assigned by a broad substring match.
