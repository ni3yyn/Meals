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

CATEGORY_ROWS = [
    ("fruits", None, "الفواكه", "Fruits", "Fruits"),
    ("vegetables", None, "الخضروات", "Vegetables", "Légumes"),
    ("dairy", None, "الألبان ومنتجاتها", "Dairy", "Produits laitiers"),
    ("meat_offal", None, "اللحوم والأحشاء", "Meat & offal", "Viandes et abats"),
    ("seafood", None, "المأكولات البحرية", "Seafood", "Produits de la mer"),
    ("legumes", None, "البقوليات", "Legumes", "Légumineuses"),
    ("canned_preserved", None, "المعلبات والمحفوظات", "Canned & preserved foods", "Conserves et aliments préservés"),
    ("grains_cereals", None, "الحبوب", "Grains & cereals", "Céréales"),
    ("bakery_pasta", None, "الخبز والمعجنات والعجائن", "Bakery & pasta", "Boulangerie et pâtes"),
    ("nuts_seeds", None, "المكسرات والبذور", "Nuts & seeds", "Noix et graines"),
    ("oils_fats", None, "الزيوت والدهون", "Oils & fats", "Huiles et matières grasses"),
    ("spices", None, "التوابل والبهارات", "Spices", "Épices"),
    ("herbs", None, "الأعشاب العطرية", "Herbs", "Herbes aromatiques"),
    ("sauces_condiments", None, "الصلصات والمنكهات", "Sauces & condiments", "Sauces et condiments"),
    ("sweeteners", None, "المحليات", "Sweeteners", "Édulcorants"),
    ("eggs", None, "البيض", "Eggs", "Œufs"),
    ("mushrooms", None, "الفطريات", "Mushrooms & fungi", "Champignons et fungi"),
    ("frozen", None, "الأطعمة المجمدة", "Frozen foods", "Aliments surgelés"),
    ("baking", None, "مكونات الخَبز والحلويات", "Baking & dessert ingredients", "Ingrédients de pâtisserie"),
    ("beverages", None, "المشروبات", "Beverages", "Boissons"),
    ("other", None, "مواد غذائية أخرى", "Other food items", "Autres aliments"),
    # Stable subcategory IDs; parentId points to one of the categories above.
    ("citrus", "fruits", "الحمضيات", "Citrus fruits", "Agrumes"),
    ("berries", "fruits", "التوتيات", "Berries", "Baies"),
    ("tropical_fruits", "fruits", "الفواكه الاستوائية", "Tropical fruits", "Fruits tropicaux"),
    ("stone_fruits", "fruits", "الفواكه ذات النواة", "Stone fruits", "Fruits à noyau"),
    ("dried_fruits", "fruits", "الفواكه المجففة", "Dried fruits", "Fruits séchés"),
    ("leafy_vegetables", "vegetables", "الخضروات الورقية", "Leafy vegetables", "Légumes feuilles"),
    ("root_tubers", "vegetables", "الجذور والدرنيات", "Roots & tubers", "Racines et tubercules"),
    ("bulb_vegetables", "vegetables", "الخضروات البصلية", "Bulb vegetables", "Légumes bulbes"),
    ("fruit_vegetables", "vegetables", "الخضروات الثمرية", "Fruit vegetables", "Légumes-fruits"),
    ("milk", "dairy", "الحليب", "Milk", "Lait"),
    ("fermented_dairy", "dairy", "الألبان المخمرة", "Fermented dairy", "Laits fermentés"),
    ("yogurt", "dairy", "الياغورت", "Yogurt", "Yaourts"),
    ("cream_butter", "dairy", "القشدة والزبدة", "Cream & butter", "Crème et beurre"),
    ("cheese", "dairy", "الأجبان", "Cheese", "Fromages"),
    ("red_meat", "meat_offal", "اللحوم الحمراء", "Red meat", "Viandes rouges"),
    ("poultry", "meat_offal", "الدواجن", "Poultry", "Volailles"),
    ("offal", "meat_offal", "الأحشاء", "Offal", "Abats"),
    ("processed_meat", "meat_offal", "اللحوم المصنعة", "Processed meat", "Viandes transformées"),
    ("fish", "seafood", "الأسماك", "Fish", "Poissons"),
    ("shellfish", "seafood", "القشريات والرخويات", "Shellfish & molluscs", "Crustacés et mollusques"),
    ("chickpeas", "legumes", "الحمص", "Chickpeas", "Pois chiches"),
    ("lentils", "legumes", "العدس", "Lentils", "Lentilles"),
    ("beans", "legumes", "الفاصولياء", "Beans", "Haricots"),
    ("peas", "legumes", "البازلاء", "Peas", "Pois"),
    ("canned_legumes", "canned_preserved", "البقوليات المعلبة", "Canned legumes", "Légumineuses en conserve"),
    ("canned_vegetables", "canned_preserved", "الخضروات المعلبة", "Canned vegetables", "Légumes en conserve"),
    ("canned_seafood", "canned_preserved", "المأكولات البحرية المعلبة", "Canned seafood", "Produits de la mer en conserve"),
    ("pickled_preserved", "canned_preserved", "المخللات والمحفوظه", "Pickles & preserved foods", "Marinades et aliments conservés"),
    ("rice", "grains_cereals", "الأرز", "Rice", "Riz"),
    ("wheat_semolina", "grains_cereals", "القمح والسميد", "Wheat & semolina", "Blé et semoule"),
    ("flour", "grains_cereals", "الدقيق والفرينة", "Flour", "Farines"),
    ("oats_barley", "grains_cereals", "الشوفان والشعير", "Oats & barley", "Avoine et orge"),
    ("bread", "bakery_pasta", "الخبز", "Bread", "Pain"),
    ("pasta", "bakery_pasta", "المعكرونة", "Pasta", "Pâtes"),
    ("couscous", "bakery_pasta", "الكسكس", "Couscous", "Couscous"),
    ("fresh_herbs", "herbs", "الأعشاب الطازجة", "Fresh herbs", "Herbes fraîches"),
    ("dried_herbs", "herbs", "الأعشاب المجففة", "Dried herbs", "Herbes séchées"),
    ("whole_spices", "spices", "التوابل الكاملة", "Whole spices", "Épices entières"),
    ("ground_spices", "spices", "التوابل المطحونة", "Ground spices", "Épices moulues"),
    ("spice_blends", "spices", "خلطات التوابل", "Spice blends", "Mélanges d’épices"),
    ("cooking_oils", "oils_fats", "زيوت الطهي", "Cooking oils", "Huiles de cuisson"),
    ("sauces", "sauces_condiments", "الصلصات", "Sauces", "Sauces"),
    ("condiments", "sauces_condiments", "المنكهات", "Condiments", "Condiments"),
    ("sugar_syrups", "sweeteners", "السكر والشراب", "Sugar & syrups", "Sucres et sirops"),
]

CATEGORY_DATA = [
    {"id": row[0], "parentId": row[1], "names": {"ar": row[2], "en": row[3], "fr": row[4]}}
    for row in CATEGORY_ROWS
]
CATEGORY_IDS = {row[0] for row in CATEGORY_ROWS}


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
            record = {
                "name": english[0],
                "names": {
                    lang: values[0]
                    for lang, values in fields.items()
                    if lang in {"ar", "en", "fr"} and values
                },
                "aliases": {
                    lang: list(values[1:])
                    for lang, values in fields.items()
                    if lang in {"ar", "en", "fr"} and len(values) > 1
                },
                "allNames": {
                    lang: list(values)
                    for lang, values in fields.items()
                    if lang in {"ar", "en", "fr"} and values
                },
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
            property_match = re.match(r"^(ecobalyse_default_origin|usda_fdc_code|ciqual_food_code):en:\s*(.*)$", line)
            if property_match and property_match.group(2).strip():
                fields[property_match.group(1)].append(property_match.group(2).strip())
    flush()
    return records


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def parse_foodon_owl(path: Path) -> dict[str, dict[str, Any]]:
    """Extract FoodOn classes, labels, synonyms and named subclass links."""
    records: dict[str, dict[str, Any]] = {}
    for _, element in ET.iterparse(path, events=("end",)):
        if element.tag != OWL_CLASS:
            continue
        iri = element.attrib.get(RDF + "about", "")
        match = re.search(r"(FOODON_[0-9]+)", iri)
        if match:
            label = ""
            synonyms: list[str] = []
            parents: list[str] = []
            for child in list(element):
                local = _local_name(child.tag)
                value = (child.text or "").strip()
                if local == "label" and value and not label:
                    label = value
                elif "synonym" in local.lower() and value:
                    synonyms.append(value)
                elif local == "subClassOf":
                    parent_iri = child.attrib.get(RDF + "resource", "")
                    parent_match = re.search(r"(FOODON_[0-9]+)", parent_iri)
                    if parent_match:
                        parents.append(parent_match.group(1))
            if label:
                records[match.group(1)] = {
                    "id": match.group(1),
                    "name": label,
                    "aliases": list(dict.fromkeys(synonyms)),
                    "parents": list(dict.fromkeys(parents)),
                }
        element.clear()
    return records


def build_foodon_indexes(foodon: dict[str, dict[str, Any]]) -> tuple[dict[str, set[str]], dict[str, list[str]]]:
    label_index: dict[str, set[str]] = defaultdict(set)
    for item_id, item in foodon.items():
        for name in [item["name"], *item["aliases"]]:
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

    return label_index, ancestor_cache


def category_for(names: Iterable[str]) -> tuple[str, str | None]:
    text = " ".join(str(value).casefold() for value in names if value)
    compact = normalize_label(text)

    def has(*terms: str) -> bool:
        return any(normalize_label(term) in compact for term in terms)

    canned = has("canned", "tinned", "in conserve", "en conserve", "appertized", "preserved", "conserva", "conserve")
    seafood = has("fish", "tuna", "sardine", "anchovy", "salmon", "shrimp", "prawn", "seafood", "shellfish", "mollusc", "mussel", "oyster", "squid", "octopus", "crab", "lobster", "سمك", "تونة", "سردين", "جمبري")
    legumes = has("chickpea", "garbanzo", "lentil", "pulse", "legume", "dried bean", "kidney bean", "white bean", "haricot", "حمص", "عدس", "فاصولياء", "لوبيا")
    vegetables = has("vegetable", "tomato", "potato", "onion", "carrot", "pepper", "courgette", "zucchini", "cabbage", "spinach", "lettuce", "eggplant", "aubergine", "green bean", "pea pod", "خضروات", "بطاطا", "طماطم", "بصل", "جزر")
    fruit = has("fruit", "apple", "banana", "orange", "lemon", "date", "grape", "berry", "mango", "pear", "peach", "apricot", "تمر", "تفاح", "موز", "برتقال")
    dairy = has("milk", "lait", "yogurt", "yoghurt", "raib", "leben", "kefir", "buttermilk", "fermented milk", "cream", "cheese", "fromage", "butter", "beurre", "حليب", "لبن", "رايب", "جبن")
    meat = has("meat", "beef", "lamb", "mutton", "veal", "chicken", "turkey", "duck", "pork", "offal", "liver", "kidney", "heart", "tripe", "tongue", "لحوم", "دجاج", "كبد", "قلب")
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

    if canned:
        if seafood:
            return "canned_preserved", "canned_seafood"
        if legumes:
            return "canned_preserved", "canned_legumes"
        if vegetables:
            return "canned_preserved", "canned_vegetables"
        return "canned_preserved", "pickled_preserved"
    if dairy:
        if has("cheese", "fromage", "جبن"):
            return "dairy", "cheese"
        if has("yogurt", "yoghurt", "raib", "leben", "kefir", "buttermilk", "fermented milk", "لبن", "رايب"):
            return "dairy", "fermented_dairy"
        if has("cream", "butter", "beurre"):
            return "dairy", "cream_butter"
        return "dairy", "milk"
    if seafood:
        if has("shrimp", "prawn", "crab", "lobster", "mussel", "oyster", "squid", "octopus", "shellfish", "mollusc"):
            return "seafood", "shellfish"
        return "seafood", "fish"
    if meat:
        if has("liver", "kidney", "heart", "tripe", "tongue", "offal", "كبد", "قلب"):
            return "meat_offal", "offal"
        if has("chicken", "turkey", "duck", "poultry", "دجاج"):
            return "meat_offal", "poultry"
        if has("sausage", "bacon", "ham", "processed meat"):
            return "meat_offal", "processed_meat"
        return "meat_offal", "red_meat"
    if legumes:
        if has("chickpea", "garbanzo", "حمص"):
            return "legumes", "chickpeas"
        if has("lentil", "عدس"):
            return "legumes", "lentils"
        if has("pea", "بازلاء"):
            return "legumes", "peas"
        return "legumes", "beans"
    if fruit:
        if has("lemon", "lime", "orange", "citrus", "grapefruit", "برتقال", "ليمون"):
            return "fruits", "citrus"
        if has("berry", "strawberry", "raspberry", "blueberry"):
            return "fruits", "berries"
        if has("date", "raisin", "dried fruit", "تمر", "زبيب"):
            return "fruits", "dried_fruits"
        if has("peach", "plum", "apricot", "cherry", "peach", "مشمش"):
            return "fruits", "stone_fruits"
        if has("mango", "pineapple", "papaya", "banana", "kiwi"):
            return "fruits", "tropical_fruits"
        return "fruits", None
    if vegetables:
        if has("potato", "carrot", "beet", "radish", "turnip", "sweet potato", "بطاطا", "جزر"):
            return "vegetables", "root_tubers"
        if has("onion", "garlic", "shallot", "leek", "بصل", "ثوم"):
            return "vegetables", "bulb_vegetables"
        if has("spinach", "lettuce", "cabbage", "kale", "chard", "parsley leaf", "سبانخ"):
            return "vegetables", "leafy_vegetables"
        return "vegetables", "fruit_vegetables"
    if nuts:
        return "nuts_seeds", None
    if spices:
        return "spices", "whole_spices" if has("whole", "seed", "peppercorn") else "ground_spices"
    if herbs:
        return "herbs", "fresh_herbs" if has("fresh", "leaf", "leaves") else "dried_herbs"
    if oils:
        return "oils_fats", "cooking_oils"
    if sauces:
        return "sauces_condiments", "sauces" if has("sauce", "mayonnaise", "ketchup", "harissa", "paste") else "condiments"
    if sweeteners:
        return "sweeteners", "sugar_syrups"
    if eggs:
        return "eggs", None
    if mushrooms:
        return "mushrooms", None
    if frozen:
        return "frozen", None
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
        return "baking", None
    if beverages:
        return "beverages", None
    return "other", None


def default_units(category_id: str, subcategory_id: str | None, names: Iterable[str]) -> list[str]:
    text = normalize_label(" ".join(names))
    if category_id == "eggs" or "egg" in text:
        return ["piece", "dozen", "g", "kg"]
    if category_id in {"dairy", "beverages"} or any(token in text for token in ("milk", "lait", "juice", "oil", "sauce", "water")):
        return ["ml", "l", "g", "kg"]
    if category_id in {"spices", "herbs"}:
        return ["g", "kg", "ملعقة صغيرة", "ملعقة كبيرة"]
    if category_id == "other":
        return ["g", "kg", "ml", "l", "piece"]
    return ["g", "kg", "piece"]


def normalize_recipe_unit(unit: str, quantity: Any) -> str | None:
    raw = str(unit or "").strip()
    key = re.sub(r"[\s_-]+", "", raw.lower())
    mapping = {
        "kg": "g", "mg": "g", "oz": "g", "lb": "g", "lbs": "g",
        "l": "ml", "liter": "ml", "litre": "ml",
        "pcs": "piece", "pc": "piece", "piece": "piece", "pieces": "piece",
        "tbsp": "ملعقة كبيرة", "tbs": "ملعقة كبيرة", "tablespoon": "ملعقة كبيرة", "tablespoons": "ملعقة كبيرة",
        "tsp": "ملعقة صغيرة", "teaspoon": "ملعقة صغيرة", "teaspoons": "ملعقة صغيرة",
        "cup": "كوب", "cups": "كوب",
        "taste": "حسب الحاجة", "totaste": "حسب الحاجة",
        "pinch": "رشة", "clove": "فص", "cloves": "فص",
        "bunch": "ربطة", "slice": "شريحة", "handful": "حفنة",
        "sprig": "sprig", "stalk": "stalk",
    }
    if not key:
        return "حسب الحاجة" if str(quantity or "").strip() == "" else "piece"
    return mapping.get(key, raw)


def _clean_english_ingredient(value: str) -> str:
    text = str(value or "").strip()
    if not text:
        return ""
    # Reuse the source Focus parser so its supported units/fractions remain aligned.
    try:
        from tools.translate_focus_recipe_corpus import parse_ingredient_line
        parsed = parse_ingredient_line(text)
        name = str(parsed.get("name") or text)
    except Exception:
        name = text
    name = re.sub(r"^\s*(?:of|a|an)\s+", "", name, flags=re.IGNORECASE)
    name = re.sub(r"\s*,?\s*(?:to taste|as needed|for garnish)$", "", name, flags=re.IGNORECASE)
    return name.strip(" ,;:")


def room_ingredient_id(ingredient: dict[str, Any], recipe_id: str) -> str:
    source_id = str(ingredient.get("id") or "").strip()
    if source_id:
        safe = re.sub(r"[^a-z0-9._-]", "-", source_id.lower())
        return "orc-ing-unitools-" + safe
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
            english = str(ingredient.get("sourceNameEn") or "").strip()
            if not english:
                english = _clean_english_ingredient(
                    str(ingredient.get("sourceTextEn") or ingredient.get("sourceTextEs") or "")
                )
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


def match_recipe_group(
    group: dict[str, Any],
    off_records: list[dict[str, Any]],
    indexes: dict[str, dict[str, set[int]]],
) -> tuple[int | None, str, list[int]]:
    english = normalize_label(group["names"].get("en", ""))
    arabic = normalize_label(group["names"].get("ar", ""))
    french = normalize_label(group["names"].get("fr", ""))
    candidates: set[int] = set()
    for lang, key in (("en", english), ("ar", arabic), ("fr", french)):
        if key:
            candidates.update(indexes[lang].get(key, set()))
    if not candidates:
        return None, "unmatched", []
    exact_primary = {
        index for index in candidates
        if english and normalize_label(off_records[index]["names"].get("en", "")) == english
    }
    if len(exact_primary) == 1:
        return next(iter(exact_primary)), "exact_en_primary", sorted(candidates)
    if len(candidates) == 1:
        return next(iter(candidates)), "exact_alias", sorted(candidates)
    exact_ar = {
        index for index in candidates
        if arabic and normalize_label(off_records[index]["names"].get("ar", "")) == arabic
    }
    if len(exact_ar) == 1:
        return next(iter(exact_ar)), "exact_ar_primary", sorted(candidates)
    return None, "ambiguous", sorted(candidates)


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


def uuid_for(source_key: str) -> str:
    return str(uuid.uuid5(UUID_NAMESPACE, source_key))


def choose_names(record: dict[str, Any], groups: list[dict[str, Any]]) -> dict[str, str | None]:
    names: dict[str, str | None] = {}
    for lang in ("ar", "en", "fr"):
        values: Counter[str] = Counter()
        taxonomy_name = record["names"].get(lang) if record else None
        if taxonomy_name:
            values[str(taxonomy_name)] += 1000
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
    category_id, subcategory_id = category_for([*all_labels, *foodon_labels])

    legacy_ids = sorted({
        legacy_id
        for group in groups
        for legacy_id in group["legacyIds"]
    })
    if legacy_ids:
        product_id = legacy_ids[0]
    elif record:
        product_id = uuid_for("openfoodfacts:en:" + normalize_label(record["name"]))
    else:
        first = groups[0]
        product_id = uuid_for("recipe:" + first["key"])

    names = choose_names(record, groups)
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

    observed_units = sorted({
        unit for group in groups for unit in group["units"]
    })
    accepted_units = observed_units or default_units(category_id, subcategory_id, [
        str(value or "") for value in names.values()
    ])
    source_mappings: dict[str, Any] = {}
    if record:
        source_mappings["openFoodFactsIngredientTerm"] = record["name"]
        for property_name in ("usdaFdcCode", "ciqualFoodCode"):
            value = record.get("sourceProperties", {}).get(property_name)
            if value:
                source_mappings[property_name] = value
    if foodon_ids:
        source_mappings["foodOnIds"] = foodon_ids

    return {
        "id": product_id,
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
        "countryOfOrigin": None,
        "nutrition": None,
        "sourceMappings": source_mappings,
        "recipeIngredientIds": legacy_ids,
    }


def build_catalogue(
    recipe_data: dict[str, Any],
    off_records: list[dict[str, Any]],
    foodon: dict[str, dict[str, Any]],
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    recipes = recipe_data.get("recipes") or []
    groups = collect_recipe_groups(recipes)
    off_index = make_indexes(off_records)
    foodon_index, foodon_ancestors = build_foodon_indexes(foodon)

    off_to_groups: dict[int, list[dict[str, Any]]] = defaultdict(list)
    matched_ids: dict[str, str] = {}
    matching_methods: Counter[str] = Counter()
    ambiguous: list[dict[str, Any]] = []
    unmatched: list[dict[str, Any]] = []

    for group in groups:
        index, method, candidates = match_recipe_group(group, off_records, off_index)
        matching_methods[method] += 1
        if index is not None:
            off_to_groups[index].append(group)
            continue
        report = {
            "nameAr": group["names"].get("ar"),
            "nameEn": group["names"].get("en"),
            "legacyIds": group["legacyIds"],
            "units": group["units"],
        }
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
        product = build_product(record, groups_for_record, foodon_ids, foodon)
        products.append(product)
        for group in groups_for_record:
            consumed_groups.add(group["key"])
            for legacy_id in group["legacyIds"]:
                matched_ids[legacy_id] = product["id"]

    # Every recipe ingredient must resolve to a product, even when neither external
    # dataset contains its exact name. These are reported for future curation.
    recipe_only_groups = [group for group in groups if group["key"] not in consumed_groups]
    recipe_only_by_key: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for group in recipe_only_groups:
        identity = normalize_label(group["names"].get("en") or group["names"].get("ar"))
        recipe_only_by_key[identity].append(group)

    for identity, related_groups in recipe_only_by_key.items():
        product = build_product(None, related_groups, [], foodon)
        products.append(product)
        for group in related_groups:
            for legacy_id in group["legacyIds"]:
                matched_ids[legacy_id] = product["id"]

    # Final uniqueness guard: an ID or canonical English name may appear only once.
    by_id: dict[str, dict[str, Any]] = {}
    by_english: dict[str, str] = {}
    collisions: list[dict[str, str]] = []
    for product in products:
        product_id = str(product["id"])
        english_key = normalize_label(str(product["names"].get("en") or ""))
        if product_id in by_id:
            collisions.append({"type": "id", "value": product_id})
            continue
        if english_key and english_key in by_english:
            # Exact normalized duplicates are merged only when no contradictory
            # category exists. Incompatible categories are rejected as ambiguous.
            existing = by_id[by_english[english_key]]
            if (
                existing["categoryId"] == product["categoryId"]
                and existing.get("subcategoryId") == product.get("subcategoryId")
            ):
                for lang in ("ar", "en", "fr"):
                    current = existing["names"].get(lang)
                    other = product["names"].get(lang)
                    if other and not current:
                        existing["names"][lang] = other
                    existing["aliases"][lang] = sorted(set(existing["aliases"][lang]) | set(product["aliases"][lang]))
                existing["recipeIngredientIds"] = sorted(set(existing["recipeIngredientIds"]) | set(product["recipeIngredientIds"]))
                existing["acceptedUnits"] = sorted(set(existing["acceptedUnits"]) | set(product["acceptedUnits"]))
                existing["sourceMappings"].update(product["sourceMappings"])
                for legacy_id in product["recipeIngredientIds"]:
                    matched_ids[legacy_id] = existing["id"]
                continue
            collisions.append({"type": "english-name", "value": english_key})
            continue
        by_id[product_id] = product
        if english_key:
            by_english[english_key] = product_id

    if collisions:
        raise RuntimeError(
            "Duplicate product identities remain after deterministic merging: "
            + json.dumps(collisions[:20], ensure_ascii=False)
        )

    products = sorted(by_id.values(), key=lambda item: (
        normalize_label(str(item["names"].get("en") or "")),
        str(item["id"]),
    ))
    all_recipe_ids = {
        room_ingredient_id(ingredient, str(recipe.get("id") or ""))
        for recipe in recipes
        for ingredient in (recipe.get("ingredients") or [])
        if isinstance(ingredient, dict) and (ingredient.get("name") or ingredient.get("sourceNameEn") or ingredient.get("sourceTextEn"))
    }
    unmapped_ids = sorted(all_recipe_ids - set(matched_ids))
    if unmapped_ids:
        raise RuntimeError(
            f"Recipe ingredient mappings incomplete: {len(unmapped_ids)} IDs have no product."
        )

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
        "schemaVersion": 1,
        "catalogueType": "generic-food-items",
        "idStrategy": "reuse-one-existing-recipe-ingredient-id-per-item; UUIDv5 for new items",
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
        "categories": CATEGORY_DATA,
        "products": products,
    }
    mapping = {
        "schemaVersion": 1,
        "description": "Maps existing Room recipe ingredient IDs to canonical food-item IDs.",
        "recipeIngredientToProductId": dict(sorted(matched_ids.items())),
    }
    missing_ar = sum(1 for p in products if not p["names"].get("ar"))
    missing_en = sum(1 for p in products if not p["names"].get("en"))
    missing_fr = sum(1 for p in products if not p["names"].get("fr"))
    missing_category = sum(1 for p in products if p["categoryId"] == "other")
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
            "offMatchedGroups": sum(matching_methods[k] for k in ("exact_en_primary", "exact_alias", "exact_ar_primary")),
            "unmatchedRecipeGroups": len(unmatched),
            "ambiguousRecipeGroups": len(ambiguous),
            "missingArabicNames": missing_ar,
            "missingEnglishNames": missing_en,
            "missingFrenchNames": missing_fr,
            "fallbackOtherCategory": missing_category,
            "missingImages": sum(1 for p in products if not p["imageUrl"]),
            "missingNutrition": sum(1 for p in products if not p["nutrition"]),
            "duplicateIds": 0,
            "duplicateEnglishNames": 0,
        },
        "recipeMatchingMethods": dict(sorted(matching_methods.items())),
        "unmatchedRecipeIngredients": unmatched[:500],
        "ambiguousRecipeIngredients": ambiguous[:500],
        "dataAvailabilityNotes": [
            "The OFF ingredient taxonomy supplies multilingual labels, aliases and taxonomy relations, not product photographs or complete nutrient panels.",
            "FoodOn supplies food classes, labels, synonyms and semantic hierarchy; it is not a complete image or nutrition database.",
            "imageUrl, quantity, countryOfOrigin and nutrition remain null unless a later source provides item-specific evidence.",
            "Quantity and barcode belong to a future market-product variant, not to this generic food-item identity.",
        ],
    }
    return catalogue, mapping, report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recipes", type=Path, default=ROOT / "data/recipes/ar/recipes.json")
    parser.add_argument("--off-taxonomy", type=Path)
    parser.add_argument("--foodon-owl", type=Path)
    parser.add_argument("--download-dir", type=Path, default=Path(os.environ.get("MIDATI_DATA_CACHE", ".cache/product-catalogue")))
    parser.add_argument("--output", type=Path, default=ROOT / "data/products/catalogue.json")
    parser.add_argument("--mapping-output", type=Path, default=ROOT / "data/products/recipe-ingredient-map.json")
    parser.add_argument("--audit-output", type=Path, default=ROOT / "data/products/build-audit.json")
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
        off_records = parse_off_taxonomy(off_path)
        foodon = parse_foodon_owl(foodon_path)
        if len(off_records) < 1000:
            raise RuntimeError(f"Unexpectedly few OFF taxonomy entries parsed: {len(off_records)}")
        if len(foodon) < 1000:
            raise RuntimeError(f"Unexpectedly few FoodOn classes parsed: {len(foodon)}")
        catalogue, mapping, audit = build_catalogue(recipe_data, off_records, foodon)
    except Exception as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1

    for path, value in (
        (args.output, catalogue),
        (args.mapping_output, mapping),
        (args.audit_output, audit),
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
        "0 duplicate IDs/names."
    )
    print(f"Catalogue: {args.output}")
    print(f"Recipe mapping: {args.mapping_output}")
    print(f"Audit: {args.audit_output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
