# Meals branded icon library

All interface icons live in `shared/src/commonMain/composeResources/drawable` as Android XML vector drawables. Compose Multiplatform loads these as vector painters on Android and iOS from the same shared source set.

## Visual language

- Soft, rounded, generous geometry with no sharp ends.
- Warm porcelain, sage, apricot, butter and lilac accents.
- Several intentional colour layers per icon; do not apply a monochrome tint.
- Use `MealsIconName` and `MealsIcon()` from `dz.meals.app.ui.icons` instead of Material's `Icons` object.
- Add content descriptions to meaningful controls and `null` for decorative artwork.

## Adding an icon

1. Add `meals_icon_<name>.xml` to the shared drawable resource directory.
2. Add the matching enum value and `Res.drawable.meals_icon_<name>` entry in `MealsIcons.kt`.
3. Use the typed enum in the UI. Avoid raw resource lookups in screen code.

The library covers weekly planning, recipes, shopping, pantry, settings, app updates, navigation, locking, search, favorites, timing, information, editing and deletion.
