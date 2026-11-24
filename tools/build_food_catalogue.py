#!/usr/bin/env python3
"""Build Midati's generic food catalogue from OFF, FoodOn and recipe usage.

The output contains generic food identities only. Retailer/brand/barcode records
and pack quantities belong to the separate future market-product/pricing system.

IDs are random UUIDv4 values persisted in identity_registry.json. They are
never derived from names, slugs, translations or category labels.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import unicodedata
import uuid
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

OFF_COMMIT = "f5abb0a0ca8ae5702f3e192219511adf211b55ee"
OFF_BLOB_SHA = "0c54acc70878b778a38d893c877ea49c6ef87ff6"
FOODON_COMMIT = "8a27493346a122e86ac1748aac24199d5af69166"
FOODON_BLOB_SHA = "d134da045e75194fad2872e24401c893e109eb44"
SCHEMA_VERSION = 1

SUBCATEGORY_LABELS = {
    "milk": ("الحليب", "Milk", "Lait"),
    "fermented_milk": ("الألبان المخمرة", "Fermented milk", "Laits fermentés"),
    "yogurt": ("الزبادي", "Yogurt", "Yaourt"),
    "cheese": ("الجبن", "Cheese", "Fromage"),
    "cream": ("القشدة والكريمة", "Cream", "Crème"),
    "butter": ("الزبدة", "Butter", "Beurre"),
    "beef": ("لحم البقر", "Beef", "Bœuf"),
    "lamb": ("لحم الضأن", "Lamb", "Agneau"),
    "poultry": ("الدواجن", "Poultry", "Volaille"),
    "offal": ("الأحشاء", "Offal", "Abats"),
    "fish": ("الأسماك", "Fish", "Poissons"),
    "crustaceans": ("القشريات", "Crustaceans", "Crustacés"),
    "molluscs": ("الرخويات", "Molluscs", "Mollusques"),
    "citrus": ("الحمضيات", "Citrus", "Agrumes"),
    "berries": ("التوت", "Berries", "Baies"),
    "root_vegetables": ("الخضروات الجذرية", "Root vegetables", "Légumes-racines"),
    "leaf_vegetables": ("الخضروات الورقية", "Leaf vegetables", "Légumes-feuilles"),
    "tomato": ("الطماطم", "Tomato products", "Tomates"),
    "chickpeas": ("الحمص", "Chickpeas", "Pois chiches"),
    "lentils": ("العدس", "Lentils", "Lentilles"),
    "beans": ("الفاصولياء", "Beans", "Haricots"),
    "peas": ("البازلاء", "Peas", "Pois"),
    "rice": ("الأرز", "Rice", "Riz"),
    "wheat": ("القمح", "Wheat", "Blé"),
    "flour": ("الدقيق والفرينة", "Flour", "Farines"),
    "pasta": ("المعكرونة", "Pasta", "Pâtes"),
    "bread": ("الخبز", "Bread", "Pain"),
    "nuts": ("المكسرات", "Nuts", "Fruits à coque"),
    "seeds": ("البذور", "Seeds", "Graines"),
    "oils": ("الزيوت", "Oils", "Huiles"),
    "spice_blends": ("خلطات التوابل", "Spice blends", "Mélanges d'épices"),
    "herbs": ("الأعشاب", "Herbs", "Herbes"),
    "sauces": ("الصلصات", "Sauces", "Sauces"),
    "mayonnaise": ("المايونيز", "Mayonnaise", "Mayonnaise"),
    "vinegar": ("الخل", "Vinegar", "Vinaigre"),
    "jams_preserves": ("المربى والمحافظات", "Jams & preserves", "Confitures et conserves"),
    "canned_legumes": ("البقوليات المعلبة", "Canned legumes", "Légumineuses en conserve"),
    "canned_fish": ("الأسماك المعلبة", "Canned fish", "Poissons en conserve"),
    "frozen_vegetables": ("الخضروات المجمدة", "Frozen vegetables", "Légumes surgelés"),
    "chocolate_cocoa": ("الشوكولاتة والكاكاو", "Chocolate & cocoa", "Chocolat et cacao"),
}

CATEGORY_KEYWORDS = {
    "canned_preserved": ("canned", "tinned", "preserved", "pickled", "conserve", "appertised"),
    "frozen_foods": ("frozen", "deep frozen", "surgel"),
    "beverages": ("beverage", "drink", "juice", "water", "coffee", "tea", "infusion"),
    "seafood": ("seafood", "fish", "crustacean", "shellfish", "mollusc", "mollusk", "shrimp", "prawn", "sardine", "tuna", "salmon", "anchovy", "seaweed", "roe"),
    "meat_offal": ("meat", "beef", "pork", "lamb", "mutton", "veal", "goat", "poultry", "chicken", "turkey", "duck", "liver", "kidney", "heart", "offal", "tripe", "sausage", "ham"),
    "dairy": ("dairy", "milk", "yogurt", "yoghurt", "raib", "leben", "lben", "kefir", "cheese", "cream", "butter", "whey", "curd", "lact"),
    "eggs": ("egg",),
    "mushrooms": ("mushroom", "fungus", "fungi", "truffle"),
    "fruits": ("fruit", "berry", "berries", "citrus", "apple", "banana", "date", "orange", "lemon", "grape", "pear", "peach", "plum", "fig"),
    "vegetables": ("vegetable", "tomato", "potato", "onion", "carrot", "cabbage", "pepper", "lettuce", "spinach", "root vegetable", "leaf vegetable", "tuber"),
    "legumes": ("legume", "pulse", "chickpea", "garbanzo", "lentil", "bean", "pea", "faba", "broad bean", "soybean"),
    "grains_cereals": ("cereal", "grain", "wheat", "rice", "corn", "maize", "oat", "barley", "rye", "millet", "semolina", "flour", "starch"),
    "pasta_bakery": ("pasta", "noodle", "bread", "bakery", "dough", "couscous", "pita"),
    "nuts_seeds": ("nut", "almond", "walnut", "hazelnut", "pistachio", "peanut", "seed", "sesame"),
    "oils_fats": ("oil", "fat", "margarine", "shortening", "lard"),
    "spices": ("spice", "peppercorn", "cumin", "paprika", "curry", "cinnamon", "clove", "chili", "chilli", "seasoning"),
    "herbs": ("herb", "parsley", "coriander leaf", "cilantro", "thyme", "mint", "basil", "rosemary", "oregano", "dill"),
    "sauces_condiments": ("sauce", "condiment", "mayonnaise", "ketchup", "mustard", "vinegar", "dressing", "harissa", "paste"),
    "sweeteners": ("sweetener", "sugar", "honey", "syrup", "molasses", "jam", "chocolate", "cocoa", "confection"),
    "baking_dessert": ("baking", "baker", "yeast", "leavening", "gelatin", "pudding", "dessert", "custard", "vanilla"),
}

PREPARATION_WORDS = {
    "cooked", "raw", "roasted", "boiled", "grilled", "fried", "baked",
    "steamed", "chopped", "minced", "sliced", "diced", "shredded",
    "grated", "peeled", "skinless", "boneless", "drained", "soaked",
    "trimmed", "deseeded", "seeded", "mashed", "pureed", "puréed",
    "thinly", "finely", "roughly",
}
PREPARATION_PHRASES = ("low sodium", "reduced sodium", "unsalted")

UNIT_ALIASES = {
    "g": "g", "gram": "g", "grams": "g", "kg": "g", "kilogram": "g",
    "kilograms": "g", "mg": "g", "milligram": "g", "milligrams": "g",
    "oz": "g", "ounce": "g", "ounces": "g", "lb": "g", "lbs": "g",
    "pound": "g", "pounds": "g",
    "ml": "ml", "milliliter": "ml", "milliliters": "ml",
    "millilitre": "ml", "millilitres": "ml", "l": "ml", "liter": "ml",
    "liters": "ml", "litre": "ml", "litres": "ml", "cl": "ml",
    "pc": "piece", "pcs": "piece", "piece": "piece", "pieces": "piece",
    "unit": "piece", "units": "piece", "whole": "piece",
    "clove": "clove", "cloves": "clove", "sprig": "sprig",
    "sprigs": "sprig", "stalk": "stalk", "stalks": "stalk",
    "bunch": "bunch", "bunches": "bunch", "handful": "handful",
    "handfuls": "handful", "slice": "slice", "slices": "slice",
    "cup": "cup", "cups": "cup", "tbsp": "tbsp", "tablespoon": "tbsp",
    "tablespoons": "tbsp", "tsp": "tsp", "teaspoon": "tsp",
    "teaspoons": "tsp", "pinch": "pinch", "pinches": "pinch",
    "can": "can", "cans": "can", "tin": "can", "tins": "can",
    "pack": "pack", "package": "pack", "packages": "pack",
    "to taste": "to_taste", "totaste": "to_taste", "to_taste": "to_taste",
    "as needed": "to_taste",
}

LEGACY_SEEDS = [
    ("semolina", "سميد متوسط", "Semolina", "Semoule moyenne", ["سميد", "semoule", "smid"]),
    ("chicken", "دجاج", "Chicken", "Poulet", ["دجاجة", "poulet"]),
    ("carrot", "جزر", "Carrot", "Carotte", ["زرودية", "carotte", "zrodiya"]),
    ("zucchini", "قرعة خضراء", "Zucchini", "Courgette", ["كوسة", "courgette", "qar3a"]),
    ("chickpeas", "حمص", "Chickpeas", "Pois chiches", ["حمص يابس", "pois chiche", "homs"]),
    ("tomato", "طماطم", "Tomato", "Tomate", ["مطيشة", "tomate", "maticha"]),
    ("lentils", "عدس", "Lentils", "Lentilles", ["عدس يابس", "lentille", "3dess"]),
    ("onion", "بصل", "Onion", "Oignon", ["بصلة", "oignon", "bsal"]),
    ("garlic", "ثوم", "Garlic", "Ail", ["ثومة", "ail", "thoum"]),
    ("oil", "زيت", "Cooking oil", "Huile", ["زيت المائدة", "huile"]),
    ("eggs", "بيض", "Eggs", "Œufs", ["بيضات", "oeuf", "œuf", "bayd"]),
    ("pepper", "فلفل حلو", "Sweet pepper", "Poivron", ["فلفلة", "poivron", "felfel"]),
    ("pasta", "معكرونة", "Pasta", "Pâtes", ["مقرونة", "pates", "pasta"]),
    ("tomato-paste", "طماطم مصبرة", "Tomato paste", "Concentré de tomate", ["عكري", "concentré tomate"]),
    ("potato", "بطاطا", "Potato", "Pomme de terre", ["اطاطبلا", "pomme de terre", "patate", "batata"]),
    ("cheese", "جبن", "Cheese", "Fromage", ["فرماج", "fromage", "jben"]),
]


def split_values(value: str) -> list[str]:
    return [part.strip() for part in value.split(",") if part.strip()]


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKD", str(value or "")).casefold()
    value = "".join(ch for ch in value if not unicodedata.combining(ch))
    value = value.replace("ـ", "")
    value = re.sub(r"[\u064b-\u065f\u0670]", "", value)
    value = value.translate(str.maketrans({"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي"}))
    value = re.sub(r"[^\w]+", " ", value, flags=re.UNICODE)
    return re.sub(r"\s+", " ", value).strip()


def english_match_variants(value: str) -> list[str]:
    original = normalize(value)
    if not original:
        return []
    without_phrases = original
    for phrase in PREPARATION_PHRASES:
        without_phrases = re.sub(r"\b" + re.escape(phrase) + r"\b", " ", without_phrases)
    tokens = [token for token in re.sub(r"\s+", " ", without_phrases).strip().split() if token not in PREPARATION_WORDS]
    variants = [original]
    reduced = " ".join(tokens)
    if reduced and reduced != original:
        variants.append(reduced)
    return list(dict.fromkeys(variants))


def clean_recipe_english(value: str) -> str:
    text = str(value or "").strip()
    if not text:
        return ""
    before, separator, after = text.partition(":")
    if separator and re.match(r"\s*(?:about\b|approximately\b|approx\.?\b|\d|one\b|half\b|a\s+\w+\b)", after, re.I):
        text = before.strip()
    text = re.sub(
        r"\s+(?:about\s+)?\d+(?:[.,]\d+)?(?:\s+\d+/\d+)?\s*"
        r"(?:g|kg|mg|ml|l|oz|lb|lbs?|cups?|cup|tbsp|tsp|tablespoons?|teaspoons?|"
        r"pieces?|pcs?|cloves?|bunch(?:es)?|stalks?|sprigs?)\b.*$",
        "",
        text,
        flags=re.I,
    ).strip(" ,;:-")
    return re.sub(r"\s+", " ", text)


def first_property(values: dict[str, list[str]], key: str) -> str | None:
    entries = values.get(key) or []
    if not entries:
        return None
    return entries[0].split(":", 1)[-1].strip() or None


def parse_off_taxonomy(text: str) -> list[dict[str, Any]]:
    nodes: list[dict[str, Any]] = []
    block: list[str] = []

    def flush() -> None:
        if not block:
            return
        values: dict[str, list[str]] = defaultdict(list)
        parents: list[str] = []
        for raw in block:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("< en:"):
                parents.append(line[5:].strip())
                continue
            match = re.match(r"^([a-z][a-z0-9_-]*):\s*(.*)$", line, re.I)
            if match:
                key, value = match.group(1).lower(), match.group(2).strip()
                values[key].extend(split_values(value))
        if values.get("en"):
            nodes.append({
                "english": values["en"][0],
                "languages": {
                    language: values.get(language, [])
                    for language in ("en", "ar", "fr")
                    if values.get(language)
                },
                "parents": list(dict.fromkeys(parents)),
                "ciqualCode": first_property(values, "ciqual_food_code"),
                "usdaFdcCode": first_property(values, "usda_fdc_code"),
                "wikidata": first_property(values, "wikidata"),
            })
        block.clear()

    for line in text.splitlines():
        if not line.strip():
            flush()
        else:
            block.append(line)
    flush()
    return nodes


def parse_foodon_tsv(text: str) -> tuple[dict[str, set[str]], dict[str, set[str]]]:
    parents: dict[str, set[str]] = defaultdict(set)
    label_index: dict[str, set[str]] = defaultdict(set)
    lines = text.splitlines()
    allowed_types = {
        "label", "label (alternative)", "synonym",
        "synonym (exact)", "synonym (broad)", "synonym (related)",
    }
    for line in lines[1:]:
        row = line.split("\t")
        if len(row) < 4:
            continue
        subject = row[0].strip().strip("<>")
        if "FOODON_" not in subject:
            continue
        parents[subject]
        parent = row[1].strip().strip("<>")
        if parent:
            parents[subject].add(parent)
        kind = row[2].strip().strip('"').lower()
        if kind not in allowed_types:
            continue
        literal = row[3].strip()
        match = re.match(r'^"(.*)"(?:@([a-zA-Z-]+)|\^\^.*)?$', literal)
        if not match:
            continue
        label = match.group(1).replace(r'\\"', '"').strip()
        language = (match.group(2) or "").lower()
        if label and language == "en":
            label_index[normalize(label)].add(subject)
    return parents, label_index


def contains_term(text: str, term: str) -> bool:
    term = normalize(term)
    if not term:
        return False
    if " " in term:
        return term in text
    return re.search(r"(?<!\w)" + re.escape(term) + r"(?!\w)", text) is not None


def classify_item(name: str, parents: list[str]) -> tuple[str, str | None]:
    normalized_name = normalize(name)
    parent_text = " ".join(normalize(parent) for parent in parents)
    combined = parent_text + " " + normalized_name
    category_id = "other_foods"

    if any(contains_term(combined, word) for word in CATEGORY_KEYWORDS["canned_preserved"]):
        category_id = "canned_preserved"
    elif any(contains_term(combined, word) for word in CATEGORY_KEYWORDS["frozen_foods"]):
        category_id = "frozen_foods"
    else:
        # More specific food families are checked before generic plant/food terms.
        for candidate in (
            "seafood", "meat_offal", "mushrooms", "eggs", "dairy",
            "fruits", "vegetables", "legumes", "grains_cereals",
            "pasta_bakery", "nuts_seeds", "oils_fats", "spices",
            "herbs", "sauces_condiments", "sweeteners", "baking_dessert",
            "beverages",
        ):
            if any(contains_term(combined, word) for word in CATEGORY_KEYWORDS[candidate]):
                category_id = candidate
                break

    subcategory = None
    for parent in parents:
        parent_key = normalize(parent)
        candidates = []
        for key in SUBCATEGORY_LABELS:
            candidate = key.replace("_", " ")
            if parent_key == candidate:
                candidates.append((0, key))
            elif candidate in parent_key or parent_key in candidate:
                candidates.append((1, key))
        if candidates:
            subcategory = sorted(candidates)[0][1]
            break

    if category_id == "canned_preserved" and subcategory is None:
        if any(contains_term(combined, word) for word in ("chickpea", "garbanzo", "lentil", "bean", "pulse", "legume")):
            subcategory = "canned_legumes"
        elif any(contains_term(combined, word) for word in ("fish", "tuna", "sardine", "salmon")):
            subcategory = "canned_fish"
        elif contains_term(combined, "tomato"):
            subcategory = "tomato"
    if category_id == "dairy" and any(contains_term(combined, word) for word in ("fermented milk", "yogurt", "yoghurt", "kefir", "raib", "leben")):
        subcategory = subcategory or "fermented_milk"
    if category_id == "dairy" and contains_term(combined, "cheese"):
        subcategory = "cheese"
    if category_id == "sauces_condiments" and contains_term(combined, "mayonnaise"):
        subcategory = "mayonnaise"
    if category_id == "legumes":
        for word, sub_id in (("chickpea", "chickpeas"), ("garbanzo", "chickpeas"), ("lentil", "lentils"), ("bean", "beans"), ("pea", "peas")):
            if contains_term(combined, word):
                subcategory = sub_id
                break
    if category_id == "grains_cereals":
        for word, sub_id in (("rice", "rice"), ("wheat", "wheat"), ("flour", "flour"), ("semolina", "flour")):
            if contains_term(combined, word):
                subcategory = sub_id
                break
    if category_id == "seafood":
        if any(contains_term(combined, word) for word in ("shrimp", "prawn", "crab", "lobster", "crustacean")):
            subcategory = "crustaceans"
        elif any(contains_term(combined, word) for word in ("mollusc", "mollusk", "oyster", "mussel", "squid", "octopus")):
            subcategory = "molluscs"
        else:
            subcategory = subcategory or "fish"
    if category_id == "meat_offal":
        if any(contains_term(combined, word) for word in ("liver", "kidney", "heart", "tripe", "offal")):
            subcategory = "offal"
        elif any(contains_term(combined, word) for word in ("chicken", "turkey", "duck", "poultry")):
            subcategory = "poultry"
        elif contains_term(combined, "beef"):
            subcategory = "beef"
        elif any(contains_term(combined, word) for word in ("lamb", "mutton")):
            subcategory = "lamb"
    if category_id == "fruits" and any(contains_term(combined, word) for word in ("citrus", "orange", "lemon", "lime")):
        subcategory = "citrus"
    if category_id == "vegetables":
        if any(contains_term(combined, word) for word in ("root vegetable", "taproot", "tuber", "carrot", "potato")):
            subcategory = "root_vegetables"
        elif any(contains_term(combined, word) for word in ("leaf vegetable", "leafy", "spinach", "lettuce")):
            subcategory = "leaf_vegetables"
        elif contains_term(combined, "tomato"):
            subcategory = "tomato"
    return category_id, subcategory


def index_off_nodes(
    nodes: list[dict[str, Any]],
    foodon_names: dict[str, set[str]],
) -> tuple[dict[str, dict[str, Any]], dict[str, set[str]]]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for node in nodes:
        key = normalize(node["english"])
        if key:
            grouped[key].append(node)

    records: dict[str, dict[str, Any]] = {}
    match_index: dict[str, set[str]] = defaultdict(set)
    for key, group in grouped.items():
        group.sort(
            key=lambda item: (
                bool(item["languages"].get("ar")),
                bool(item["languages"].get("fr")),
                len(item["languages"].get("en", [])),
            ),
            reverse=True,
        )
        primary = group[0]
        languages: dict[str, set[str]] = {code: set() for code in ("ar", "en", "fr")}
        parents: set[str] = set()
        off_refs: set[str] = set()
        foodon_ids: set[str] = set()
        for node in group:
            parents.update(node["parents"])
            off_refs.add("en:" + node["english"])
            for lang, values in node["languages"].items():
                languages[lang].update(values)
                for value in values:
                    norm = normalize(value)
                    if norm:
                        match_index[norm].add(key)
                    if lang == "en":
                        foodon_ids.update(foodon_names.get(norm, set()))
            for parent in node["parents"]:
                norm = normalize(parent)
                if norm:
                    match_index[norm].add(key)

        names = {
            "en": primary["english"],
            "ar": primary["languages"].get("ar", [None])[0],
            "fr": primary["languages"].get("fr", [None])[0],
        }
        category_id, subcategory_id = classify_item(names["en"], list(parents))
        records[key] = {
            "registryKey": "off:" + key,
            "names": names,
            "aliases": languages,
            "parents": sorted(parents),
            "categoryId": category_id,
            "subcategoryId": subcategory_id,
            "sourceRefs": {
                "openFoodFacts": sorted(off_refs),
                "foodOn": sorted(foodon_ids),
                "ciqualCode": primary["ciqualCode"],
                "usdaFdcCode": primary["usdaFdcCode"],
                "wikidata": primary["wikidata"],
            },
            "units": Counter(),
            "recipeUsage": 0,
        }
        match_index[key].add(key)
        for value in languages["en"]:
            foodon_ids.update(foodon_names.get(normalize(value), set()))
        records[key]["sourceRefs"]["foodOn"] = sorted(foodon_ids)
    return records, match_index


def choose_match(
    english: str,
    arabic: str,
    french: str,
    match_index: dict[str, set[str]],
) -> tuple[str | None, str, list[str]]:
    for value, match_type in (
        (english, "off_english"),
        (arabic, "off_arabic"),
        (french, "off_french"),
    ):
        variants = english_match_variants(value) if match_type == "off_english" else [normalize(value)]
        for candidate in variants:
            candidates = match_index.get(candidate, set())
            if len(candidates) == 1:
                return next(iter(candidates)), match_type, []
            if len(candidates) > 1:
                return None, "ambiguous", sorted(candidates)[:20]
    return None, "not_found", []


def normalize_unit(raw: str) -> str:
    compact = re.sub(r"[\s_-]+", "", str(raw or "").strip().lower())
    original = str(raw or "").strip().lower()
    return UNIT_ALIASES.get(original, UNIT_ALIASES.get(compact, original))


def choose_base_unit(units: Counter[str]) -> str | None:
    return units.most_common(1)[0][0] if units else None


def add_alias(record: dict[str, Any], language: str, value: str) -> None:
    if language not in {"ar", "en", "fr"}:
        return
    clean = re.sub(r"\s+", " ", str(value or "").strip())
    if not clean:
        return
    canonical = normalize(record["names"].get(language) or "")
    if normalize(clean) != canonical:
        record["aliases"].setdefault(language, set()).add(clean)


def ensure_id(registry: dict[str, str], key: str) -> str:
    if key not in registry:
        registry[key] = str(uuid.uuid4())
    value = registry[key]
    try:
        uuid.UUID(value)
    except (ValueError, TypeError) as error:
        raise ValueError(f"Invalid UUID in identity registry for {key}") from error
    return value


def source_recipe_ingredients(recipe: dict[str, Any]) -> list[dict[str, Any]]:
    output = []
    for index, item in enumerate(recipe.get("ingredients") or []):
        if not isinstance(item, dict):
            continue
        english_raw = item.get("sourceNameEn") or item.get("sourceTextEn") or item.get("sourceText") or ""
        english = clean_recipe_english(english_raw)
        output.append({
            "index": index,
            "english": english,
            "englishRaw": str(english_raw or ""),
            "arabic": str(item.get("name") or "").strip(),
            "french": str(item.get("sourceNameFr") or "").strip(),
            "unit": normalize_unit(str(item.get("unit") or "")),
            "sourceId": str(item.get("id") or "").strip(),
        })
    return output


def recipe_record_key(item: dict[str, Any]) -> str:
    stable_name = normalize(item["english"]) or normalize(item["arabic"])
    if not stable_name:
        stable_name = "source-" + hashlib.sha256(item["englishRaw"].encode("utf-8")).hexdigest()
    return "recipe:" + stable_name


def add_recipe_record(
    records: dict[str, dict[str, Any]],
    item: dict[str, Any],
    foodon_names: dict[str, set[str]],
) -> str:
    key = recipe_record_key(item)
    if key not in records:
        category_id, subcategory_id = classify_item(item["english"], [])
        records[key] = {
            "registryKey": key,
            "names": {
                "ar": item["arabic"] or None,
                "en": item["english"] or None,
                "fr": item["french"] or None,
            },
            "aliases": {"ar": set(), "en": set(), "fr": set()},
            "parents": [],
            "categoryId": category_id,
            "subcategoryId": subcategory_id,
            "sourceRefs": {
                "openFoodFacts": [],
                "foodOn": sorted(foodon_names.get(normalize(item["english"]), set())),
                "ciqualCode": None,
                "usdaFdcCode": None,
                "wikidata": None,
                "recipeSourceIds": [],
            },
            "units": Counter(),
            "recipeUsage": 0,
        }
    record = records[key]
    add_alias(record, "ar", item["arabic"])
    add_alias(record, "en", item["english"])
    add_alias(record, "fr", item["french"])
    if item["sourceId"]:
        source_ids = record["sourceRefs"].setdefault("recipeSourceIds", [])
        if item["sourceId"] not in source_ids:
            source_ids.append(item["sourceId"])
    if item["unit"] and item["unit"] != "to_taste":
        record["units"][item["unit"]] += 1
    record["recipeUsage"] += 1
    return key


def load_registry(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("Identity registry must be a JSON object.")
    return {str(key): str(value) for key, value in data.items()}


def load_categories(path: Path) -> list[dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    categories = data.get("categories") if isinstance(data, dict) else None
    if not isinstance(categories, list) or not categories:
        raise ValueError("Category taxonomy must contain a non-empty categories array.")
    ids = [item.get("id") for item in categories]
    if len(ids) != len(set(ids)) or any(not item for item in ids):
        raise ValueError("Category IDs must be present and unique.")
    return categories


def subcategory_owner(subcategory_id: str) -> str | None:
    owners = {
        "milk": "dairy", "fermented_milk": "dairy", "yogurt": "dairy",
        "cheese": "dairy", "cream": "dairy", "butter": "dairy",
        "beef": "meat_offal", "lamb": "meat_offal", "poultry": "meat_offal", "offal": "meat_offal",
        "fish": "seafood", "crustaceans": "seafood", "molluscs": "seafood",
        "citrus": "fruits", "berries": "fruits",
        "root_vegetables": "vegetables", "leaf_vegetables": "vegetables", "tomato": "vegetables",
        "chickpeas": "legumes", "lentils": "legumes", "beans": "legumes", "peas": "legumes",
        "rice": "grains_cereals", "wheat": "grains_cereals", "flour": "grains_cereals",
        "pasta": "pasta_bakery", "bread": "pasta_bakery",
        "nuts": "nuts_seeds", "seeds": "nuts_seeds", "oils": "oils_fats",
        "spice_blends": "spices", "herbs": "herbs",
        "sauces": "sauces_condiments", "mayonnaise": "sauces_condiments", "vinegar": "sauces_condiments",
        "jams_preserves": "canned_preserved", "canned_legumes": "canned_preserved",
        "canned_fish": "canned_preserved", "frozen_vegetables": "frozen_foods",
        "chocolate_cocoa": "baking_dessert",
    }
    return owners.get(subcategory_id)


def build(args: argparse.Namespace) -> dict[str, Any]:
    off_text = args.off_taxonomy.read_text(encoding="utf-8")
    foodon_text = args.foodon_tsv.read_text(encoding="utf-8")
    recipes_doc = json.loads(args.recipes.read_text(encoding="utf-8"))
    if recipes_doc.get("complete") is not True or recipes_doc.get("translatedCount") != recipes_doc.get("expectedCount"):
        raise ValueError("Refusing to build from an incomplete recipe catalogue.")
    recipes = recipes_doc.get("recipes") or []
    if len(recipes) != int(recipes_doc["expectedCount"]):
        raise ValueError("Recipe count does not match the catalogue metadata.")

    categories_source = load_categories(args.categories)
    foodon_parents, foodon_names = parse_foodon_tsv(foodon_text)
    off_nodes = parse_off_taxonomy(off_text)
    records, match_index = index_off_nodes(off_nodes, foodon_names)
    registry_path = args.output_dir / "identity_registry.json"
    registry = load_registry(registry_path)

    recipe_map: list[dict[str, Any]] = []
    ambiguous_matches: list[dict[str, Any]] = []
    matched_off = 0
    new_recipe_occurrences = 0
    new_recipe_keys: set[str] = set()
    foodon_referenced = 0

    for recipe in recipes:
        recipe_id = str(recipe.get("id") or "")
        if not recipe_id:
            raise ValueError("A recipe is missing its ID.")
        for item in source_recipe_ingredients(recipe):
            key, match_type, candidates = choose_match(
                item["english"], item["arabic"], item["french"], match_index
            )
            if key is None:
                key = recipe_record_key(item)
                if key not in records:
                    new_recipe_keys.add(key)
                add_recipe_record(records, item, foodon_names)
                new_recipe_occurrences += 1
                if match_type == "ambiguous":
                    ambiguous_matches.append({
                        "recipeId": recipe_id,
                        "ingredientIndex": item["index"],
                        "english": item["english"],
                        "arabic": item["arabic"],
                        "candidateKeys": candidates,
                    })
                match_type = "recipe_catalogue"
            else:
                matched_off += 1
                record = records[key]
                add_alias(record, "ar", item["arabic"])
                add_alias(record, "en", item["english"])
                add_alias(record, "fr", item["french"])
                if item["unit"] and item["unit"] != "to_taste":
                    record["units"][item["unit"]] += 1
                record["recipeUsage"] += 1
                if item["sourceId"]:
                    source_ids = record["sourceRefs"].setdefault("recipeSourceIds", [])
                    if item["sourceId"] not in source_ids:
                        source_ids.append(item["sourceId"])
            if records[key]["sourceRefs"].get("foodOn"):
                foodon_referenced += 1
            recipe_map.append({
                "recipeId": recipe_id,
                "ingredientIndex": item["index"],
                "foodItemId": ensure_id(registry, records[key]["registryKey"]),
                "sourceIngredientId": item["sourceId"] or None,
                "sourceNames": {
                    "ar": item["arabic"] or None,
                    "en": item["english"] or None,
                    "fr": item["french"] or None,
                },
                "matchedBy": match_type,
            })

    legacy_id_map: dict[str, str] = {}
    for legacy_id, ar, en, fr, aliases in LEGACY_SEEDS:
        match_key, _, _ = choose_match(en, ar, fr, match_index)
        if match_key is None:
            candidate_keys = {
                candidate for alias in aliases
                for candidate in match_index.get(normalize(alias), set())
            }
            if len(candidate_keys) == 1:
                match_key = next(iter(candidate_keys))
        if match_key is None:
            item = {"english": en, "englishRaw": en, "arabic": ar, "french": fr, "unit": "", "sourceId": legacy_id}
            match_key = add_recipe_record(records, item, foodon_names)
            new_recipe_keys.add(match_key)
        legacy_id_map[legacy_id] = ensure_id(registry, records[match_key]["registryKey"])
        record = records[match_key]
        for alias in aliases:
            if re.search(r"[\u0600-\u06ff]", alias):
                language = "ar"
            elif re.search(r"[éèêàùçœ]", alias, re.I):
                language = "fr"
            else:
                language = "en"
            add_alias(record, language, alias)

    items: list[dict[str, Any]] = []
    label_collisions: dict[str, list[str]] = defaultdict(list)
    for key, record in records.items():
        names = record["names"]
        item_id = ensure_id(registry, key)
        aliases = []
        for language in ("ar", "en", "fr"):
            canonical = normalize(names.get(language) or "")
            for value in sorted(record["aliases"].get(language, set()), key=str.casefold):
                if normalize(value) and normalize(value) != canonical:
                    aliases.append({"language": language, "value": value})
        item = {
            "id": item_id,
            "names": names,
            "acceptedUnits": sorted(record["units"]),
            "baseUnit": choose_base_unit(record["units"]),
            "imageUrl": None,
            "categoryId": record["categoryId"],
            "subcategoryId": record["subcategoryId"],
            "aliases": aliases,
            "originCountry": None,
            "nutritionPer100g": None,
            "sourceRefs": record["sourceRefs"],
            "recipeUsageCount": record["recipeUsage"],
        }
        items.append(item)
        for language in ("ar", "en", "fr"):
            norm = normalize(names.get(language) or "")
            if norm:
                label_collisions[f"{language}:{norm}"].append(item_id)

    duplicate_english = [
        {"normalizedName": key, "foodItemIds": values}
        for key, values in label_collisions.items()
        if key.startswith("en:") and len(values) > 1
    ]
    non_unique_translations = [
        {"normalizedName": key, "foodItemIds": values}
        for key, values in label_collisions.items()
        if not key.startswith("en:") and len(values) > 1
    ]

    ids = [item["id"] for item in items]
    if len(ids) != len(set(ids)):
        raise ValueError("Duplicate food item IDs were generated.")
    registry_values = list(registry.values())
    if len(registry_values) != len(set(registry_values)):
        raise ValueError("Identity registry contains duplicate UUIDs.")
    english_keys = [normalize(item["names"].get("en") or "") for item in items]
    nonempty_english = [key for key in english_keys if key]
    if len(nonempty_english) != len(set(nonempty_english)):
        raise ValueError("Duplicate canonical English food names remain after merging.")
    if duplicate_english:
        raise ValueError("Duplicate English labels remain in the catalogue.")

    categories = []
    for category in categories_source:
        category_id = category["id"]
        subcategories = []
        for sub_id, labels in SUBCATEGORY_LABELS.items():
            if subcategory_owner(sub_id) == category_id:
                subcategories.append({
                    "id": sub_id,
                    "names": {"ar": labels[0], "en": labels[1], "fr": labels[2]},
                })
        categories.append({
            "id": category_id,
            "names": category["names"],
            "subcategories": subcategories,
        })
    valid_categories = {item["id"] for item in categories}
    if any(item["categoryId"] not in valid_categories for item in items):
        raise ValueError("An item references a category missing from categories.json.")

    items.sort(key=lambda item: (normalize(item["names"].get("en") or ""), item["id"]))
    recipe_map.sort(key=lambda row: (row["recipeId"], row["ingredientIndex"]))
    generated_at = datetime.now(timezone.utc).isoformat()
    source_manifest = {
        "openFoodFacts": {
            "repository": "https://github.com/openfoodfacts/openfoodfacts-server",
            "path": "taxonomies/food/ingredients.txt",
            "commit": OFF_COMMIT,
            "gitBlobSha": OFF_BLOB_SHA,
            "license": "ODbL-1.0 / DbCL-1.0",
            "attribution": "Open Food Facts",
        },
        "foodOn": {
            "repository": "https://github.com/FoodOntology/foodon",
            "path": "foodon-synonyms.tsv",
            "commit": FOODON_COMMIT,
            "gitBlobSha": FOODON_BLOB_SHA,
            "license": "CC-BY-4.0",
            "attribution": "FoodOn",
        },
        "recipeCatalogue": {
            "path": "data/recipes/ar/recipes.json",
            "revision": recipes_doc.get("generatedAt"),
        },
    }
    catalogue = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": generated_at,
        "idStrategy": "random UUIDv4 persisted in identity_registry.json; never derived from display names",
        "scope": "Generic food identities only. Brand, barcode and package-quantity records belong to market_products.",
        "sources": source_manifest,
        "categories": categories,
        "items": items,
    }
    report = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": generated_at,
        "recipeCount": len(recipes),
        "recipeIngredientOccurrences": len(recipe_map),
        "catalogueItems": len(items),
        "offTaxonomyNodes": len(off_nodes),
        "offCanonicalItemsAfterDeduplication": len(records) - len(new_recipe_keys),
        "matchedRecipeOccurrencesToOff": matched_off,
        "recipeOnlyOccurrencesAdded": new_recipe_occurrences,
        "distinctRecipeOnlyItemsAdded": len(new_recipe_keys),
        "recipeOccurrencesWithFoodOnCrossReference": foodon_referenced,
        "foodOnConceptsWithEnglishLabels": len(foodon_names),
        "foodOnParentRelations": sum(len(parents) for parents in foodon_parents.values()),
        "legacySeedIdsMapped": len(legacy_id_map),
        "ambiguousMatchesRequiringReview": ambiguous_matches,
        "duplicateEnglishCanonicalNames": duplicate_english,
        "nonUniqueArabicOrFrenchTranslations": non_unique_translations,
        "itemsMissingArabic": sum(not item["names"].get("ar") for item in items),
        "itemsMissingFrench": sum(not item["names"].get("fr") for item in items),
        "itemsWithNoObservedRecipeUnit": sum(not item["acceptedUnits"] for item in items),
        "itemsWithRecipeUsage": sum(item["recipeUsageCount"] > 0 for item in items),
        "imagesUnavailableFromTheseTaxonomySources": sum(not item["imageUrl"] for item in items),
        "originUnavailableFromTheseTaxonomySources": sum(not item["originCountry"] for item in items),
        "nutritionUnavailableFromTheseTaxonomySources": sum(not item["nutritionPer100g"] for item in items),
        "notes": [
            "FoodOn is used for semantic cross-references and parent concepts; non-food ontology classes are not imported as grocery items.",
            "Open Food Facts supplies taxonomy labels and translations, not retailer stock, brand products, barcodes or price observations.",
            "No image, production country or nutrient values are invented. Such fields remain null unless a source supplies verified values.",
            "Recipe quantities remain on recipe_ingredients; package quantities belong to future market_products records.",
            "Ambiguous exact aliases are not auto-merged. A recipe-only record is created and the ambiguity is reported for review.",
            "The combined source-derived database may carry source-specific share-alike and attribution obligations; review ODbL/DbCL and CC BY terms before external redistribution.",
        ],
    }

    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "catalogue.json").write_text(
        json.dumps(catalogue, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (args.output_dir / "recipe_ingredient_map.json").write_text(
        json.dumps({
            "schemaVersion": SCHEMA_VERSION,
            "generatedAt": generated_at,
            "recipeCatalogueRevision": recipes_doc.get("generatedAt"),
            "items": recipe_map,
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (args.output_dir / "legacy_id_map.json").write_text(
        json.dumps({"schemaVersion": SCHEMA_VERSION, "items": legacy_id_map}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (args.output_dir / "build_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    registry_path.write_text(
        json.dumps(dict(sorted(registry.items())), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recipes", type=Path, default=Path("data/recipes/ar/recipes.json"))
    parser.add_argument("--off-taxonomy", type=Path, required=True)
    parser.add_argument("--foodon-tsv", type=Path, required=True)
    parser.add_argument("--categories", type=Path, default=Path("data/food-catalogue/categories.json"))
    parser.add_argument("--output-dir", type=Path, default=Path("data/food-catalogue"))
    args = parser.parse_args()
    report = build(args)
    print(json.dumps({
        "recipeCount": report["recipeCount"],
        "recipeIngredientOccurrences": report["recipeIngredientOccurrences"],
        "catalogueItems": report["catalogueItems"],
        "matchedRecipeOccurrencesToOff": report["matchedRecipeOccurrencesToOff"],
        "distinctRecipeOnlyItemsAdded": report["distinctRecipeOnlyItemsAdded"],
        "ambiguousMatchesRequiringReview": len(report["ambiguousMatchesRequiringReview"]),
        "duplicateEnglishCanonicalNames": len(report["duplicateEnglishCanonicalNames"]),
        "legacySeedIdsMapped": report["legacySeedIdsMapped"],
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print("FOOD CATALOGUE BUILD FAILED: " + str(error), file=sys.stderr)
        raise
