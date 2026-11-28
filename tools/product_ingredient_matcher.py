#!/usr/bin/env python3
"""Generate deterministic, review-only candidates linking market products to recipe ingredients.

This tool NEVER approves a mapping. Every exported row has review_status=PENDING.
It accepts the app's market product catalogue JSON plus the canonical food catalogue JSON.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import unicodedata
from pathlib import Path
from typing import Any


def normalize(value: Any) -> str:
    text = unicodedata.normalize("NFKD", str(value or "").casefold())
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    replacements = {"أ": "ا", "إ": "ا", "آ": "ا", "ى": "ي", "ة": "ه", "ؤ": "و", "ئ": "ي"}
    text = "".join(replacements.get(ch, ch) for ch in text)
    text = re.sub(r"[^\w]+", " ", text, flags=re.UNICODE)
    return re.sub(r"\s+", " ", text).strip()


def split_aliases(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return [str(x).strip() for x in value if str(x).strip()]
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
            if isinstance(parsed, list):
                return [str(x).strip() for x in parsed if str(x).strip()]
        except Exception:
            pass
        return [x.strip() for x in re.split(r"[,;|]", value) if x.strip()]
    return []


def find_records(payload: Any, preferred_keys: tuple[str, ...]) -> list[dict]:
    if isinstance(payload, list):
        return [x for x in payload if isinstance(x, dict)]
    if isinstance(payload, dict):
        for key in preferred_keys:
            value = payload.get(key)
            if isinstance(value, list):
                return [x for x in value if isinstance(x, dict)]
        for value in payload.values():
            if isinstance(value, (dict, list)):
                found = find_records(value, preferred_keys)
                if found:
                    return found
    return []


def load_records(path: Path, keys: tuple[str, ...]) -> list[dict]:
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    records = find_records(payload, keys)
    if not records:
        raise ValueError(f"No records found in {path}. Expected a JSON array or one of: {keys}")
    return records


def field(record: dict, *names: str) -> str:
    for name in names:
        value = record.get(name)
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def tokens(value: str) -> set[str]:
    return {x for x in normalize(value).split() if len(x) > 1}


def similarity(product_text: str, ingredient_texts: list[str]) -> tuple[float, str]:
    p = normalize(product_text)
    pt = tokens(p)
    best_score, best_method = 0.0, "token_overlap"
    for raw in ingredient_texts:
        n = normalize(raw)
        if not n:
            continue
        if p == n:
            return 1.0, "exact_normalized"
        if len(n) >= 3 and (f" {n} " in f" {p} "):
            score = 0.94
            method = "ingredient_phrase"
        else:
            it = tokens(n)
            if not pt or not it:
                continue
            overlap = len(pt & it)
            # Ingredient token coverage is useful for products such as "brand salt 1kg".
            score = overlap / len(it)
            if overlap and (it <= pt):
                score = max(score, 0.90)
            method = "token_overlap"
        if score > best_score:
            best_score, best_method = score, method
    return min(best_score, 1.0), best_method


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--products", required=True, type=Path, help="Market products JSON (not source listings)")
    parser.add_argument("--ingredients", required=True, type=Path, help="Canonical food catalogue JSON")
    parser.add_argument("--output", type=Path, default=Path("product_ingredient_candidates.csv"))
    parser.add_argument("--min-score", type=float, default=0.35)
    parser.add_argument("--top-k", type=int, default=3)
    args = parser.parse_args()
    if not 0 <= args.min_score <= 1:
        parser.error("--min-score must be between 0 and 1")
    if args.top_k < 1:
        parser.error("--top-k must be >= 1")

    products = load_records(args.products, ("products", "marketProducts", "items", "records"))
    ingredients = load_records(args.ingredients, ("foods", "foodItems", "ingredients", "items", "records"))

    ingredient_index = []
    for row in ingredients:
        iid = field(row, "id", "ingredientId", "foodId")
        if not iid:
            continue
        names = [field(row, "nameAr", "name"), field(row, "nameEn"), field(row, "nameFr")]
        names += split_aliases(row.get("aliasesArCsv")) + split_aliases(row.get("aliasesEnCsv"))
        names += split_aliases(row.get("aliasesFrCsv")) + split_aliases(row.get("aliases"))
        names = list(dict.fromkeys(x for x in names if x))
        if names:
            ingredient_index.append((iid, names, row))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    columns = [
        "product_id", "product_name", "brand", "gtin", "ingredient_id",
        "ingredient_name_ar", "ingredient_name_en", "score", "match_method",
        "review_status", "evidence_json"
    ]
    seen_pairs: set[tuple[str, str]] = set()
    product_count = 0
    candidate_count = 0
    with args.output.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        for product in products:
            pid = field(product, "id", "productId", "marketProductId")
            name = field(product, "name", "title", "product_name")
            if not pid or not name:
                continue
            product_count += 1
            product_text = " ".join(filter(None, [
                name, field(product, "brand"), field(product, "description"),
                " ".join(split_aliases(product.get("nameVariantsJson"))),
                " ".join(split_aliases(product.get("categoryNamesJson")))
            ]))
            ranked = []
            for iid, names, ingredient in ingredient_index:
                score, method = similarity(product_text, names)
                if score >= args.min_score:
                    ranked.append((score, method, iid, names, ingredient))
            ranked.sort(key=lambda item: (-item[0], item[2]))
            for score, method, iid, names, ingredient in ranked[:args.top_k]:
                pair = (pid, iid)
                if pair in seen_pairs:
                    continue
                seen_pairs.add(pair)
                evidence = {
                    "product_name": name,
                    "product_brand": field(product, "brand"),
                    "matched_ingredient_names": names,
                    "ingredient_category_id": field(ingredient, "categoryId"),
                    "ingredient_subcategory_id": field(ingredient, "subcategoryId"),
                    "algorithm": "normalized_phrase_token_v1",
                    "score_is_candidate_only": True
                }
                writer.writerow({
                    "product_id": pid,
                    "product_name": name,
                    "brand": field(product, "brand"),
                    "gtin": field(product, "gtin"),
                    "ingredient_id": iid,
                    "ingredient_name_ar": field(ingredient, "nameAr", "name"),
                    "ingredient_name_en": field(ingredient, "nameEn"),
                    "score": f"{score:.4f}",
                    "match_method": method,
                    "review_status": "PENDING",
                    "evidence_json": json.dumps(evidence, ensure_ascii=False, sort_keys=True)
                })
                candidate_count += 1

    print(f"Products inspected: {product_count}")
    print(f"Canonical ingredients indexed: {len(ingredient_index)}")
    print(f"Candidate mappings exported: {candidate_count}")
    print(f"Review status: PENDING for every row (no automatic approvals)")
    print(f"Output: {args.output.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
