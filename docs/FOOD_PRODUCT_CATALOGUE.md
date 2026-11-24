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

**This is a generated migration crosswalk, not yet a Room database migration.** Do not switch existing database foreign keys or delete old ingredient rows until pantry, shopping, recipe, price-observation and any other references have been migrated transactionally and the app has a safe migration path for existing installs.

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
