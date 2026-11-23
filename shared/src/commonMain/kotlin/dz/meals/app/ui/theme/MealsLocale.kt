package dz.meals.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.LayoutDirection

/**
 * The active app language, independent of the device language.
 *
 * Keep this as a BCP-47 tag rather than a Boolean so future script/region
 * variants (for example ar-DZ or ku-Latn) do not require a new state model.
 */
const val DEFAULT_MEALS_LOCALE_TAG = "ar-DZ"
const val ENGLISH_MEALS_LOCALE_TAG = "en"

val LocalMealsLocaleTag = compositionLocalOf { DEFAULT_MEALS_LOCALE_TAG }

fun normalizeMealsLocaleTag(localeTag: String): String {
    val normalized = localeTag.trim().replace('_', '-')
    return when {
        normalized.equals("en", ignoreCase = true) ||
            normalized.startsWith("en-", ignoreCase = true) -> ENGLISH_MEALS_LOCALE_TAG
        normalized.equals("ar", ignoreCase = true) ||
            normalized.startsWith("ar-", ignoreCase = true) -> DEFAULT_MEALS_LOCALE_TAG
        else -> DEFAULT_MEALS_LOCALE_TAG
    }
}

fun isMealsEnglish(localeTag: String): Boolean =
    normalizeMealsLocaleTag(localeTag) == ENGLISH_MEALS_LOCALE_TAG

/**
 * Resolves writing direction from the full BCP-47 tag.
 * Explicit script subtags win over language defaults (for example ku-Arab
 * versus ku-Latn), preventing a locale code alone from forcing the wrong flow.
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

/** Composable convenience used only at presentation boundaries. */
@Composable
fun mealsText(source: String): String =
    mealsText(source, LocalMealsLocaleTag.current)

/**
 * Translates UI/catalogue text without changing persisted values or domain IDs.
 * Unknown/user-authored text is deliberately returned untouched; it must never
 * be guessed, transliterated, or silently altered.
 */
fun mealsText(source: String, localeTag: String): String {
    if (!isMealsEnglish(localeTag)) return source
    MealsEnglishTranslations.translate(source)?.let { return it }
    return translateDynamicMealsText(source) ?: translateKnownTokens(source)
}

private fun translateDynamicMealsText(source: String): String? {
    val patterns = listOf(
        Regex("^الإصدار الحالي (.+)$") to "Current version %s",
        Regex("^الإصدار الجديد: (.+)$") to "New version: %s",
        Regex("^الخطوة (\\d+) من 5$") to "Step %s of 5",
        Regex("^الخطوة (\\d+)$") to "Step %s",
        Regex("^المخزون الحالي \\((\\d+)\\)$") to "Current pantry (%s)",
        Regex("^تاريخ الانتهاء المُدخل: (.+)$") to "Expiry date entered: %s",
        Regex("^ملاحظة: (.+)$") to "Note: %s",
        Regex("^تم اختيار (.+)$") to "%s selected",
        Regex("^(.+) د$") to "%s DZD",
        Regex("^(.+) عناصر متبقية$") to "%s items left",
        Regex("^المتبقي · (.+)$") to "Remaining · %s",
        Regex("^الكل · (.+)$") to "All · %s",
        Regex("^المدفوع فعلياً: (.+)$") to "Actually paid: %s",
        Regex("^المدفوع (.+)$") to "Paid %s",
        Regex("^التكلفة التقديرية: (.+)$") to "Estimated cost: %s",
        Regex("^الإنفاق الفعلي المُدخل: (.+)$") to "Entered actual spend: %s",
        Regex("^المطلوب (.+)$") to "Needed %s",
        Regex("^(.+) · في المخزون (.+)$") to "%s · in pantry %s",
        Regex("^تنبيه: يوجد (.+) عنصر دون سعر مؤكد\\.$") to "Notice: %s item has no confirmed price.",
        Regex("^الأسعار تقديرية · (.+) دون سعر$") to "Estimated prices · %s without a price",
        Regex("^(.+) من (.+) وجبات محددة$") to "%s of %s meals selected",
        Regex("^(.+) مكتملة · (.+) من (.+) وجبات$") to "%s completed · %s of %s meals",
        Regex("^اكتملت جميع الخطوات · (.+) من (.+)$") to "All steps complete · %s of %s",
        Regex("^$") to ""
    )
    for ((pattern, replacement) in patterns) {
        val match = pattern.matchEntire(source) ?: continue
        var result = replacement
        for (index in 1..match.groupValues.lastIndex) {
            result = result.replaceFirst("%s", match.groupValues[index])
        }
        return result
    }

    if (source.startsWith("الخطوة ") && source.contains(" من ")) {
        return source.replaceFirst("الخطوة ", "Step ").replace(" من ", " of ")
    }
    if (source.startsWith("تاريخ الانتهاء المُدخل: ")) {
        return "Expiry date entered: " + source.removePrefix("تاريخ الانتهاء المُدخل: ")
    }
    if (source.startsWith("ملاحظة: ")) {
        return "Note: " + source.removePrefix("ملاحظة: ")
    }
    return null
}

/**
 * Handles formatter output (units, weekdays/months and currency) while leaving
 * user-entered words alone. Domain values continue to be stored in base units.
 */
private fun translateKnownTokens(source: String): String {
    var value = source
    val tokens = listOf(
        "الإثنين" to "Monday", "الثلاثاء" to "Tuesday", "الأربعاء" to "Wednesday",
        "الخميس" to "Thursday", "الجمعة" to "Friday", "السبت" to "Saturday", "الأحد" to "Sunday",
        "يناير" to "January", "فبراير" to "February", "مارس" to "March", "أبريل" to "April",
        "مايو" to "May", "يونيو" to "June", "يوليو" to "July", "أغسطس" to "August",
        "سبتمبر" to "September", "أكتوبر" to "October", "نوفمبر" to "November", "ديسمبر" to "December",
        "كغ" to "kg", "لتر" to "L", "غ" to "g", "مل" to "ml",
        "حبتان" to "2 pieces", "حبات" to "pieces", "حبة" to "piece",
        "دج" to "DZD", "دقيقة" to "min", "دقائق" to "min"
    )
    tokens.forEach { (arabic, english) -> value = value.replace(arabic, english) }
    if (value.contains("DZD")) value = value.replace('\u202F', ',')
    return value
}

/** Unicode isolate around inserted values prevents punctuation leaking across BiDi runs. */
fun bidiIsolate(value: String): String = "\u2068$value\u2069"
