#!/usr/bin/env python3
"""Build Midati's generic food-item catalogue from OFF + FoodOn + recipe usage.

The output is a generic food identity catalogue, not a branded/packaged product
catalogue. Retailer products and barcode/package variants belong in the separate
market-product/pricing domain designed for a later phase.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import unicodedata
import urllib.error
import urllib.request
import uuid
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[1]
OFF_COMMIT = "f5abb0a0ca8ae5702f3e192219511adf211b55ee"
FOODON_COMMIT = "8a27493346a122e86ac1748aac24199d5af69166"
OFF_URL = (
    "https://raw.githubusercontent.com/openfoodfacts/openfoodfacts-server/"
    + OFF_COMMIT + "/taxonomies/food/ingredients.txt"
)
FOODON_URL = (
    "https://raw.githubusercontent.com/FoodOntology/foodon/"
    + FOODON_COMMIT + "/foodon-base.owl"
)
OFF_GIT_BLOB_SHA = "0c54acc70878b778a38d893c877ea49c6ef87ff6"
FOODON_GIT_BLOB_SHA = "f36ed27ce6e7b4b039f2edbcb72061892f96b670"

UUID_NAMESPACE = uuid.UUID("ed3f7bd4-4b12-5d6d-9eb1-2b6de75e1a55")
OFF_REPOSITORY = "https://github.com/openfoodfacts/openfoodfacts-server"
FOODON_REPOSITORY = "https://github.com/FoodOntology/foodon"

RDF = "{http://www.w3.org/1999/02/22-rdf-syntax-ns#}"
RDFS_LABEL = "{http://www.w3.org/2000/01/rdf-schema#}label"
OWL_CLASS = "{http://www.w3.org/2002/07/owl#}Class"
XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"

CATEGORY_PATH = ROOT / "data" / "products" / "categories.json"
CURATED_ITEMS_PATH = ROOT / "data" / "products" / "curated-food-items.json"
CATEGORY_CATALOGUE = json.loads(CATEGORY_PATH.read_text(encoding="utf-8"))
CATEGORY_DATA = CATEGORY_CATALOGUE.get("categories") or []

CATEGORY_ROOT_IDS = {
    "fruits": "foodcat-fruits",
    "vegetables": "foodcat-vegetables",
    "dairy": "foodcat-dairy",
    "meat_offal": "foodcat-meat",
    "poultry": "foodcat-poultry",
    "seafood": "foodcat-seafood",
    "legumes": "foodcat-legumes",
    "canned_preserved": "foodcat-canned",
    "grains_cereals": "foodcat-grains",
    "bakery_pasta": "foodcat-pasta-bakery",
    "nuts_seeds": "foodcat-nuts-seeds",
    "oils_fats": "foodcat-oils",
    "spices": "foodcat-spices",
    "herbs": "foodcat-herbs",
    "sauces_condiments": "foodcat-sauces",
    "sweeteners": "foodcat-sweeteners",
    "eggs": "foodcat-eggs",
    "mushrooms": "foodcat-mushrooms",
    "frozen": "foodcat-frozen",
    "baking": "foodcat-baking",
    "beverages": "foodcat-beverages",
    "other": "foodcat-other",
}

SUBCATEGORY_IDS = {
    ("fruits", "citrus"): "foodsub-fruits-citrus",
    ("fruits", "berries"): "foodsub-fruits-berries",
    ("fruits", "tropical_fruits"): "foodsub-fruits-tropical",
    ("fruits", "stone_fruits"): "foodsub-fruits-other",
    ("fruits", "dried_fruits"): "foodsub-fruits-dates",
    ("fruits", None): "foodsub-fruits-other",
    ("vegetables", "leafy_vegetables"): "foodsub-vegetables-leafy",
    ("vegetables", "root_tubers"): "foodsub-vegetables-roots",
    ("vegetables", "bulb_vegetables"): "foodsub-vegetables-bulbs",
    ("vegetables", "fruit_vegetables"): "foodsub-vegetables-fruit",
    ("vegetables", None): "foodsub-vegetables-other",
    ("dairy", "milk"): "foodsub-dairy-milk",
    ("dairy", "fermented_dairy"): "foodsub-dairy-fermented",
    ("dairy", "yogurt"): "foodsub-dairy-yogurt",
    ("dairy", "cheese"): "foodsub-dairy-cheese",
    ("dairy", "cream_butter"): "foodsub-dairy-cream-butter",
    ("dairy", None): "foodsub-dairy-other",
    ("meat_offal", "red_meat"): "foodsub-meat-red",
    ("meat_offal", "offal"): "foodsub-meat-offal",
    ("meat_offal", "processed_meat"): "foodsub-meat-processed",
    ("poultry", "chicken"): "foodsub-poultry-chicken",
    ("poultry", "turkey"): "foodsub-poultry-turkey",
    ("poultry", None): "foodsub-poultry-other",
    ("seafood", "fish"): "foodsub-seafood-fish",
    ("seafood", "crustaceans"): "foodsub-seafood-crustaceans",
    ("seafood", "molluscs"): "foodsub-seafood-molluscs",
    ("seafood", "shellfish"): "foodsub-seafood-crustaceans",
    ("seafood", None): "foodsub-seafood-fish",
    ("legumes", "chickpeas"): "foodsub-legumes-chickpeas",
    ("legumes", "lentils"): "foodsub-legumes-lentils",
    ("legumes", "beans"): "foodsub-legumes-beans",
    ("legumes", "peas"): "foodsub-legumes-peas-fava",
    ("legumes", None): "foodsub-legumes-other",
    ("canned_preserved", "canned_legumes"): "foodsub-canned-legumes",
    ("canned_preserved", "canned_vegetables"): "foodsub-canned-vegetables",
    ("canned_preserved", "canned_seafood"): "foodsub-canned-fish",
    ("canned_preserved", "canned_fruit"): "foodsub-canned-fruit",
    ("canned_preserved", "pickled_preserved"): "foodsub-canned-pickles",
    ("canned_preserved", None): "foodsub-canned-other",
    ("grains_cereals", "rice"): "foodsub-grains-rice",
    ("grains_cereals", "wheat_semolina"): "foodsub-grains-wheat-semolina",
    ("grains_cereals", "flour"): "foodsub-grains-flour",
    ("grains_cereals", "oats_barley"): "foodsub-grains-oats",
    ("grains_cereals", None): "foodsub-grains-other",
    ("bakery_pasta", "bread"): "foodsub-pasta-bakery-bread",
    ("bakery_pasta", "pasta"): "foodsub-pasta-bakery-pasta",
    ("bakery_pasta", "couscous"): "foodsub-pasta-bakery-couscous",
    ("bakery_pasta", None): "foodsub-pasta-bakery-pastry",
    ("nuts_seeds", "tree_nuts"): "foodsub-nuts-tree",
    ("nuts_seeds", "seeds"): "foodsub-nuts-seeds",
    ("nuts_seeds", "peanuts"): "foodsub-nuts-peanuts",
    ("oils_fats", "olive_oil"): "foodsub-oils-olive",
    ("oils_fats", "cooking_oils"): "foodsub-oils-vegetable",
    ("oils_fats", None): "foodsub-oils-other",
    ("spices", "whole_spices"): "foodsub-spices-whole",
    ("spices", "ground_spices"): "foodsub-spices-ground",
    ("spices", "spice_blends"): "foodsub-spices-blends",
    ("spices", None): "foodsub-spices-ground",
    ("herbs", "fresh_herbs"): "foodsub-herbs-fresh",
    ("herbs", "dried_herbs"): "foodsub-herbs-dried",
    ("herbs", None): "foodsub-herbs-fresh",
    ("sauces_condiments", "mayonnaise"): "foodsub-sauces-mayonnaise",
    ("sauces_condiments", "ketchup_mustard"): "foodsub-sauces-ketchup-mustard",
    ("sauces_condiments", "tomato_sauce"): "foodsub-sauces-tomato",
    ("sauces_condiments", None): "foodsub-sauces-other",
    ("sweeteners", "sugar"): "foodsub-sweeteners-sugar",
    ("sweeteners", "honey"): "foodsub-sweeteners-honey",
    ("sweeteners", "jam"): "foodsub-sweeteners-jam",
    ("sweeteners", "syrup"): "foodsub-sweeteners-syrup",
    ("sweeteners", None): "foodsub-sweeteners-sugar",
    ("eggs", None): "foodsub-eggs-hen",
    ("mushrooms", None): "foodsub-mushrooms-edible",
    ("frozen", "frozen_vegetables"): "foodsub-frozen-vegetables",
    ("frozen", "frozen_fruit"): "foodsub-frozen-fruit",
    ("frozen", "frozen_meat_fish"): "foodsub-frozen-meat-fish",
    ("frozen", None): "foodsub-frozen-other",
    ("baking", "yeast"): "foodsub-baking-yeast",
    ("baking", "cocoa"): "foodsub-baking-cocoa",
    ("baking", None): "foodsub-baking-other",
    ("beverages", "coffee"): "foodsub-beverages-coffee",
    ("beverages", "tea"): "foodsub-beverages-tea",
    ("beverages", "juice"): "foodsub-beverages-juice",
    ("beverages", "water"): "foodsub-beverages-water",
    ("beverages", None): "foodsub-beverages-other",
    ("other", None): "foodsub-other-general",
}

def canonical_category_ids(category_slug: str, subcategory_slug: str | None) -> tuple[str, str | None]:
    category_id = CATEGORY_ROOT_IDS.get(category_slug, CATEGORY_ROOT_IDS["other"])
    subcategory_id = SUBCATEGORY_IDS.get((category_slug, subcategory_slug))
    if subcategory_id is None:
        subcategory_id = SUBCATEGORY_IDS.get((category_slug, None))
    valid_category = next((item for item in CATEGORY_DATA if item.get("id") == category_id), None)
    valid_subcategories = {
        child.get("id")
        for child in (valid_category or {}).get("subcategories", [])
    }
    if subcategory_id not in valid_subcategories:
        subcategory_id = None
    return category_id, subcategory_id


def normalize_label(value: str) -> str:
    value = unicodedata.normalize("NFKC", str(value or "")).casefold()
    value = value.replace("ـ", "")
    value = "".join(
        char for char in value
        if not (0x064B <= ord(char) <= 0x065F or ord(char) == 0x0670)
    )
    value = "".join(
        char if char.isalnum() else " "
        for char in value
    )
    return " ".join(value.split())


def split_values(value: str) -> list[str]:
    return list(dict.fromkeys(part.strip() for part in value.split(",") if part.strip()))


def git_blob_sha(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()


def download_pinned(url: str, target: Path, expected_blob_sha: str) -> Path:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.is_file() and git_blob_sha(target.read_bytes()) == expected_blob_sha:
        return target
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "Midati-Food-Catalogue-Builder/1.0"},
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            data = response.read()
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise RuntimeError(f"Could not download pinned dataset {url}: {error}") from error
    actual = git_blob_sha(data)
    if actual != expected_blob_sha:
        raise RuntimeError(
            f"Dataset checksum mismatch for {url}: expected {expected_blob_sha}, got {actual}"
        )
    temporary = target.with_suffix(target.suffix + ".part")
    temporary.write_bytes(data)
    temporary.replace(target)
    return target


def parse_off_taxonomy(path: Path) -> list[dict[str, Any]]:
    """Parse the Open Food Facts food/ingredients taxonomy blocks."""
    records: list[dict[str, Any]] = []
    fields: dict[str, list[str]] = defaultdict(list)
    parents: list[str] = []

    def flush() -> None:
        nonlocal fields, parents
        english = fields.get("en", [])
        if english:
            all_names = {
                lang: list(values)
                for lang, values in fields.items()
                if lang in {"ar", "en", "fr"} and values
            }
            # OFF sometimes places لبن/رايب under milk as an Arabic alias.
            # In Algerian grocery usage those are separate fermented dairy foods.
            if normalize_label(english[0]) in {"milk", "fresh milk", "cow milk", "whole milk"}:
                not_fresh_milk = {"لبن", "اللبن", "رايب", "الرايب"}
                arabic_values = [
                    value for value in all_names.get("ar", [])
                    if normalize_label(value) not in not_fresh_milk
                ]
                if arabic_values:
                    all_names["ar"] = arabic_values
                else:
                    all_names.pop("ar", None)
            record = {
                "name": english[0],
                "names": {lang: values[0] for lang, values in all_names.items() if values},
                "aliases": {lang: list(values[1:]) for lang, values in all_names.items() if len(values) > 1},
                "allNames": all_names,
                "parents": list(dict.fromkeys(parents)),
                "sourceProperties": {},
            }
            for key, values in fields.items():
                if key.startswith("ecobalyse_default_origin"):
                    record["sourceProperties"]["defaultOrigin"] = values[0] if values else ""
                elif key.startswith("usda_fdc_code"):
                    record["sourceProperties"]["usdaFdcCode"] = values[0] if values else ""
                elif key.startswith("ciqual_food_code"):
                    record["sourceProperties"]["ciqualFoodCode"] = values[0] if values else ""
                elif key == "countryOfOrigin" and values:
                    record["sourceProperties"]["countryOfOrigin"] = list(dict.fromkeys(values))
            records.append(record)
        fields = defaultdict(list)
        parents = []

    with path.open("r", encoding="utf-8") as source:
        for raw_line in source:
            line = raw_line.strip()
            if not line:
                flush()
                continue
            if line.startswith("#"):
                continue
            parent_match = re.match(r"^<\s*en:(.+)$", line)
            if parent_match:
                parents.append(parent_match.group(1).strip())
                continue
            language_match = re.match(r"^([a-z]{2,3}):\s*(.*)$", line, re.IGNORECASE)
            if language_match:
                language = language_match.group(1).lower()
                value = language_match.group(2).strip()
                if language in {"ar", "en", "fr"} and value:
                    fields[language].extend(split_values(value))
                continue
            origin_match = re.match(r"^(?:origin|origins|country_of_origin):[a-z]{2,3}:\s*(.*)$", line, re.IGNORECASE)
            if origin_match and origin_match.group(1).strip():
                for origin in re.split(r"[,;]", origin_match.group(1)):
                    value = re.sub(r"^[a-z]{2,3}:\s*", "", origin.strip(), flags=re.IGNORECASE)
                    value = value.replace("_", " ").strip()
                    if value:
                        fields["countryOfOrigin"].append(value[:1].upper() + value[1:])

            property_match = re.match(r"^(ecobalyse_default_origin|usda_fdc_code|ciqual_food_code):en:\s*(.*)$", line)
            if property_match and property_match.group(2).strip():
                fields[property_match.group(1)].append(property_match.group(2).strip())
    flush()
    return records


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def parse_foodon_owl(path: Path) -> dict[str, dict[str, Any]]:
    """Extract FoodOn classes, language-tagged labels, synonyms and parents."""
    records: dict[str, dict[str, Any]] = {}
    for _, element in ET.iterparse(path, events=("end",)):
        if element.tag != OWL_CLASS:
            continue
        iri = element.attrib.get(RDF + "about", "")
        match = re.search(r"(FOODON_[0-9]+)", iri)
        if match:
            names: dict[str, list[str]] = defaultdict(list)
            aliases: dict[str, list[str]] = defaultdict(list)
            parents: list[str] = []
            for child in list(element):
                local = _local_name(child.tag)
                value = (child.text or "").strip()
                language = (child.attrib.get(XML_LANG) or "en").lower().split("-", 1)[0]
                if local == "label" and value and language in {"ar", "en", "fr"}:
                    if value not in names[language]:
                        names[language].append(value)
                elif "synonym" in local.lower() and value and language in {"ar", "en", "fr"}:
                    if value not in aliases[language]:
                        aliases[language].append(value)
                elif local == "subClassOf":
                    parent_iri = child.attrib.get(RDF + "resource", "")
                    parent_match = re.search(r"(FOODON_[0-9]+)", parent_iri)
                    if parent_match:
                        parents.append(parent_match.group(1))
            primary_name = (
                (names.get("en") or names.get("fr") or names.get("ar") or [""])[0]
            )
            if primary_name:
                records[match.group(1)] = {
                    "id": match.group(1),
                    "name": primary_name,
                    "names": {
                        language: values[0]
                        for language, values in names.items()
                        if values
                    },
                    "aliasesByLanguage": {
                        language: values
                        for language, values in aliases.items()
                        if values
                    },
                    "aliases": list(dict.fromkeys(
                        value for values in aliases.values() for value in values
                    )),
                    "parents": list(dict.fromkeys(parents)),
                }
        element.clear()
    return records


def build_foodon_indexes(foodon: dict[str, dict[str, Any]]) -> tuple[dict[str, set[str]], dict[str, list[str]]]:
    label_index: dict[str, set[str]] = defaultdict(set)
    for item_id, item in foodon.items():
        names = list(item.get("names", {}).values())
        language_aliases = [
            value
            for values in item.get("aliasesByLanguage", {}).values()
            for value in values
        ]
        for name in [item["name"], *names, *item["aliases"], *language_aliases]:
            key = normalize_label(name)
            if key:
                label_index[key].add(item_id)

    ancestor_cache: dict[str, list[str]] = {}

    def ancestors(item_id: str, seen: set[str] | None = None) -> list[str]:
        if item_id in ancestor_cache:
            return ancestor_cache[item_id]
        seen = set() if seen is None else set(seen)
        if item_id in seen or len(seen) > 20:
            return []
        seen.add(item_id)
        item = foodon.get(item_id)
        if not item:
            return []
        result: list[str] = []
        for parent_id in item["parents"]:
            parent = foodon.get(parent_id)
            if parent:
                result.append(parent["name"])
                result.extend(ancestors(parent_id, seen))
        result = list(dict.fromkeys(result))
        ancestor_cache[item_id] = result
        return result

    for item_id in foodon:
        ancestors(item_id)
    return label_index, ancestor_cache


def category_for(names: Iterable[str]) -> tuple[str, str | None]:
    text = " ".join(str(value).casefold() for value in names if value)
    compact = normalize_label(text)

    def has(*terms: str) -> bool:
        return any(normalize_label(term) in compact for term in terms)

    canned = has("canned", "tinned", "in conserve", "en conserve", "appertized", "preserved", "conserva", "conserve", "معلب")
    seafood = has("fish", "tuna", "sardine", "anchovy", "salmon", "shrimp", "prawn", "seafood", "shellfish", "mollusc", "mussel", "oyster", "squid", "octopus", "crab", "lobster", "سمك", "تونة", "سردين", "جمبري")
    legumes = has("chickpea", "garbanzo", "lentil", "pulse", "legume", "dried bean", "kidney bean", "white bean", "haricot", "حمص", "عدس", "فاصولياء", "لوبيا")
    vegetables = has("vegetable", "tomato", "potato", "onion", "carrot", "pepper", "courgette", "zucchini", "cabbage", "spinach", "lettuce", "eggplant", "aubergine", "green bean", "pea pod", "خضروات", "بطاطا", "طماطم", "بصل", "جزر")
    fruit = has("fruit", "apple", "banana", "orange", "lemon", "date", "grape", "berry", "mango", "pear", "peach", "apricot", "تمر", "تفاح", "موز", "برتقال")
    dairy = has("milk", "lait", "yogurt", "yoghurt", "yoghourt", "yaourt", "raib", "rayeb", "leben", "lben", "kefir", "buttermilk", "fermented milk", "cream", "cheese", "fromage", "butter", "beurre", "حليب", "لبن", "رايب", "زبادي", "جبن")
    meat = has("meat", "beef", "lamb", "mutton", "veal", "chicken", "turkey", "duck", "pork", "offal", "liver", "kidney", "heart", "tripe", "tongue", "لحوم", "دجاج", "كبد", "قلب")
    poultry = has("chicken", "turkey", "duck", "poultry", "دجاج", "ديك رومي")
    grains = has("grain", "cereal", "wheat", "semolina", "semoule", "flour", "rice", "oat", "barley", "cornmeal", "couscous", "سميد", "فرينة", "دقيق", "أرز")
    nuts = has("nut", "almond", "walnut", "hazelnut", "pistachio", "peanut", "seed", "sesame", "لوز", "جوز", "بذور")
    spices = has("spice", "peppercorn", "cumin", "coriander seed", "paprika", "turmeric", "cinnamon", "clove", "saffron", "بهارات", "كمون", "فلفل أسود", "قرفة")
    herbs = has("herb", "parsley", "coriander leaf", "cilantro", "mint", "thyme", "basil", "rosemary", "معدنوس", "قصبر", "نعناع")
    oils = has("oil", "fat", "margarine", "olive oil", "sunflower oil", "زيت", "سمن", "مارغرين")
    sauces = has("sauce", "mayonnaise", "ketchup", "mustard", "harissa", "paste", "condiment", "vinegar", "صلصة", "مايونيز", "خردل", "هريسة")
    sweeteners = has("sugar", "honey", "syrup", "molasses", "jam", "sweetener", "عسل", "سكر", "مربى")
    eggs = has("egg", "بيض")
    mushrooms = has("mushroom", "fungus", "truffle", "فطر")
    frozen = has("frozen", "deep frozen", "surgelé", "مجمد")
    baking = has("baking powder", "baking soda", "yeast", "cocoa", "chocolate", "vanilla extract", "gelatin", "خميرة", "كاكاو")
    beverages = has("beverage", "drink", "juice", "coffee", "tea", "water", "مشروب", "عصير", "قهوة", "شاي")

    if frozen:
        if has("vegetable", "vegetables"):
            return "frozen", "frozen_vegetables"
        if fruit:
            return "frozen", "frozen_fruit"
        if seafood or meat:
            return "frozen", "frozen_meat_fish"
        return "frozen", None

    if canned:
        if seafood:
            return "canned_preserved", "canned_seafood"
        if legumes:
            return "canned_preserved", "canned_legumes"
        if fruit:
            return "canned_preserved", "canned_fruit"
        if vegetables:
            return "canned_preserved", "canned_vegetables"
        return "canned_preserved", "pickled_preserved"

    # Commercially prepared sauces/pastes must not be misfiled as raw produce.
    if sauces:
        if has("mayonnaise", "مايونيز"):
            return "sauces_condiments", "mayonnaise"
        if has("ketchup", "mustard", "خردل"):
            return "sauces_condiments", "ketchup_mustard"
        if has("tomato paste", "tomato sauce", "paste", "معجون الطماطم"):
            return "sauces_condiments", "tomato_sauce"
        return "sauces_condiments", None

    if dairy:
        if has("cheese", "fromage", "جبن"):
            return "dairy", "cheese"
        # Lben and raib are separate Algerian fermented dairy foods.
        if has("raib", "rayeb", "leben", "lben", "kefir", "buttermilk", "fermented milk", "لبن", "اللبن", "رايب", "الرايب"):
            return "dairy", "fermented_dairy"
        if has("yogurt", "yoghurt", "yoghourt", "yaourt", "زبادي", "yogurt drink"):
            return "dairy", "yogurt"
        if has("cream", "butter", "beurre"):
            return "dairy", "cream_butter"
        if has("milk", "lait", "حليب"):
            return "dairy", "milk"
        return "dairy", None

    if seafood:
        if has("shrimp", "prawn", "crab", "lobster", "shellfish", "crustacean"):
            return "seafood", "crustaceans"
        if has("mussel", "oyster", "squid", "octopus", "mollusc", "clam"):
            return "seafood", "molluscs"
        return "seafood", "fish"

    if poultry:
        if has("turkey", "ديك رومي"):
            return "poultry", "turkey"
        if has("chicken", "دجاج"):
            return "poultry", "chicken"
        return "poultry", None

    if meat:
        if has("liver", "kidney", "heart", "tripe", "tongue", "offal", "كبد", "قلب"):
            return "meat_offal", "offal"
        if has("sausage", "bacon", "ham", "processed meat"):
            return "meat_offal", "processed_meat"
        return "meat_offal", "red_meat"

    if legumes:
        if has("chickpea", "garbanzo", "حمص"):
            return "legumes", "chickpeas"
        if has("lentil", "عدس"):
            return "legumes", "lentils"
        if has("pea", "بازلاء", "fava"):
            return "legumes", "peas"
        return "legumes", "beans"

    if vegetables:
        if has("potato", "carrot", "beet", "radish", "turnip", "sweet potato", "بطاطا", "جزر"):
            return "vegetables", "root_tubers"
        if has("onion", "garlic", "shallot", "leek", "بصل", "ثوم"):
            return "vegetables", "bulb_vegetables"
        if has("spinach", "lettuce", "cabbage", "kale", "chard", "parsley leaf", "سبانخ"):
            return "vegetables", "leafy_vegetables"
        return "vegetables", "fruit_vegetables"

    if fruit:
        if has("lemon", "lime", "orange", "citrus", "grapefruit", "برتقال", "ليمون"):
            return "fruits", "citrus"
        if has("berry", "strawberry", "raspberry", "blueberry"):
            return "fruits", "berries"
        if has("date", "raisin", "dried fruit", "تمر", "زبيب"):
            return "fruits", "dried_fruits"
        if has("peach", "plum", "apricot", "cherry", "مشمش"):
            return "fruits", "stone_fruits"
        if has("mango", "pineapple", "papaya", "banana", "kiwi"):
            return "fruits", "tropical_fruits"
        return "fruits", None

    if nuts:
        if has("peanut", "فول سوداني"):
            return "nuts_seeds", "peanuts"
        if has("almond", "walnut", "hazelnut", "pistachio", "لوز", "جوز"):
            return "nuts_seeds", "tree_nuts"
        return "nuts_seeds", "seeds"
    if spices:
        return "spices", "spice_blends" if has("blend", "mix", "seasoning", "بهارات مشكلة") else (
            "whole_spices" if has("whole", "seed", "peppercorn") else "ground_spices"
        )
    if herbs:
        return "herbs", "fresh_herbs" if has("fresh", "leaf", "leaves", "طازج") else "dried_herbs"
    if oils:
        return "oils_fats", "olive_oil" if has("olive oil", "زيت الزيتون") else "cooking_oils"
    if sweeteners:
        if has("honey", "عسل"):
            return "sweeteners", "honey"
        if has("jam", "مربى"):
            return "sweeteners", "jam"
        if has("syrup", "molasses"):
            return "sweeteners", "syrup"
        return "sweeteners", "sugar"
    if eggs:
        return "eggs", None
    if mushrooms:
        return "mushrooms", None
    if grains:
        if has("rice", "riz", "أرز"):
            return "grains_cereals", "rice"
        if has("semolina", "semoule", "wheat", "قمح", "سميد"):
            return "grains_cereals", "wheat_semolina"
        if has("flour", "farine", "فرينة", "دقيق"):
            return "grains_cereals", "flour"
        return "grains_cereals", "oats_barley"
    if has("bread", "pasta", "couscous", "noodle", "خبز", "عجائن"):
        if has("couscous", "كسكس"):
            return "bakery_pasta", "couscous"
        if has("pasta", "noodle", "macaroni"):
            return "bakery_pasta", "pasta"
        return "bakery_pasta", "bread"
    if baking:
        return "baking", "cocoa" if has("cocoa", "chocolate") else "yeast" if has("yeast", "baking powder", "خميرة") else None
    if beverages:
        if has("coffee", "قهوة"):
            return "beverages", "coffee"
        if has("tea", "شاي"):
            return "beverages", "tea"
        if has("juice", "عصير"):
            return "beverages", "juice"
        if has("water", "ماء"):
            return "beverages", "water"
        return "beverages", None
    return "other", None

def default_units(category_id: str, subcategory_id: str | None, names: Iterable[str]) -> list[str]:
    text = normalize_label(" ".join(names))
    if category_id == "foodcat-eggs" or "egg" in text:
        return ["piece", "dozen", "g", "kg"]
    if category_id in {"foodcat-dairy", "foodcat-beverages", "foodcat-oils", "foodcat-sauces"} or any(token in text for token in ("milk", "lait", "juice", "oil", "sauce", "water")):
        return ["ml", "l", "g", "kg"]
    if category_id in {"foodcat-spices", "foodcat-herbs"}:
        return ["g", "kg", "ملعقة صغيرة", "ملعقة كبيرة"]
    if category_id == "foodcat-other":
        return ["g", "kg", "ml", "l", "piece"]
    return ["g", "kg", "piece"]


def normalize_recipe_unit(unit: str, quantity: Any) -> str | None:
    raw = str(unit or "").strip()
    key = re.sub(r"[\s_-]+", "", raw.lower())
    mapping = {
        "kg": "g", "mg": "g", "oz": "g", "lb": "g", "lbs": "g",
        "l": "ml",
        "pcs": "piece", "pc": "piece", "piece": "piece", "pieces": "piece",
        "tbsp": "ملعقة كبيرة", "tbs": "ملعقة كبيرة", "tablespoon": "ملعقة كبيرة", "tablespoons": "ملعقة كبيرة",
        "tsp": "ملعقة صغيرة", "teaspoon": "ملعقة صغيرة", "teaspoons": "ملعقة صغيرة",
        "cup": "كوب", "cups": "كوب",
        "taste": "حسب الحاجة", "totaste": "حسب الحاجة",
        "pinch": "رشة", "pinches": "رشة",
        "clove": "فص", "cloves": "فص",
        "bunch": "ربطة", "bunches": "ربطة",
        "slice": "شريحة", "slices": "شريحة",
        "handful": "حفنة", "handfuls": "حفنة",
        "sprig": "sprig", "sprigs": "sprig",
        "stalk": "stalk", "stalks": "stalk",
    }
    if not key:
        return "حسب الحاجة" if str(quantity or "").strip() == "" else "piece"
    return mapping.get(key, raw)


def _clean_english_ingredient(value: str) -> str:
    """Extract a generic food name; quantity and recipe preparation are not identity."""
    text = str(value or "").strip()
    if not text:
        return ""

    # Imported rows sometimes put the measure after a colon.
    if ":" in text:
        left, right = text.split(":", 1)
        if re.match(
            r"^\s*(?:about\s+|approximately\s+|~\s*)?(?:\d|to\s+taste\b|as\s+needed\b)",
            right,
            flags=re.IGNORECASE,
        ):
            text = left.strip()

    # Keep useful food-name parentheses only as a fallback, e.g. queso Paraguay.
    parenthetical_names = []
    for match in re.finditer(r"\(([^()]*)\)", text):
        candidate = match.group(1).strip()
        if candidate and not re.search(r"\d|\b(?:g|kg|ml|l|oz|lb)\b", candidate, re.I):
            parenthetical_names.append(candidate)
    text = re.sub(r"\([^)]*\)", " ", text)

    amount = r"(?:\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?)"
    units = (
        r"(?:kilograms?|kilogrammes?|kg|grams?|grammes?|g|millilit(?:er|re)s?|ml|"
        r"lit(?:er|re)s?|l|ounces?|oz|pounds?|lbs?|cups?|cup|tablespoons?|tbsp|tbs|"
        r"teaspoons?|tsp|pieces?|pcs?|cloves?|bunch(?:es)?|sprigs?|stalks?|slices?|"
        r"handfuls?|pinches?|cans?|tins?|jars?|bottles?|packs?|packages?|dozens?)"
    )
    text = re.sub(
        rf"^\s*(?:(?:about|approximately|around)\s+)?{amount}\s*(?:{units}\b\s*)?",
        "",
        text,
        count=1,
        flags=re.IGNORECASE,
    )
    text = re.sub(
        r"^\s*(?:a|an)\s+(?:bunch|handful|pinch|dash|sprig|clove|slice|can|tin|jar|bottle|cup|tablespoon|teaspoon)\s+(?:of\s+)?",
        "",
        text,
        count=1,
        flags=re.IGNORECASE,
    )

    # Some source rows omit the count but still put a measure word before
    # the food name (e.g. "bunch Chinese broccoli").
    text = re.sub(
        r"^\s*(?:bunches?|handfuls?|pinches?|dashes?|sprigs?|stalks?|cloves?|slices?|cans?|tins?|jars?|bottles?|cups?|tablespoons?|teaspoons?)\s+(?:of\s+)?",
        "",
        text,
        count=1,
        flags=re.IGNORECASE,
    )

    # "1 small or 3 Thai eggplants" names one food with alternate quantities.
    text = re.sub(
        r"^\s*(?:extra[- ]?)?(?:small|medium|large)\s+or\s+\d+(?:[.,]\d+)?\s+",
        "",
        text,
        count=1,
        flags=re.IGNORECASE,
    )
    text = re.sub(
        r"^\s*(?:extra[- ]large|large|medium|small|ripe|unripe)\s+",
        "",
        text,
        count=1,
        flags=re.IGNORECASE,
    )
    text = re.sub(
        r"\s+(?:plus|and)\s+(?:a\s+)?(?:pinch|dash|sprinkle)\b.*$",
        "",
        text,
        flags=re.IGNORECASE,
    )

    preparation_phrases = (
        "cut into chunks", "cut into cubes", "cut into pieces",
        "thinly sliced", "finely chopped", "roughly chopped",
        "skin removed", "seeds removed", "for garnish", "as needed",
        "to taste", "for serving",
    )
    for phrase in preparation_phrases:
        text = re.sub(r"\b" + re.escape(phrase) + r"\b", " ", text, flags=re.IGNORECASE)
    preparation_words = {
        "cooked", "uncooked", "raw", "boiled", "baked", "roasted", "steamed",
        "grilled", "fried", "skinless", "boneless", "chopped", "diced",
        "sliced", "grated", "peeled", "shredded", "halved", "quartered",
        "crumbled", "hydrated", "soaked", "drained", "rinsed", "washed",
        "trimmed", "deseeded", "deveined", "mashed", "pureed", "puréed",
    }
    text = re.sub(
        r"\b(?:" + "|".join(sorted(map(re.escape, preparation_words), key=len, reverse=True)) + r")\b",
        " ",
        text,
        flags=re.IGNORECASE,
    )
    text = re.sub(r"^\s*(?:of|a|an)\s+", "", text, flags=re.IGNORECASE)
    text = re.sub(r"\s+", " ", text).strip(" ,;:-")
    if not text or normalize_label(text) in preparation_words:
        return parenthetical_names[0] if parenthetical_names else ""
    return text

def room_ingredient_id(ingredient: dict[str, Any], recipe_id: str) -> str:
    source_id = str(ingredient.get("id") or "").strip()
    if source_id:
        # Exact mirror of ArabicRecipeCatalogueInstaller.kt: no slugification.
        return "orc-ing-unitools-" + source_id.lower()
    arabic_name = str(ingredient.get("name") or "").strip()
    unit = normalize_recipe_unit(str(ingredient.get("unit") or ""), ingredient.get("quantity"))
    # The exact legacy Focus ID algorithm is intentionally mirrored until the
    # app switches recipe references through the generated mapping file.
    normalized = re.sub(r"\s+", " ", arabic_name.lower().strip()) + "|" + str(unit or "").strip().lower()
    suffix = hashlib.sha256(normalized.encode("utf-8")).digest()[:10].hex()
    return "orc-ing-" + suffix


def collect_recipe_groups(recipes: list[dict[str, Any]]) -> list[dict[str, Any]]:
    groups: dict[str, dict[str, Any]] = {}
    id_to_key: dict[str, str] = {}
    for recipe in recipes:
        recipe_id = str(recipe.get("id") or "")
        for ingredient in recipe.get("ingredients") or []:
            if not isinstance(ingredient, dict):
                continue
            arabic = str(ingredient.get("name") or "").strip()
            english_source = str(
                ingredient.get("sourceNameEn")
                or ingredient.get("sourceTextEn")
                or ingredient.get("sourceTextEs")
                or ""
            ).strip()
            # Both upstream imports sometimes place a quantity/preparation note
            # inside the English ingredient string. Strip that consistently,
            # including UniTools sourceNameEn, while retaining meaningful food
            # qualifiers such as cooked, canned, skinless or unsweetened.
            english = _clean_english_ingredient(english_source)
            if not arabic and not english:
                continue
            legacy_id = room_ingredient_id(ingredient, recipe_id)
            identity = normalize_label(english) or normalize_label(arabic)
            if not identity:
                identity = normalize_label(legacy_id)
            key = id_to_key.get(legacy_id, ("en:" if english else "ar:") + identity)
            if key not in groups:
                groups[key] = {
                    "key": key,
                    "names": {"ar": Counter(), "en": Counter(), "fr": Counter()},
                    "aliases": {"ar": set(), "en": set(), "fr": set()},
                    "legacyIds": set(),
                    "units": set(),
                    "recipeIds": set(),
                }
            group = groups[key]
            group["legacyIds"].add(legacy_id)
            group["recipeIds"].add(recipe_id)
            if arabic:
                group["names"]["ar"][arabic] += 1
            if english:
                group["names"]["en"][english] += 1
            if ingredient.get("sourceNameFr"):
                french = str(ingredient["sourceNameFr"]).strip()
                if french:
                    group["names"]["fr"][french] += 1
            unit = normalize_recipe_unit(str(ingredient.get("unit") or ""), ingredient.get("quantity"))
            if unit and unit != "حسب الحاجة":
                group["units"].add(unit)
            id_to_key[legacy_id] = key
    output = []
    for group in groups.values():
        group["legacyIds"] = sorted(group["legacyIds"])
        group["recipeIds"] = sorted(group["recipeIds"])
        group["units"] = sorted(group["units"])
        group["names"] = {
            lang: (values.most_common(1)[0][0] if values else "")
            for lang, values in group["names"].items()
        }
        group["aliases"] = {
            lang: sorted(set(values) - ({group["names"][lang]} if group["names"][lang] else set()))
            for lang, values in group["aliases"].items()
        }
        output.append(group)
    return output


def off_aliases(record: dict[str, Any]) -> dict[str, set[str]]:
    return {
        lang: set(record["allNames"].get(lang, []))
        for lang in ("ar", "en", "fr")
    }


def make_indexes(records: list[dict[str, Any]]) -> dict[str, dict[str, set[int]]]:
    indexes: dict[str, dict[str, set[int]]] = {
        lang: defaultdict(set) for lang in ("ar", "en", "fr")
    }
    for index, record in enumerate(records):
        for lang, values in off_aliases(record).items():
            for value in values:
                key = normalize_label(value)
                if key:
                    indexes[lang][key].add(index)
    return indexes


def ingredient_name_variants(value: str) -> list[str]:
    """Return only safe, exact aliases after removing preparation qualifiers."""
    text = str(value or "").strip()
    if not text:
        return []
    variants = [text]
    if "," in text:
        parts = [part.strip() for part in text.split(",") if part.strip()]
        if len(parts) > 1:
            qualifier_text = " ".join(parts[1:])
            qualifiers = {
                "cooked", "raw", "boiled", "baked", "roasted", "steamed",
                "grilled", "fried", "skinless", "boneless", "low sodium",
                "reduced fat", "nonfat", "non-fat", "light", "plain",
                "unsweetened", "fresh", "chopped", "minced", "sliced",
                "shredded", "grated", "diced", "drained", "peeled",
                "cubed", "lean", "large", "medium", "small", "ripe",
                "green", "white", "yellow", "red", "whole", "halved",
                "quartered", "crumbled", "thinly sliced", "finely chopped",
            }
            qualifier_tokens = {
                normalize_label(token)
                for token in re.split(r"[,;/]+", qualifier_text)
                if token.strip()
            }
            if qualifier_tokens and all(
                token in qualifiers
                or any(token.startswith(qualifier + " ") for qualifier in qualifiers)
                for token in qualifier_tokens
            ):
                variants.append(parts[0])
    # Remove exact duplicate variants while preserving specificity order.
    seen: set[str] = set()
    output: list[str] = []
    for value in variants:
        key = normalize_label(value)
        if key and key not in seen:
            seen.add(key)
            output.append(value)
    return output


def match_recipe_group(
    group: dict[str, Any],
    off_records: list[dict[str, Any]],
    indexes: dict[str, dict[str, set[int]]],
) -> tuple[int | None, str, list[int]]:
    english = str(group["names"].get("en", "") or "")
    arabic = normalize_label(group["names"].get("ar", ""))
    french = normalize_label(group["names"].get("fr", ""))

    # Exact canonical/alias match first. Only then try safe exact base-name
    # variants, where the comma tail consists solely of preparation qualifiers.
    english_variants = ingredient_name_variants(english)
    for variant_index, variant in enumerate(english_variants):
        key = normalize_label(variant)
        candidates = set(indexes["en"].get(key, set())) if key else set()
        if not candidates:
            continue
        exact_primary = {
            index for index in candidates
            if key and normalize_label(off_records[index]["names"].get("en", "")) == key
        }
        if len(exact_primary) == 1:
            method = "exact_en_primary" if variant_index == 0 else "exact_en_base_variant"
            return next(iter(exact_primary)), method, sorted(candidates)
        if len(candidates) == 1:
            method = "exact_alias" if variant_index == 0 else "exact_en_base_alias"
            return next(iter(candidates)), method, sorted(candidates)
        return None, "ambiguous", sorted(candidates)

    candidates: set[int] = set()
    if arabic:
        candidates.update(indexes["ar"].get(arabic, set()))
    if french:
        candidates.update(indexes["fr"].get(french, set()))
    if len(candidates) == 1:
        return next(iter(candidates)), "exact_localized_alias", sorted(candidates)
    if len(candidates) > 1:
        exact_ar = {
            index for index in candidates
            if arabic and normalize_label(off_records[index]["names"].get("ar", "")) == arabic
        }
        if len(exact_ar) == 1:
            return next(iter(exact_ar)), "exact_ar_primary", sorted(candidates)
        return None, "ambiguous", sorted(candidates)
    return None, "unmatched", []


def foodon_matches(
    record: dict[str, Any],
    foodon: dict[str, dict[str, Any]],
    foodon_index: dict[str, set[str]],
) -> list[str]:
    hits: set[str] = set()
    for value in [
        *record["allNames"].get("en", []),
        *record["allNames"].get("fr", []),
        *record["allNames"].get("ar", []),
    ]:
        key = normalize_label(value)
        if key:
            hits.update(foodon_index.get(key, set()))
    return sorted(hits)


def match_foodon_group(
    group: dict[str, Any],
    foodon_index: dict[str, set[str]],
    foodon: dict[str, dict[str, Any]],
    foodon_ancestors: dict[str, list[str]],
) -> list[str]:
    """Find one exact FoodOn food-product class without fuzzy merging."""
    values: list[str] = []
    values.extend(ingredient_name_variants(str(group["names"].get("en") or "")))
    values.extend(
        str(group["names"].get(language) or "")
        for language in ("ar", "fr")
    )
    for language in ("ar", "en", "fr"):
        values.extend(group["aliases"].get(language, []))

    candidates: set[str] = set()
    for value in values:
        key = normalize_label(value)
        if key:
            candidates.update(foodon_index.get(key, set()))

    # FoodOn also models organisms, processes and materials. Only match a
    # named food-product class, never an organism class such as a chicken.
    return sorted(
        item_id
        for item_id in candidates
        if item_id in foodon
        and any(
            normalize_label(parent) == "food product"
            for parent in foodon_ancestors.get(item_id, [])
        )
    )


def assign_stable_product_ids(
    products: list[dict[str, Any]],
    registry: dict[str, Any],
) -> dict[str, Any]:
    """Assign persistent opaque UUIDv4 IDs, never IDs derived from display names."""
    ids: dict[str, str] = registry.setdefault("ids", {})
    redirects: dict[str, str] = registry.setdefault("redirects", {})
    key_owners: dict[str, int] = {}

    def resolve(value: str) -> str:
        seen: set[str] = set()
        while value in redirects and value not in seen:
            seen.add(value)
            value = redirects[value]
        return value

    for product_index, product in enumerate(products):
        source_keys = set(str(key) for key in product.pop("identityKeys", []) if str(key))
        source_keys.update(
            "legacy-room:" + str(legacy_id)
            for legacy_id in product.get("recipeIngredientIds", [])
            if legacy_id
        )
        if not source_keys:
            raise RuntimeError("Food item has no stable source identity key.")

        for key in source_keys:
            previous_owner = key_owners.get(key)
            if previous_owner is not None and previous_owner != product_index:
                raise RuntimeError("A source identity key was assigned to multiple food items: " + key)
            key_owners[key] = product_index

        existing_ids = sorted({resolve(ids[key]) for key in source_keys if key in ids})
        if existing_ids:
            product_id = existing_ids[0]
            for retired_id in existing_ids[1:]:
                if retired_id != product_id:
                    redirects[retired_id] = product_id
        else:
            product_id = str(uuid.uuid4())

        for key in source_keys:
            ids[key] = product_id
        product["id"] = product_id

    for old_id in list(redirects):
        redirects[old_id] = resolve(redirects[old_id])

    assigned = [str(product["id"]) for product in products]
    if len(assigned) != len(set(assigned)):
        raise RuntimeError("Persistent UUID registry assigned duplicate food-item IDs.")

    registry["version"] = 1
    registry["idFormat"] = "UUIDv4"
    return registry


def choose_names(
    record: dict[str, Any] | None,
    groups: list[dict[str, Any]],
    foodon_ids: list[str],
    foodon: dict[str, dict[str, Any]],
) -> dict[str, str | None]:
    names: dict[str, str | None] = {}
    for lang in ("ar", "en", "fr"):
        values: Counter[str] = Counter()
        taxonomy_name = record["names"].get(lang) if record else None
        if taxonomy_name:
            values[str(taxonomy_name)] += 1000
        for foodon_id in foodon_ids:
            foodon_name = (foodon.get(foodon_id, {}).get("names") or {}).get(lang)
            if foodon_name:
                values[str(foodon_name)] += 500
        for group in groups:
            value = group["names"].get(lang)
            if value:
                values[str(value)] += 1
        names[lang] = values.most_common(1)[0][0] if values else None
    return names


def build_product(
    record: dict[str, Any] | None,
    groups: list[dict[str, Any]],
    foodon_ids: list[str],
    foodon: dict[str, dict[str, Any]],
    foodon_ancestors: dict[str, list[str]],
) -> dict[str, Any]:
    all_labels: list[str] = []
    if record:
        for lang in ("en", "fr", "ar"):
            all_labels.extend(record["allNames"].get(lang, []))
        all_labels.extend(record.get("parents", []))
    for group in groups:
        all_labels.extend(group["names"].values())
        for lang in ("ar", "en", "fr"):
            all_labels.extend(group["aliases"].get(lang, []))

    foodon_labels: list[str] = []
    for item_id in foodon_ids:
        item = foodon.get(item_id)
        if item:
            foodon_labels.append(item["name"])
            foodon_labels.extend(item.get("aliases", []))
        foodon_labels.extend(foodon_ancestors.get(item_id, []))
    category_slug, subcategory_slug = category_for([*all_labels, *foodon_labels])
    category_id, subcategory_id = canonical_category_ids(category_slug, subcategory_slug)
    source_properties = record.get("sourceProperties", {}) if record else {}
    category_id = source_properties.get("midatiCategoryId") or category_id
    subcategory_id = source_properties.get("midatiSubcategoryId") or subcategory_id

    legacy_ids = sorted({
        legacy_id
        for group in groups
        for legacy_id in group["legacyIds"]
    })
    identity_keys: set[str] = set()
    if record:
        source_properties = record.get("sourceProperties", {})
        curated_key = source_properties.get("midatiCuratedKey")
        open_food_facts_term = source_properties.get("openFoodFactsTerm")
        if not curated_key:
            open_food_facts_term = open_food_facts_term or record["name"]
        if open_food_facts_term:
            identity_keys.add("openfoodfacts:" + normalize_label(open_food_facts_term))
        if curated_key:
            identity_keys.add("midati-curated:" + str(curated_key))
    # FoodOn IDs are ontology cross-references, not necessarily unique:
    # one broad FoodOn class can legitimately relate to several OFF terms.
    # Use a FoodOn identity key only for a FoodOn-only product with no OFF record.
    if not record:
        for foodon_id in foodon_ids:
            identity_keys.add("foodon:" + str(foodon_id))
    for group in groups:
        group_name = normalize_label(group["names"].get("en") or group["names"].get("ar"))
        if group_name:
            identity_keys.add("recipe-term:" + group_name)
        identity_keys.update("legacy-room:" + legacy_id for legacy_id in group["legacyIds"])

    names = choose_names(record, groups, foodon_ids, foodon)
    aliases: dict[str, set[str]] = {"ar": set(), "en": set(), "fr": set()}
    if record:
        for lang in aliases:
            aliases[lang].update(record["allNames"].get(lang, [])[1:])
    for group in groups:
        for lang in aliases:
            name = group["names"].get(lang)
            if name and name != names[lang]:
                aliases[lang].add(name)
            aliases[lang].update(group["aliases"].get(lang, []))
    # FoodOn labels and synonyms enrich each language without overriding a
    # preferred OFF label or producing a second identity.
    for item_id in foodon_ids:
        foodon_item = foodon.get(item_id)
        if foodon_item:
            for language in ("ar", "en", "fr"):
                translated = (foodon_item.get("names") or {}).get(language)
                if translated:
                    aliases[language].add(translated)
                aliases[language].update(
                    (foodon_item.get("aliasesByLanguage") or {}).get(language, [])
                )

    observed_units = sorted({
        unit for group in groups for unit in group["units"]
    })
    # Always retain market-scale units (g/kg or ml/l) alongside units used by
    # recipes, which may only mention cups, spoons or pieces.
    standard_units = default_units(category_id, subcategory_id, [
        str(value or "") for value in names.values()
    ])
    curated_units = source_properties.get("midatiAcceptedUnits", []) if record else []
    accepted_units = list(dict.fromkeys([*standard_units, *observed_units, *curated_units]))
    source_mappings: dict[str, Any] = {}
    if record:
        open_food_facts_term = source_properties.get("openFoodFactsTerm")
        if not source_properties.get("midatiCuratedKey"):
            open_food_facts_term = open_food_facts_term or record["name"]
        if open_food_facts_term:
            source_mappings["openFoodFactsIngredientTerm"] = open_food_facts_term
        if source_properties.get("midatiCuratedKey"):
            source_mappings["midatiCuratedKey"] = source_properties["midatiCuratedKey"]
        for property_name in ("usdaFdcCode", "ciqualFoodCode"):
            value = record.get("sourceProperties", {}).get(property_name)
            if value:
                source_mappings[property_name] = value
    if foodon_ids:
        source_mappings["foodOnIds"] = foodon_ids

    return {
        "id": None,
        "identityKeys": sorted(identity_keys),
        "names": names,
        "acceptedUnits": accepted_units,
        "imageUrl": None,
        "categoryId": category_id,
        "subcategoryId": subcategory_id,
        "aliases": {
            lang: sorted(value for value in values if value and value != names[lang])
            for lang, values in aliases.items()
        },
        # Reserved for a later market-product/package variant, not a food identity.
        "quantity": None,
        "countryOfOrigin": (
            record.get("sourceProperties", {}).get("countryOfOrigin")
            if record and record.get("sourceProperties", {}).get("countryOfOrigin")
            else None
        ),
        "nutrition": None,
        "sourceMappings": source_mappings,
        "recipeIngredientIds": legacy_ids,
    }



def merge_curated_food_items(
    off_records: list[dict[str, Any]],
    curated_items: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """Merge a reviewed local food name into one exact existing item or create it."""
    records = list(off_records)
    for curated in curated_items:
        key = str(curated.get("key") or "").strip()
        names = curated.get("names") or {}
        aliases = curated.get("aliases") or {}
        if not key or not str(names.get("en") or "").strip():
            raise RuntimeError("Curated food item requires a stable key and English name.")
        supplied = {
            lang: [
                value.strip()
                for value in [names.get(lang), *(aliases.get(lang) or [])]
                if isinstance(value, str) and value.strip()
            ]
            for lang in ("ar", "en", "fr")
        }
        primary_terms = {
            normalize_label(value)
            for value in names.values()
            if isinstance(value, str) and normalize_label(value)
        }
        alias_terms = {
            normalize_label(value)
            for values in aliases.values()
            for value in values
            if isinstance(value, str) and normalize_label(value)
        }
        # Curated concepts may intentionally split a broad/incorrect external
        # synonym (e.g. Algerian lben/raib listed under generic milk). Match only
        # preferred labels, never aliases, so a culturally reviewed item can
        # become a separate identity without inheriting an ambiguous synonym.
        primary_matches = []
        for index, record in enumerate(records):
            record_terms = {
                normalize_label(value)
                for value in (record.get("names") or {}).values()
                if isinstance(value, str) and normalize_label(value)
            }
            if primary_terms.intersection(record_terms):
                primary_matches.append(index)
        candidates = primary_matches
        if len(candidates) > 1:
            raise RuntimeError(
                f"Curated item {key!r} matches multiple existing concepts; refusing ambiguous merge."
            )
        if candidates:
            record = records[candidates[0]]
            record.setdefault("sourceProperties", {}).setdefault(
                "openFoodFactsTerm", record.get("name", "")
            )
        else:
            record = {
                "name": str(names["en"]).strip(),
                "names": {},
                "aliases": {},
                "allNames": {},
                "parents": [],
                "sourceProperties": {},
            }
            records.append(record)

        for lang, values in supplied.items():
            current = list(record.setdefault("allNames", {}).get(lang, []))
            for value in values:
                if value not in current:
                    current.append(value)
            record["allNames"][lang] = current
            if current and not record.setdefault("names", {}).get(lang):
                record["names"][lang] = current[0]
            record.setdefault("aliases", {})[lang] = [
                value for value in current if value != record["names"].get(lang)
            ]
        source_properties = record.setdefault("sourceProperties", {})
        source_properties["midatiCuratedKey"] = key
        source_properties["midatiCategoryId"] = curated.get("categoryId")
        source_properties["midatiSubcategoryId"] = curated.get("subcategoryId")
        source_properties["midatiAcceptedUnits"] = list(curated.get("acceptedUnits") or [])
    return records


def build_catalogue(
    recipe_data: dict[str, Any],
    off_records: list[dict[str, Any]],
    foodon: dict[str, dict[str, Any]],
    identity_registry: dict[str, Any] | None = None,
    curated_items: list[dict[str, Any]] | None = None,
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any], dict[str, Any]]:
    identity_registry = identity_registry or {"version": 1, "ids": {}, "redirects": {}}
    recipes = recipe_data.get("recipes") or []
    groups = collect_recipe_groups(recipes)
    off_records = merge_curated_food_items(off_records, curated_items or [])
    foodon_index, foodon_ancestors = build_foodon_indexes(foodon)

    # Keep only genuine food-like taxonomy terms: known grocery classes, explicit
    # FoodOn food concepts, or exact terms already used by Midati recipes. This
    # excludes taxonomy entries such as processing operations and non-food chemicals.
    recipe_terms = {
        normalize_label(value)
        for group in groups
        for value in group["names"].values()
        if value
    }
    filtered_off: list[dict[str, Any]] = []
    for record in off_records:
        labels = [
            *record["allNames"].get("en", []),
            *record["allNames"].get("fr", []),
            *record["allNames"].get("ar", []),
            *record.get("parents", []),
        ]
        is_recipe_term = any(normalize_label(value) in recipe_terms for value in labels if value)
        foodon_ids = foodon_matches(record, foodon, foodon_index)
        category_id, _ = category_for(labels)
        if category_id != "other" or foodon_ids or is_recipe_term:
            filtered_off.append(record)
    off_records = filtered_off
    off_index = make_indexes(off_records)

    off_to_groups: dict[int, list[dict[str, Any]]] = defaultdict(list)
    foodon_to_off_indices: dict[str, set[int]] = defaultdict(set)
    for off_index_value, off_record in enumerate(off_records):
        for foodon_id in foodon_matches(off_record, foodon, foodon_index):
            foodon_to_off_indices[foodon_id].add(off_index_value)

    foodon_only_groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    matching_methods: Counter[str] = Counter()
    ambiguous: list[dict[str, Any]] = []
    unmatched: list[dict[str, Any]] = []

    for group in groups:
        index, method, candidates = match_recipe_group(group, off_records, off_index)
        if index is not None:
            matching_methods[method] += 1
            off_to_groups[index].append(group)
            continue

        report = {
            "nameAr": group["names"].get("ar"),
            "nameEn": group["names"].get("en"),
            "legacyIds": group["legacyIds"],
            "units": group["units"],
        }
        foodon_candidates = match_foodon_group(
            group, foodon_index, foodon, foodon_ancestors
        )
        if len(foodon_candidates) == 1:
            foodon_id = foodon_candidates[0]
            matching_off_indices = foodon_to_off_indices.get(foodon_id, set())
            if len(matching_off_indices) == 1:
                matching_methods["foodon_exact_to_off"] += 1
                off_to_groups[next(iter(matching_off_indices))].append(group)
                continue
            if len(matching_off_indices) == 0:
                matching_methods["foodon_exact"] += 1
                foodon_only_groups[foodon_id].append(group)
                continue
            matching_methods["foodon_ambiguous_off_link"] += 1
            report["foodOnId"] = foodon_id
            report["candidateOFFNames"] = sorted(
                off_records[index]["names"].get("en") or ""
                for index in matching_off_indices
            )
            ambiguous.append(report)
            continue

        if len(foodon_candidates) > 1:
            matching_methods["foodon_ambiguous"] += 1
            report["candidateFoodOnIds"] = foodon_candidates[:20]
            ambiguous.append(report)
            continue

        matching_methods[method] += 1
        if method == "ambiguous":
            report["candidateNames"] = [
                off_records[candidate]["names"].get("en") for candidate in candidates
            ]
            ambiguous.append(report)
        else:
            unmatched.append(report)

    products: list[dict[str, Any]] = []
    consumed_groups: set[str] = set()
    for index, record in enumerate(off_records):
        groups_for_record = off_to_groups.get(index, [])
        foodon_ids = foodon_matches(record, foodon, foodon_index)
        product = build_product(record, groups_for_record, foodon_ids, foodon, foodon_ancestors)
        products.append(product)
        for group in groups_for_record:
            consumed_groups.add(group["key"])

    # FoodOn-only matches are grouped by exact ontology class, so alternate
    # recipe spellings of the same verified concept share one food identity.
    for foodon_id, related_groups in sorted(foodon_only_groups.items()):
        product = build_product(None, related_groups, [foodon_id], foodon, foodon_ancestors)
        products.append(product)
        for group in related_groups:
            consumed_groups.add(group["key"])

    # Every recipe ingredient must resolve to a product, even when neither external
    # dataset contains its exact name. These are reported for future curation.
    recipe_only_groups = [group for group in groups if group["key"] not in consumed_groups]
    recipe_only_by_key: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for group in recipe_only_groups:
        identity = normalize_label(group["names"].get("en") or group["names"].get("ar"))
        recipe_only_by_key[identity].append(group)

    for identity, related_groups in recipe_only_by_key.items():
        product = build_product(None, related_groups, [], foodon, foodon_ancestors)
        products.append(product)

    # Merge exact normalized English duplicates before assigning UUIDs.
    by_english: dict[str, dict[str, Any]] = {}
    unique_products: list[dict[str, Any]] = []
    category_conflicts: list[dict[str, str]] = []
    for product in products:
        english_key = normalize_label(str(product["names"].get("en") or ""))
        existing = by_english.get(english_key) if english_key else None
        if existing is None:
            unique_products.append(product)
            if english_key:
                by_english[english_key] = product
            continue

        if (
            existing["categoryId"] != product["categoryId"]
            or existing.get("subcategoryId") != product.get("subcategoryId")
        ):
            category_conflicts.append({
                "englishName": english_key,
                "existingCategoryId": existing["categoryId"],
                "incomingCategoryId": product["categoryId"],
            })
            existing["categoryId"] = "foodcat-other"
            existing["subcategoryId"] = None

        for lang in ("ar", "en", "fr"):
            if product["names"].get(lang) and not existing["names"].get(lang):
                existing["names"][lang] = product["names"][lang]
            existing["aliases"][lang] = sorted(
                set(existing["aliases"][lang]) | set(product["aliases"][lang])
            )
        existing["recipeIngredientIds"] = sorted(
            set(existing["recipeIngredientIds"]) | set(product["recipeIngredientIds"])
        )
        existing["acceptedUnits"] = sorted(
            set(existing["acceptedUnits"]) | set(product["acceptedUnits"])
        )
        existing["identityKeys"] = sorted(
            set(existing["identityKeys"]) | set(product["identityKeys"])
        )
        existing["sourceMappings"].update(product["sourceMappings"])
        existing["imageUrl"] = existing["imageUrl"] or product["imageUrl"]
        existing["countryOfOrigin"] = existing["countryOfOrigin"] or product["countryOfOrigin"]
        existing["nutrition"] = existing["nutrition"] or product["nutrition"]

    products = sorted(unique_products, key=lambda item: (
        normalize_label(str(item["names"].get("en") or "")),
        min(item["identityKeys"]) if item["identityKeys"] else "",
    ))

    identity_registry = assign_stable_product_ids(products, identity_registry)

    matched_ids: dict[str, str] = {}
    for product in products:
        for legacy_id in product["recipeIngredientIds"]:
            existing_id = matched_ids.get(legacy_id)
            if existing_id and existing_id != product["id"]:
                raise RuntimeError(
                    f"Legacy Room ingredient ID {legacy_id} resolves to multiple food items."
                )
            matched_ids[legacy_id] = product["id"]

    all_recipe_ids = {
        room_ingredient_id(ingredient, str(recipe.get("id") or ""))
        for recipe in recipes
        for ingredient in (recipe.get("ingredients") or [])
        if isinstance(ingredient, dict) and (
            ingredient.get("name")
            or ingredient.get("sourceNameEn")
            or ingredient.get("sourceTextEn")
            or ingredient.get("sourceTextEs")
        )
    }
    unmapped_ids = sorted(all_recipe_ids - set(matched_ids))
    if unmapped_ids:
        raise RuntimeError(
            f"Recipe ingredient mappings incomplete: {len(unmapped_ids)} IDs have no product."
        )

    recipe_rows: list[dict[str, Any]] = []
    for recipe in recipes:
        recipe_id = str(recipe.get("id") or "")
        for ingredient_index, ingredient in enumerate(recipe.get("ingredients") or []):
            if not isinstance(ingredient, dict) or not (
                ingredient.get("name")
                or ingredient.get("sourceNameEn")
                or ingredient.get("sourceTextEn")
                or ingredient.get("sourceTextEs")
            ):
                continue
            legacy_id = room_ingredient_id(ingredient, recipe_id)
            food_item_id = matched_ids.get(legacy_id)
            if not food_item_id:
                raise RuntimeError(
                    f"Missing canonical UUID for recipe ingredient {legacy_id}."
                )
            recipe_rows.append({
                "recipeId": recipe_id,
                "ingredientIndex": ingredient_index,
                "legacyRoomIngredientId": legacy_id,
                "foodItemId": food_item_id,
            })

    id_counts = Counter(str(product["id"]) for product in products)
    en_counts = Counter(
        normalize_label(str(product["names"].get("en") or ""))
        for product in products
        if product["names"].get("en")
    )
    duplicate_ids = sorted(key for key, count in id_counts.items() if count > 1)
    duplicate_english = sorted(key for key, count in en_counts.items() if count > 1)
    if duplicate_ids or duplicate_english:
        raise RuntimeError(
            f"Duplicate guard failed: {len(duplicate_ids)} IDs, {len(duplicate_english)} English names."
        )

    catalogue = {
        "schemaVersion": 2,
        "catalogueType": "generic-food-items",
        "idFormat": "UUIDv4",
        "idStrategy": "persistent UUIDv4 registry; independent of localized/display names",
        "sourceRevisions": {
            "openFoodFacts": {
                "repository": OFF_REPOSITORY,
                "commit": OFF_COMMIT,
                "file": "taxonomies/food/ingredients.txt",
                "gitBlobSha": OFF_GIT_BLOB_SHA,
                "license": "Open Database License 1.0; individual contents Database Contents License 1.0",
            },
            "foodOn": {
                "repository": FOODON_REPOSITORY,
                "commit": FOODON_COMMIT,
                "file": "foodon-base.owl",
                "gitBlobSha": FOODON_GIT_BLOB_SHA,
                "license": "CC BY 4.0",
            },
            "midatiRecipes": {
                "file": "data/recipes/ar/recipes.json",
                "revision": str(recipe_data.get("revision") or "current-main-commit"),
            },
        },
        "categoryCatalogue": "data/products/categories.json",
        "categorySchemaVersion": CATEGORY_CATALOGUE.get("schemaVersion", 1),
        "products": products,
    }
    mapping = {
        "schemaVersion": 2,
        "description": "Maps existing Room recipe ingredient IDs and recipe rows to canonical UUIDv4 food-item IDs.",
        "legacyRoomIngredientIdToFoodItemId": dict(sorted(matched_ids.items())),
        "recipeIngredientToProductId": dict(sorted(matched_ids.items())),
        "recipeRows": recipe_rows,
    }
    missing_ar = sum(1 for p in products if not p["names"].get("ar"))
    missing_en = sum(1 for p in products if not p["names"].get("en"))
    missing_fr = sum(1 for p in products if not p["names"].get("fr"))
    valid_category_ids = {item.get("id") for item in CATEGORY_DATA}
    valid_subcategory_ids = {
        child.get("id")
        for item in CATEGORY_DATA
        for child in item.get("subcategories", [])
    }
    invalid_categories = [
        {"id": p["id"], "categoryId": p["categoryId"], "subcategoryId": p["subcategoryId"]}
        for p in products
        if p["categoryId"] not in valid_category_ids
        or (p["subcategoryId"] is not None and p["subcategoryId"] not in valid_subcategory_ids)
    ]
    if invalid_categories:
        raise RuntimeError("Product references invalid Midati category IDs: " + json.dumps(invalid_categories[:10]))
    missing_category = sum(1 for p in products if p["categoryId"] == "foodcat-other")
    report = {
        "schemaVersion": 1,
        "sourceCounts": {
            "offTaxonomyRecords": len(off_records),
            "foodOnClasses": len(foodon),
            "recipeCount": len(recipes),
            "uniqueRecipeIngredientGroups": len(groups),
            "uniqueRecipeIngredientIds": len(all_recipe_ids),
        },
        "catalogueCounts": {
            "products": len(products),
            "mappedRecipeIngredientIds": len(matched_ids),
            "uniqueRecipeIngredientsMapped": len(groups),
            "offMatchedGroups": sum(
                count for method, count in matching_methods.items()
                if method.startswith("exact_")
            ),
            "unmatchedRecipeGroups": len(unmatched),
            "ambiguousRecipeGroups": len(ambiguous),
            "foodOnMatchedGroups": matching_methods["foodon_exact"] + matching_methods["foodon_exact_to_off"],
            "foodOnAmbiguousGroups": matching_methods["foodon_ambiguous"] + matching_methods["foodon_ambiguous_off_link"],
            "offMatchCoveragePercent": round(
            100.0 * sum(
                count for method, count in matching_methods.items()
                if method.startswith("exact_")
            ) / max(len(groups), 1),
            2,
        ),
            "missingArabicNames": missing_ar,
            "missingEnglishNames": missing_en,
            "missingFrenchNames": missing_fr,
            "fallbackOtherCategory": missing_category,
            "missingImages": sum(1 for p in products if not p["imageUrl"]),
            "missingNutrition": sum(1 for p in products if not p["nutrition"]),
            "duplicateIds": 0,
            "duplicateEnglishNames": 0,
            "uuidv4Ids": sum(
                1 for p in products
                if re.fullmatch(
                    r"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
                    str(p["id"]),
                )
            ),
            "recipeRowsMapped": len(recipe_rows),
            "legacyIdConflicts": 0,
        },
        "recipeMatchingMethods": dict(sorted(matching_methods.items())),
        "unmatchedRecipeIngredients": unmatched[:500],
        "ambiguousRecipeIngredients": ambiguous[:500],
        "categoryConflicts": category_conflicts[:500],
        "dataAvailabilityNotes": [
            "The OFF ingredient taxonomy supplies multilingual labels, aliases and taxonomy relations, not product photographs or complete nutrient panels.",
            "FoodOn supplies food classes, labels, synonyms and semantic hierarchy; it is not a complete image or nutrition database.",
            "imageUrl, quantity, countryOfOrigin and nutrition remain null unless a later source provides item-specific evidence.",
            "Quantity and barcode belong to a future market-product variant, not to this generic food-item identity.",
        ],
    }
    return catalogue, mapping, report, identity_registry


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recipes", type=Path, default=ROOT / "data/recipes/ar/recipes.json")
    parser.add_argument("--off-taxonomy", type=Path)
    parser.add_argument("--foodon-owl", type=Path)
    parser.add_argument("--download-dir", type=Path, default=Path(os.environ.get("MIDATI_DATA_CACHE", ".cache/product-catalogue")))
    parser.add_argument("--output", type=Path, default=ROOT / "data/products/catalogue.json")
    parser.add_argument("--mapping-output", type=Path, default=ROOT / "data/products/recipe-ingredient-map.json")
    parser.add_argument("--audit-output", type=Path, default=ROOT / "data/products/build-audit.json")
    parser.add_argument("--identity-registry", type=Path, default=ROOT / "data/products/identity_registry.json")
    args = parser.parse_args()

    if not args.recipes.is_file():
        parser.error(f"Recipe catalogue not found: {args.recipes}")
    try:
        recipe_data = json.loads(args.recipes.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        parser.error(f"Could not read recipe catalogue: {error}")
    if recipe_data.get("complete") is not True or recipe_data.get("translatedCount") != recipe_data.get("expectedCount"):
        parser.error("Refusing to build from an incomplete recipe catalogue.")
    recipe_ids = [str(item.get("id") or "") for item in recipe_data.get("recipes") or []]
    if not recipe_ids or len(recipe_ids) != len(set(recipe_ids)):
        parser.error("Recipe catalogue is empty or has duplicate recipe IDs.")

    try:
        off_path = args.off_taxonomy or download_pinned(
            OFF_URL, args.download_dir / "off-ingredients.txt", OFF_GIT_BLOB_SHA
        )
        foodon_path = args.foodon_owl or download_pinned(
            FOODON_URL, args.download_dir / "foodon-base.owl", FOODON_GIT_BLOB_SHA
        )
        curated_items = (
            json.loads(CURATED_ITEMS_PATH.read_text(encoding="utf-8"))
            if CURATED_ITEMS_PATH.is_file() else []
        )
        off_records = parse_off_taxonomy(off_path)
        foodon = parse_foodon_owl(foodon_path)
        if len(off_records) < 1000:
            raise RuntimeError(f"Unexpectedly few OFF taxonomy entries parsed: {len(off_records)}")
        if len(foodon) < 1000:
            raise RuntimeError(f"Unexpectedly few FoodOn classes parsed: {len(foodon)}")
        if args.identity_registry.is_file():
            identity_registry = json.loads(args.identity_registry.read_text(encoding="utf-8"))
        else:
            identity_registry = {"version": 1, "idFormat": "UUIDv4", "ids": {}, "redirects": {}}
        catalogue, mapping, audit, identity_registry = build_catalogue(
            recipe_data, off_records, foodon, identity_registry, curated_items
        )
    except Exception as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1

    for path, value in (
        (args.output, catalogue),
        (args.mapping_output, mapping),
        (args.audit_output, audit),
        (args.identity_registry, identity_registry),
    ):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n",
            encoding="utf-8",
        )

    counts = audit["catalogueCounts"]
    print(
        "Built food catalogue: "
        f"{counts['products']} unique items; "
        f"{counts['mappedRecipeIngredientIds']} recipe ingredient IDs mapped; "
        f"{counts['offMatchedGroups']} exact OFF matches; "
        f"{counts['unmatchedRecipeGroups']} recipe-only items; "
        f"{counts['ambiguousRecipeGroups']} ambiguous matches; "
        f"{counts['recipeRowsMapped']} recipe rows linked; "
        f"{counts['uuidv4Ids']} UUIDv4 IDs; "
        "0 duplicate IDs/names."
    )
    print(f"Catalogue: {args.output}")
    print(f"Recipe mapping: {args.mapping_output}")
    print(f"Audit: {args.audit_output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
