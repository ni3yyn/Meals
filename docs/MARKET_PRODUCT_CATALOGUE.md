# Midati retail market-product catalogue

This is the first bundled retail-product snapshot for Midati. It is separate from the generic food/ingredient taxonomy and from user-reported recipe price observations.

## Snapshot

- Captured: 2026-11-27
- Raw source rows: 1,958
- Unique market products: 1,803
- Retailer/source listings: 1,803
- Price observations: 1,803
- Sources: Ahah (209), MeGusta Market (496), Supérette DZ (1,098)
- Product images: 1,803
- Validated GTINs: 49 product records
- Clean product brands: 3; suspicious brand text remains in source metadata
- Price ranges: 38 records retain minimum and maximum values
- Out-of-stock observations are retained, not filtered
- 62 products remain flagged for food-scope review

The product identity, retailer listing and price observation are separate records. Retailer SKUs are source-scoped and are not treated as global barcodes. GTINs are stored as strings, spreadsheet `.0` suffixes are removed, and the GS1 Modulo-10 check digit is verified before a GTIN is promoted to the product master.

## Deduplication rules

Product IDs are opaque UUIDv4 values persisted in `identity_registry.json`; do not regenerate them for routine source refreshes.

- Ahah and MeGusta duplicate locale/category captures are merged only when their source identity key agrees and product name, SKU and price fields do not conflict.
- Supérette's scraper identity hash incorrectly grouped some variants with different internal SKUs. Supérette products are therefore keyed by the retailer SKU instead, preserving all 1,098 records as distinct products.
- Cross-retailer auto-merging is deliberately disabled. This snapshot has no shared validated GTINs or exact normalized name with a known, non-empty matching package size across the three source catalogues. The one exact name-only match is Graines de Chia; Supérette exposes several variable-weight options and Ahah has no pack size, so those listings remain separate rather than risking a false merge. Similar names alone are not enough evidence to collapse variants.

The 155 repeated source rows are retained as grouped source keys, alternate product URLs, image variants and category-path metadata. Each source listing has one corresponding price snapshot. Different prices are never averaged or overwritten.

## Room integration

Room schema v18 adds:

- `market_products`: unique retail product identity, display name, clean brand/GTIN, packaging, image, categories and food-scope review status.
- `market_product_sources`: retailer/store, SKU, source IDs, URLs, location, raw brand and extraction diagnostics.
- `market_price_observations`: DZD exact/range prices, capture time, availability and source notice.

`MarketProductCatalogueInstaller` reads the checked-in manifest and seven JSON parts from packaged Android assets, validates UUIDs, GTIN check digits, references and counts, then upserts all rows in one transaction. It is revision-aware and idempotent. The migration creates empty tables without modifying existing generic foods, ingredients, shopping lists, recipe data or user-entered price observations.

The app initializes this snapshot through `MealsRepository.ensureInitialized()`. The repository exposes search and product-source/price lookup methods for later UI integration.

## Provenance and interpretation

Each price remains associated with its source retailer/store, wilaya/commune where available, source URL and capture timestamp. Ahah explicitly labels its displayed prices as indicative and requiring in-store confirmation; that source notice is preserved. These are retailer observations, not a statistically representative national price series. No national average or synthetic reference price is calculated.

Each source listing retains category paths, alternate URLs, source product keys, enrichment diagnostics and brand/SKU metadata. The build audit is in `build-audit.json`.

The catalogue is bundled offline. Remote image URLs still depend on retailer hosting and network availability; the app does not download all product images into the APK.
