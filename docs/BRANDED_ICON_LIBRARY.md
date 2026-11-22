# Meals soft-3D icon system

All Meals interface icons use the semantic `MealsIconName` vocabulary in
`shared/src/commonMain/kotlin/dz/meals/app/ui/icons/MealsIcons.kt`.

## Visual language

- **Soft 3D, not outline glyphs.** Artwork uses dimensional forms, soft studio lighting, rounded bevels, natural material shading and multi-colour detail.
- Static artwork is bundled as transparent 256 px PNG resources under `shared/src/commonMain/composeResources/drawable/`.
- No tint is applied at render time: tinting flattens the multi-colour shading and removes the material character.
- Use `MealsIconName` and `MealsIcon()` for ordinary icon artwork. Use `MealsNavigationIcon()` for animated navigation destinations.
- Keep icon hit targets at least 44dp and give meaningful controls Arabic content descriptions. Decorative artwork uses a null content description.

## Active motion

The four primary destinations load one-shot Lottie animations from shared Compose resources:

- Plan: calendar pop.
- Cookbook: a closed-to-open book morph.
- Shopping: basket bounce.
- Pantry: refrigerator jelly motion.

The animation files live under `shared/src/commonMain/composeResources/files/` and are rendered with Compottie in common code, so Android and iOS use the same assets and timing. When a destination becomes inactive, its resting soft-3D artwork is shown again.

## Adding or refreshing assets

1. Update the icon list and `ASSET_MAP` in `scripts/generate-meals-icons.sh`.
2. Add the semantic enum entry and drawable mapping in `MealsIconName`.
3. Add a navigation animation mapping only when the icon has a suitable Lottie export.
4. Run **Generate Meals soft-3D icons** from GitHub Actions (workflow dispatch), or push a change to the generator/workflow. The workflow pins `withicons@0.6.0` and the PNG renderer version, exports PNG/Lottie files and commits the generated Compose resources.
5. Keep `WITH_ICONS_MIT_LICENSE.txt` with the redistributed artwork.

The artwork is provided by With Icons under the MIT licence. The licence notice is included beside the shared resources.

The icon-only action primitives (`MealsIconAction` and `MealsIconButton`) continue to use the Meals Liquid Glass material: translucent Haze-backed face, white gradient rim, inset hairline and narrow specular highlight.
