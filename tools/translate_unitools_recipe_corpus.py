#!/usr/bin/env python3
"""Import UniTools World Recipes into Meals' Arabic catalogue.

The immutable upstream snapshot is screened before translation. Alcohol-containing
recipes are excluded; common pork products are adapted to beef/lamb/olive oil when
a practical substitution exists. Unresolved pork and unverified gelatin are excluded.
Existing Focus Arabic records are retained; the legacy Polish corpus is retired.
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
from typing import Any

try:
    import pycountry
except ImportError as error:  # pragma: no cover - checked in CI workflow
    raise SystemExit("Install pycountry before running this importer.") from error

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE_REPOSITORY = "https://github.com/farcrak/unitools-recipes"
SOURCE_REVISION = "2f554073f7e730b18d707fe1164d18308f6ec16c"
SOURCE_URL = (
    "https://raw.githubusercontent.com/farcrak/unitools-recipes/"
    + SOURCE_REVISION
    + "/unitools-recipes-v1.json"
)
DATASET_PAGE = "https://theunitools.com/en/data"
EXPECTED_SOURCE_COUNT = 501
SOURCE_NAME = "UniTools World Recipes"
SOURCE_VERSION = "2.0.0"
SOURCE_LICENSE = "CC BY-SA 4.0"
LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
OUTPUT_DEFAULT = ROOT / "data" / "recipes" / "ar" / "recipes.json"
MAX_BATCH_SIZE = 20


class PipelineError(RuntimeError):
    pass


ALCOHOL_PATTERNS = (
    r"\b(?:red|white|rose|rosé|cooking|rice|dessert|fortified)\s+wine\b",
    r"\bwine\b|\bvin(?:o|s)\b",
    r"\bwine\s+vinegar\b",
    r"\b(?:beer|lager|ale|stout|porter)\b",
    r"\bsake\b|\bmirin\b",
    r"\b(?:sherry|jerez|vermouth|port\s+wine)\b",
    r"\b(?:rum|ron|vodka|whisk(?:y|ey)|bourbon|brandy|cognac)\b",
    r"\b(?:tequila|mezcal|gin|absinthe|liqueur|liquor)\b",
    r"\b(?:champagne|prosecco|cava|sparkling\s+wine)\b",
    r"\b(?:marsala|madeira|kirsch|calvados|grappa|amaretto)\b",
    r"\b(?:sangria|sangría|cider\s+alcoholic)\b",
)

PORK_REPLACEMENTS = (
    (r"\bpork\s+tenderloins?\b", "beef tenderloin"),
    (r"\bpork\s+loins?\b", "beef loin"),
    (r"\bpork\s+chops?\b", "lamb chops"),
    (r"\bpork\s+shoulders?\b", "lamb shoulder"),
    (r"\bpork\s+bell(?:y|ies)\b", "beef brisket"),
    (r"\bpork\s+short\s+ribs?\b", "beef short ribs"),
    (r"\bpork\s+ribs?\b", "beef ribs"),
    (r"\bpork\s+(?:shanks?|hocks?|trotters?|feet)\b", "beef shank"),
    (r"\bham\s+hocks?\b", "beef shank"),
    (r"\b(?:ground|minced)\s+pork\b", "ground beef"),
    (r"\bpork\s+sausages?\b", "beef sausage"),
    (r"\bpork\s+fat\b|\bpork\s+drippings\b", "olive oil"),
    (r"\bpork\s+gelatin\b|\bpig\s+gelatin\b", "bovine gelatin"),
    (r"\bguanciale\b", "smoked beef cheek"),
    (r"\bpancetta\b", "smoked beef cubes"),
    (r"\bprosciutto\b", "smoked beef slices"),
    (r"\bjam[oó]n\s*(?:serrano|ib[eé]rico)?\b", "smoked beef slices"),
    (r"\b(?:speck|lardo)\b", "smoked beef slices"),
    (r"\bcanadian\s+bacon\b|\bpork\s+bacon\b", "smoked beef slices"),
    (r"\bturkey\s+bacon\b", "turkey bacon"),
    (r"\bchicken\s+bacon\b", "chicken bacon"),
    (r"\bbeef\s+bacon\b", "beef bacon"),
    (r"\bbacon\b", "beef bacon"),
    (r"\b(?:turkey|chicken|beef)\s+ham\b", lambda m: m.group(0)),
    (r"\bham\b", "beef ham"),
    (r"\b(?:beef|chicken|turkey)\s+chorizo\b", lambda m: m.group(0)),
    (r"\bchorizo\b", "beef chorizo"),
    (r"\bpepperoni\b", "beef pepperoni"),
    (r"\bmortadella\b", "beef mortadella"),
    (r"\bsalami\b", "beef salami"),
    (r"\b(?:blood\s+sausage|morcilla)\b", "beef blood sausage"),
    (r"\b(?:fuet|sobrasada)\b", "beef sausage"),
    (r"\bchicharr[oó]n\b|\bpork\s+rinds?\b", "crispy beef cracklings"),
    (r"\bporchetta\b", "beef roast"),
    (r"\b(?:pig|hog)\s+(?:feet|trotters|knuckles|skin)\b", "beef shank"),
    (r"\blard\b|\bmanteca\b", "olive oil"),
    (r"\bpork\b|\bpig(?:'s)?\s+meat\b|\bswine\b|\bhog\s+meat\b", "beef"),
)

NEGATIVE_PORK = re.compile(
    r"\b(?:no|without|free\s+of)\s+(?:any\s+)?pork\b|"
    r"\bpork[-\s]free\b|\bdoes\s+not\s+contain\s+pork\b",
    re.IGNORECASE,
)
SAFE_MEAT = re.compile(
    r"\b(?:beef|veal|lamb|mutton|turkey|chicken|duck|goose)\s+"
    r"(?:bacon|ham|sausage|chorizo|pepperoni|mortadella|salami)\b|"
    r"\b(?:vegetarian|vegan|plant[- ]based)\s+sausage\b|"
    r"\bbeef\s+blood\s+sausage\b|\bcrispy\s+beef\s+cracklings\b",
    re.IGNORECASE,
)
PORK_POSITIVE = re.compile(
    r"\b(?:pork|pig(?:'s)?|swine|hog|ham|bacon|pancetta|prosciutto|"
    r"guanciale|speck|lardo|chorizo|pepperoni|mortadella|salami|"
    r"blood\s+sausage|morcilla|fuet|sobrasada|chicharr[oó]n|porchetta|"
    r"lard|manteca|jam[oó]n|tocino|panceta|cerdo|puerco|cochino)\b",
    re.IGNORECASE,
)
GELATIN = re.compile(r"\bgelatin(?:e)?\b", re.IGNORECASE)
SAFE_GELATIN = re.compile(
    r"\b(?:bovine|beef|veal|fish|halal|agar(?:-agar)?|pectin|plant[- ]based|vegetable)\b",
    re.IGNORECASE,
)


def clean_text(value: Any) -> str:
    if isinstance(value, dict):
        return str(value.get("en") or "")
    if value is None:
        return ""
    return str(value)


def recipe_english_text(recipe: dict[str, Any]) -> list[str]:
    values = [
        clean_text(recipe.get("name")),
        clean_text(recipe.get("summary")),
    ]
    for ingredient in recipe.get("ingredients") or []:
        if isinstance(ingredient, dict):
            values.extend((clean_text(ingredient.get("name")), clean_text(ingredient.get("note"))))
    for step in recipe.get("steps") or []:
        if isinstance(step, dict):
            values.append(clean_text(step.get("text")))
        else:
            values.append(str(step))
    return [value for value in values if value]


def alcohol_match(recipe: dict[str, Any]) -> str | None:
    text = "\n".join(recipe_english_text(recipe))
    for pattern in ALCOHOL_PATTERNS:
        match = re.search(pattern, text, re.IGNORECASE)
        if match:
            return match.group(0)
    return None


def _replace_pork_text(text: str) -> tuple[str, list[dict[str, str]]]:
    protected: dict[str, str] = {}

    def protect(pattern: re.Pattern[str], value: str) -> None:
        nonlocal text
        def repl(match: re.Match[str]) -> str:
            token = f"MEALSSAFETYTOKEN{len(protected)}END"
            protected[token] = match.group(0)
            return token
        text = pattern.sub(repl, text)

    # A recipe explicitly stating that it is pork-free is not a pork recipe.
    protect(NEGATIVE_PORK, "")
    protect(SAFE_MEAT, "")

    changes: list[dict[str, str]] = []
    for pattern, replacement in PORK_REPLACEMENTS:
        if callable(replacement):
            text, count = re.subn(pattern, replacement, text, flags=re.IGNORECASE)
            if count:
                changes.append({"replacement": "preserved non-pork meat", "occurrences": str(count)})
            continue
        text, count = re.subn(pattern, replacement, text, flags=re.IGNORECASE)
        if count:
            changes.append({"replacement": replacement, "occurrences": str(count)})

    # Protect safe meat names created by pork substitutions before handling generic sausage.
    protect(SAFE_MEAT, "")

    # An unspecified sausage is commonly pork-based; use a neutral beef substitute.
    def sausage_replacement(match: re.Match[str]) -> str:
        return "beef sausages" if match.group(0).lower().endswith("s") else "beef sausage"
    text, sausage_count = re.subn(r"\bsausages?\b", sausage_replacement, text, flags=re.IGNORECASE)
    if sausage_count:
        changes.append({"replacement": "beef sausage", "occurrences": str(sausage_count)})

    for token, original in protected.items():
        text = text.replace(token, original)
    return text, changes


def adapt_recipe(recipe: dict[str, Any]) -> tuple[dict[str, Any] | None, list[dict[str, str]], str | None]:
    alcohol = alcohol_match(recipe)
    if alcohol:
        return None, [], f"alcohol ingredient or method reference: {alcohol}"

    # Unspecified animal gelatin has an unclear source and is not safe to assume bovine.
    for text in recipe_english_text(recipe):
        if GELATIN.search(text) and not SAFE_GELATIN.search(text):
            return None, [], "unverified gelatin source"

    adapted = copy.deepcopy(recipe)
    changes: list[dict[str, str]] = []

    def update_localized(container: Any, key: str) -> None:
        if isinstance(container, dict):
            english = container.get("en")
            if isinstance(english, str):
                updated, found = _replace_pork_text(english)
                container["en"] = updated
                changes.extend(found)

    update_localized(adapted.get("name"), "name")
    update_localized(adapted.get("summary"), "summary")
    for ingredient in adapted.get("ingredients") or []:
        if not isinstance(ingredient, dict):
            continue
        update_localized(ingredient.get("name"), "name")
        update_localized(ingredient.get("note"), "note")
    for step in adapted.get("steps") or []:
        if isinstance(step, dict):
            update_localized(step.get("text"), "text")

    leftovers = []
    for text in recipe_english_text(adapted):
        candidate = NEGATIVE_PORK.sub("", text)
        candidate = SAFE_MEAT.sub("", candidate)
        if PORK_POSITIVE.search(candidate):
            leftovers.append(text)
    if leftovers:
        return None, changes, "unresolved pork-derived product/reference"
    return adapted, changes, None


def humanize(value: str) -> str:
    return re.sub(r"\s+", " ", str(value).replace("_", " ").replace("-", " ")).strip()


def country_name(code: str) -> str:
    country = pycountry.countries.get(alpha_2=str(code).upper())
    return country.name if country else str(code)


def load_source() -> tuple[dict[str, Any], str]:
    request = urllib.request.Request(
        SOURCE_URL,
        headers={"Accept": "application/json", "User-Agent": "Meals-Unitools-Recipe-Import/1.0"},
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            raw = response.read()
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise PipelineError(f"Could not download pinned UniTools JSON: {error}") from error
    digest = hashlib.sha256(raw).hexdigest()
    try:
        dataset = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise PipelineError(f"UniTools source is not valid UTF-8 JSON: {error}") from error
    if not isinstance(dataset, dict):
        raise PipelineError("UniTools dataset root is not an object.")
    if dataset.get("name") != SOURCE_NAME or str(dataset.get("version")) != SOURCE_VERSION:
        raise PipelineError("UniTools dataset name/version changed; review before importing.")
    if dataset.get("license") != SOURCE_LICENSE:
        raise PipelineError("UniTools content licence changed; refusing unreviewed redistribution.")
    records = dataset.get("recipes")
    if not isinstance(records, list) or len(records) != EXPECTED_SOURCE_COUNT:
        raise PipelineError(
            f"Expected {EXPECTED_SOURCE_COUNT} UniTools recipes; received "
            f"{len(records) if isinstance(records, list) else 'no list'}."
        )
    slugs = [str(item.get("slug", "")) for item in records if isinstance(item, dict)]
    if len(slugs) != EXPECTED_SOURCE_COUNT or any(not slug for slug in slugs) or len(set(slugs)) != EXPECTED_SOURCE_COUNT:
        raise PipelineError("UniTools dataset contains missing or duplicate recipe slugs.")
    return dataset, digest


def collect_taxonomy(records: list[dict[str, Any]]) -> list[str]:
    terms: list[str] = []
    seen: set[str] = set()
    for recipe in records:
        candidates = [
            country_name(str(recipe.get("country") or "")),
            humanize(str(recipe.get("category") or "")),
            humanize(str(recipe.get("difficulty") or "")),
        ]
        candidates.extend(humanize(str(item)) for item in recipe.get("diets") or [])
        for candidate in candidates:
            key = candidate.strip()
            if key and key.casefold() not in seen:
                seen.add(key.casefold())
                terms.append(key)
    return terms


def post_batch(endpoint: str, token: str, batch: list[dict[str, Any]]) -> dict[str, Any]:
    payload = json.dumps({"token": token, "recipes": batch}, ensure_ascii=False, separators=(",", ":")).encode()
    request = urllib.request.Request(
        endpoint,
        data=payload,
        headers={"Content-Type": "application/json; charset=utf-8", "User-Agent": "Meals-UniTools-Arabic/1.0"},
        method="POST",
    )
    last_error: Exception | None = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=360) as response:
                result = json.loads(response.read().decode("utf-8", errors="replace"))
            if not isinstance(result, dict):
                raise PipelineError("Translation endpoint returned a non-object response.")
            if result.get("error") == "unauthorized":
                raise PipelineError("Google Apps Script token rejected; check repository secrets.")
            if result.get("error"):
                raise PipelineError(str(result["error"]))
            return result
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")[:500]
            last_error = PipelineError(f"Translation endpoint HTTP {error.code}: {detail}")
            if error.code not in (408, 425, 429, 500, 502, 503, 504):
                raise last_error from error
        except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as error:
            last_error = error
        if attempt < 3:
            time.sleep(min(2 ** attempt, 12))
    raise PipelineError(f"Translation endpoint failed after retries: {last_error}")


def translate_taxonomy(
    endpoint: str, token: str, terms: list[str]
) -> dict[str, str]:
    request = {
        "id": "unitools:taxonomy",
        "sourceLanguage": "en",
        "name": "",
        "summary": "",
        "category": "",
        "cuisine": "",
        "tags": terms,
        "categories": [],
        "dietary": [],
        "allergens": [],
        "ingredients": [],
        "steps": [],
    }
    response = post_batch(endpoint, token, [request])
    item = next(
        (entry for entry in response.get("results", []) if entry.get("id") == "unitools:taxonomy"),
        None,
    )
    translated = (item or {}).get("translation", {}).get("tags", [])
    if len(translated) != len(terms) or any(not str(value).strip() for value in translated):
        errors = response.get("errors") or []
        message = errors[0].get("error") if errors and isinstance(errors[0], dict) else "incomplete taxonomy response"
        raise PipelineError(f"Could not translate UniTools taxonomy labels: {message}")
    return dict(zip(terms, [str(value).strip() for value in translated], strict=True))


def translation_request(recipe: dict[str, Any]) -> dict[str, Any]:
    ingredients = recipe.get("ingredients") or []
    steps = recipe.get("steps") or []
    return {
        "id": "unitools:" + str(recipe["slug"]),
        "sourceLanguage": "en",
        "name": clean_text(recipe.get("name")),
        "summary": clean_text(recipe.get("summary")),
        "category": "",
        "cuisine": "",
        "tags": [],
        "categories": [],
        "dietary": [],
        "allergens": [],
        "ingredients": [
            {
                "name": clean_text(item.get("name")),
                "note": clean_text(item.get("note")),
            }
            for item in ingredients
        ],
        "steps": [
            clean_text(item.get("text")) if isinstance(item, dict) else str(item)
            for item in steps
        ],
    }


def output_recipe(
    source: dict[str, Any],
    translation: dict[str, Any],
    taxonomy: dict[str, str],
    pork_changes: list[dict[str, str]],
    dataset_page: str,
) -> dict[str, Any]:
    slug = str(source["slug"])
    raw_ingredients = source.get("ingredients") or []
    translated_ingredients = translation.get("ingredients") or []
    if len(translated_ingredients) != len(raw_ingredients):
        raise PipelineError(f"Ingredient translation count mismatch for {slug}.")
    raw_steps = source.get("steps") or []
    translated_steps = translation.get("steps") or []
    if len(translated_steps) != len(raw_steps):
        raise PipelineError(f"Step translation count mismatch for {slug}.")

    category = humanize(str(source.get("category") or ""))
    difficulty = humanize(str(source.get("difficulty") or ""))
    country_en = country_name(str(source.get("country") or ""))
    required = [country_en, category, difficulty] + [
        humanize(str(item)) for item in source.get("diets") or []
    ]
    missing = [value for value in required if value not in taxonomy]
    if missing:
        raise PipelineError(f"Missing Arabic taxonomy values for {slug}: {', '.join(missing)}")

    ingredients = []
    for raw, translated in zip(raw_ingredients, translated_ingredients, strict=True):
        ingredients.append({
            "id": str(raw.get("id") or ""),
            "name": str(translated.get("name") or clean_text(raw.get("name"))),
            "sourceNameEn": clean_text(raw.get("name")),
            "note": str(translated.get("note") or ""),
            "quantity": raw.get("quantity"),
            "unit": str(raw.get("unit") or ""),
            "scaling": str(raw.get("scaling") or "linear"),
            "sourceNoteEn": clean_text(raw.get("note")),
        })

    step_minutes = [
        (step.get("minutes") if isinstance(step, dict) else None)
        for step in raw_steps
    ]
    diets = [humanize(str(item)) for item in source.get("diets") or []]
    diets_ar = [taxonomy[item] for item in diets]
    categories = [category] if category else []
    categories_ar = [taxonomy[category]] if category else []
    photo = source.get("photo") if isinstance(source.get("photo"), dict) else {}
    nutrition = source.get("nutritionPerServing") or {}

    adaptation_text = (
        "Pork-derived products were automatically replaced with beef, lamb or olive-oil alternatives."
        if pork_changes else ""
    )
    adapted_source = copy.deepcopy(source)
    return {
        "id": "unitools:" + slug,
        "sourceId": slug,
        "name": str(translation.get("name") or ""),
        "summary": str(translation.get("summary") or ""),
        "nativeName": str(source.get("nativeName") or ""),
        "sourceNameEn": clean_text(source.get("name")),
        "sourceSummaryEn": clean_text(source.get("summary")),
        "stepsEn": [clean_text(step.get("text")) if isinstance(step, dict) else str(step) for step in raw_steps],
        "category": taxonomy.get(category, "وصفة"),
        "cuisine": taxonomy[country_en],
        "countryCode": str(source.get("country") or "").upper(),
        "difficulty": taxonomy[difficulty],
        "categories": categories,
        "categoriesAr": categories_ar,
        "servings": source.get("baseServings"),
        "prepMinutes": source.get("prepMinutes"),
        "cookMinutes": source.get("cookMinutes"),
        "ingredients": ingredients,
        "steps": [str(item) for item in translated_steps],
        "stepMinutes": step_minutes,
        "tags": diets_ar,
        "dietary": diets,
        "dietaryAr": diets_ar,
        "allergens": [],
        "nutrition": {
            "kcal": nutrition.get("calories"),
            "protein_g": nutrition.get("protein"),
            "carbs_g": nutrition.get("carbs"),
            "fat_g": nutrition.get("fat"),
            "basis": "per_serving",
            "estimated": True,
        },
        "kcal": nutrition.get("calories"),
        "protein_g": nutrition.get("protein"),
        "carbs_g": nutrition.get("carbs"),
        "fat_g": nutrition.get("fat"),
        "image": str(photo.get("url") or ""),
        "imageAuthor": str(photo.get("author") or ""),
        "imageLicense": str(photo.get("license") or ""),
        "photo": photo or None,
        "sourceRecord": adapted_source,
        "sourceLanguage": "en",
        "sourceLanguages": ["en", "ru"],
        "machineTranslated": True,
        "translatedFrom": "unitools:" + slug,
        "porkAdapted": bool(pork_changes),
        "adaptationChanges": pork_changes,
        "adaptations": [adaptation_text] if adaptation_text else [],
        "provenance": {
            "source": "unitools-world-recipes",
            "title": SOURCE_NAME + ": " + clean_text(source.get("name")),
            "url": dataset_page,
            "dataset_url": dataset_page,
            "attribution": "UniTools — theunitools.com. CC BY-SA 4.0.",
            "source_license": SOURCE_LICENSE,
            "license": SOURCE_LICENSE,
            "license_url": LICENSE_URL,
            "adapted": bool(pork_changes),
            "changes": (
                "Machine-translated from English into Arabic. Source recipe structure, "
                "stable ingredient IDs, scaling rules, step durations, per-serving nutrition, "
                "country identity and individual photo attribution/licence are retained. "
                + adaptation_text
            ).strip(),
        },
        "license": SOURCE_LICENSE,
        "licenseUrl": LICENSE_URL,
        "sourceUrl": dataset_page,
        "sourceRevisionUrl": SOURCE_REPOSITORY + "/tree/" + SOURCE_REVISION,
        "sourceHistoryUrl": SOURCE_REPOSITORY + "/commits/" + SOURCE_REVISION,
        "sourceCredit": "UniTools — theunitools.com",
        "sourceLicense": SOURCE_LICENSE,
        "changes": (
            "Machine-translated from English into Arabic; the original source structure and "
            "individual photo attribution/licence are retained. " + adaptation_text
        ).strip(),
    }


def read_output(path: pathlib.Path) -> dict[str, Any]:
    if not path.is_file():
        raise PipelineError("Existing Arabic catalogue not found; refusing to replace unknown user data.")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise PipelineError(f"Could not read existing Arabic catalogue: {error}") from error
    if not isinstance(value, dict) or not isinstance(value.get("recipes"), list):
        raise PipelineError("Existing Arabic catalogue has an unexpected shape.")
    return value


def write_output(path: pathlib.Path, output: dict[str, Any], expected: int) -> None:
    recipes = output.get("recipes") or []
    recipes.sort(key=lambda item: str(item.get("id", "")))
    output["translatedCount"] = len(recipes)
    output["expectedCount"] = expected
    output["complete"] = len(recipes) == expected
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
        raise PipelineError("Set GOOGLE_APPS_SCRIPT_URL and GOOGLE_APPS_SCRIPT_TOKEN in GitHub Actions secrets.")
    if not endpoint.startswith("https://script.google.com/macros/s/") or not endpoint.endswith("/exec"):
        raise PipelineError("GOOGLE_APPS_SCRIPT_URL must be the deployed Apps Script /exec URL.")
    if not 1 <= args.batch_size <= MAX_BATCH_SIZE:
        raise PipelineError(f"--batch-size must be 1..{MAX_BATCH_SIZE}.")
    if args.max_recipes < 1:
        raise PipelineError("--max-recipes must be at least 1.")

    dataset, source_digest = load_source()
    eligible: list[tuple[dict[str, Any], list[dict[str, str]]]] = []
    excluded_alcohol: list[dict[str, str]] = []
    excluded_pork: list[dict[str, str]] = []
    pork_adapted = 0
    for raw in dataset["recipes"]:
        adapted, changes, rejection = adapt_recipe(raw)
        if rejection:
            audit = {
                "id": str(raw.get("slug") or ""),
                "title": clean_text(raw.get("name")),
                "reason": rejection,
            }
            (excluded_alcohol if rejection.startswith("alcohol") else excluded_pork).append(audit)
            continue
        if changes:
            pork_adapted += 1
        eligible.append((adapted, changes))

    if not eligible:
        raise PipelineError("Safety checks excluded every UniTools recipe; catalogue was not changed.")

    output = read_output(args.output)
    focus_records = [
        item for item in output["recipes"]
        if str(item.get("id", "")).startswith("focus:")
    ]
    previous_audit = (output.get("sourceAudit") or {}).get("unitoolsRecipes") or {}
    if previous_audit.get("sourceSha256") not in (None, source_digest):
        raise PipelineError("Pinned UniTools source bytes changed; review the dataset diff before re-importing.")

    existing = {
        str(item.get("id")): item
        for item in output["recipes"]
        if str(item.get("id", "")).startswith("unitools:")
    }
    valid_ids = {"unitools:" + str(recipe["slug"]) for recipe, _ in eligible}
    stale_ids = set(existing) - valid_ids
    existing = {key: value for key, value in existing.items() if key in valid_ids}

    taxonomy_terms = collect_taxonomy([recipe for recipe, _ in eligible])
    taxonomy = translate_taxonomy(endpoint, token, taxonomy_terms)
    pending = [
        (recipe, changes)
        for recipe, changes in eligible
        if "unitools:" + str(recipe["slug"]) not in existing
    ][: args.max_recipes]
    translated_count = 0

    def save_checkpoint() -> None:
        merged = focus_records + list(existing.values())
        output["recipes"] = merged
        output["sourceCorpus"] = {
            "name": "Meals Arabic Recipe Catalogue",
            "sources": [
                {
                    "name": SOURCE_NAME,
                    "repository": SOURCE_REPOSITORY,
                    "revision": SOURCE_REVISION,
                    "license": SOURCE_LICENSE,
                    "licenseUrl": LICENSE_URL,
                    "attribution": "UniTools — theunitools.com",
                },
                {
                    "name": "Focus Recipes v1.0",
                    "license": "CC BY 4.0",
                    "licenseUrl": "https://creativecommons.org/licenses/by/4.0/",
                },
            ],
        }
        output["sourceAudit"] = dict(output.get("sourceAudit") or {})
        output["sourceAudit"]["unitoolsRecipes"] = {
            "sourceVersion": SOURCE_VERSION,
            "sourceRevision": SOURCE_REVISION,
            "sourceSha256": source_digest,
            "sourceCount": EXPECTED_SOURCE_COUNT,
            "eligibleCount": len(eligible),
            "importedCount": len(existing),
            "excludedAlcoholCount": len(excluded_alcohol),
            "excludedAlcohol": excluded_alcohol,
            "excludedPorkCount": len(excluded_pork),
            "excludedPork": excluded_pork,
            "porkAdaptedCount": pork_adapted,
            "photoCount": sum(bool((recipe.get("photo") or {}).get("url")) for recipe, _ in eligible),
            "missingPhotoCount": sum(not bool((recipe.get("photo") or {}).get("url")) for recipe, _ in eligible),
            "staleRemovedCount": len(stale_ids),
        }
        output["translation"] = {
            "provider": "Google Apps Script LanguageApp",
            "type": "machine",
            "direction": "English and Spanish to Arabic",
        }
        expected = len(focus_records) + len(eligible)
        write_output(args.output, output, expected)

    save_checkpoint()
    for start in range(0, len(pending), args.batch_size):
        batch = pending[start : start + args.batch_size]
        response = post_batch(endpoint, token, [translation_request(recipe) for recipe, _ in batch])
        results = {
            str(item.get("id", "")): item.get("translation")
            for item in response.get("results") or []
            if isinstance(item, dict) and isinstance(item.get("translation"), dict)
        }
        errors = response.get("errors") or []
        for recipe, changes in batch:
            recipe_id = "unitools:" + str(recipe["slug"])
            translated = results.get(recipe_id)
            if translated is None:
                detail = next(
                    (
                        item.get("error", "translation result missing")
                        for item in errors
                        if isinstance(item, dict) and item.get("id") == recipe_id
                    ),
                    "translation result missing",
                )
                save_checkpoint()
                raise PipelineError(f"Failed to translate {recipe_id}: {detail}")
            existing[recipe_id] = output_recipe(recipe, translated, taxonomy, changes, DATASET_PAGE)
            translated_count += 1
        save_checkpoint()
        print(
            f"Translated {translated_count}/{len(pending)} new recipes in this run; "
            f"{len(existing)}/{len(eligible)} UniTools recipes available."
        )

    # Preserve only the new source set plus the existing, independently licensed Focus set.
    output["recipes"] = focus_records + list(existing.values())
    expected = len(focus_records) + len(eligible)
    write_output(args.output, output, expected)
    if not output["complete"]:
        raise PipelineError(
            f"UniTools import is incomplete ({output['translatedCount']}/{expected}); "
            "checkpoint saved and can be resumed."
        )

    ids = [str(item.get("id", "")) for item in output["recipes"]]
    if len(ids) != len(set(ids)):
        raise PipelineError("Combined catalogue contains duplicate IDs.")
    if any(not item.startswith(("focus:", "unitools:")) for item in ids):
        raise PipelineError("Legacy Polish recipe IDs remain in the output.")
    print(
        "Validated complete Arabic catalogue: "
        f"{len(focus_records)} Focus + {len(existing)} UniTools; "
        f"{len(excluded_alcohol)} alcohol exclusions; "
        f"{len(excluded_pork)} unresolved-pork exclusions; "
        f"{pork_adapted} pork adaptations."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PipelineError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
