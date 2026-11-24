#!/usr/bin/env python3
"""Validate Midati's canonical food taxonomy and protect its existing identities.

This is a dependency-free release/build guard. It deliberately validates the
full generated catalogue and the frozen starting-point crosswalk, not a sample.
"""

from __future__ import annotations

import json
import re
import unicodedata
import uuid
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
PRODUCTS_DIR = ROOT / "data" / "products"
TAXONOMY_PATH = PRODUCTS_DIR / "categories.json"
CATALOGUE_PATH = PRODUCTS_DIR / "catalogue.json"
MAPPING_PATH = PRODUCTS_DIR / "recipe-ingredient-map.json"
REGISTRY_PATH = PRODUCTS_DIR / "identity_registry.json"
AUDIT_PATH = PRODUCTS_DIR / "build-audit.json"
BASELINE_PATH = PRODUCTS_DIR / "catalogue-baseline.json"

EXPECTED_LOCALES = ("ar", "en", "fr")
EXPECTED_CATEGORY_COUNT = 22
EXPECTED_SUBCATEGORY_COUNT = 82
CATEGORY_ID_PATTERN = re.compile(r"^foodcat-[a-z0-9]+(?:-[a-z0-9]+)*$")
SUBCATEGORY_ID_PATTERN = re.compile(r"^foodsub-[a-z0-9]+(?:-[a-z0-9]+)*$")
ARABIC_LETTER_PATTERN = re.compile(r"[\u0600-\u06ff\u0750-\u077f\u08a0-\u08ff]")


class ValidationError(Exception):
    """Raised when a canonical catalogue invariant is broken."""


def fail(message: str) -> None:
    raise ValidationError(message)


def reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            fail(f"JSON contains duplicate object key {key!r}.")
        result[key] = value
    return result


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=reject_duplicate_keys,
        )
    except FileNotFoundError:
        fail(f"Required file does not exist: {path.relative_to(ROOT)}")
    except json.JSONDecodeError as error:
        fail(f"Invalid JSON in {path.relative_to(ROOT)}: {error}")
    if not isinstance(value, dict):
        fail(f"Root value must be an object: {path.relative_to(ROOT)}")
    return value


def normalized_label(value: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", value).casefold().split())


def validate_localized_names(
    names: Any,
    *,
    owner: str,
    require_arabic: bool = True,
) -> None:
    if not isinstance(names, dict):
        fail(f"{owner}: localized names must be an object.")
    if set(names) != set(EXPECTED_LOCALES):
        fail(
            f"{owner}: names must contain exactly {', '.join(EXPECTED_LOCALES)}; "
            f"found {sorted(names)}."
        )

    for locale in EXPECTED_LOCALES:
        value = names.get(locale)
        if not isinstance(value, str) or not value.strip():
            fail(f"{owner}: missing non-empty {locale!r} label.")
        if value != value.strip() or value != unicodedata.normalize("NFC", value):
            fail(f"{owner}: {locale!r} label has surrounding whitespace or is not NFC.")
        if locale == "ar" and require_arabic and not ARABIC_LETTER_PATTERN.search(value):
            fail(f"{owner}: Arabic label does not contain Arabic script: {value!r}.")


def validate_taxonomy(
    taxonomy: dict[str, Any],
    baseline: dict[str, Any],
) -> tuple[dict[str, str], set[str]]:
    expected_root_keys = {"schemaVersion", "languageCodes", "categories"}
    if set(taxonomy) != expected_root_keys:
        fail(
            "categories.json root keys must be exactly "
            "schemaVersion, languageCodes and categories."
        )
    if taxonomy.get("schemaVersion") != 1:
        fail("Unsupported categories.json schemaVersion; expected 1.")
    if taxonomy.get("languageCodes") != list(EXPECTED_LOCALES):
        fail("Taxonomy languages must be ordered exactly as ar, en, fr.")

    categories = taxonomy.get("categories")
    if not isinstance(categories, list) or len(categories) != EXPECTED_CATEGORY_COUNT:
        actual = len(categories) if isinstance(categories, list) else "not an array"
        fail(f"Taxonomy must define exactly {EXPECTED_CATEGORY_COUNT} categories; found {actual}.")

    category_ids: list[str] = []
    subcategory_parent: dict[str, str] = {}
    category_names_by_locale: dict[str, set[str]] = {
        locale: set() for locale in EXPECTED_LOCALES
    }

    for index, category in enumerate(categories):
        owner = f"categories[{index}]"
        if not isinstance(category, dict) or set(category) != {"id", "names", "subcategories"}:
            fail(f"{owner}: expected exactly id, names and subcategories.")

        category_id = category.get("id")
        if not isinstance(category_id, str) or not CATEGORY_ID_PATTERN.fullmatch(category_id):
            fail(f"{owner}: invalid stable category ID {category_id!r}.")
        if category_id in category_ids:
            fail(f"Duplicate category ID: {category_id}.")
        category_ids.append(category_id)

        validate_localized_names(category.get("names"), owner=category_id)
        for locale in EXPECTED_LOCALES:
            label = normalized_label(category["names"][locale])
            if label in category_names_by_locale[locale]:
                fail(f"Duplicate {locale} main-category label: {category['names'][locale]!r}.")
            category_names_by_locale[locale].add(label)

        children = category.get("subcategories")
        if not isinstance(children, list) or not children:
            fail(f"{category_id}: every category must define at least one subcategory.")

        sibling_names: dict[str, set[str]] = {
            locale: set() for locale in EXPECTED_LOCALES
        }
        for child_index, child in enumerate(children):
            child_owner = f"{category_id}.subcategories[{child_index}]"
            if not isinstance(child, dict) or set(child) != {"id", "names"}:
                fail(f"{child_owner}: expected exactly id and names.")

            child_id = child.get("id")
            if not isinstance(child_id, str) or not SUBCATEGORY_ID_PATTERN.fullmatch(child_id):
                fail(f"{child_owner}: invalid stable subcategory ID {child_id!r}.")
            if child_id in subcategory_parent or child_id in category_ids:
                fail(f"Duplicate taxonomy ID: {child_id}.")
            subcategory_parent[child_id] = category_id

            validate_localized_names(child.get("names"), owner=child_id)
            for locale in EXPECTED_LOCALES:
                label = normalized_label(child["names"][locale])
                if label in sibling_names[locale]:
                    fail(
                        f"{category_id}: duplicate {locale} sibling subcategory label "
                        f"{child['names'][locale]!r}."
                    )
                sibling_names[locale].add(label)

    if len(subcategory_parent) != EXPECTED_SUBCATEGORY_COUNT:
        fail(
            f"Taxonomy must define exactly {EXPECTED_SUBCATEGORY_COUNT} subcategories; "
            f"found {len(subcategory_parent)}."
        )

    baseline_taxonomy = baseline.get("taxonomy") or {}
    baseline_categories = baseline_taxonomy.get("categories") or []
    current_structure = [
        {
            "id": category["id"],
            "subcategoryIds": [child["id"] for child in category["subcategories"]],
        }
        for category in categories
    ]
    if current_structure != baseline_categories:
        fail(
            "Canonical category/subcategory IDs or their order/parentage changed "
            "without a reviewed baseline migration."
        )
    if baseline_taxonomy.get("languageCodes") != list(EXPECTED_LOCALES):
        fail("Frozen taxonomy baseline has an unexpected locale contract.")

    return subcategory_parent, set(category_ids)


def validate_catalogue(
    catalogue: dict[str, Any],
    taxonomy: dict[str, Any],
    subcategory_parent: dict[str, str],
    category_ids: set[str],
    baseline: dict[str, Any],
    registry: dict[str, Any],
) -> set[str]:
    if catalogue.get("catalogueType") != "generic-food-items":
        fail("catalogue.json is not the generic food-item catalogue.")
    if catalogue.get("idFormat") != "UUIDv4":
        fail("Food identity format must remain UUIDv4.")
    if catalogue.get("categoryCatalogue") != "data/products/categories.json":
        fail("catalogue.json must reference the canonical categories.json source.")
    if catalogue.get("categorySchemaVersion") != taxonomy.get("schemaVersion"):
        fail("catalogue.json was built against a different category schema version.")

    products = catalogue.get("products")
    if not isinstance(products, list) or not products:
        fail("catalogue.json has no products array.")

    ids: list[str] = []
    english_names: dict[str, str] = {}
    for index, product in enumerate(products):
        owner = f"catalogue.products[{index}]"
        if not isinstance(product, dict):
            fail(f"{owner}: product record must be an object.")

        product_id = product.get("id")
        try:
            parsed_id = uuid.UUID(str(product_id))
        except (ValueError, TypeError, AttributeError):
            fail(f"{owner}: invalid food UUID {product_id!r}.")
        if parsed_id.version != 4 or parsed_id.variant != uuid.RFC_4122:
            fail(f"{owner}: food ID must be an RFC 4122 UUIDv4: {product_id}.")
        ids.append(str(parsed_id))

        names = product.get("names")
        if not isinstance(names, dict) or not isinstance(names.get("ar"), str) or not names["ar"].strip():
            fail(f"{owner}: every food identity must have a real Arabic name.")
        for locale in EXPECTED_LOCALES:
            value = names.get(locale)
            if value is not None and (not isinstance(value, str) or not value.strip()):
                fail(f"{owner}: {locale!r} name must be non-empty text or null.")

        english = names.get("en")
        if isinstance(english, str) and english.strip():
            key = normalized_label(english)
            previous = english_names.get(key)
            if previous is not None:
                fail(
                    f"Duplicate normalized English food name {english!r} "
                    f"on IDs {previous} and {product_id}."
                )
            english_names[key] = str(product_id)

        # The product record owns only taxonomy references, never translated
        # category labels. The locale-specific strings live in categories.json.
        forbidden = {
            "category",
            "subcategory",
            "categoryName",
            "categoryNameAr",
            "categoryNameEn",
            "categoryNameFr",
            "subcategoryName",
            "subcategoryNameAr",
            "subcategoryNameEn",
            "subcategoryNameFr",
        }
        duplicated_labels = forbidden.intersection(product)
        if duplicated_labels:
            fail(
                f"{owner}: category labels are duplicated in the food record: "
                f"{sorted(duplicated_labels)}."
            )

        category_id = product.get("categoryId")
        subcategory_id = product.get("subcategoryId")
        if category_id not in category_ids:
            fail(f"{owner}: unknown categoryId {category_id!r}.")
        if subcategory_id not in subcategory_parent:
            fail(f"{owner}: unknown or missing subcategoryId {subcategory_id!r}.")
        if subcategory_parent[subcategory_id] != category_id:
            fail(
                f"{owner}: subcategory {subcategory_id!r} does not belong to "
                f"category {category_id!r}."
            )

    if len(ids) != len(set(ids)):
        fail("catalogue.json contains duplicate food UUIDs.")

    baseline_catalogue = baseline.get("catalogue") or {}
    baseline_ids = set(baseline_catalogue.get("foodItemIds") or [])
    if len(baseline_ids) != baseline_catalogue.get("foodItemCount"):
        fail("Frozen baseline food identity list is inconsistent.")
    missing_ids = baseline_ids.difference(ids)
    if missing_ids:
        fail(
            f"{len(missing_ids)} existing food identities were removed or re-keyed; "
            f"examples: {sorted(missing_ids)[:8]}."
        )

    registry_ids = set((registry.get("ids") or {}).values())
    if not set(ids).issubset(registry_ids):
        fail("One or more published food UUIDs are missing from the persistent identity registry.")

    return set(ids)


def validate_recipe_crosswalk(
    mapping: dict[str, Any],
    catalogue_ids: set[str],
    baseline: dict[str, Any],
) -> tuple[int, int]:
    if mapping.get("schemaVersion") != 2:
        fail("Unsupported recipe ingredient crosswalk schema; expected version 2.")

    legacy_map = mapping.get("legacyRoomIngredientIdToFoodItemId")
    compatibility_map = mapping.get("recipeIngredientToProductId")
    rows = mapping.get("recipeRows")
    if not isinstance(legacy_map, dict) or not isinstance(compatibility_map, dict):
        fail("Recipe crosswalk is missing its legacy-ID mapping objects.")
    if legacy_map != compatibility_map:
        fail("Legacy and compatibility recipe ingredient mappings have diverged.")
    if not isinstance(rows, list):
        fail("Recipe crosswalk recipeRows must be an array.")

    invalid_targets = {
        value for value in legacy_map.values() if value not in catalogue_ids
    }
    if invalid_targets:
        fail(f"Legacy ingredient map points to {len(invalid_targets)} missing food UUIDs.")

    row_lookup: dict[tuple[str, int], tuple[str, str]] = {}
    for index, row in enumerate(rows):
        if not isinstance(row, dict):
            fail(f"recipeRows[{index}] must be an object.")
        recipe_id = row.get("recipeId")
        ingredient_index = row.get("ingredientIndex")
        legacy_id = row.get("legacyRoomIngredientId")
        food_id = row.get("foodItemId")
        if not isinstance(recipe_id, str) or not recipe_id:
            fail(f"recipeRows[{index}] has an invalid recipeId.")
        if not isinstance(ingredient_index, int) or isinstance(ingredient_index, bool) or ingredient_index < 0:
            fail(f"recipeRows[{index}] has an invalid ingredientIndex.")
        if not isinstance(legacy_id, str) or not isinstance(food_id, str):
            fail(f"recipeRows[{index}] has invalid identity references.")
        row_key = (recipe_id, ingredient_index)
        if row_key in row_lookup:
            fail(f"Duplicate recipe ingredient row key: {row_key}.")
        if legacy_map.get(legacy_id) != food_id:
            fail(f"Recipe row {row_key} does not resolve through the legacy ID map.")
        if food_id not in catalogue_ids:
            fail(f"Recipe row {row_key} points to a missing food identity.")
        row_lookup[row_key] = (legacy_id, food_id)

    baseline_legacy = baseline.get("legacyRoomIngredientIdToFoodItemId") or {}
    missing_or_changed = [
        legacy_id
        for legacy_id, food_id in baseline_legacy.items()
        if legacy_map.get(legacy_id) != food_id
    ]
    if missing_or_changed:
        fail(
            f"{len(missing_or_changed)} pre-existing Room ingredient mappings changed; "
            f"examples: {missing_or_changed[:8]}."
        )

    baseline_rows = baseline.get("recipeRows") or []
    for row in baseline_rows:
        if not isinstance(row, list) or len(row) != 4:
            fail("Frozen recipe-row baseline contains an invalid row.")
        recipe_id, ingredient_index, legacy_id, food_id = row
        if row_lookup.get((recipe_id, ingredient_index)) != (legacy_id, food_id):
            fail(
                "A pre-existing recipe ingredient row disappeared or changed: "
                f"{recipe_id}[{ingredient_index}]."
            )

    baseline_catalogue = baseline.get("catalogue") or {}
    if len(legacy_map) < baseline_catalogue.get("legacyRoomIngredientIdCount", 0):
        fail("The legacy Room ingredient mapping count fell below the frozen baseline.")
    if len(rows) < baseline_catalogue.get("recipeIngredientRowCount", 0):
        fail("The recipe ingredient row count fell below the frozen baseline.")

    return len(legacy_map), len(rows)


def validate_audit(
    audit: dict[str, Any],
    *,
    product_count: int,
    legacy_count: int,
    row_count: int,
) -> None:
    counts = audit.get("catalogueCounts") or {}
    expected = {
        "products": product_count,
        "mappedRecipeIngredientIds": legacy_count,
        "recipeRowsMapped": row_count,
        "uuidv4Ids": product_count,
        "duplicateIds": 0,
        "duplicateEnglishNames": 0,
        "missingArabicNames": 0,
    }
    for key, value in expected.items():
        if counts.get(key) != value:
            fail(
                f"build-audit.json {key}={counts.get(key)!r}, "
                f"but validated data requires {value!r}."
            )


def main() -> int:
    taxonomy = read_json(TAXONOMY_PATH)
    catalogue = read_json(CATALOGUE_PATH)
    mapping = read_json(MAPPING_PATH)
    registry = read_json(REGISTRY_PATH)
    audit = read_json(AUDIT_PATH)
    baseline = read_json(BASELINE_PATH)

    if baseline.get("schemaVersion") != 1:
        fail("Unsupported catalogue-baseline.json schemaVersion; expected 1.")

    subcategory_parent, category_ids = validate_taxonomy(taxonomy, baseline)
    catalogue_ids = validate_catalogue(
        catalogue,
        taxonomy,
        subcategory_parent,
        category_ids,
        baseline,
        registry,
    )
    legacy_count, row_count = validate_recipe_crosswalk(mapping, catalogue_ids, baseline)
    validate_audit(
        audit,
        product_count=len(catalogue_ids),
        legacy_count=legacy_count,
        row_count=row_count,
    )

    print(
        "Validated canonical Midati food data: "
        f"{len(catalogue_ids):,} food identities, "
        f"{len(category_ids)} categories, "
        f"{len(subcategory_parent)} subcategories, "
        f"{legacy_count:,} legacy Room mappings and "
        f"{row_count:,} recipe ingredient rows. "
        "All frozen identities and recipe links are preserved."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValidationError as error:
        raise SystemExit(f"Food taxonomy validation failed: {error}") from error
