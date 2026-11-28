#!/usr/bin/env python3
"""Deterministic, review-first matching for Midati retail products.

The matcher only proposes mappings. It never marks a candidate VERIFIED.
A human must explicitly set review_decision=APPROVE in the review CSV.
"""

from __future__ import annotations

import csv
import json
import re
import unicodedata
from collections import defaultdict
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence


ALGORITHM_VERSION = "midati-lexical-review-v1"
CSV_FIELDS = [
    "product_id",
    "product_name",
    "brand",
    "package_quantity",
    "package_unit",
    "product_category_names",
    "food_scope_review_needed",
    "food_scope_review_reason",
    "ingredient_id",
    "ingredient_name_ar",
    "ingredient_name_en",
    "ingredient_name_fr",
    "ingredient_category_id",
    "ingredient_subcategory_id",
    "ingredient_accepted_units",
    "candidate_rank",
    "candidate_score",
    "match_method",
    "match_reason",
    "compatibility_warning",
    "review_decision",
    "review_note",
]

_UUID_V4 = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    re.IGNORECASE,
)
_ARABIC_MARKS = re.compile(r"[\u0610-\u061a\u064b-\u065f\u0670\u06d6-\u06ed]")
_NON_WORD = re.compile(r"[^\w]+", re.UNICODE)
_WHITESPACE = re.compile(r"\s+")

# Deliberately conservative: these describe materially different retail forms.
_FORM_TERMS = {
    "CANNED": ("canned", "tinned", "in can", "conserve", "conserves", "boite", "en boite", "معلب", "معلبه"),
    "DRIED": ("dried", "dry", "dehydrated", "sec", "seche", "seches", "مجفف", "مجففه"),
    "FRESH": ("fresh", "frais", "fraiche", "طازج", "طازجه"),
    "FROZEN": ("frozen", "surgele", "surgeles", "surgelee", "مجمد", "مجمده"),
    "POWDER": ("powder", "poudre", "en poudre", "مسحوق"),
    "PASTE": ("paste", "concentrate", "concentre", "معجون"),
    "JUICE": ("juice", "jus", "عصير"),
    "OIL": ("oil", "huile", "زيت"),
    "GROUND": ("ground", "moulu", "moulue", "مطحون", "مطحونه"),
    "COOKED": ("cooked", "cuit", "cuite", "مطبوخ", "مطبوخه"),
    "SMOKED": ("smoked", "fume", "fumee", "مدخن", "مدخنه"),
}
_CATEGORY_FORMS = {
    "foodcat-canned": "CANNED",
    "foodcat-oils": "OIL",
    "foodsub-oils-vegetable": "OIL",
    "foodsub-oils-animal": "OIL",
}
_UNIT_FAMILIES = {
    "g": "MASS", "gram": "MASS", "grams": "MASS", "kg": "MASS", "kilogram": "MASS",
    "mg": "MASS", "غ": "MASS", "غرام": "MASS", "غرامات": "MASS", "كيلو": "MASS",
    "ml": "VOLUME", "milliliter": "VOLUME", "millilitre": "VOLUME",
    "l": "VOLUME", "liter": "VOLUME", "litre": "VOLUME", "cl": "VOLUME",
    "مل": "VOLUME", "ملل": "VOLUME", "ل": "VOLUME", "لتر": "VOLUME",
    "piece": "COUNT", "pieces": "COUNT", "unit": "COUNT", "units": "COUNT",
    "unite": "COUNT", "unites": "COUNT", "pc": "COUNT", "pcs": "COUNT",
    "piece(s)": "COUNT", "قطعه": "COUNT", "حبه": "COUNT",
}
_STOPWORDS = {
    "a", "an", "and", "the", "of", "for", "in", "with", "to",
    "de", "du", "des", "la", "le", "les", "d", "au", "aux", "en",
    "في", "من", "على", "عن", "و", "او", "الى", "مع",
}


def _flatten_text(value: Any) -> list[str]:
    """Collect textual values from strings/lists/dicts without stringifying objects."""
    if isinstance(value, str):
        value = value.strip()
        return [value] if value else []
    if isinstance(value, Mapping):
        result: list[str] = []
        for key in sorted(value, key=lambda item: str(item)):
            result.extend(_flatten_text(value[key]))
        return result
    if isinstance(value, (list, tuple)):
        result = []
        for item in value:
            result.extend(_flatten_text(item))
        return result
    return []


def normalize_text(value: Any) -> str:
    """Normalize Arabic and Latin text without translating or inventing aliases."""
    text = unicodedata.normalize("NFKD", str(value or "")).casefold()
    text = _ARABIC_MARKS.sub("", text)
    text = text.replace("\u0640", "")
    text = text.translate(str.maketrans({
        "أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا",
        "ى": "ي", "ة": "ه", "ؤ": "و", "ئ": "ي",
    }))
    text = "".join(char for char in unicodedata.normalize("NFD", text) if not unicodedata.combining(char))
    text = _NON_WORD.sub(" ", text)
    return _WHITESPACE.sub(" ", text).strip()


def _tokens(value: str) -> set[str]:
    result: set[str] = set()
    for token in normalize_text(value).split():
        if token in _STOPWORDS or token.isdigit() or len(token) < 2:
            continue
        # Arabic definite article differences should not hide otherwise exact tokens.
        if re.fullmatch(r"[\u0600-\u06ff]+", token) and token.startswith("ال") and len(token) > 3:
            result.add(token[2:])
            result.add(token)
            continue

        # Keep the original English token and add a conservative singular
        # candidate for the review index (chickpeas -> chickpea). Scoring still
        # uses the full label, so this cannot approve a match by itself.
        result.add(token)
        if len(token) > 4 and token.endswith("ies"):
            result.add(token[:-3] + "y")
        elif len(token) > 4 and token.endswith("es"):
            result.add(token[:-2])
        elif len(token) > 3 and token.endswith("s") and not token.endswith("ss"):
            result.add(token[:-1])
    return result


def _unique_normalized(values: Iterable[Any]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        normalized = normalize_text(value)
        if normalized and normalized not in seen:
            seen.add(normalized)
            result.append(normalized)
    return result


def _product_variants(product: Mapping[str, Any]) -> list[str]:
    values: list[Any] = [product.get("name", "")]
    values.extend(_flatten_text(product.get("nameVariants", [])))
    return _unique_normalized(values)


def _food_variants(food: Mapping[str, Any]) -> list[str]:
    values: list[Any] = []
    names = food.get("names", {})
    aliases = food.get("aliases", {})
    for language in ("ar", "fr", "en"):
        values.append(names.get(language, "") if isinstance(names, Mapping) else "")
        values.extend(_flatten_text(aliases.get(language, []) if isinstance(aliases, Mapping) else []))
    source_mappings = food.get("sourceMappings", {})
    if isinstance(source_mappings, Mapping):
        values.append(source_mappings.get("openFoodFactsIngredientTerm", ""))
    return _unique_normalized(values)


def _category_values(value: Any) -> list[str]:
    return _unique_normalized(_flatten_text(value))


def _forms(values: Iterable[str], category_ids: Iterable[str] = ()) -> set[str]:
    normalized_values = [
        f" {normalize_text(item)} "
        for item in values
        if normalize_text(item)
    ]
    found = {
        form
        for form, terms in _FORM_TERMS.items()
        if any(
            f" {normalize_text(term)} " in normalized
            for term in terms
            for normalized in normalized_values
        )
    }
    for category_id in category_ids:
        mapped = _CATEGORY_FORMS.get(str(category_id).strip().lower())
        if mapped:
            found.add(mapped)
    return found


def _unit_family(value: Any) -> str | None:
    unit = normalize_text(value)
    return _UNIT_FAMILIES.get(unit)


def _unit_compatible(product: Mapping[str, Any], food: Mapping[str, Any]) -> bool:
    package_family = _unit_family(product.get("packageUnit"))
    accepted = food.get("acceptedUnits", [])
    accepted_families = {
        family for family in (_unit_family(unit) for unit in _flatten_text(accepted)) if family
    }
    return not package_family or not accepted_families or package_family in accepted_families


def _compatibility(
    product: Mapping[str, Any],
    food: Mapping[str, Any],
    product_variants: Sequence[str],
    food_variants: Sequence[str],
) -> tuple[bool, str]:
    product_categories = _category_values(product.get("categoryNames", []))
    food_categories = [str(food.get("categoryId", "")), str(food.get("subcategoryId", ""))]
    product_forms = _forms([*product_variants, *product_categories])
    food_forms = _forms(food_variants, food_categories)

    if product_forms and food_forms and product_forms.isdisjoint(food_forms):
        return False, "FORM_CONFLICT:" + ",".join(sorted(product_forms)) + "!=" + ",".join(sorted(food_forms))
    if product_forms and not food_forms:
        # Explicitly packaged forms must not silently collapse into a generic food.
        return False, "PRODUCT_FORM_NOT_PRESENT_ON_CANONICAL_INGREDIENT"
    if not _unit_compatible(product, food):
        return False, "PACKAGE_UNIT_INCOMPATIBLE_WITH_ACCEPTED_INGREDIENT_UNITS"
    if food_forms and not product_forms:
        return True, "CANONICAL_FORM_MORE_SPECIFIC_THAN_PRODUCT_LABEL"
    return True, ""


def _pair_score(product_text: str, food_text: str) -> tuple[int, str, str]:
    if product_text == food_text:
        return 100, "EXACT_NAME", "Exact normalized multilingual name"
    if min(len(product_text), len(food_text)) >= 4 and (
        product_text in food_text or food_text in product_text
    ):
        ratio = min(len(product_text), len(food_text)) / max(len(product_text), len(food_text))
        return min(97, round(88 + 9 * ratio)), "PHRASE_CONTAINMENT", "One normalized name contains the other"

    product_tokens = _tokens(product_text)
    food_tokens = _tokens(food_text)
    common = product_tokens.intersection(food_tokens)
    if not common:
        return 0, "NO_MATCH", ""
    sequence_score = SequenceMatcher(None, product_text, food_text, autojunk=False).ratio() * 100.0
    coverage = len(common) / max(1, min(len(product_tokens), len(food_tokens)))
    score = round((0.68 * sequence_score) + (0.32 * coverage * 100.0))
    return max(0, min(100, score)), "LEXICAL_SIMILARITY", "Shared normalized name tokens"


def _best_match(
    product: Mapping[str, Any],
    food: Mapping[str, Any],
    product_variants: Sequence[str],
    food_variants: Sequence[str],
) -> tuple[int, str, str, str] | None:
    compatible, warning = _compatibility(product, food, product_variants, food_variants)
    if not compatible:
        return None

    best: tuple[int, str, str] = (0, "NO_MATCH", "")
    for product_text in product_variants:
        for food_text in food_variants:
            score, method, reason = _pair_score(product_text, food_text)
            candidate = (score, method, reason)
            if candidate[0] > best[0] or (
                candidate[0] == best[0] and candidate[1] < best[1]
            ):
                best = candidate
    if best[0] <= 0:
        return None
    if warning:
        best = (max(0, best[0] - 10), best[1], best[2])
    return best[0], best[1], best[2], warning


def build_candidate_rows(
    market_products: Sequence[Mapping[str, Any]],
    food_items: Sequence[Mapping[str, Any]],
    eligible_ingredient_ids: set[str],
    *,
    min_score: int = 42,
    max_candidates: int = 5,
) -> list[dict[str, str]]:
    """Return deterministic top-N review candidates; never auto-approve them."""
    if not 0 <= min_score <= 100:
        raise ValueError("min_score must be between 0 and 100")
    if max_candidates < 1:
        raise ValueError("max_candidates must be positive")

    foods_by_id: dict[str, Mapping[str, Any]] = {}
    food_variants_by_id: dict[str, list[str]] = {}
    token_index: dict[str, set[str]] = defaultdict(set)

    for food in food_items:
        food_id = str(food.get("id", "")).strip().lower()
        if food_id not in eligible_ingredient_ids:
            continue
        if food_id in foods_by_id:
            raise ValueError(f"Duplicate canonical food ID: {food_id}")
        variants = _food_variants(food)
        if not variants:
            continue
        foods_by_id[food_id] = food
        food_variants_by_id[food_id] = variants
        for variant in variants:
            for token in _tokens(variant):
                token_index[token].add(food_id)

    rows: list[dict[str, str]] = []
    seen_product_ids: set[str] = set()

    for product in market_products:
        product_id = str(product.get("id", "")).strip().lower()
        if not product_id or product_id in seen_product_ids:
            raise ValueError(f"Missing or duplicate market product ID: {product_id}")
        seen_product_ids.add(product_id)

        product_variants = _product_variants(product)
        if not product_variants:
            continue
        candidate_ids: set[str] = set()
        for variant in product_variants:
            for token in _tokens(variant):
                candidate_ids.update(token_index.get(token, set()))

        candidates: list[dict[str, Any]] = []
        for ingredient_id in candidate_ids:
            food = foods_by_id[ingredient_id]
            match = _best_match(
                product, food, product_variants, food_variants_by_id[ingredient_id]
            )
            if match is None:
                continue
            score, method, reason, warning = match
            if score < min_score:
                continue
            names = food.get("names", {}) if isinstance(food.get("names"), Mapping) else {}
            candidates.append({
                "product_id": product_id,
                "product_name": str(product.get("name", "")),
                "brand": str(product.get("brand") or ""),
                "package_quantity": "" if product.get("packageQuantity") is None else str(product.get("packageQuantity")),
                "package_unit": str(product.get("packageUnit") or ""),
                "product_category_names": " | ".join(_category_values(product.get("categoryNames", []))),
                "food_scope_review_needed": str(bool(product.get("foodScopeReviewNeeded", False))).lower(),
                "food_scope_review_reason": str(product.get("foodScopeReviewReason") or ""),
                "ingredient_id": ingredient_id,
                "ingredient_name_ar": str(names.get("ar", "")),
                "ingredient_name_en": str(names.get("en", "")),
                "ingredient_name_fr": str(names.get("fr", "")),
                "ingredient_category_id": str(food.get("categoryId", "")),
                "ingredient_subcategory_id": str(food.get("subcategoryId", "")),
                "ingredient_accepted_units": " | ".join(_category_values(food.get("acceptedUnits", []))),
                "candidate_score": int(score),
                "match_method": method,
                "match_reason": reason,
                "compatibility_warning": warning,
            })

        candidates.sort(key=lambda row: (
            -int(row["candidate_score"]),
            row["ingredient_name_en"].casefold(),
            row["ingredient_id"],
        ))
        for rank, candidate in enumerate(candidates[:max_candidates], start=1):
            candidate["candidate_rank"] = str(rank)
            candidate["review_decision"] = ""
            candidate["review_note"] = ""
            rows.append({field: str(candidate.get(field, "")) for field in CSV_FIELDS})

    rows.sort(key=lambda row: (row["product_id"], int(row["candidate_rank"]), row["ingredient_id"]))
    return rows


def write_candidates_csv(path: str | Path, rows: Sequence[Mapping[str, Any]]) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=CSV_FIELDS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def read_review_csv(path: str | Path) -> list[dict[str, str]]:
    with Path(path).open("r", encoding="utf-8-sig", newline="") as stream:
        return [dict(row) for row in csv.DictReader(stream)]


def build_mapping_payload(
    market_products: Sequence[Mapping[str, Any]],
    candidate_rows: Sequence[Mapping[str, Any]],
    reviewed_rows: Sequence[Mapping[str, Any]],
    *,
    source_catalogue_revision: str,
    generated_at_epoch_millis: int,
) -> dict[str, Any]:
    """Export one mapping per product; VERIFIED requires an explicit CSV approval."""
    products_by_id = {str(item.get("id", "")).lower(): item for item in market_products}
    candidate_keys = {
        (
            str(row.get("product_id", "")),
            str(row.get("ingredient_id", "")),
            str(row.get("candidate_rank", "")),
        )
        for row in candidate_rows
    }
    grouped: dict[str, list[dict[str, str]]] = defaultdict(list)
    reviewed_keys: set[tuple[str, str, str]] = set()

    for raw in reviewed_rows:
        row = {key: str(value or "").strip() for key, value in raw.items()}
        product_id = row.get("product_id", "").lower()
        ingredient_id = row.get("ingredient_id", "").lower()
        decision = row.get("review_decision", "").upper()
        if decision not in {"", "APPROVE", "REJECT"}:
            raise ValueError(f"Unsupported review_decision {decision!r} for {product_id}")
        candidate_rank = row.get("candidate_rank", "")
        review_key = (product_id, ingredient_id, candidate_rank)
        if review_key not in candidate_keys:
            raise ValueError(f"Review CSV contains a candidate not generated by this run: {product_id}/{ingredient_id}")
        if review_key in reviewed_keys:
            raise ValueError(f"Review CSV contains a duplicate candidate row: {product_id}/{ingredient_id}")
        reviewed_keys.add(review_key)
        if product_id not in products_by_id:
            raise ValueError(f"Review CSV references an unknown market product: {product_id}")
        grouped[product_id].append(row)

    if reviewed_keys != candidate_keys:
        missing = len(candidate_keys - reviewed_keys)
        extra = len(reviewed_keys - candidate_keys)
        raise ValueError(
            f"Review CSV must preserve every generated candidate row (missing={missing}, extra={extra})."
        )

    mappings: list[dict[str, Any]] = []
    for product_id in sorted(grouped):
        rows = grouped[product_id]
        decisions = [row for row in rows if row.get("review_decision", "").upper() == "APPROVE"]
        if len(decisions) > 1:
            raise ValueError(f"Only one ingredient can be approved for product {product_id}")
        product = products_by_id[product_id]

        if decisions:
            chosen = decisions[0]
            if bool(product.get("foodScopeReviewNeeded", False)):
                raise ValueError(
                    f"Product {product_id} needs a food-scope review before it can be VERIFIED"
                )
            status = "VERIFIED"
            reviewed_at: int | None = generated_at_epoch_millis
            rationale_prefix = "Human-approved"
        else:
            non_rejected = [
                row for row in rows
                if row.get("review_decision", "").upper() != "REJECT"
            ]
            if non_rejected:
                chosen = min(
                    non_rejected,
                    key=lambda row: (int(row.get("candidate_rank", "999999")), row.get("ingredient_id", "")),
                )
                status = "SUGGESTED"
                reviewed_at = None
                rationale_prefix = "Deterministic suggestion; requires human review"
            else:
                rejected = [
                    row for row in rows
                    if row.get("review_decision", "").upper() == "REJECT"
                ]
                if not rejected:
                    continue
                chosen = min(
                    rejected,
                    key=lambda row: (int(row.get("candidate_rank", "999999")), row.get("ingredient_id", "")),
                )
                status = "REJECTED"
                reviewed_at = generated_at_epoch_millis
                rationale_prefix = "Human-rejected"

        score = int(chosen.get("candidate_score", "0"))
        method = chosen.get("match_method", "LEXICAL_SIMILARITY") or "LEXICAL_SIMILARITY"
        note = chosen.get("review_note", "").strip()
        details = f"score={score}; method={method}; rank={chosen.get('candidate_rank', '')}"
        rationale = f"{rationale_prefix}; {details}"
        if note:
            rationale += f"; note={note}"
        mappings.append({
            "productId": product_id,
            "ingredientId": chosen["ingredient_id"].lower(),
            "status": status,
            "matchMethod": "MANUAL" if status == "VERIFIED" else method,
            "confidenceScore": round(score / 100.0, 4),
            "rationale": rationale[:500],
            "algorithmVersion": ALGORITHM_VERSION,
            "reviewedAtEpochMillis": reviewed_at,
            "updatedAtEpochMillis": generated_at_epoch_millis,
        })

    mapped_product_ids = [row["productId"] for row in mappings]
    if len(mapped_product_ids) != len(set(mapped_product_ids)):
        raise ValueError("Final mapping payload contains more than one row for a market product")

    return {
        "format": "midati-product-ingredient-matches",
        "formatVersion": 1,
        "sourceCatalogueRevision": source_catalogue_revision,
        "catalogueProductCount": len(market_products),
        "generatedAtEpochMillis": generated_at_epoch_millis,
        "mappings": mappings,
    }


def validate_catalogue_payload(
    manifest: Mapping[str, Any],
    parts: Sequence[Mapping[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    """Validate identities and cross-references before matching."""
    counts = manifest.get("counts", {})
    all_products: list[dict[str, Any]] = []
    all_listings: list[dict[str, Any]] = []
    all_prices: list[dict[str, Any]] = []
    for expected_index, part in enumerate(parts, start=1):
        if part.get("format") != "midati-market-product-catalogue-part":
            raise ValueError(f"Unsupported product part format at index {expected_index}")
        if int(part.get("formatVersion", -1)) != 1 or int(part.get("partIndex", -1)) != expected_index:
            raise ValueError(f"Unexpected product part version/index at {expected_index}")
        all_products.extend(part.get("products", []))
        all_listings.extend(part.get("listings", []))
        all_prices.extend(part.get("priceObservations", []))

    if len(all_products) != int(counts.get("products", -1)):
        raise ValueError("Product count does not match the manifest")
    if len(all_listings) != int(counts.get("sourceListings", -1)):
        raise ValueError("Listing count does not match the manifest")
    if len(all_prices) != int(counts.get("priceObservations", -1)):
        raise ValueError("Price-observation count does not match the manifest")

    product_ids = [str(row.get("id", "")).lower() for row in all_products]
    listing_ids = [str(row.get("id", "")).lower() for row in all_listings]
    price_ids = [str(row.get("id", "")).lower() for row in all_prices]
    if len(product_ids) != len(set(product_ids)) or any(not _UUID_V4.fullmatch(value) for value in product_ids):
        raise ValueError("Market product IDs are missing, duplicated, or not UUIDv4")
    if len(listing_ids) != len(set(listing_ids)) or any(not _UUID_V4.fullmatch(value) for value in listing_ids):
        raise ValueError("Retail listing IDs are missing, duplicated, or not UUIDv4")
    if len(price_ids) != len(set(price_ids)) or any(not _UUID_V4.fullmatch(value) for value in price_ids):
        raise ValueError("Price-observation IDs are missing, duplicated, or not UUIDv4")

    product_set = set(product_ids)
    listing_set = set(listing_ids)
    listing_keys: set[tuple[str, str]] = set()
    for listing in all_listings:
        if str(listing.get("productId", "")).lower() not in product_set:
            raise ValueError(f"Listing references missing product: {listing.get('id')}")
        key = (str(listing.get("source", "")), str(listing.get("sourceProductKey", "")))
        if not all(key) or key in listing_keys:
            raise ValueError("Retail source/product-key pair is missing or duplicated")
        listing_keys.add(key)
    for observation in all_prices:
        if str(observation.get("productId", "")).lower() not in product_set:
            raise ValueError(f"Price references missing product: {observation.get('id')}")
        if str(observation.get("sourceListingId", "")).lower() not in listing_set:
            raise ValueError(f"Price references missing listing: {observation.get('id')}")
    return all_products, all_listings, all_prices


def mapping_quality_report(
    market_products: Sequence[Mapping[str, Any]],
    candidate_rows: Sequence[Mapping[str, Any]],
    eligible_ingredient_count: int,
) -> dict[str, Any]:
    grouped: dict[str, list[Mapping[str, Any]]] = defaultdict(list)
    for row in candidate_rows:
        grouped[str(row["product_id"])].append(row)
    gaps = []
    for rows in grouped.values():
        ranked = sorted(rows, key=lambda row: int(row["candidate_rank"]))
        if len(ranked) > 1:
            gap = int(ranked[0]["candidate_score"]) - int(ranked[1]["candidate_score"])
            if gap < 5:
                gaps.append(gap)
    return {
        "marketProductCount": len(market_products),
        "productsWithCandidates": len(grouped),
        "productsWithoutCandidates": len(market_products) - len(grouped),
        "candidateRows": len(candidate_rows),
        "productsWithCloseTopCandidates": len(gaps),
        "foodScopeReviewNeededProducts": sum(
            1 for product in market_products if bool(product.get("foodScopeReviewNeeded", False))
        ),
        "eligibleCanonicalIngredientCount": eligible_ingredient_count,
        "verifiedMappingsAutomaticallyCreated": 0,
        "note": "All generated matches are suggestions until a reviewer explicitly approves a candidate.",
    }


def write_json(path: str | Path, value: Mapping[str, Any]) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
