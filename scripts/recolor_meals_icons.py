#!/usr/bin/env python3
"""Recolour violet accents in generated Meals soft-3D icons.

The hue and saturation are adjusted while preserving per-pixel value, alpha,
highlights and shadows. Only the five known violet assets are processed.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DRAWABLES = ROOT / "shared/src/commonMain/composeResources/drawable"

# Hue values are Pillow HSV's 0..255 scale. Green accents are deliberately
# desaturated to stay botanical rather than neon; warm accents stay terracotta.
TARGET_COLOURS = {
    "meals_icon_download.png": (18, 0.88),  # warm amber / terracotta
    "meals_icon_refresh.png": (101, 0.66),   # restrained forest green
    "meals_icon_info.png": (108, 0.64),      # muted botanical green
    "meals_icon_edit.png": (12, 0.88),       # soft terracotta
    "meals_icon_add.png": (94, 0.66),        # natural leaf green
}

PURPLE_MIN = 174
PURPLE_MAX = 224
MIN_SATURATION = 24


def recolour(path: Path, target_hue: int, saturation_scale: float) -> int:
    original = Image.open(path).convert("RGBA")
    alpha = original.getchannel("A")
    hsv = original.convert("HSV")
    pixels = hsv.load()
    changed = 0

    for y in range(hsv.height):
        for x in range(hsv.width):
            hue, saturation, value = pixels[x, y]
            if (
                alpha.getpixel((x, y)) > 0
                and PURPLE_MIN <= hue <= PURPLE_MAX
                and saturation >= MIN_SATURATION
            ):
                pixels[x, y] = (
                    target_hue,
                    max(0, min(255, round(saturation * saturation_scale))),
                    value,
                )
                changed += 1

    if changed == 0:
        raise RuntimeError(
            f"No purple pixels found in {path.name}; refusing a silent no-op"
        )

    recoloured_rgb = hsv.convert("RGB")
    recoloured = Image.merge("RGBA", (*recoloured_rgb.split(), alpha))
    recoloured.save(path, format="PNG", optimize=True)
    return changed


def main() -> None:
    for filename, (hue, saturation_scale) in TARGET_COLOURS.items():
        path = DRAWABLES / filename
        if not path.is_file():
            raise FileNotFoundError(f"Generated icon is missing: {path}")
        count = recolour(path, hue, saturation_scale)
        print(f"Recoloured {count} purple pixels in {filename}")


if __name__ == "__main__":
    main()
