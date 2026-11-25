# Algerian recipe catalogue

Midati bundles a separate Algerian recipe collection at:

`app/src/main/assets/recipes/algerian_room_v17.json`

The file is a Room v17 entity payload generated from the reviewed 187-recipe Dzirielle starter. It is not the existing 912-recipe Arabic catalogue and must not be passed to `ArabicRecipeCatalogueInstaller`.

## Runtime installation

- `AlgerianRecipeCatalogueInstaller` runs when the Recipes tab is opened, after normal food taxonomy and seed initialization.
- Installation is offline and idempotent. A collection-specific revision is stored in the `algerian_recipe_catalogue` preferences.
- The installer validates the payload format, Room version, collection ID, expected row counts, recipe/ingredient references, and ingredient taxonomy references before writing.
- Ingredients, recipes, recipe ingredients, steps, and attributions are upserted in one Room transaction. Existing non-Algerian rows are not cleared.
- Recipe IDs use the `dz-algerian-dzirielle-` prefix. The Recipes screen exposes a dedicated **جزائرية** scope using that prefix.
- Room schema version stays at 17; no database migration is required.

## Updating the catalogue

When replacing the payload, preserve stable recipe IDs, update the SHA-256 revision and expected count in `AlgerianRecipeCatalogueInstaller`, and rerun `AlgerianRecipeCatalogueInstallerTest`. Keep the row counts in the JSON `counts` object synchronized with the arrays in `entities`.

## Source and image rights

Recipe source URLs are retained in `recipe_attributions`. The current Dzirielle image URLs are not accompanied by verified redistribution licences; the import preserves their source metadata but does not establish permission to redistribute the images. Confirm image rights before a public release.
