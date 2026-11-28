# Midati product-to-ingredient review pipeline

This folder contains the deterministic part of the retail catalogue matching workflow. It deliberately generates **review candidates**, never trusted mappings.

## Responsibilities

### Deterministic Python / Google Colab
- Download a pinned Git commit of the repository data and code.
- Validate the product parts, source listings, price observations, stable IDs, food catalogue and legacy identity crosswalk.
- Limit targets to canonical food IDs that can exist in Room's `ingredients` table: the union of the 1,261 legacy crosswalk entries and the stable seed IDs.
- Normalize Arabic diacritics/tatweel and multilingual labels, propose candidates, and block clearly incompatible product forms or unit families.
- Export a review CSV, a mapping JSON matching the Android importer, and reproducible QA/source metadata.
- Never auto-approve fuzzy matches.

### Android / Kotlin
- Owns the Room schema, migrations, installer, verified-only DAO query and eventual price estimator.
- Accepts only product/ingredient IDs that exist in the installed app catalogue.
- Requires an exact base-catalogue revision and matching product count.
- Rejects invalid IDs, duplicate product mappings and attempts to verify food-scope-review products.
- Uses only `VERIFIED` mappings for any future price estimate. `SUGGESTED` and `REJECTED` rows are never pricing evidence.

## Run the tests locally

From the repository root:

```bash
python -m unittest tools.market_product_matching.test_pipeline -v
```

The same tests run in GitHub Actions on pushes and pull requests.

## Run the Colab notebook

Open [notebooks/midati_market_product_ingredient_review.ipynb](../../notebooks/midati_market_product_ingredient_review.ipynb) in Google Colab.

The notebook pins its source commit and uses public GitHub raw/API endpoints, so no source upload, token, or repository write permission is needed. It exports an editable CSV. Set `review_decision` to `APPROVE`, `REJECT`, or leave it blank; preserve every generated row.

- `APPROVE` requires a reviewer and yields `VERIFIED`.
- `REJECT` yields `REJECTED`.
- Blank yields `SUGGESTED`.
- Food-scope-review items cannot be approved by this workflow.
- More than one approval for the same product is rejected.

## Import the approved artifact

Place the final `ingredient-matches.json` at:

```
data/market-products/ingredient-matches.json
```

Commit it on `main`. Do not modify the market product part files or the manifest for a mapping-only refresh. The Android build computes a base revision that excludes this exact filename, while still invalidating the map if any underlying market catalogue source changes.

The Room installer may defer applying the mappings until recipe ingredients have been installed. The repository retries installation on later initialization. It is safe for a clean first launch to have no mapping file yet.

## Important limitations

- Matching is multilingual lexical evidence, not a semantic food ontology. It is an efficient review queue, not an auto-approval authority.
- Product packaging with unknown quantity/unit cannot yet be used to calculate recipe purchase quantities.
- Out-of-stock and unknown-availability observations are preserved in source data, but must not be treated as purchasable price evidence.
- Market observations are retailer/location-attributed and currently unverified/indicative. Do not turn them into a national price average or present another Wilaya as local.
- The notebook does not change the app database or commit files to GitHub. The reviewer controls the mapping import.
