# Localization and RTL/LTR implementation

Midati supports Arabic (the default, `ar-DZ`) and English (`en`). The choice is stored in the existing DataStore preferences and applied at the Compose root. It is independent of the device language.

## Text entry point

In UI source files use the shared `MealsText` wrapper (normally imported as `Text`):

```kotlin
import dz.meals.app.ui.components.MealsText as Text
```

The wrapper translates exact source phrases through `MealsEnglishTranslations`. When adding or changing user-visible copy, add its English translation to that catalogue in the same change. Do not call Material3 `Text` directly for user-visible labels, placeholders, dialogs, errors, accessibility labels, empty states, or action text. If a richer `AnnotatedString` UI is introduced, add a localization-aware overload rather than bypassing the layer.

For non-composable code (search, exported text, notification/share payloads), call the pure overload:

```kotlin
mealsText(sourceText, localeTag)
```

When displaying a localized catalogue value in search, search both its original value/aliases and `mealsText(originalValue, localeTag)`. Keep IDs, enum values, database values, analytics keys and sorting/filtering keys stable; localization belongs at the presentation boundary only.

## Dynamic text and BiDi safety

Avoid creating a new translation by concatenating arbitrary text fragments. For a dynamic message:
1. Add a deterministic pattern to `translateDynamicMealsText`.
2. Use ordered placeholders (`%s`) in the English template.
3. Each inserted argument is translated only when it is an exact known catalogue value, otherwise left unchanged.
4. The inserted argument is wrapped with Unicode FSI/PDI via `bidiIsolate`, so a user-supplied Arabic name, Latin SKU, date or number cannot pull adjacent punctuation into the wrong visual run.

For text assembled outside `MealsText` (share/export content), localize each known label and wrap user-authored values with `bidiIsolate`. Never translate, transliterate, or normalize arbitrary user-entered names, shop names, notes, wilayas, or custom ingredients. Add exact catalogue translations only for known built-in values.

When displaying a free-form user-entered value on its own, use `MealsText(value, translate = false)` so it cannot accidentally collide with a built-in catalogue key. For values embedded in a translated sentence or export, use a typed message template and wrap the inserted value with `bidiIsolate`.

Keep numeric IDs, version strings, dates, prices and codes intact. Use stable machine-readable values for storage and calculations; format units, dates and currency only for display. Do not infer that every short Arabic suffix means currency: for example, the onboarding duration suffix `د` means minutes, while Algerian dinars use `دج`.

## RTL/LTR layout rules

- The root `CompositionLocalProvider` supplies both `LocalLayoutDirection` and `LocalMealsLocaleTag`. Do not add screen-local hard-coded RTL/LTR providers.
- Prefer logical layout APIs: `start`/`end`, `Alignment.Start`/`End`, and `Arrangement.Start`/`End`. Avoid physical left/right padding or alignment for content flow.
- Compose mirrors start/end layout and text direction. Do not mirror an entire row, screen, photograph, logo, food illustration or branded icon.
- `MealsIcon` mirrors only directional navigation artwork (`ArrowBack`, `ArrowForward`, `ChevronRight`). Do not add another `scaleX = -1` at call sites. If a mirrored chevron is rotated, reverse its rotation for RTL so open/closed arrows still point down/up.
- For icon actions, select the semantic Back/Forward glyph; let `MealsIcon` handle the physical RTL reflection. Avoid hard-coding a right-pointing icon to mean “back”.
- Gesture code must convert physical drag direction into logical forward/back using `LocalLayoutDirection`. Horizontal entry/exit animations must use the same direction sign as the gesture; otherwise English will swipe correctly but animate backwards.
- Horizontal pagers, selection indicators, dropdowns and category tabs must be checked in both directions. Use Compose's RTL-aware layout behavior rather than manually reversing children twice.
- Keep numeric fields and machine identifiers readable. Do not change their underlying value or keyboard parsing when switching language. When a number, URL, ID, date, or user string is embedded inside prose, isolate the embedded value rather than wrapping or reversing the entire text.

## Regression checks

Run `./gradlew :app:testDebugUnitTest` and the Android release workflow. The locale tests cover language-tag normalization, script-aware direction, known translations, dynamic placeholders, currency vs duration units, and unknown/user-entered text preservation.

Manually verify both languages on:
- settings language selector and immediate screen-wide switch;
- onboarding page swipes and validation;
- recipe category swipes, cooking-step swipes, and progress;
- previous/next week icons and day expansion chevrons;
- pantry and recipe search using English names;
- dropdown menus, subtabs, bottom sheets and empty/error states;
- shopping-list share/export, including mixed Arabic/Latin custom item names and notes.
