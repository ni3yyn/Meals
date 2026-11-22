# Meals icon system

All app interface glyphs resolve through `MealsIconName` and `MealsIcon()` in
`shared/src/commonMain/kotlin/dz/meals/app/ui/icons/MealsIcons.kt`.

## Visual language

- **Outline-only, always.** The shared icon pack is Lucide for Compose Multiplatform.
- Icons use one consistent stroke style; selected states change tint and motion, never switch to filled artwork.
- Use `MealsIconName` rather than Material's `Icons` object or raw painter resources.
- Keep icon hit targets at least 44dp and give meaningful controls Arabic content descriptions.
- Decorative icons use a null content description.

## Adding an icon

1. Add the semantic enum entry to `MealsIconName`.
2. Map it to a Lucide `ImageVector` in `MealsIconName.imageVector()`.
3. Use `MealsIcon()` or `MealsNavigationIcon()` in screen code.

The icon-only action primitives (`MealsIconAction` and `MealsIconButton`) share the liquid-glass material: translucent Haze-backed face, white gradient rim, inset hairline and a narrow specular highlight.
