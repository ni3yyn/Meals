package dz.meals.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.LayoutDirection
import dz.meals.app.shared.format.prettyQuantity
import kotlin.math.abs
import kotlin.math.roundToLong

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
        Regex("^اختر وصفة لـ (.+)$") to "Choose a recipe for %s",
        Regex("^(\\d+) دقيقة · (\\d+) حصص$") to "%s min · %s servings",
        Regex("^(-?\\d+(?:[.,]\\d+)?) د$") to "%s min",
        Regex("^الكمية \\((.+)\\)$") to "Quantity (%s)",
        Regex("^الأسعار تقديرية، وهناك (\\d+) عناصر دون سعر\\.$") to "Prices are estimates; %s items have no price.",
        Regex("^(\\d+) عناصر دون سعر$") to "%s items without a price",
        Regex("^(\\d+(?:[.,]\\d+)?) حصص$") to "%s servings",
        Regex("^إضافة (.+)$") to "Add %s",
        Regex("^تعديل (.+)$") to "Edit %s",
        Regex("^الوحدة: (.+)$") to "Unit: %s",
        Regex("^الوحدة الأساسية: (.+)$") to "Base unit: %s",
        Regex("^حجم العبوة أو الكمية المباعة \\((.+)\\)$") to "Pack size or quantity sold (%s)",
        Regex("^نحتفظ بنسبة (.+)% كاحتياط حتى لا تتجاوزي ميزانيتك\\.$") to "We keep a %s% buffer so you do not exceed your budget.",
        Regex("^بلاغ محلي · (.+)$") to "Local report · %s",
        Regex("^تعذر الوصول إلى خادم التحديثات \\(HTTP (\\d+)\\)\\.$") to "Could not reach update server (HTTP %s).",
        Regex("^(\\d+) عناصر متبقية$") to "%s items left",
        Regex("^المتبقي · (\\d+)$") to "Remaining · %s",
        Regex("^الكل · (\\d+)$") to "All · %s",
        Regex("^المدفوع فعلياً: (.+)$") to "Actually paid: %s",
        Regex("^المدفوع (.+)$") to "Paid %s",
        Regex("^التكلفة التقديرية: (.+)$") to "Estimated cost: %s",
        Regex("^الإنفاق الفعلي المُدخل: (.+)$") to "Entered actual spend: %s",
        // Match the combined value before the broader single-value "Needed" template.
        Regex("^المطلوب (.+) · في المخزون (.+)$") to "Needed %s · in pantry %s",
        Regex("^المطلوب (.+)$") to "Needed %s",
        Regex("^تنبيه: يوجد (\\d+) عنصر دون سعر مؤكد\\.$") to "Notice: %s items have no confirmed price.",
        Regex("^الأسعار تقديرية · (.+) دون سعر$") to "Estimated prices · %s without a price",
        Regex("^(\\d+) من (\\d+) وجبات محددة$") to "%s of %s meals selected",
        Regex("^(\\d+) مكتملة · (\\d+) من (\\d+) وجبات$") to "%s completed · %s of %s meals",
        Regex("^اكتملت جميع الخطوات · (.+) من (.+)$") to "All steps complete · %s of %s",
    )
    for ((pattern, replacement) in patterns) {
        val match = pattern.matchEntire(source) ?: continue
        var result = replacement
        for (index in 1..match.groupValues.lastIndex) {
            val argument = match.groupValues[index]
            val localizedArgument = when (pattern.pattern) {
                "^إضافة (.+)$", "^تعديل (.+)$", "^اختر وصفة لـ (.+)$" ->
                    MealsEnglishTranslations.translate(argument) ?: argument
                "^الكمية \\((.+)\\)$",
                "^الوحدة: (.+)$", "^الوحدة الأساسية: (.+)$",
                "^حجم العبوة أو الكمية المباعة \\((.+)\\)$" ->
                    translateUnitSymbol(argument) ?: argument
                // These templates carry user-entered notes, dates, shop labels or IDs.
                "^ملاحظة: (.+)$", "^تاريخ الانتهاء المُدخل: (.+)$",
                "^بلاغ محلي · (.+)$", "^الإصدار الحالي (.+)$", "^الإصدار الجديد: (.+)$" ->
                    argument
                else -> translateKnownTokens(argument)
            }
            result = result.replaceFirst("%s", bidiIsolate(localizedArgument))
        }
        // English count nouns need singular forms for one; keep Arabic source
        // forms unchanged and never rewrite arbitrary user-authored strings.
        val countForNoun = when (pattern.pattern) {
            "^(\\d+) دقيقة · (\\d+) حصص$" -> match.groupValues[2]
            "^(\\d+) من (\\d+) وجبات محددة$" -> match.groupValues[2]
            "^(\\d+) مكتملة · (\\d+) من (\\d+) وجبات$" -> match.groupValues[3]
            else -> match.groupValues.getOrNull(1).orEmpty()
        }
        if (countForNoun.toIntOrNull() == 1) {
            result = result
                .replace(" items left", " item left")
                .replace(" items without a price", " item without a price")
                .replace(" items have no price", " item has no price")
                .replace(" items have no confirmed price", " item has no confirmed price")
                .replace(" servings", " serving")
                .replace(" meals selected", " meal selected")
                .replace(" meals", " meal")
        }
        return result
    }

    if (source.startsWith("الخطوة ") && source.contains(" من ")) {
        return source.replaceFirst("الخطوة ", "Step ").replace(" من ", " of ")
    }
    if (source.startsWith("تاريخ الانتهاء المُدخل: ")) {
        return "Expiry date entered: " + bidiIsolate(source.removePrefix("تاريخ الانتهاء المُدخل: "))
    }
    if (source.startsWith("ملاحظة: ")) {
        return "Note: " + bidiIsolate(source.removePrefix("ملاحظة: "))
    }
    return null
}

/**
 * Handles formatter output (units, weekdays/months and currency) while leaving
 * user-entered words alone. Domain values continue to be stored in base units.
 */
private val englishWeekdays = mapOf(
    "الإثنين" to "Monday",
    "الثلاثاء" to "Tuesday",
    "الأربعاء" to "Wednesday",
    "الخميس" to "Thursday",
    "الجمعة" to "Friday",
    "السبت" to "Saturday",
    "الأحد" to "Sunday"
)

private val englishMonths = mapOf(
    "يناير" to "January",
    "فبراير" to "February",
    "مارس" to "March",
    "أبريل" to "April",
    "مايو" to "May",
    "يونيو" to "June",
    "يوليو" to "July",
    "أغسطس" to "August",
    "سبتمبر" to "September",
    "أكتوبر" to "October",
    "نوفمبر" to "November",
    "ديسمبر" to "December"
)

/** Exact unit symbols are translated only when a dynamic label has established unit context. */
private fun translateUnitSymbol(value: String): String? = when (value) {
    "كغ" -> "kg"
    "لتر" -> "L"
    "غ" -> "g"
    "مل" -> "ml"
    "حبة" -> "piece"
    "حبتان", "حبات" -> "pieces"
    "دقيقة", "دقائق", "د" -> "min"
    "دج" -> "DZD"
    else -> null
}

private fun isFormattedNumber(value: String): Boolean =
    value.isNotEmpty() && value.all { it.isDigit() || it in "-.,\u202F" }

/**
 * Translates only complete formatter-shaped values. Never replace Arabic letter
 * sequences inside arbitrary prose or custom ingredient/shop names (for example
 * the substring "دج" inside "دجاج").
 */
private fun translateKnownTokens(source: String): String {
    if (source.endsWith(" دج")) {
        val amount = source.removeSuffix(" دج")
        if (isFormattedNumber(amount)) return amount.replace('\u202F', ',') + " DZD"
    }

    val separator = source.lastIndexOf(' ')
    if (separator > 0) {
        val amount = source.substring(0, separator)
        val unit = translateUnitSymbol(source.substring(separator + 1))
        if (isFormattedNumber(amount) && unit != null) return "$amount $unit"
    }

    // Date formatters emit a strict weekday/day/month value or a two-date range.
    val weekdayParts = source.split(' ')
    if (
        weekdayParts.size == 3 &&
        weekdayParts[1].toIntOrNull() != null &&
        englishWeekdays.containsKey(weekdayParts[0]) &&
        englishMonths.containsKey(weekdayParts[2])
    ) {
        return "${englishWeekdays.getValue(weekdayParts[0])} ${weekdayParts[1]} ${englishMonths.getValue(weekdayParts[2])}"
    }

    fun translateDatePart(part: String): String? {
        val words = part.split(' ')
        if (words.size !in 2..3 || words[0].toIntOrNull() == null) return null
        val month = englishMonths[words[1]] ?: return null
        if (words.size == 3 && words[2].toIntOrNull() == null) return null
        return listOf(words[0], month, words.getOrNull(2)).filterNotNull().joinToString(" ")
    }

    if (source.contains(" - ")) {
        val parts = source.split(" - ")
        if (parts.size == 2) {
            val startDate = translateDatePart(parts[0])
            val endDate = translateDatePart(parts[1])
            if (startDate != null && endDate != null) return "$startDate - $endDate"
        }
    }

    translateDatePart(source)?.let { return it }
    return source
}

/**
 * Converts a physical horizontal drag into a logical next/previous action.
 * A positive physical drag means next in RTL and previous in LTR.
 */
fun isLogicalForwardSwipe(
    horizontalDrag: Float,
    layoutDirection: LayoutDirection
): Boolean = when (layoutDirection) {
    LayoutDirection.Rtl -> horizontalDrag > 0f
    LayoutDirection.Ltr -> horizontalDrag < 0f
}

/**
 * Display a stable domain unit in the active UI language. Unit IDs remain
 * machine-readable (g/ml/piece) in storage and calculations.
 */
/**
 * Formats the amount shown beside a recipe ingredient. The imported recipe
 * catalogue includes cooking units beyond the pantry's base units, so never
 * leak raw source labels such as "sprig" or "toTaste" into the UI.
 */
private fun Double.prettyCookingMeasureQuantity(): String {
    val roundedHundredths = (this * 100.0).roundToLong()
    val sign = if (roundedHundredths < 0L) "-" else ""
    val absolute = abs(roundedHundredths)
    val whole = absolute / 100L
    val fraction = (absolute % 100L).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

fun mealsRecipeIngredientQuantity(quantity: Double, unit: String, localeTag: String): String {
    if (!quantity.isFinite()) return "—"
    val normalized = unit.trim().lowercase()
    val english = isMealsEnglish(localeTag)
    val isSpoonMeasure = normalized in setOf(
        "spoon", "ملعقة", "tbsp", "tbs", "tbsps", "tablespoon", "tablespoons", "ملعقة كبيرة",
        "tsp", "tsps", "teaspoon", "teaspoons", "ملعقة صغيرة"
    )
    val number = if (isSpoonMeasure) quantity.prettyCookingMeasureQuantity() else quantity.prettyQuantity()

    if (normalized in setOf("totaste", "to_taste", "to taste", "حسب الحاجة")) {
        return if (english) "to taste" else "حسب الحاجة"
    }

    fun countedArabic(one: String, two: String, many: String): String = when {
        quantity == 1.0 -> one
        quantity == 2.0 -> two
        quantity > 0.0 && quantity % 1.0 != 0.0 -> "$number $one"
        else -> "$number $many"
    }

    fun countedEnglish(one: String, many: String): String {
        // English uses the singular for fractional amounts below one (0.25 teaspoon).
        val useSingular = quantity == 1.0 || (quantity > 0.0 && quantity < 1.0)
        return "$number " + if (useSingular) one else many
    }

    return when (normalized) {
        "g" -> if (abs(quantity) >= 1000.0) {
            "${(quantity / 1000.0).prettyQuantity()} ${if (english) "kg" else "كغ"}"
        } else "$number ${if (english) "g" else "غ"}"
        "ml" -> if (abs(quantity) >= 1000.0) {
            "${(quantity / 1000.0).prettyQuantity()} ${if (english) "L" else "لتر"}"
        } else "$number ${if (english) "ml" else "مل"}"
        "piece", "pieces", "pc", "pcs" -> if (english) {
            countedEnglish("piece", "pieces")
        } else countedArabic("حبة", "حبتان", "حبات")
        "tbsp", "tbs", "tbsps", "tablespoon", "tablespoons", "ملعقة كبيرة" -> if (english) {
            countedEnglish("tablespoon", "tablespoons")
        } else countedArabic("ملعقة كبيرة", "ملعقتان كبيرتان", "ملاعق كبيرة")
        "tsp", "tsps", "teaspoon", "teaspoons", "ملعقة صغيرة" -> if (english) {
            countedEnglish("teaspoon", "teaspoons")
        } else countedArabic("ملعقة صغيرة", "ملعقتان صغيرتان", "ملاعق صغيرة")
        // The legacy import uses "spoon" for French "à thé" (teaspoon).
        "spoon" -> if (english) {
            countedEnglish("teaspoon", "teaspoons")
        } else countedArabic("ملعقة صغيرة", "ملعقتان صغيرتان", "ملاعق صغيرة")
        "ملعقة" -> if (english) {
            countedEnglish("spoon", "spoons")
        } else countedArabic("ملعقة", "ملعقتان", "ملاعق")
        "cup", "cups", "كوب" -> if (english) {
            countedEnglish("cup", "cups")
        } else countedArabic("كوب", "كوبان", "أكواب")
        "sprig", "sprigs" -> if (english) {
            countedEnglish("sprig", "sprigs")
        } else countedArabic("غصن", "غصنان", "أغصان")
        "clove", "cloves", "فص" -> if (english) {
            countedEnglish("clove", "cloves")
        } else countedArabic("فص", "فصان", "فصوص")
        "pinch", "pinches", "رشة" -> if (english) {
            countedEnglish("pinch", "pinches")
        } else countedArabic("رشة", "رشتان", "رشات")
        "bunch", "bunches", "ربطة" -> if (english) {
            countedEnglish("bunch", "bunches")
        } else countedArabic("ربطة", "ربطتان", "ربطات")
        "slice", "slices", "شريحة" -> if (english) {
            countedEnglish("slice", "slices")
        } else countedArabic("شريحة", "شريحتان", "شرائح")
        "handful", "handfuls", "حفنة" -> if (english) {
            countedEnglish("handful", "handfuls")
        } else countedArabic("حفنة", "حفنتان", "حفنات")
        "stalk", "stalks" -> if (english) {
            countedEnglish("stalk", "stalks")
        } else countedArabic("عود", "عودان", "أعواد")
        else -> "$number ${mealsUnitLabel(unit, localeTag)}"
    }
}

fun mealsUnitLabel(unit: String, localeTag: String): String {
    val normalized = unit.trim().lowercase()
    return if (isMealsEnglish(localeTag)) {
        when (normalized) {
            "g" -> "g"
            "ml" -> "ml"
            "piece" -> "piece"
            "kg" -> "kg"
            "l" -> "L"
            "sprig", "sprigs" -> "sprig"
            "totaste", "to_taste" -> "to taste"
            "tbsp" -> "tbsp"
            "tsp" -> "tsp"
            "spoon" -> "tsp"
            "ملعقة" -> "spoon"
            else -> unit
        }
    } else {
        when (normalized) {
            "g" -> "غ"
            "ml" -> "مل"
            "piece" -> "حبة"
            "kg" -> "كغ"
            "l" -> "لتر"
            "sprig", "sprigs" -> "غصن"
            "totaste", "to_taste" -> "حسب الحاجة"
            "tbsp" -> "ملعقة كبيرة"
            "tsp" -> "ملعقة صغيرة"
            "spoon" -> "ملعقة صغيرة"
            else -> unit
        }
    }
}

/** Physical x offset needed to reveal an action placed at logical Start. */
fun logicalStartRevealSign(layoutDirection: LayoutDirection): Float =
    if (layoutDirection == LayoutDirection.Rtl) -1f else 1f

/** Physical x sign used by entry/exit animations for logical forward movement. */
fun logicalAnimationDirectionSign(layoutDirection: LayoutDirection): Int =
    if (layoutDirection == LayoutDirection.Rtl) -1 else 1

/** Unicode isolate around inserted values prevents punctuation leaking across BiDi runs. */
fun bidiIsolate(value: String): String = "\u2068$value\u2069"
