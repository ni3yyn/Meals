#!/usr/bin/env python3
"""Resumable Polish-to-Arabic translation pipeline for the Midati recipe corpus.

The provider is a small Google Apps Script web app that calls LanguageApp once
per recipe. Successful records are checkpointed to one aggregate JSON file after
every batch, so reruns resume without paying the translation cost again.
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
CORPUS_REVISION = "86102603bac07d79b94bc35cba5b50346798cc09"
CORPUS_REPOSITORY = "https://github.com/Rippy1911/open-recipe-corpus"
OUTPUT_DEFAULT = ROOT / "data" / "recipes" / "ar" / "recipes.json"
LICENSE_NAME = "CC BY-SA 4.0"
LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"


class PipelineError(RuntimeError):
    pass


def load_jsonl(path: pathlib.Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as error:
                raise PipelineError(f"Invalid JSON in {path}:{line_number}: {error}") from error
            if isinstance(value, dict):
                records.append(value)
    return records


def read_output(path: pathlib.Path) -> dict[str, Any]:
    if not path.is_file():
        return {
            "schemaVersion": 1,
            "language": "ar",
            "sourceLanguage": "pl",
            "targetLanguage": "ar",
            "sourceCorpus": {
                "name": "Open Recipe Corpus",
                "repository": CORPUS_REPOSITORY,
                "revision": CORPUS_REVISION,
                "license": LICENSE_NAME,
                "licenseUrl": LICENSE_URL,
                "attribution": (
                    "Adapted from Open Recipe Corpus (Rippy1911), "
                    "licensed under CC BY-SA 4.0. See recipe-level provenance."
                ),
            },
            "translation": {
                "provider": "Google Apps Script LanguageApp",
                "type": "machine",
                "direction": "Polish to Arabic",
            },
            "generatedAt": None,
            "complete": False,
            "translatedCount": 0,
            "expectedCount": 0,
            "recipes": [],
            "failedRecipes": [],
        }

    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise PipelineError(f"Could not read existing output {path}: {error}") from error
    if not isinstance(value, dict) or not isinstance(value.get("recipes"), list):
        raise PipelineError(f"Existing output has an unexpected format: {path}")
    return value


def make_request_record(
    metadata: dict[str, Any], text_record: dict[str, Any]
) -> dict[str, Any]:
    return {
        "id": str(text_record.get("id", "")),
        "sourceLanguage": "pl",
        "name": text_record.get("name") or "",
        "summary": text_record.get("summary") or "",
        "category": metadata.get("category") or "",
        "cuisine": metadata.get("cuisine") or "",
        "ingredients": text_record.get("ingredients") or [],
        "steps": text_record.get("steps") or [],
        "tags": metadata.get("tags") or [],
        "allergens": metadata.get("allergens") or [],
    }


def build_output_recipe(
    metadata: dict[str, Any],
    source: dict[str, Any],
    translated: dict[str, Any],
) -> dict[str, Any]:
    source_ingredients = source.get("ingredients") or []
    amounts = metadata.get("ingredients") or []
    translated_ingredients = translated.get("ingredients") or []
    ingredients: list[dict[str, Any]] = []

    for index, item in enumerate(translated_ingredients):
        item = item if isinstance(item, dict) else {}
        amount = amounts[index] if index < len(amounts) and isinstance(amounts[index], dict) else {}
        quantity = amount.get("qty")
        unit = amount.get("unit")
        ingredients.append(
            {
                "name": item.get("name") or "",
                "note": item.get("note") or "",
                "quantity": quantity,
                "unit": unit,
            }
        )

    times = metadata.get("time_min") or {}
    provenance = metadata.get("provenance") or {}
    return {
        "id": str(source.get("id", "")),
        "name": translated.get("name") or "",
        "summary": translated.get("summary") or "",
        "category": translated.get("category") or "",
        "cuisine": translated.get("cuisine") or "",
        "servings": metadata.get("servings"),
        "prepMinutes": times.get("prep"),
        "cookMinutes": times.get("cook"),
        "ingredients": ingredients,
        "steps": translated.get("steps") or [],
        "tags": translated.get("tags") or [],
        "allergens": translated.get("allergens") or [],
        "sourceLanguage": "pl",
        "machineTranslated": True,
        "translatedFrom": str(source.get("id", "")),
        "provenance": provenance,
        "license": provenance.get("license") or LICENSE_NAME,
        "licenseUrl": provenance.get("license_url") or LICENSE_URL,
        "sourceUrl": provenance.get("url") or "",
        "sourceRevisionUrl": provenance.get("revision_url") or "",
        "sourceHistoryUrl": provenance.get("history_url") or "",
        "sourceCredit": provenance.get("credit") or provenance.get("attribution") or "",
        "sourceLicense": provenance.get("source_license") or "",
        "changes": (
            "Machine-translated from the Polish corpus text into Arabic. "
            "Recipe-level attribution and source links are retained."
        ),
    }


def write_output(path: pathlib.Path, output: dict[str, Any], expected_count: int) -> None:
    recipes = output.get("recipes") or []
    recipes.sort(key=lambda item: str(item.get("id", "")))
    output["translatedCount"] = len(recipes)
    output["expectedCount"] = expected_count
    output["complete"] = len(recipes) >= expected_count
    output["generatedAt"] = datetime.now(timezone.utc).isoformat()
    path.parent.mkdir(parents=True, exist_ok=True)

    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(output, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def post_batch(
    endpoint: str,
    token: str,
    batch: list[dict[str, Any]],
    timeout_seconds: int = 360,
) -> dict[str, Any]:
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
            "User-Agent": "Midati-Recipe-Translation-Pipeline/1.0",
        },
        method="POST",
    )

    last_error: Exception | None = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
                raw = response.read().decode("utf-8", errors="replace")
            try:
                result = json.loads(raw)
            except json.JSONDecodeError as error:
                preview = raw[:300].replace("\n", " ")
                raise PipelineError(
                    "Translation endpoint did not return JSON. Check that the Apps "
                    f"Script is deployed as an anonymous web app. Response: {preview}"
                ) from error
            if not isinstance(result, dict):
                raise PipelineError("Translation endpoint returned an unexpected response.")
            if result.get("error") == "unauthorized":
                raise PipelineError(
                    "The Apps Script token was rejected. Check the two GitHub Actions secrets."
                )
            if result.get("error"):
                raise PipelineError(str(result["error"]))
            return result
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            last_error = PipelineError(f"Translation endpoint HTTP {error.code}: {body[:500]}")
            if error.code not in (408, 425, 429, 500, 502, 503, 504):
                raise last_error from error
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            last_error = error

        if attempt < 3:
            time.sleep(min(2 ** attempt, 12))

    raise PipelineError(f"Translation endpoint request failed after retries: {last_error}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--metadata",
        type=pathlib.Path,
        default=ROOT / "local-data" / "open-recipe-corpus" / "data" / "recipes.jsonl",
    )
    parser.add_argument(
        "--source-text",
        type=pathlib.Path,
        default=ROOT / "local-data" / "open-recipe-corpus" / "data" / "text" / "pl.jsonl",
    )
    parser.add_argument("--output", type=pathlib.Path, default=OUTPUT_DEFAULT)
    parser.add_argument("--batch-size", type=int, default=10)
    parser.add_argument("--max-recipes", type=int, default=4000)
    parser.add_argument("--force", action="store_true", help="Re-translate existing records.")
    args = parser.parse_args()

    endpoint = os.environ.get("GOOGLE_APPS_SCRIPT_URL", "").strip()
    token = os.environ.get("GOOGLE_APPS_SCRIPT_TOKEN", "").strip()
    if not endpoint or not token:
        raise PipelineError(
            "Set GOOGLE_APPS_SCRIPT_URL and GOOGLE_APPS_SCRIPT_TOKEN in the environment."
        )
    if not endpoint.startswith("https://script.google.com/macros/s/") or not endpoint.endswith("/exec"):
        raise PipelineError("GOOGLE_APPS_SCRIPT_URL must be the deployed Apps Script /exec URL.")
    if not 1 <= args.batch_size <= 20:
        raise PipelineError("--batch-size must be between 1 and 20.")
    if args.max_recipes < 1:
        raise PipelineError("--max-recipes must be at least 1.")
    if not args.metadata.is_file() or not args.source_text.is_file():
        raise PipelineError(
            "The corpus files are missing. Run python tools/download_recipe_corpus.py first."
        )

    metadata_by_id = {
        str(record.get("id", "")): record
        for record in load_jsonl(args.metadata)
        if record.get("id") is not None
    }
    source_records = [
        record
        for record in load_jsonl(args.source_text)
        if str(record.get("id", "")) in metadata_by_id
    ]
    # Keep one canonical source record per recipe ID.
    source_by_id: dict[str, dict[str, Any]] = {}
    for record in source_records:
        source_by_id[str(record.get("id", ""))] = record
    source_records = list(source_by_id.values())
    source_records.sort(key=lambda item: str(item.get("id", "")))

    if not source_records:
        raise PipelineError("No Polish recipe text records matched the recipe metadata.")

    output = read_output(args.output)
    existing = {
        str(recipe.get("id", "")): recipe
        for recipe in output.get("recipes", [])
        if isinstance(recipe, dict) and recipe.get("id") is not None
    }
    failures = {
        str(item.get("id", "")): item
        for item in output.get("failedRecipes", [])
        if isinstance(item, dict) and item.get("id") is not None
    }
    if args.force:
        existing.clear()

    pending = [
        record for record in source_records
        if str(record.get("id", "")) not in existing
    ][: args.max_recipes]

    output["recipes"] = list(existing.values())
    output["failedRecipes"] = list(failures.values())
    print(
        f"Corpus: {len(source_records)} unique Polish recipes; "
        f"already translated: {len(existing)}; pending this run: {len(pending)}."
    )

    if not pending:
        write_output(args.output, output, len(source_records))
        print(f"Nothing to translate. Output is complete: {output['complete']}.")
        return 0

    translated_count = 0
    quota_reached = False
    hard_failures = 0

    for start in range(0, len(pending), args.batch_size):
        batch_records = pending[start : start + args.batch_size]
        request_batch = [
            make_request_record(metadata_by_id[str(record["id"])], record)
            for record in batch_records
        ]
        response = post_batch(endpoint, token, request_batch)
        results = response.get("results") or []
        returned_ids: set[str] = set()

        for result in results:
            if not isinstance(result, dict):
                continue
            recipe_id = str(result.get("id", ""))
            translation = result.get("translation")
            source = source_by_id.get(recipe_id)
            metadata = metadata_by_id.get(recipe_id)
            if not recipe_id or not isinstance(translation, dict) or source is None or metadata is None:
                continue
            existing[recipe_id] = build_output_recipe(metadata, source, translation)
            failures.pop(recipe_id, None)
            returned_ids.add(recipe_id)
            translated_count += 1

        batch_errors = response.get("errors") or []
        for error in batch_errors:
            if not isinstance(error, dict):
                continue
            recipe_id = str(error.get("id", ""))
            if recipe_id:
                failures[recipe_id] = {
                    "id": recipe_id,
                    "error": str(error.get("error") or "Unknown translation error")[:500],
                }
                if not error.get("quotaExceeded"):
                    hard_failures += 1

        output["recipes"] = list(existing.values())
        output["failedRecipes"] = list(failures.values())
        write_output(args.output, output, len(source_records))

        print(
            f"Checkpoint: {len(existing)}/{len(source_records)} translated; "
            f"{len(returned_ids)} completed in this batch."
        )

        if response.get("quotaExceeded"):
            quota_reached = True
            print("Google Apps Script daily translation quota reached; progress was saved.")
            break
        if response.get("deferred"):
            print("Apps Script execution time safety limit reached; progress was saved.")
            continue
        if hard_failures:
            # Preserve successful work and fail visibly rather than silently
            # presenting an incomplete machine-translated corpus as finished.
            print(f"Encountered {hard_failures} recipe-level errors in this run.", file=sys.stderr)
            break

    output["recipes"] = list(existing.values())
    output["failedRecipes"] = list(failures.values())
    write_output(args.output, output, len(source_records))

    print(
        f"Finished this run: +{translated_count}; total "
        f"{len(existing)}/{len(source_records)}; output: {args.output}"
    )
    if quota_reached:
        print("This run paused cleanly. The scheduled workflow will resume tomorrow.")
        return 0
    if hard_failures:
        return 1
    if output["complete"]:
        print("All unique Polish recipes have an Arabic translation.")
        return 0
    print("More recipes remain; run the workflow again to continue.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PipelineError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
