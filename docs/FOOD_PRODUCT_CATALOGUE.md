# Midati generic food-item catalogue

This pipeline creates a **generic food identity catalogue** for recipe ingredients and grocery planning. It does not create branded supermarket products. Barcodes, retailer SKUs, package sizes and retailer-specific images/prices belong to the separate market-product and pricing databases.

## Generated files

- \`data/products/catalogue.json\`: canonical food items, multilingual names, aliases, unit support and Midati category IDs.
- \`data/products/recipe-ingredient-map.json\`: explicit map from every existing Room recipe ingredient ID to one canonical food-item ID.
- \`data/products/build-audit.json\`: source counts, matching coverage, unresolved/ambiguous recipe matches, category conflicts and missing optional data.

The workflow commits these generated files to \`main\`. It runs after the combined recipe catalogue changes, when the builder/tests/workflow change, or when manually dispatched.

## Food item shape

Each product record is a generic food identity:

\`\`\`json
{
  "id": "orc-ing-unitools-...",
  "names": {
    "ar": "حمص",
    "en": "Chickpeas",
    "fr": "Pois chiches"
  },
  "acceptedUnits": ["g", "kg"],
  "imageUrl": null,
  "categoryId": "legumes",
  "subcategoryId": "chickpeas",
  "aliases": {
    "ar": [],
    "en": ["chickpea"],
    "fr": ["pois chiche"]
  },
  "quantity": null,
  "countryOfOrigin": null,
  "nutrition": null,
  "sourceMappings": {},
  "recipeIngredientIds": ["orc-ing-unitools-..."]
}
\`\`\`

- \`id\` is opaque and stable, never a lowercase English label. When an item matches recipe usage, the catalogue reuses one existing recipe ingredient ID and maps every other legacy ID for that same exact item to it. New taxonomy-only items receive deterministic UUIDv5 IDs.
- \`names\` and \`aliases\` are multilingual. Missing source translations stay null/empty rather than being guessed.
- \`acceptedUnits\` contains units already used by recipes when observed. Otherwise, it gets a conservative category-based default set.
- \`quantity\` is reserved for a future market-product/package variant. It is not the recipe quantity.
- \`imageUrl\`, \`countryOfOrigin\` and \`nutrition\` remain null unless an item-specific source supports them. The two taxonomy files used here do not provide reliable generic product images or complete per-food nutrition panels.
- \`sourceMappings\` preserves source identifiers/codes without using source IDs as Midati's public identity.

## Matching and duplicate policy

1. Extract every ingredient in the current combined recipe catalogue.
2. Recreate the exact current Room ingredient IDs. UniTools IDs use the importer's source ID; Focus IDs mirror the Android SHA-256 name/unit algorithm.
3. Match English, French and Arabic names/aliases exactly after Unicode, punctuation and Arabic-diacritic normalization.
4. Merge exact canonical duplicates and map all associated legacy recipe IDs to one canonical ID.
5. Do not fuzzy-merge ambiguous terms. An ambiguous match becomes a recipe-derived item and is listed in the audit for review.
6. Fail the build if any existing recipe ingredient ID is unmapped, or if duplicate IDs or normalized English names remain.

This stage produces the crosswalk but does not rewrite the installed Room database or migrate pantry, shopping, price-observation or plan references. That integration must consume the mapping and migrate every foreign-key-like reference transactionally in a later change.

## Sources and reproducibility

### Open Food Facts ingredients taxonomy

Pinned to commit \`f5abb0a0ca8ae5702f3e192219511adf211b55ee\`, file \`taxonomies/food/ingredients.txt\`. It provides multilingual terms, alternate names, parent relationships and selected external codes. The source format and taxonomy are documented in the [Open Food Facts ingredients taxonomy](https://github.com/openfoodfacts/openfoodfacts-server/blob/main/taxonomies/food/ingredients.txt).

### FoodOn

Pinned to commit \`8a27493346a122e86ac1748aac24199d5af69166\`, file \`foodon-base.owl\`. The builder extracts FOODON class IDs, English labels, synonyms and subclass ancestry to enrich classification and retain semantic references. FoodOn describes raw and processed food materials/products; see the [FoodOn project](https://github.com/FoodOntology/foodon).

The workflow downloads both files by immutable commit and verifies their Git blob SHA before parsing. GitHub Actions caches these source files, so subsequent runs avoid repeat downloads. The datasets are not copied into this repository.

## Data rights

Open Food Facts documents the database under ODbL 1.0, individual contents under DbCL 1.0, and product images under CC BY-SA. FoodOn is CC BY 4.0. Because this catalogue combines and transforms data from both sources, review the ODbL share-alike and attribution requirements before distributing it as part of a public/commercial product. Source attribution and pinned revisions are retained in the generated catalogue.

- [Open Food Facts reuse/licensing guidance](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/)
- [FoodOn repository and license](https://github.com/FoodOntology/foodon)

## Important scope limits

The ingredient taxonomy is not a barcode catalogue. For this first catalogue, Mayonnaise, canned chickpeas, milk, raib, legumes, semolina and similar generic food identities can be represented when the source taxonomy has them. A later market-product record will reference this food-item ID and add brand, barcode (including scale barcode rules), package quantity/unit, retailer SKU and other package-specific facts. The price database will remain separate and link to the market-product ID.

Images, actual country of origin and nutrient values must come from a product-specific source or a verified nutrition dataset. Recipe photos, generic source descriptions and Ecobalyse default-origin assumptions are not copied into these fields.
