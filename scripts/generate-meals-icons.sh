#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GENERATOR_DIR="${RUNNER_TEMP:-/tmp}/meals-with-icons-generator"
PNG_DIR="$GENERATOR_DIR/png"
LOTTIE_DIR="$GENERATOR_DIR/lottie"
DRAWABLE_DIR="$ROOT_DIR/shared/src/commonMain/composeResources/drawable"
FILES_DIR="$ROOT_DIR/shared/src/commonMain/composeResources/files"

mkdir -p "$GENERATOR_DIR" "$PNG_DIR" "$LOTTIE_DIR" "$DRAWABLE_DIR" "$FILES_DIR"
cd "$GENERATOR_DIR"
npm init -y >/dev/null
npm install --no-audit --no-fund withicons@0.6.0 @resvg/resvg-js@2.6.2
CLI="$GENERATOR_DIR/node_modules/.bin/withicons"

# One consistent, multi-colour soft-3D family for every app glyph.
ICONS=(
  calendar-days book shopping-basket refrigerator settings-2 cloud-download
  download refresh check chevron-right x plus wallet arrow-left arrow-right
  lock unlock cooking-pot search star star-off clock info-circle edit trash-2
  utensils
)
"$CLI" export "${ICONS[@]}" \
  --style soft3d --format png --size 256 --padding 0.06 --out "$PNG_DIR"

declare -A ASSET_MAP=(
  [meals_icon_plan]=calendar-days
  [meals_icon_cookbook]=book
  [meals_icon_basket]=shopping-basket
  [meals_icon_pantry]=refrigerator
  [meals_icon_settings]=settings-2
  [meals_icon_update]=cloud-download
  [meals_icon_download]=download
  [meals_icon_refresh]=refresh
  [meals_icon_check]=check
  [meals_icon_chevron_right]=chevron-right
  [meals_icon_close]=x
  [meals_icon_add]=plus
  [meals_icon_wallet]=wallet
  [meals_icon_arrow_back]=arrow-left
  [meals_icon_arrow_forward]=arrow-right
  [meals_icon_lock]=lock
  [meals_icon_lock_open]=unlock
  [meals_icon_meal]=cooking-pot
  [meals_icon_search]=search
  [meals_icon_star]=star
  [meals_icon_star_outline]=star-off
  [meals_icon_clock]=clock
  [meals_icon_info]=info-circle
  [meals_icon_edit]=edit
  [meals_icon_delete]=trash-2
)
for target in "${!ASSET_MAP[@]}"; do
  source="${ASSET_MAP[$target]}"
  exported="$PNG_DIR/$source-soft3d-256.png"
  test -s "$exported" || { echo "::error::Missing generated icon: $exported"; exit 1; }
  cp "$exported" "$DRAWABLE_DIR/$target.png"
done

# Active navigation icons play once when selected. The cookbook swaps from
# closed to open; the other destinations use the icon's own tuned soft-3D move.
"$CLI" export book --style soft3d --format lottie --motion swap \
  --to book-open --effect morph --out "$LOTTIE_DIR"
"$CLI" export calendar-days shopping-basket refrigerator cooking-pot \
  --style soft3d --format lottie --motion once --out "$LOTTIE_DIR"

declare -A MOTION_MAP=(
  [meals_nav_plan.json]=calendar-days
  [meals_nav_cookbook.json]=book
  [meals_nav_basket.json]=shopping-basket
  [meals_nav_pantry.json]=refrigerator
  [meals_meal_lid_open.json]=cooking-pot
)
for target in "${!MOTION_MAP[@]}"; do
  source="${MOTION_MAP[$target]}"
  exported="$(find "$LOTTIE_DIR" -maxdepth 1 -type f -name "$source-soft3d-*.json" -print -quit)"
  test -n "$exported" && test -s "$exported" || {
    echo "::error::Missing Lottie export for $source"
    ls -la "$LOTTIE_DIR"
    exit 1
  }
  cp "$exported" "$FILES_DIR/$target"
done

# Keep the upstream MIT notice alongside the redistributed icon artwork.
cp "$GENERATOR_DIR/node_modules/@withicons/mcp/LICENSE" \
  "$FILES_DIR/WITH_ICONS_MIT_LICENSE.txt"

echo "Generated $(find "$DRAWABLE_DIR" -maxdepth 1 -type f -name 'meals_icon_*.png' | wc -l) soft-3D PNGs."
echo "Generated $(find "$FILES_DIR" -maxdepth 1 -type f -name 'meals_*.json' | wc -l) Lottie animations."
