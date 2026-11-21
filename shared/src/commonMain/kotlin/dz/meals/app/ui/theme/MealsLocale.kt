package dz.meals.app.ui.theme

import androidx.compose.ui.unit.LayoutDirection

/**
 * Current Meals UI language. The platform host can pass a different active
 * app locale to MealsApp when language selection is introduced.
 */
const val DEFAULT_MEALS_LOCALE_TAG = "ar-DZ"

/**
 * Resolves writing direction from a BCP-47 locale tag instead of assuming that
 * every future translation is RTL. Explicit script subtags take precedence
 * over language defaults (for example, ku-Arab versus ku-Latn).
 */
fun mealsLayoutDirection(localeTag: String): LayoutDirection {
    val parts = localeTag
        .trim()
        .replace('_', '-')
        .split('-')
        .filter { it.isNotBlank() }

    val language = parts.firstOrNull()?.lowercase().orEmpty()
    val script = parts.drop(1)
        .firstOrNull { it.length == 4 && it.all(Char::isLetter) }
        ?.lowercase()

    val scriptDirection = when (script) {
        "arab", "hebr", "thaa", "nkoo", "adlm", "rohg", "syrc" -> LayoutDirection.Rtl
        "latn", "cyrl", "grek", "deva", "beng", "guru", "taml", "thai" -> LayoutDirection.Ltr
        else -> null
    }

    if (scriptDirection != null) return scriptDirection

    val rtlLanguages = setOf(
        "ar", "arc", "dv", "fa", "he", "khw", "ps", "sd", "ug", "ur", "yi"
    )
    return if (language in rtlLanguages) LayoutDirection.Rtl else LayoutDirection.Ltr
}
