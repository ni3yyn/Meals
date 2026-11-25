# Midati icon system

All functional interface icons use the semantic `MealsIconName` vocabulary in
`shared/src/commonMain/kotlin/dz/meals/app/ui/icons/MealsIcons.kt`.

## One consistent icon family

- The complete 25-icon PNG set is bundled in
  `shared/src/commonMain/composeResources/drawable/` using the
  `meals_icon_<name>.png` resource names.
- Screens and shared components must use `MealsIconName` and `MealsIcon()`
  rather than introducing a second icon family or drawing equivalent glyphs.
- Do not tint the supplied artwork; preserve its original colours and detail.
- `MealsNavigationIcon()` uses the same PNG artwork as every other occurrence.
  Its selected state only applies the existing subtle spring scale response;
  it does not switch to a separate Lottie illustration.
- Keep icon hit targets at least 44dp and give meaningful controls Arabic
  content descriptions. Decorative artwork uses a null content description.

## Adding or replacing icons

1. Add or replace the PNG in
   `shared/src/commonMain/composeResources/drawable/` with the
   `meals_icon_<name>.png` naming convention.
2. Add the semantic enum entry and drawable mapping in `MealsIcons.kt`.
3. Use the semantic name in screens and components. Do not add a parallel
   icon implementation or duplicate the same artwork elsewhere.
4. Keep the applicable licence notice beside redistributed artwork.

The Mida brand mark, launcher artwork, splash artwork and onboarding
illustrations are separate brand/illustration assets; they are not members of
this functional icon set and should not be replaced with action icons.

The icon-only action primitives (`MealsIconAction` and `MealsIconButton`)
continue to use the existing Meals Liquid Glass material: translucent
Haze-backed face, white gradient rim, inset hairline and narrow specular
highlight. This change only replaces icon artwork.
