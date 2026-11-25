#!/usr/bin/env python3
"""Import, safety-adapt and translate Focus Recipes into Midati's Arabic catalogue.

The source is downloaded from Focus Recipes v1.0 (CC BY 4.0). Wine/alcohol
recipes are excluded. Pork-based recipes are adapted to beef/lamb or olive oil
where a practical substitute exists; unresolved cases are excluded.

The existing 3,013-recipe corpus is preserved and Focus records are appended
with their complete nutrition, dietary, cuisine, category, image and provenance
metadata. Translation is resumable by source ID.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import pathlib
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from fractions import Fraction
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE_URL = "https://focusathletics.app/data/focus-recipes.json"
DATASET_PAGE = "https://focusathletics.app/en/recipes/dataset"
SOURCE_NAME = "Focus Recipes"
SOURCE_VERSION = "1.0"
SOURCE_LICENSE = "CC BY 4.0"
LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/"
EXPECTED_SOURCE_COUNT = 500
OUTPUT_DEFAULT = ROOT / "data" / "recipes" / "ar" / "recipes.json"
MAX_BATCH_SIZE = 20


class PipelineError(RuntimeError):
    pass


ALCOHOL_PATTERNS = (
    (r"\bred\s+wine\b", "red wine"),
    (r"\bwine\b|\bvino\b|\balcohol(?:ic)?\b", "wine/alcohol"),
    (r"\bwhite\s+wine\b", "white wine"),
    (r"\bcooking\s+wine\b", "cooking wine"),
    (r"\bwine\s+vinegar\b", "wine vinegar"),
    (r"\bvinagre\s+de\s+vino\b", "vinagre de vino"),
    (r"\bvino(?:\s+(?:tinto|blanco|de\s+cocina|para\s+cocinar))?\b", "vino"),
    (r"\bbeer\b|\bbeer\s+(\w+)\b", "beer"),
    (r"\bcerveza\b", "cerveza"),
    (r"\bale\b|\bstout\b", "ale/stout"),
    (r"\bsake\b|\bmirin\b", "sake/mirin"),
    (r"\bsherry\b|\bjerez\b", "sherry/jerez"),
    (r"\bvermouth\b|\bvermut\b", "vermouth"),
    (r"\bport\s+wine\b|\boporto\b", "port"),
    (r"\brum\b|\bron\b", "rum"),
    (r"\bvodka\b", "vodka"),
    (r"\bwhisk(?:y|ey)\b|\bbourbon\b", "whisky/bourbon"),
    (r"\bbrandy\b|\bcognac\b|\bcoñac\b", "brandy/cognac"),
    (r"\btequila\b|\bmezcal\b", "tequila/mezcal"),
    (r"\bchampagne\b|\bchampán\b|\bcava\b|\bprosecco\b", "sparkling wine"),
    (r"\bliqu(?:or|eur)\b|\blicor\b", "liqueur"),
    (r"\bsangria\b|\bsangría\b", "sangria"),
    (r"\bcalvados\b|\bkirsch\b|\bgrappa\b", "spirits"),
)

# More specific forms are deliberately replaced before generic ones.
PORK_REPLACEMENTS = (
    (r"\bpork\s+tenderloin\b", "beef tenderloin"),
    (r"\bpork\s+loin\b", "beef loin"),
    (r"\bpork\s+chops?\b", "lamb chops"),
    (r"\bpork\s+shoulder\b", "lamb shoulder"),
    (r"\bpork\s+belly\b", "beef brisket"),
    (r"\bpork\s+short\s+ribs?\b", "beef short ribs"),
    (r"\bpork\s+ribs?\b", "beef ribs"),
    (r"\bground\s+pork\b|\bminced\s+pork\b", "ground beef"),
    (r"\bpork\s+sausage\b|\bsausage\s+made\s+from\s+pork\b", "beef sausage"),
    (r"\bcanadian\s+bacon\b", "smoked beef slices"),
    (r"\bbeef\s+bacon\b", "beef bacon"),
    (r"\bturkey\s+bacon\b", "turkey bacon"),
    (r"\bbacon\b", "smoked beef strips"),
    (r"\bpancetta\b", "smoked beef cubes"),
    (r"\bprosciutto\b", "smoked beef slices"),
    (r"\bjam[oó]n\s+serrano\b|\bjam[oó]n\s+ib[eé]rico\b", "jamón de res"),
    (r"\bjam[oó]n\b(?!\s+de\s+(?:res|pavo))", "jamón de res"),
    (r"\bham\b", "beef ham"),
    (r"\bchorizo\b", "beef chorizo"),
    (r"\bpepperoni\b", "beef pepperoni"),
    (r"\bmortadella\b", "beef mortadella"),
    (r"\bsalami\b", "beef salami"),
    (r"\bbutifarra\b|\blonganiza\b|\bchistorra\b", "embutido de res"),
    (r"\bmorcilla\b", "morcilla de res"),
    (r"\bblood\s+sausage\b", "beef blood sausage"),
    (r"\bsalchich[oó]n\b", "salchichón de res"),
    (r"\b(?:fuet|sobrasada)\b", "embutido de res"),
    (r"\bguanciale\b", "smoked beef cheek"),
    (r"\bfatback\b", "olive oil"),
    (r"\bchicharr[oó]n\b", "chicharrón de res"),
    (r"\bporchetta\b", "beef roast"),
    (r"\bmeatballs?\s+made\s+with\s+pork\b", "beef meatballs"),
    (r"\bcarne\s+molida\s+de\s+cerdo\b|\bcarne\s+picada\s+de\s+cerdo\b", "carne molida de res"),
    (r"\bchuletas?\s+de\s+cerdo\b", "chuletas de cordero"),
    (r"\bcostillas?\s+de\s+cerdo\b", "costillas de res"),
    (r"\blomo\s+de\s+cerdo\b|\bsolomillo\s+de\s+cerdo\b", "lomo de res"),
    (r"\bpanceta\b|\btocino\b", "tiras de res ahumadas"),
    (r"\bmanteca\s+de\s+cerdo\b|\bgrasa\s+de\s+cerdo\b|\blard\b", "aceite de oliva"),
    (r"\bcarne\s+de\s+cerdo\b|\bcarne\s+de\s+puerco\b", "carne de res"),
    (r"\bpork\s+rinds?\b", "crispy beef cracklings"),
    (r"\bcerdo\b|\bpuerco\b|\bcochino\b", "res"),
    (r"\bsalchichas?\b", "salchicha de res"),
    (r"\bpork\b", "beef"),
)

PORK_POSITIVE_PATTERN = re.compile(
    r"\b(?:pork|pig(?:'s)?(?:\s+meat|\s+feet|\s+trotters|\s+skin|\s+rind)?|swine|hog|ham|bacon|pancetta|prosciutto|"
    r"chorizo|pepperoni|mortadella|salami|butifarra|morcilla|blood\s+sausage|"
    r"salchich[oó]n|fuet|sobrasada|guanciale|fatback|chicharr[oó]n|porchetta|"
    r"lech[oó]n|cochinillo|lard|"
    r"cerdo|puerco|cochino|cerdito|porcin[oea]|jam[oó]n|tocino|panceta|"
    r"chuletas?\s+de\s+cerdo|costillas?\s+de\s+cerdo)\b",
    re.IGNORECASE,
)
NEGATIVE_PORK_PATTERN = re.compile(
    r"\b(?:no|without|free\s+of)\s+(?:any\s+)?pork\b|"
    r"\bpork[-\s]free\b|\bsin\s+(?:carne\s+de\s+)?(?:cerdo|puerco)\b",
    re.IGNORECASE,
)

QUANTITY_PATTERN = re.compile(
    r"""^\s*(?P<qty>\d+(?:[.,]\d+)?(?:\s+\d+/\d+|\s*/\s*\d+)?)
        \s*(?P<unit>kg|g|mg|ml|l|oz|lb|lbs?|cups?|cup|tazas?|taza|
        tbsp|tbsps|tbsp\.?|cdas?|cda\.?|cucharadas?|tsp|tsps|tsp\.?|
        cdtas?|cdta\.?|cditas?|cdita\.?|cucharaditas?|pieces?|pcs?|
        unidades?|unidades|dientes?|cloves?|paquetes?|packs?|package|
        packages?|latas?|cans?|slices?|rebanadas?|ramas?|stalks?|bunch(?:es)?|
        manojos?|puñados?|handfuls?)\b
        \s*(?P<rest>.*)$""",
    re.IGNORECASE | re.VERBOSE,
)

UNIT_ALIASES = {
    "kg": "kg", "kilogram": "kg", "kilograms": "kg", "kilogramo": "kg", "kilogramos": "kg",
    "g": "g", "gram": "g", "grams": "g", "gramo": "g", "gramos": "g",
    "mg": "mg", "ml": "ml", "milliliter": "ml", "milliliters": "ml",
    "l": "l", "liter": "l", "liters": "l", "litro": "l", "litros": "l",
    "oz": "oz", "lb": "lb", "lbs": "lb",
    "cup": "cup", "cups": "cup", "taza": "cup", "tazas": "cup",
    "tbsp": "tbsp", "tbsps": "tbsp", "cda": "tbsp", "cdas": "tbsp",
    "cucharada": "tbsp", "cucharadas": "tbsp",
    "tsp": "tsp", "tsps": "tsp", "cdta": "tsp", "cdtas": "tsp",
    "cdita": "tsp", "cditas": "tsp", "cucharadita": "tsp", "cucharaditas": "tsp",
    "piece": "pcs", "pieces": "pcs", "pc": "pcs", "pcs": "pcs",
    "unidad": "pcs", "unidades": "pcs", "diente": "clove", "dientes": "clove",
    "clove": "clove", "cloves": "clove",
    "paquete": "pcs", "paquetes": "pcs", "pack": "pcs", "packs": "pcs",
    "package": "pcs", "packages": "pcs", "lata": "pcs", "latas": "pcs", "can": "pcs", "cans": "pcs",
    "slice": "slice", "slices": "slice", "rebanada": "slice", "rebanadas": "slice",
    "stalk": "pcs", "stalks": "pcs", "rama": "pcs", "ramas": "pcs",
    "bunch": "bunch", "bunches": "bunch", "manojo": "bunch", "manojos": "bunch",
    "handful": "handful", "handfuls": "handful", "puñado": "handful", "puñados": "handful",
}


def load_source() -> tuple[dict[str, Any], str]:
    request = urllib.request.Request(
        SOURCE_URL,
        headers={
            "Accept": "application/json",
            "User-Agent": "Midati-Focus-Recipe-Import/1.0 (https://github.com/ni3yyn/Meals)",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            raw = response.read()
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise PipelineError(f"Could not download the Focus Recipes JSON: {error}") from error

    digest = hashlib.sha256(raw).hexdigest()
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise PipelineError(f"Focus Recipes source is not valid UTF-8 JSON: {error}") from error

    if not isinstance(value, dict):
        raise PipelineError("Focus Recipes source root must be an object.")
    if value.get("name") != SOURCE_NAME or str(value.get("version")) != SOURCE_VERSION:
        raise PipelineError("Focus Recipes dataset identity/version changed; review the source before importing.")
    if value.get("license") != SOURCE_LICENSE:
        raise PipelineError("Focus Recipes licence changed; refusing to redistribute without review.")
    records = value.get("recipes")
    if not isinstance(records, list) or len(records) != EXPECTED_SOURCE_COUNT:
        raise PipelineError(
            f"Expected {EXPECTED_SOURCE_COUNT} source recipes, received "
            f"{len(records) if isinstance(records, list) else 'no list'}."
        )
    ids = [str(item.get("id", "")) for item in records if isinstance(item, dict)]
    if len(ids) != EXPECTED_SOURCE_COUNT or any(not item for item in ids) or len(set(ids)) != len(ids):
        raise PipelineError("Focus Recipes contains a missing or duplicate recipe ID.")
    return value, digest


def recipe_text_fields(recipe: dict[str, Any]) -> list[str]:
    values: list[str] = []
    for field in ("title_es", "title_en", "description_es", "description_en"):
        value = recipe.get(field)
        if isinstance(value, str):
            values.append(value)
    for field in ("ingredients_es", "ingredients_en", "steps_es", "steps_en"):
        value = recipe.get(field)
        if isinstance(value, list):
            values.extend(str(item) for item in value)
    return values


def alcohol_match(recipe: dict[str, Any]) -> str | None:
    combined = "\n".join(recipe_text_fields(recipe))
    for pattern, label in ALCOHOL_PATTERNS:
        if re.search(pattern, combined, re.IGNORECASE):
            return label
    return None


SAFE_NON_PORK_PHRASES = re.compile(
    r"\b(?:turkey|chicken|beef)\s+(?:bacon|ham|sausage|chorizo|pepperoni|mortadella|salami)\b|"
    r"\bbeef\s+blood\s+sausage\b|\bchicharr[oó]n\s+de\s+res\b|"
    r"\bsalchich[oó]n\s+de\s+res\b|"
    r"\b(?:vegan|vegetarian|plant[- ]based)\s+sausage\b|"
    r"\b(?:beef|turkey|chicken)\s+chorizo\b|"
    r"\b(?:salchichas?)\s+de\s+(?:res|pollo|pavo)\b|"
    r"\bchorizo\s+de\s+res\b|\bjam[oó]n\s+de\s+(?:res|pavo)\b|"
    r"\bmorcilla\s+de\s+res\b",
    re.IGNORECASE,
)


def protect_negative_pork_phrases(text: str) -> tuple[str, dict[str, str]]:
    protected: dict[str, str] = {}

    def replace(match: re.Match[str]) -> str:
        marker = f"FOCUSSAFETYTOKEN{len(protected)}END"
        protected[marker] = match.group(0)
        return marker

    protected_text = NEGATIVE_PORK_PATTERN.sub(replace, text)
    # Negative mentions describe absence, not an ingredient, so they do not
    # trigger substitutions or the post-adaptation ingredient safety check.
    return protected_text, protected


def substitute_pork(text: str) -> tuple[str, list[dict[str, str]]]:
    safe_text, protected = protect_negative_pork_phrases(text)

    def protect_safe_meat(match: re.Match[str]) -> str:
        marker = f"FOCUSSAFEMEATTOKEN{len(protected)}END"
        protected[marker] = match.group(0)
        return marker

    safe_text = SAFE_NON_PORK_PHRASES.sub(protect_safe_meat, safe_text)
    changes: list[dict[str, str]] = []
    for pattern, replacement in PORK_REPLACEMENTS:
        safe_text, count = re.subn(pattern, replacement, safe_text, flags=re.IGNORECASE)
        if count:
            changes.append({"replacement": replacement, "occurrences": count})

    # Generic English sausages are commonly pork-based unless another meat is stated.
    sausage_changes = 0

    def adapt_generic_sausage(match: re.Match[str]) -> str:
        nonlocal sausage_changes
        prefix = safe_text[max(0, match.start() - 40):match.start()]
        if re.search(
            r"\b(?:beef|chicken|turkey)(?:\s+blood)?\s*$|"
            r"\b(?:vegetarian|vegan)\s*$|\bplant[- ]based\s*$",
            prefix,
            re.IGNORECASE,
        ):
            return match.group(0)
        sausage_changes += 1
        suffix = "s" if match.group(1) else ""
        return "beef sausage" + suffix

    safe_text = re.sub(r"\bsausage(s)?\b", adapt_generic_sausage, safe_text, flags=re.IGNORECASE)
    if sausage_changes:
        changes.append({"replacement": "beef sausage", "occurrences": sausage_changes})

    for marker, original in protected.items():
        safe_text = safe_text.replace(marker, original)
    return safe_text, changes


def has_unresolved_pork(text: str) -> bool:
    candidate = NEGATIVE_PORK_PATTERN.sub("", text)
    candidate = SAFE_NON_PORK_PHRASES.sub("", candidate)
    return bool(PORK_POSITIVE_PATTERN.search(candidate))


UNVERIFIED_GELATIN_PATTERN = re.compile(r"\b(?:gelatin|gelatine|gelatina)\b", re.IGNORECASE)
SAFE_GELATIN_SOURCE_PATTERN = re.compile(
    r"\b(?:bovine|bovino|bovina|beef|veal|res|vacuno|vacuna|ternera|fish|pescado|"
    r"agar(?:-agar)?|pectin|pectina|plant[- ]based|vegetal)\b",
    re.IGNORECASE,
)


def adapt_recipe(recipe: dict[str, Any]) -> tuple[dict[str, Any] | None, list[dict[str, str]], str | None]:
    alcohol = alcohol_match(recipe)
    if alcohol:
        return None, [], f"alcohol ingredient or instruction: {alcohol}"

    ingredient_and_method_text = [
        str(value)
        for field in ("ingredients_es", "ingredients_en", "steps_es", "steps_en")
        for value in (recipe.get(field) or [])
    ]
    for value in ingredient_and_method_text:
        if UNVERIFIED_GELATIN_PATTERN.search(value) and not SAFE_GELATIN_SOURCE_PATTERN.search(value):
            return None, [], "unverified animal gelatin source (pork-derived risk)"

    adapted = copy.deepcopy(recipe)
    changes: list[dict[str, str]] = []
    fields = ("title_es", "title_en", "description_es", "description_en")
    for field in fields:
        value = adapted.get(field)
        if isinstance(value, str):
            updated, field_changes = substitute_pork(value)
            adapted[field] = updated
            changes.extend(field_changes)

    for field in ("ingredients_es", "ingredients_en", "steps_es", "steps_en"):
        values = adapted.get(field)
        if isinstance(values, list):
            updated_values = []
            for value in values:
                updated, field_changes = substitute_pork(str(value))
                updated_values.append(updated)
                changes.extend(field_changes)
            adapted[field] = updated_values

    remaining = [text for text in recipe_text_fields(adapted) if has_unresolved_pork(text)]
    if remaining:
        return None, changes, "unresolved pork-derived ingredient/reference"
    return adapted, changes, None


def humanize_key(value: str) -> str:
    return re.sub(r"\s+", " ", str(value).replace("_", " ").replace("-", " ")).strip()


def parse_quantity(value: str) -> float | None:
    normalized = value.strip().replace(",", ".")
    if not normalized:
        return None
    try:
        if " " in normalized and "/" in normalized:
            whole, fraction = normalized.split(None, 1)
            result = float(whole) + float(Fraction(fraction))
        elif "/" in normalized:
            result = float(Fraction(normalized))
        else:
            result = float(normalized)
    except (ValueError, ZeroDivisionError):
        return None
    return result if result > 0 else None


def parse_ingredient_line(source_text: str) -> dict[str, Any]:
    text = str(source_text).strip()
    name_part, amount_part = text, ""
    if ":" in text:
        candidate_name, candidate_amount = text.split(":", 1)
        if candidate_name.strip() and candidate_amount.strip():
            name_part, amount_part = candidate_name.strip(), candidate_amount.strip()
    else:
        match = QUANTITY_PATTERN.match(text)
        if match:
            amount_part = match.group("qty").strip()
            if match.group("unit"):
                amount_part += " " + match.group("unit").strip()
            trailing = match.group("rest").strip()
            name_part = re.sub(r"^(?:de|del|of)\s+", "", trailing, flags=re.IGNORECASE).strip() or text
        else:
            leading_quantity = re.match(
                r"^\s*(\d+(?:[.,]\d+)?(?:\s+\d+/\d+|\s*/\s*\d+)?)\s+(.+)$",
                text,
            )
            if leading_quantity:
                amount_part = leading_quantity.group(1) + " pcs"
                name_part = leading_quantity.group(2).strip()
    amount_match = QUANTITY_PATTERN.match(amount_part) if amount_part else None
    quantity: float | None = None
    unit = ""
    note = ""
    if amount_match:
        quantity = parse_quantity(amount_match.group("qty"))
        unit_key = amount_match.group("unit").strip().lower().rstrip(".")
        unit = UNIT_ALIASES.get(unit_key, "pcs")
        note = amount_match.group("rest").strip(" ,;")
    elif amount_part:
        leading_number = re.match(r"^\s*(\d+(?:[.,]\d+)?(?:\s+\d+/\d+|\s*/\s*\d+)?)\s*(.*)$", amount_part)
        if leading_number:
            quantity = parse_quantity(leading_number.group(1))
            remainder = leading_number.group(2).strip()
            unit = "pcs"
            note = remainder
        else:
            note = amount_part
    return {
        "name": name_part or text,
        "note": note,
        "quantity": quantity,
        "unit": unit or ("to_taste" if not quantity else "pcs"),
        "quantityText": amount_part,
        "sourceText": text,
    }


def post_batch(endpoint: str, token: str, batch: list[dict[str, Any]]) -> dict[str, Any]:
    payload = json.dumps(
        {"token": token, "recipes": batch},
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=payload,
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "User-Agent": "Midati-Focus-Recipe-Translation/1.0",
        },
        method="POST",
    )
    last_error: Exception | None = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=360) as response:
                raw = response.read().decode("utf-8", errors="replace")
            result = json.loads(raw)
            if not isinstance(result, dict):
                raise PipelineError("Translation endpoint returned an unexpected response.")
            if result.get("error") == "unauthorized":
                raise PipelineError("The Apps Script token was rejected; verify the existing GitHub secrets.")
            if result.get("error"):
                raise PipelineError(str(result["error"]))
            return result
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            last_error = PipelineError(f"Translation endpoint HTTP {error.code}: {body[:500]}")
            if error.code not in (408, 425, 429, 500, 502, 503, 504):
                raise last_error from error
        except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as error:
            last_error = error
        if attempt < 3:
            time.sleep(min(2 ** attempt, 12))
    raise PipelineError(f"Translation endpoint failed after retries: {last_error}")


def collect_taxonomy_terms(records: list[tuple[dict[str, Any], list[dict[str, str]]]]) -> list[str]:
    terms: list[str] = []
    seen: set[str] = set()
    for recipe, _ in records:
        candidates = [recipe.get("cuisine", "")]
        candidates.extend(recipe.get("categories") or [])
        candidates.extend(recipe.get("dietary") or [])
        for candidate in candidates:
            term = humanize_key(candidate)
            if term and term not in seen:
                seen.add(term)
                terms.append(term)
    return terms


def build_taxonomy_translation_request(terms: list[str]) -> dict[str, Any]:
    return {
        "id": "focus:taxonomy-labels",
        "sourceLanguage": "en",
        "name": "",
        "summary": "",
        "category": "",
        "cuisine": "",
        "tags": terms,
        "allergens": [],
        "ingredients": [],
        "steps": [],
    }


def map_taxonomy_translation(terms: list[str], translation: dict[str, Any]) -> dict[str, str]:
    translated = [str(item).strip() for item in translation.get("tags") or []]
    if len(translated) != len(terms) or any(not item for item in translated):
        raise PipelineError(
            "Google Apps Script did not return a complete English-to-Arabic taxonomy translation."
        )
    return dict(zip(terms, translated, strict=True))


def translate_taxonomy_terms(
    endpoint: str,
    token: str,
    records: list[tuple[dict[str, Any], list[dict[str, str]]]],
) -> dict[str, str]:
    terms = collect_taxonomy_terms(records)
    if not terms:
        return {}
    response = post_batch(endpoint, token, [build_taxonomy_translation_request(terms)])
    result = next(
        (
            item for item in response.get("results") or []
            if isinstance(item, dict) and item.get("id") == "focus:taxonomy-labels"
        ),
        None,
    )
    if not result or not isinstance(result.get("translation"), dict):
        errors = response.get("errors") or []
        reason = errors[0].get("error") if errors and isinstance(errors[0], dict) else "missing translation result"
        raise PipelineError(f"Could not translate Focus taxonomy labels: {reason}")
    return map_taxonomy_translation(terms, result["translation"])


def build_translation_request(recipe: dict[str, Any]) -> dict[str, Any]:
    ingredients = [parse_ingredient_line(item) for item in recipe.get("ingredients_es", [])]
    return {
        "id": "focus:" + str(recipe["id"]),
        "sourceLanguage": "es",
        "name": recipe.get("title_es", ""),
        "summary": recipe.get("description_es", ""),
        "category": "",
        "cuisine": "",
        "allergens": [],
        "ingredients": [{"name": item["name"], "note": item["note"]} for item in ingredients],
        "steps": recipe.get("steps_es", []),
    }


def build_output_recipe(
    source: dict[str, Any],
    translation: dict[str, Any],
    pork_changes: list[dict[str, str]],
    taxonomy_ar: dict[str, str],
) -> dict[str, Any]:
    source_ingredients = [parse_ingredient_line(item) for item in source.get("ingredients_es", [])]
    translated_ingredients = translation.get("ingredients") or []
    ingredients: list[dict[str, Any]] = []
    english_ingredients = source.get("ingredients_en", [])
    for index, ingredient in enumerate(source_ingredients):
        translated = translated_ingredients[index] if index < len(translated_ingredients) else {}
        ingredients.append({
            "name": str(translated.get("name") or ingredient["name"]),
            "note": str(translated.get("note") or ""),
            "quantity": ingredient["quantity"],
            "unit": ingredient["unit"],
            "quantityText": ingredient["quantityText"],
            "sourceTextEs": ingredient["sourceText"],
            "sourceTextEn": str(english_ingredients[index]) if index < len(english_ingredients) else "",
        })

    categories = [str(item) for item in source.get("categories", [])]
    dietary = [str(item) for item in source.get("dietary", [])]
    taxonomy_keys = [humanize_key(item) for item in categories + dietary]
    cuisine_key = humanize_key(source.get("cuisine", ""))
    required_labels = taxonomy_keys + ([cuisine_key] if cuisine_key else [])
    missing_labels = [key for key in required_labels if key not in taxonomy_ar]
    if missing_labels:
        raise PipelineError(
            f"Missing Arabic taxonomy labels for Focus recipe {source.get('id')}: "
            + ", ".join(dict.fromkeys(missing_labels))
        )
    categories_ar = [taxonomy_ar[humanize_key(item)] for item in categories]
    dietary_ar = [taxonomy_ar[humanize_key(item)] for item in dietary]
    cuisine_ar = taxonomy_ar[cuisine_key] if cuisine_key else ""
    kcal = source.get("kcal")
    protein = source.get("protein_g")
    carbs = source.get("carbs_g")
    fat = source.get("fat_g")
    prep = source.get("prep_minutes")
    cook = source.get("cook_minutes")

    adaptation_summary = (
        "Pork-derived ingredients were replaced with practical beef/lamb or olive-oil alternatives."
        if pork_changes else ""
    )
    return {
        "id": "focus:" + str(source["id"]),
        "sourceId": str(source["id"]),
        "name": str(translation.get("name") or ""),
        "summary": str(translation.get("summary") or ""),
        "category": categories_ar[0] if categories_ar else "وصفة",
        "cuisine": cuisine_ar,
        "categories": categories,
        "categoriesAr": categories_ar,
        "servings": source.get("servings"),
        "prepMinutes": prep,
        "cookMinutes": cook,
        "ingredients": ingredients,
        "steps": [str(item) for item in translation.get("steps") or []],
        "tags": dietary_ar,
        "dietary": dietary,
        "dietaryAr": dietary_ar,
        "allergens": [],
        "nutrition": {
            "kcal": kcal,
            "protein_g": protein,
            "carbs_g": carbs,
            "fat_g": fat,
            "basis": "per_serving",
            "estimated": True,
        },
        "kcal": kcal,
        "protein_g": protein,
        "carbs_g": carbs,
        "fat_g": fat,
        "image": str(source.get("image") or ""),
        "url_es": str(source.get("url_es") or ""),
        "url_en": str(source.get("url_en") or ""),
        "slug_es": str(source.get("slug_es") or ""),
        "slug_en": str(source.get("slug_en") or ""),
        # Preserve the complete source object so future Midati filters/features can
        # use fields the current Room model does not yet consume.
        "sourceRecord": source,
        "sourceFields": {
            "title_es": str(source.get("title_es") or ""),
            "title_en": str(source.get("title_en") or ""),
            "description_es": str(source.get("description_es") or ""),
            "description_en": str(source.get("description_en") or ""),
            "ingredients_es": list(source.get("ingredients_es") or []),
            "ingredients_en": list(source.get("ingredients_en") or []),
            "steps_es": list(source.get("steps_es") or []),
            "steps_en": list(source.get("steps_en") or []),
            "cuisine": str(source.get("cuisine") or ""),
            "categories": categories,
            "dietary": dietary,
        },
        "sourceLanguage": "es",
        "sourceLanguages": ["es", "en"],
        "machineTranslated": True,
        "translatedFrom": "focus:" + str(source["id"]),
        "porkAdapted": bool(pork_changes),
        "adaptationChanges": pork_changes,
        "adaptations": [adaptation_summary] if adaptation_summary else [],
        "provenance": {
            "source": "focus-recipes",
            "title": "Focus Recipes v1.0: " + str(source.get("title_es") or source.get("title_en") or ""),
            "url": str(source.get("url_es") or DATASET_PAGE),
            "english_url": str(source.get("url_en") or ""),
            "dataset_url": DATASET_PAGE,
            "attribution": "Focus Recipes v1.0 (2026). Focus. Licensed under CC BY 4.0.",
            "source_license": SOURCE_LICENSE,
            "license": SOURCE_LICENSE,
            "license_url": LICENSE_URL,
            "adapted": bool(pork_changes),
            "changes": (
                "Machine-translated from Spanish into Arabic. Original bilingual source fields, "
                "cuisine, categories, dietary tags, per-serving nutrition, image and links are retained. "
                + adaptation_summary
            ).strip(),
        },
        "license": SOURCE_LICENSE,
        "licenseUrl": LICENSE_URL,
        "sourceUrl": str(source.get("url_es") or DATASET_PAGE),
        "sourceRevisionUrl": "",
        "sourceHistoryUrl": "",
        "sourceCredit": "Focus Recipes v1.0 (2026). Focus.",
        "sourceLicense": SOURCE_LICENSE,
        "changes": (
            "Machine-translated from Spanish into Arabic; bilingual source metadata and full nutrition/"
            "dietary structure retained. " + adaptation_summary
        ).strip(),
    }


def read_output(path: pathlib.Path) -> dict[str, Any]:
    if not path.is_file():
        raise PipelineError(
            f"Arabic catalogue not found at {path}; run the existing Polish translation pipeline first."
        )
    try:
        output = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise PipelineError(f"Could not read current Arabic catalogue: {error}") from error
    if not isinstance(output, dict) or not isinstance(output.get("recipes"), list):
        raise PipelineError("Existing Arabic catalogue has an unexpected format.")
    return output


def write_output(path: pathlib.Path, output: dict[str, Any], expected_count: int) -> None:
    recipes = output.get("recipes") or []
    recipes.sort(key=lambda item: str(item.get("id", "")))
    output["translatedCount"] = len(recipes)
    output["expectedCount"] = expected_count
    output["complete"] = len(recipes) == expected_count
    output["generatedAt"] = datetime.now(timezone.utc).isoformat()
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path, default=OUTPUT_DEFAULT)
    parser.add_argument("--batch-size", type=int, default=10)
    parser.add_argument("--max-recipes", type=int, default=EXPECTED_SOURCE_COUNT)
    args = parser.parse_args()

    endpoint = os.environ.get("GOOGLE_APPS_SCRIPT_URL", "").strip()
    token = os.environ.get("GOOGLE_APPS_SCRIPT_TOKEN", "").strip()
    if not endpoint or not token:
        raise PipelineError("Set the existing GOOGLE_APPS_SCRIPT_URL and GOOGLE_APPS_SCRIPT_TOKEN GitHub secrets.")
    if not endpoint.startswith("https://script.google.com/macros/s/") or not endpoint.endswith("/exec"):
        raise PipelineError("GOOGLE_APPS_SCRIPT_URL must be the deployed Apps Script /exec URL.")
    if not 1 <= args.batch_size <= MAX_BATCH_SIZE:
        raise PipelineError(f"--batch-size must be between 1 and {MAX_BATCH_SIZE}.")
    if args.max_recipes < 1:
        raise PipelineError("--max-recipes must be at least 1.")

    dataset, source_digest = load_source()
    adapted_records: list[tuple[dict[str, Any], list[dict[str, str]]]] = []
    excluded_alcohol: list[dict[str, str]] = []
    excluded_pork: list[dict[str, str]] = []
    pork_adapted_count = 0
    for recipe in dataset["recipes"]:
        adapted, changes, rejection = adapt_recipe(recipe)
        if rejection:
            entry = {
                "id": str(recipe.get("id", "")),
                "title": str(recipe.get("title_en") or recipe.get("title_es") or ""),
                "reason": rejection,
            }
            if rejection.startswith("alcohol"):
                excluded_alcohol.append(entry)
            else:
                excluded_pork.append(entry)
            continue
        if changes:
            pork_adapted_count += 1
        adapted_records.append((adapted, changes))

    if not adapted_records:
        raise PipelineError("Safety screening rejected every Focus recipe; no data was changed.")

    output = read_output(args.output)
    previous_audit = (output.get("sourceAudit") or {}).get("focusRecipes") or {}
    previous_digest = previous_audit.get("sourceSha256")
    if previous_digest and previous_digest != source_digest:
        raise PipelineError(
            "Focus Recipes v1.0 source bytes changed since the previous import. "
            "Review the licence and dataset diff before replacing translated records."
        )
    records = output["recipes"]
    base_records = [item for item in records if not str(item.get("id", "")).startswith("focus:")]
    existing_focus = {
        str(item.get("id", "")): item
        for item in records
        if str(item.get("id", "")).startswith("focus:")
    }
    eligible_ids = {"focus:" + str(recipe["id"]) for recipe, _ in adapted_records}
    # If the upstream v1.0 source changes its eligibility set, remove stale Focus
    # entries rather than accidentally keeping a recipe now rejected by screening.
    existing_focus = {key: value for key, value in existing_focus.items() if key in eligible_ids}

    pending = [
        (recipe, changes)
        for recipe, changes in adapted_records
        if "focus:" + str(recipe["id"]) not in existing_focus
    ][:args.max_recipes]
    expected_count = len(base_records) + len(adapted_records)

    if len(base_records) < 1:
        raise PipelineError("Existing Arabic corpus is empty; refusing to replace it.")
    if len(existing_focus) > len(adapted_records):
        raise PipelineError("Existing Focus recipe count is greater than the eligible source count.")

    print(
        f"Focus source: {len(dataset['recipes'])}; safe to include: {len(adapted_records)}; "
        f"excluded for alcohol: {len(excluded_alcohol)}; unresolved pork cases: {len(excluded_pork)}; "
        f"adapted pork recipes: {pork_adapted_count}."
    )
    print(f"Already translated: {len(existing_focus)}; pending this run: {len(pending)}.")

    audit = {
        "source": SOURCE_NAME,
        "version": SOURCE_VERSION,
        "sourceCount": len(dataset["recipes"]),
        "eligibleCount": len(adapted_records),
        "excludedAlcoholCount": len(excluded_alcohol),
        "excludedUnresolvedPorkCount": len(excluded_pork),
        "porkAdaptedCount": pork_adapted_count,
        "sourceSha256": source_digest,
        "exclusions": [
            {"id": item["id"], "reason": item["reason"]}
            for item in excluded_alcohol + excluded_pork
        ],
    }
    output["sourceCorpora"] = output.get("sourceCorpora") or []
    existing_source_names = {
        str(item.get("name", "")) for item in output["sourceCorpora"] if isinstance(item, dict)
    }
    if output.get("sourceCorpus") and output["sourceCorpus"].get("name") not in existing_source_names:
        output["sourceCorpora"].append(output["sourceCorpus"])
    focus_metadata = {
        "name": SOURCE_NAME,
        "version": SOURCE_VERSION,
        "source": DATASET_PAGE,
        "downloadUrl": SOURCE_URL,
        "license": SOURCE_LICENSE,
        "licenseUrl": LICENSE_URL,
        "attribution": "Focus Recipes v1.0 (2026). Focus. https://focusathletics.app/en/recipes/dataset",
        "sourceLanguage": "es",
        "translationLanguage": "ar",
        "eligibleCount": len(adapted_records),
    }
    output["sourceCorpora"] = [
        item for item in output["sourceCorpora"]
        if not isinstance(item, dict) or item.get("name") != SOURCE_NAME
    ] + [focus_metadata]
    output["sourceAudit"] = output.get("sourceAudit") or {}
    output["sourceAudit"]["focusRecipes"] = audit
    output["schemaVersion"] = max(int(output.get("schemaVersion", 1)), 2)
    previous_translation = output.get("translation")
    translations = output.get("translations") or []
    if isinstance(previous_translation, dict) and previous_translation not in translations:
        translations.append(previous_translation)
    focus_translation = {
        "source": "focus-recipes",
        "provider": "Google Apps Script LanguageApp",
        "type": "machine",
        "direction": "Spanish to Arabic",
        "sourceLanguage": "es",
        "taxonomySourceLanguage": "en",
        "targetLanguage": "ar",
    }
    if focus_translation not in translations:
        translations.append(focus_translation)
    output["translations"] = translations
    output["translation"] = {
        "provider": "Google Apps Script LanguageApp",
        "type": "machine",
        "direction": "Polish and Spanish to Arabic",
        "sourceLanguages": ["pl", "es"],
        "taxonomySourceLanguage": "en",
        "targetLanguage": "ar",
    }
    output["sourceLanguage"] = "multiple"
    output["safetyPolicy"] = {
        "alcoholicIngredientsOrMethods": "exclude",
        "porkDerivedIngredients": "substitute_with_beef_lamb_or_olive_oil_when_suitable",
        "unresolvedPorkReferences": "exclude",
        "unspecifiedAnimalGelatin": "exclude",
    }
    output["sourceLanguages"] = sorted(set(output.get("sourceLanguages", [])) | {"pl", "es"})
    output["recipes"] = base_records + list(existing_focus.values())

    if not pending:
        output["recipes"] = base_records + list(existing_focus.values())
        write_output(args.output, output, expected_count)
        if not output["complete"]:
            raise PipelineError(
                f"Focus corpus is incomplete: {len(output['recipes'])}/{expected_count} recipes."
            )
        print(f"All {len(adapted_records)} eligible Focus recipes already have Arabic translations.")
        return 0

    taxonomy_ar = translate_taxonomy_terms(endpoint, token, adapted_records)
    print(f"Translated {len(taxonomy_ar)} unique cuisine/category/dietary labels into Arabic.")

    translated_this_run = 0
    hard_failures: list[str] = []
    for start in range(0, len(pending), args.batch_size):
        batch = pending[start:start + args.batch_size]
        request_batch = [build_translation_request(recipe) for recipe, _ in batch]
        response = post_batch(endpoint, token, request_batch)
        results = response.get("results") or []
        returned: set[str] = set()
        source_lookup = {
            "focus:" + str(recipe["id"]): (recipe, changes)
            for recipe, changes in batch
        }

        for item in results:
            if not isinstance(item, dict):
                continue
            recipe_id = str(item.get("id", ""))
            translation = item.get("translation")
            source_and_changes = source_lookup.get(recipe_id)
            if not source_and_changes or not isinstance(translation, dict):
                continue
            source, changes = source_and_changes
            existing_focus[recipe_id] = build_output_recipe(source, translation, changes, taxonomy_ar)
            returned.add(recipe_id)
            translated_this_run += 1

        for error in response.get("errors") or []:
            if isinstance(error, dict):
                hard_failures.append(f"{error.get('id', 'unknown')}: {error.get('error', 'unknown error')}")

        output["recipes"] = base_records + list(existing_focus.values())
        write_output(args.output, output, expected_count)
        print(
            f"Checkpoint: {len(existing_focus)}/{len(adapted_records)} Focus recipes translated; "
            f"{len(returned)} completed in this batch."
        )

        if response.get("quotaExceeded"):
            raise PipelineError("Google Apps Script translation quota was reached; the incomplete output was not committed.")
        if response.get("deferred"):
            raise PipelineError(
                "Apps Script reached its execution-time safety limit within a batch. "
                "The checkpoint is saved locally; reduce --batch-size and rerun."
            )
        if hard_failures:
            raise PipelineError("; ".join(hard_failures[:10]))
        if len(returned) != len(batch):
            missing = sorted(set(source_lookup) - returned)
            raise PipelineError("Translation endpoint omitted recipe results: " + ", ".join(missing[:10]))

    output["recipes"] = base_records + list(existing_focus.values())
    write_output(args.output, output, expected_count)
    if not output["complete"]:
        raise PipelineError(
            f"Catalogue remains incomplete: {output['translatedCount']}/{output['expectedCount']} recipes."
        )
    print(
        f"Completed: +{translated_this_run} Arabic Focus translations; "
        f"combined catalogue {output['translatedCount']}/{output['expectedCount']}."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PipelineError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
