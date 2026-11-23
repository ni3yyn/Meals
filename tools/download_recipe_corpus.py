#!/usr/bin/env python3
"""Download the pinned Open Recipe Corpus into ignored local test data.

The files are deliberately not committed to Midati. They are copied into
debug-only Android assets during local builds and never enter release APKs.
"""

from __future__ import annotations

# Requires Python 3.10 or newer.

import hashlib
import pathlib
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "local-data" / "open-recipe-corpus"
UPSTREAM = "https://raw.githubusercontent.com/Rippy1911/open-recipe-corpus/86102603bac07d79b94bc35cba5b50346798cc09"

FILES = {
    "data/recipes.jsonl": ("14e1e654c64c4dc1096c7e8e008c691d4dde7cd7", 3_574_712),
    "data/text/en.jsonl": ("95b0d5ed2770e833078e2a129b3b2a01feccb4de", 1_860_510),
    "data/text/pl.jsonl": ("64c90f4649b305e4a36a980d98ff78882064c2a2", 3_763_885),
    "data/sources.json": ("e0a3be2f8abfc8d881c60c36fdd7636dd6ea5229", 595),
}
DOCUMENTS = ("LICENSE-DATA.md", "ATTRIBUTION.md", "SOURCES.md")


def git_blob_sha1(content: bytes) -> str:
    header = f"blob {len(content)}\0".encode("ascii")
    return hashlib.sha1(header + content).hexdigest()


def download(path: str, expected_sha: str | None = None, expected_size: int | None = None) -> None:
    destination = DESTINATION / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    url = f"{UPSTREAM}/{path}"
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "Midati-local-recipe-corpus-test/1.0 (https://github.com/ni3yyn/Meals)"},
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            content = response.read()
    except (urllib.error.URLError, TimeoutError) as error:
        raise RuntimeError(f"Could not download {path}: {error}") from error

    if expected_size is not None and len(content) != expected_size:
        raise RuntimeError(
            f"Unexpected size for {path}: got {len(content)}, expected {expected_size}. "
            "The upstream corpus may have changed."
        )
    if expected_sha is not None:
        actual_sha = git_blob_sha1(content)
        if actual_sha != expected_sha:
            raise RuntimeError(
                f"Checksum mismatch for {path}: got {actual_sha}, expected {expected_sha}. "
                "The file was not written."
            )

    temporary = destination.with_suffix(destination.suffix + ".tmp")
    temporary.write_bytes(content)
    temporary.replace(destination)
    print(f"Downloaded {path}: {len(content):,} bytes")


def main() -> int:
    try:
        for path, (sha, size) in FILES.items():
            download(path, sha, size)
        for path in DOCUMENTS:
            download(path)

        print()
        print("Local recipe corpus is ready at:")
        print(f"  {DESTINATION}")
        print("Run ./gradlew assembleDebug to include it in the debug app.")
        print("The dataset is ignored by Git and is not included in release builds.")
        return 0
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
