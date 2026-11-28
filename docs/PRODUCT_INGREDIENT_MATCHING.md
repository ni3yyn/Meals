# Product-to-ingredient matching (Colab data workstream)

This is an offline, deterministic candidate generator. It does not access the network, scrape retailers, write to the live database, or approve matches.

## Inputs
- `market_products.json`: export of Midati's deduplicated `market_products` rows (product-level, not each retailer listing).
- `food_catalogue.json`: canonical `food_items` catalogue used by recipe ingredient identities.

The files must contain JSON arrays or objects with a nested array named `products`, `marketProducts`, `foods`, `foodItems`, `ingredients`, `items`, or `records`.

## Colab command
```bash
python product_ingredient_matcher.py \
  --products /content/market_products.json \
  --ingredients /content/food_catalogue.json \
  --output /content/product_ingredient_candidates.csv \
  --min-score 0.35 --top-k 3
```

Every row is exported as `PENDING`. Score is a candidate-ranking aid, not a probability. Human review is mandatory, especially for product forms, flavoured/seasoned variants, blends, and ambiguous category matches.

## Import contract
After review, the reviewed CSV should be transformed into `MarketProductIngredientMatchEntity` records. Only `reviewStatus = REVIEWED` rows may contribute to plan estimates. Do not import unreviewed candidates as approved mappings.
