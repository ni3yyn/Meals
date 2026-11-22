#!/usr/bin/env python3
"""Recolour only the purple accents in the generated Meals soft-3D icon set.

Hue is shifted while saturation, value, alpha and per-pixel shading are retained.
The dark navy grounding shadows and all non-purple artwork remain untouched.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DRAWABLES = ROOT / "shared/src/commonMain/composeResources/drawable"

# Distinct but related natural accents: leafy greens and warm terracotta.
TARGET_HUES = {
    "meals_icon_download.png": 18,   # warm amber / terracotta
    "meals_icon_refresh.png": 86,    # forest green
    "meals_icon_info.png": 96,       # muted botanical green
    "meals_icon_edit.png": 12,       # soft terracotta
    "meals_icon_add.png": 78,        # fresh leaf green
}

# Purple/violet hue interval in Pillow's 0..255 HSV representation.
PURPLE_MIN = 174
PURPLE_MAX = 224
MIN_SATURATION = 24


def recolour(path: Path, target_hue: int) -> int:
    original = Image.open(path).convert("RGBA")
    alpha = original.getchannel("A")
    hsv = original.convert("HSV")
    pixels = hsv.load()
    changed = 0

    for y in range(hsv.height):
        for x in range(hsv.width):
            hue, saturation, value = pixels[x, y]
            if alpha.getpixel((x, y)) > 0 and PURPLE_MIN <= hue <= PURPLE_MAX and saturation >= MIN_SATURATION:
                pixels[x, y] = (target_hue, saturation, value)
                changed += 1

    if changed == 0:
        raise RuntimeError(f"No purple pixels found in {path.name}; refusing a silent no-op")

    recoloured_rgb = hsv.convert("RGB")
    recoloured = Image.merge("RGBA", (*recoloured_rgb.split(), alpha))
    recoloured.save(path, format="PNG", optimize=True)
    return changed


def main() -> None:
    for filename, hue in TARGET_HUES.items():
        path = DRAWABLES / filename
        if not path.is_file():
            raise FileNotFoundError(f"Generated icon is missing: {path}")
        count = recolour(path, hue)
        print(f"Recoloured {count} purple pixels in {filename}")


if __name__ == "__main__":
    main()
