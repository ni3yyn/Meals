#!/usr/bin/env python3
"""Resumable food-name translation through Midati's existing Apps Script bridge.

Only missing Arabic, English and French labels are requested. Each batch uses
one LanguageApp call, and successful batches are atomically checkpointed to a
UUID-keyed sidecar so reruns never need to repeat completed translations.
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
from collections import defaultdict
from datetime import datetime, timezone
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
CATALOGUE_DEFAULT = ROOT / "data" / "products" / "catalogue.json"
OUTPUT_DEFAULT = ROOT / "data" / "products" / "translations.json"
PROVIDER = "Google Apps Script LanguageApp"
SUPPORTED_LANGUAGES = {"ar", "en", "fr"}
SOURCE_PREFERENCES = {
    "ar": ("en", "fr"),
    "fr": ("en", "ar"),
    "en": ("ar", "fr"),
}


class PipelineError(RuntimeError):
    pass


def load_json(path: pathlib.Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise PipelineError(f"Could not read JSON file {path}: {error}") from error
    if not isinstance(value, dict):
        raise PipelineError(f"Expected a JSON object in {path}.")
    return value


def normalize_source(value: Any) -> str:
    return " ".join(str(value or "").strip().casefold().split())


def new_translation_data() -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "provider": PROVIDER,
        "languages": ["ar", "en", "fr"],
        "generatedAt": None,
        "translationCount": 0,
        "translations": {},
    }


def read_translation_data(path: pathlib.Path) -> dict[str, Any]:
    if not path.is_file():
        return new_translation_data()
    value = load_json(path)
    if not isinstance(value.get("translations"), dict):
        raise PipelineError("Translation sidecar must contain a 'translations' object.")
    value.setdefault("schemaVersion", 1)
    value["provider"] = PROVIDER
    value.setdefault("languages", ["ar", "en", "fr"])
    return value


def build_translation_tasks(
    catalogue: dict[str, Any],
    translation_data: dict[str, Any],
    target_languages: tuple[str, ...] = ("ar", "fr", "en"),
) -> tuple[list[dict[str, str]], list[str]]:
    """Build tasks for missing names, reusing only overrides bound to same source."""
    products = catalogue.get("products")
    if not isinstance(products, list):
        raise PipelineError("Food catalogue has no products array.")

    overrides = translation_data.get("translations") or {}
    tasks: list[dict[str, str]] = []
    without_source: list[str] = []

    for product in products:
        if not isinstance(product, dict):
            continue
        product_id = str(product.get("id") or "").strip()
        names = product.get("names") if isinstance(product.get("names"), dict) else {}
        if not product_id:
            raise PipelineError("A catalogue product is missing its stable UUID.")

        for target_language in target_languages:
            if target_language not in SUPPORTED_LANGUAGES:
                raise PipelineError(f"Unsupported target language: {target_language}")
            if str(names.get(target_language) or "").strip():
                continue

            source_language = ""
            source_text = ""
            for candidate in SOURCE_PREFERENCES[target_language]:
                candidate_text = str(names.get(candidate) or "").strip()
                if candidate_text:
                    source_language, source_text = candidate, candidate_text
                    break

            if not source_language or not source_text:
                without_source.append(product_id)
                continue

            language_overrides = overrides.get(product_id) or {}
            previous = language_overrides.get(target_language) or {}
            previous_text = str(previous.get("text") or "").strip()
            previous_language = str(previous.get("sourceLanguage") or "").strip().lower()
            if (
                previous_text
                and previous_language == source_language
                and normalize_source(previous.get("sourceText")) == normalize_source(source_text)
            ):
                # A saved translation might not have been merged by the catalogue
                # build yet. Reuse it rather than consuming another LanguageApp call.
                continue

            tasks.append({
                "id": product_id,
                "sourceLanguage": source_language,
                "targetLanguage": target_language,
                "sourceText": source_text,
            })

    tasks.sort(key=lambda item: (
        item["targetLanguage"],
        item["sourceLanguage"],
        item["id"],
    ))
    return tasks, sorted(set(without_source))


def write_translation_data(path: pathlib.Path, value: dict[str, Any]) -> None:
    translations = value.get("translations") or {}
    value["translationCount"] = sum(
        1
        for languages in translations.values()
        if isinstance(languages, dict)
        for entry in languages.values()
        if isinstance(entry, dict) and str(entry.get("text") or "").strip()
    )
    value["generatedAt"] = datetime.now(timezone.utc).isoformat()
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def post_batch(
    endpoint: str,
    token: str,
    source_language: str,
    target_language: str,
    items: list[dict[str, str]],
    timeout_seconds: int = 360,
) -> dict[str, Any]:
    payload = json.dumps(
        {
            "token": token,
            "kind": "food_items",
            "sourceLanguage": source_language,
            "targetLanguage": target_language,
            "items": [{"id": item["id"], "text": item["sourceText"]} for item in items],
        },
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=payload,
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "User-Agent": "Midati-Food-Name-Translation-Pipeline/1.0",
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
                    "Apps Script did not return JSON. Confirm that the deployed "
                    f"web app is updated. Response: {preview}"
                ) from error
            if not isinstance(result, dict):
                raise PipelineError("Apps Script returned an unexpected response.")
            if result.get("quotaExceeded"):
                return result
            if result.get("error"):
                if result.get("error") == "unauthorized":
                    raise PipelineError(
                        "Apps Script rejected the token. Check the existing "
                        "GOOGLE_APPS_SCRIPT_URL and GOOGLE_APPS_SCRIPT_TOKEN secrets."
                    )
                raise PipelineError(
                    f"Apps Script error {result.get('error')}: "
                    f"{str(result.get('message') or '')[:400]}"
                )
            if not isinstance(result.get("results"), list):
                raise PipelineError("Apps Script response is missing its results array.")
            return result
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            last_error = PipelineError(f"Apps Script HTTP {error.code}: {body[:500]}")
            if error.code not in (408, 425, 429, 500, 502, 503, 504):
                raise last_error from error
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            last_error = error

        if attempt < 3:
            time.sleep(min(2 ** attempt, 12))

    raise PipelineError(f"Apps Script request failed after retries: {last_error}")


def translate_catalogue(
    catalogue_path: pathlib.Path,
    output_path: pathlib.Path,
    endpoint: str,
    token: str,
    max_translations: int,
    batch_size: int,
    target_languages: tuple[str, ...],
) -> tuple[int, bool]:
    catalogue = load_json(catalogue_path)
    translation_data = read_translation_data(output_path)
    tasks, without_source = build_translation_tasks(
        catalogue, translation_data, target_languages
    )
    selected = tasks[:max_translations]

    print(
        f"Food items: {len(catalogue.get('products') or [])}; "
        f"saved language names: {translation_data.get('translationCount', 0)}; "
        f"pending translations: {len(tasks)}; "
        f"selected this run: {len(selected)}; "
        f"items with no source label: {len(without_source)}."
    )
    if without_source:
        print(
            "No available source name for UUIDs: " + ", ".join(without_source[:20]),
            file=sys.stderr,
        )
    if not selected:
        print("No missing food-item names need translation.")
        return 0, False

    grouped: dict[tuple[str, str], list[dict[str, str]]] = defaultdict(list)
    for task in selected:
        grouped[(task["sourceLanguage"], task["targetLanguage"])].append(task)

    completed = 0
    for source_language, target_language in sorted(grouped):
        group = grouped[(source_language, target_language)]
        for start in range(0, len(group), batch_size):
            batch = group[start : start + batch_size]
            response = post_batch(
                endpoint, token, source_language, target_language, batch
            )
            if response.get("quotaExceeded"):
                write_translation_data(output_path, translation_data)
                print(
                    "Google Apps Script translation quota was reached. "
                    "Previous batches were saved; the next run can resume safely."
                )
                return completed, True

            expected = {item["id"]: item for item in batch}
            returned: set[str] = set()
            for result in response.get("results") or []:
                if not isinstance(result, dict):
                    continue
                item_id = str(result.get("id") or "")
                item = expected.get(item_id)
                translated_text = str(result.get("translatedText") or "").strip()
                if item is None or not translated_text:
                    continue
                translation_data.setdefault("translations", {}).setdefault(item_id, {})[
                    target_language
                ] = {
                    "text": translated_text,
                    "sourceLanguage": source_language,
                    "sourceText": item["sourceText"],
                    "provider": PROVIDER,
                    "generatedAt": datetime.now(timezone.utc).isoformat(),
                }
                returned.add(item_id)
                completed += 1

            missing = sorted(set(expected) - returned)
            if missing:
                write_translation_data(output_path, translation_data)
                raise PipelineError(
                    "Apps Script did not return all requested names. Missing UUIDs: "
                    + ", ".join(missing[:10])
                )

            write_translation_data(output_path, translation_data)
            print(
                f"Checkpoint: +{len(returned)} translated names; "
                f"{completed}/{len(selected)} completed this run."
            )

    return completed, False


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalogue", type=pathlib.Path, default=CATALOGUE_DEFAULT)
    parser.add_argument("--output", type=pathlib.Path, default=OUTPUT_DEFAULT)
    parser.add_argument("--batch-size", type=int, default=20)
    parser.add_argument("--max-translations", type=int, default=6000)
    parser.add_argument("--languages", default="ar,fr,en")
    args = parser.parse_args()

    endpoint = os.environ.get("GOOGLE_APPS_SCRIPT_URL", "").strip()
    token = os.environ.get("GOOGLE_APPS_SCRIPT_TOKEN", "").strip()
    if not endpoint or not token:
        raise PipelineError(
            "Set the existing GOOGLE_APPS_SCRIPT_URL and GOOGLE_APPS_SCRIPT_TOKEN secrets."
        )
    if (
        not endpoint.startswith("https://script.google.com/macros/s/")
        or not endpoint.endswith("/exec")
    ):
        raise PipelineError("GOOGLE_APPS_SCRIPT_URL must be the deployed Apps Script /exec URL.")
    if not args.catalogue.is_file():
        raise PipelineError(f"Food catalogue not found: {args.catalogue}")
    if not 1 <= args.batch_size <= 20:
        raise PipelineError("--batch-size must be between 1 and 20.")
    if args.max_translations < 1:
        raise PipelineError("--max-translations must be at least 1.")

    languages = tuple(dict.fromkeys(
        language.strip().lower()
        for language in args.languages.split(",")
        if language.strip()
    ))
    if not languages or any(language not in SUPPORTED_LANGUAGES for language in languages):
        raise PipelineError("--languages must contain one or more of ar,en,fr.")

    completed, quota_reached = translate_catalogue(
        args.catalogue,
        args.output,
        endpoint,
        token,
        args.max_translations,
        args.batch_size,
        languages,
    )
    print(f"Finished this run: {completed} new language names.")
    return 0 if quota_reached or completed >= 0 else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PipelineError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
