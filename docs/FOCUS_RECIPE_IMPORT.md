# Focus Recipes Arabic import

## Source and licence

- Dataset: [Focus Recipes v1.0](https://focusathletics.app/en/recipes/dataset)
- JSON: https://focusathletics.app/data/focus-recipes.json
- Source size: 500 bilingual Spanish/English recipes
- Licence: [Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/)
- Required credit: Focus Recipes v1.0 (2026). Focus. Include the dataset URL and the recipe-level source URL where supplied.

The import stores a complete, adapted `sourceRecord` for every accepted recipe so fields that are not yet represented in Room are still available in the Arabic JSON catalogue.

## Safety screen

The pipeline checks both language variants of titles, descriptions, ingredients and method steps before translation.

- Excludes recipes that name wine, beer or other alcoholic ingredients in the recipe or cooking method. Wine vinegar is conservatively excluded too; ordinary apple-cider vinegar is not.
- Replaces common pork cuts/products with beef, lamb or olive-oil alternatives across the title, ingredient list, descriptions and steps (for example pork chops → lamb chops, pork ribs → beef ribs, bacon → smoked beef strips, lard → olive oil).
- Excludes pork references that cannot be safely or sensibly resolved. Unspecified animal gelatin is excluded; clearly bovine, fish or plant-derived gelatin is allowed.
- Existing turkey/chicken/beef products are preserved and are not rewritten as pork substitutes.

Every adapted recipe is marked with `porkAdapted` and `adaptationChanges`. Aggregate counts and per-record exclusion reasons are written to `sourceAudit.focusRecipes`. The source SHA-256 is pinned in that audit; if the upstream bytes change, the pipeline stops for review instead of silently replacing the dataset.

## Arabic output fields

The catalogue preserves:
- Arabic title, summary, category, cuisine, ingredients and preparation steps.
- Original source categories and dietary keys, plus translated `categoriesAr` and `dietaryAr` labels. Dietary labels are also available in `tags` for the current app filter path.
- Per-serving calories, protein, carbohydrates and fat, in both the nested `nutrition` object and flat compatibility fields.
- Servings, preparation/cooking minutes, recipe image, Spanish/English URLs and slugs.
- Bilingual source text, complete `sourceRecord`, source attribution and CC BY 4.0 licence metadata.

The Android Room model stores the Arabic category/dietary labels, macros and image URL. The installer maps Focus IDs to the `focus-` namespace and streams the combined catalogue to Room in batches. Focus recipes remain alongside the new UniTools World Recipes collection; the former Polish-derived Open Recipe Corpus has been retired.

## Translation and workflow

The workflow uses the shared `GOOGLE_APPS_SCRIPT_URL` and `GOOGLE_APPS_SCRIPT_TOKEN` GitHub Actions secrets and the existing Google Apps Script `LanguageApp` translation bridge.

The Python translator sends recipe text in Spanish to Arabic. It translates the unique cuisine, category and dietary labels in one separate English-to-Arabic `tags` request. This avoids treating the English metadata keys as Spanish and works with the already-deployed Apps Script version, which supports translated `tags` without requiring a redeployment.

Run **Import Focus recipes into the Arabic catalogue** from GitHub Actions when intentionally refreshing the Focus source. The default maximum is 500. Safety exclusions mean the final accepted Focus count can be lower than 500. Translation batches are checkpointed into the JSON file when a run stops early; those partial checkpoints are not pinned for Android. The UniTools import workflow later assembles the final 912-recipe catalogue (481 Focus + 431 UniTools) and only pins a complete, validated replacement catalogue using the immutable commit, byte-size and Git-blob SHA checks.

## Local tests

```bash
python -m unittest tools.test_focus_recipe_pipeline -v
```
