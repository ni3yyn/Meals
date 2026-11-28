package dz.meals.app.data

import kotlin.math.sqrt

object FoodTaxonomyIds {
    const val OTHER_CATEGORY = "foodcat-other"
    const val OTHER_SUBCATEGORY = "foodsub-other-general"
}

data class FoodCategory(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val nameFr: String,
    val sortOrder: Int
) {
    fun label(localeTag: String): String = when {
        localeTag.startsWith("en", ignoreCase = true) -> nameEn.ifBlank { nameAr }
        localeTag.startsWith("fr", ignoreCase = true) -> nameFr.ifBlank { nameAr }
        else -> nameAr
    }
}

data class FoodSubcategory(
    val id: String,
    val categoryId: String,
    val nameAr: String,
    val nameEn: String,
    val nameFr: String,
    val sortOrder: Int
) {
    fun label(localeTag: String): String = when {
        localeTag.startsWith("en", ignoreCase = true) -> nameEn.ifBlank { nameAr }
        localeTag.startsWith("fr", ignoreCase = true) -> nameFr.ifBlank { nameAr }
        else -> nameAr
    }
}

/** Stable taxonomy IDs used for rules and grouping; labels are resolved separately. */
data class FoodTaxonomyRef(
    val categoryId: String,
    val subcategoryId: String
)

data class IngredientMaster(
    val id: String,
    val nameAr: String,
    val nameFr: String,
    val baseUnit: String,
    val allergens: Set<String>,
    val aliases: List<String>,
    val nameEn: String = "",
    val categoryId: String,
    val subcategoryId: String,
    val acceptedUnits: List<String> = emptyList()
)

data class RecipeIngredient(
    val ingredientId: String,
    val quantityBase: Double,
    val unit: String,
    val prepNote: String = "",
    val optional: Boolean = false,
    val scalingRule: String = "linear"
)

data class MealRecipe(
    val id: String,
    val nameAr: String,
    val nameFr: String,
    val descriptionAr: String,
    val descriptionFr: String,
    val category: String,
    val region: String,
    val durationMinutes: Int,
    val baseServings: Int,
    val difficulty: String,
    val allergens: Set<String>,
    val ingredients: List<RecipeIngredient>,
    val stepsAr: List<String>,
    val stepsFr: List<String>,
    val attribution: RecipeAttribution? = null,
    val tags: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val dietary: List<String> = emptyList(),
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val caloriesPerServing: Double? = null,
    val proteinGramsPerServing: Double? = null,
    val carbsGramsPerServing: Double? = null,
    val fatGramsPerServing: Double? = null,
    val imageUrl: String = "",
    val nativeName: String = "",
    val countryCode: String = "",
    val imageAuthor: String = "",
    val imageLicense: String = "",
    val stepMinutes: List<Int?> = emptyList(),
    val imageSourcePageUrl: String = "",
    val imageLicenseUrl: String = "",
    val imageCredit: String = ""
)

/**
 * Recipe images are enabled only for catalogue sources whose image rights and
 * attribution are represented in the imported record, or for verified Commons
 * image overrides selected by the user. Unknown sources stay hidden.
 */
fun MealRecipe.hasVerifiedReusableImage(): Boolean {
    if (!imageUrl.startsWith("https://", ignoreCase = true)) return false

    // User-selected Commons overrides carry their own image-level licence and credit.
    if (isHttpsImageAtHost(imageUrl, "upload.wikimedia.org", "/")) {
        return imageSourcePageUrl.startsWith(
            "https://commons.wikimedia.org/wiki/File:",
            ignoreCase = true
        ) &&
            isReusableRecipeImageLicense(imageLicense, imageLicenseUrl) &&
            (!recipeImageLicenseRequiresAttribution(imageLicense) ||
                (imageAuthor.isNotBlank() && imageCredit.isNotBlank()))
    }

    // Openverse-proxied thumbnails are stable image URLs; the original provider
    // landing page and the individual image licence remain attached for credit.
    if (isHttpsImageAtHost(imageUrl, "api.openverse.org", "/v1/images/")) {
        return imageSourcePageUrl.startsWith("https://", ignoreCase = true) &&
            !isHttpsImageAtHost(imageSourcePageUrl, "api.openverse.org", "/") &&
            isReusableRecipeImageLicense(imageLicense, imageLicenseUrl) &&
            (!recipeImageLicenseRequiresAttribution(imageLicense) ||
                (imageAuthor.isNotBlank() && imageCredit.isNotBlank()))
    }

    val source = attribution ?: return false
    return when {
        id.startsWith("focus:") -> {
            isHttpsImageAtHost(
                imageUrl,
                "ylushaayhmufwdmuqafv.supabase.co",
                "/storage/v1/object/public/focus-recipes/"
            ) &&
                isHttpsImageAtHost(source.sourceUrl, "focusathletics.app", "/recetas/") &&
                source.sourceTitle.startsWith("Focus Recipes") &&
                source.credit.startsWith("Focus Recipes") &&
                source.sourceLicense == "CC BY 4.0" &&
                source.license == "CC BY 4.0" &&
                source.licenseUrl == "https://creativecommons.org/licenses/by/4.0/" &&
                isReusableRecipeImageLicense(source.license, source.licenseUrl)
        }

        id.startsWith("unitools:") -> {
            val photoLicenseUrl = imageLicenseUrl.ifBlank {
                canonicalRecipeImageLicenseUrl(imageLicense).orEmpty()
            }
            val photoNeedsAuthor = imageLicense.trim().startsWith("CC BY", ignoreCase = true)
            isHttpsImageAtHost(imageUrl, "theunitools.com", "/recipes/") &&
                isHttpsImageAtHost(source.sourceUrl, "theunitools.com", "/en/data") &&
                source.sourceTitle.startsWith("UniTools") &&
                source.credit.startsWith("UniTools") &&
                source.sourceLicense == "CC BY-SA 4.0" &&
                source.license == "CC BY-SA 4.0" &&
                source.licenseUrl == "https://creativecommons.org/licenses/by-sa/4.0/" &&
                isReusableRecipeImageLicense(source.license, source.licenseUrl) &&
                isReusableRecipeImageLicense(imageLicense, photoLicenseUrl) &&
                (!photoNeedsAuthor || imageAuthor.isNotBlank())
        }

        else -> false
    }
}

/**
 * Whether a recipe has a safe direct image URL for display.
 *
 * Most catalogues still require a verified reusable-image policy. The curated
 * Algerian starter is different: its photo records are direct Dzirielle-hosted
 * WebP URLs with a matching Dzirielle recipe page and provider credit. These
 * can be displayed in-app without copying or relabelling their licence.
 */
fun MealRecipe.hasDisplayableRecipeImage(): Boolean =
    hasVerifiedReusableImage() || hasDzirielleHostedImage()

private fun MealRecipe.hasDzirielleHostedImage(): Boolean {
    val source = attribution ?: return false
    val imagePath = imageUrl.substringBefore('?').substringBefore('#')
    return id.startsWith("dz-algerian-dzirielle-") &&
        isHttpsImageAtHost(imageUrl, "www.dzirielle.com", "/saveurs/") &&
        imagePath.endsWith(".webp", ignoreCase = true) &&
        isHttpsImageAtHost(source.sourceUrl, "www.dzirielle.com", "/recette/") &&
        source.credit.equals("Dzirielle", ignoreCase = true)
}

/** The URL host and path must both match; lookalike domains are never accepted. */
private fun isHttpsImageAtHost(url: String, expectedHost: String, expectedPathPrefix: String): Boolean {
    val normalized = url.trim()
    if (!normalized.startsWith("https://", ignoreCase = true)) return false
    val remainder = normalized.substringAfter("https://", missingDelimiterValue = "")
    val pathStart = remainder.indexOf('/')
    if (pathStart <= 0) return false
    val authority = remainder.substring(0, pathStart)
    val path = remainder.substring(pathStart).substringBefore('?').substringBefore('#')
    return authority.equals(expectedHost, ignoreCase = true) &&
        path.startsWith(expectedPathPrefix, ignoreCase = true)
}

/** Converts a known Creative Commons short name to its canonical public licence URL. */
fun canonicalRecipeImageLicenseUrl(name: String): String? {
    val normalized = name.trim().uppercase()
    if (normalized == "CC0" || normalized == "CC0 1.0") {
        return "https://creativecommons.org/publicdomain/zero/1.0/"
    }
    if (normalized == "PUBLIC DOMAIN") {
        return "https://creativecommons.org/publicdomain/mark/1.0/"
    }
    val match = Regex("^CC BY(-SA)? (1\\.0|2\\.0|2\\.5|3\\.0|4\\.0)$")
        .matchEntire(normalized) ?: return null
    val licenceType = if (match.groupValues[1].isBlank()) "by" else "by-sa"
    return "https://creativecommons.org/licenses/$licenceType/${match.groupValues[2]}/"
}

/** CC BY and CC BY-SA require author credit; CC0 and Public Domain Mark do not. */
fun recipeImageLicenseRequiresAttribution(name: String): Boolean {
    val canonicalUrl = canonicalRecipeImageLicenseUrl(name) ?: return false
    return canonicalUrl.contains("/licenses/by", ignoreCase = true)
}

/** The display name must resolve to the exact canonical Creative Commons URL. */
fun isReusableRecipeImageLicense(name: String, url: String): Boolean {
    val canonicalUrl = canonicalRecipeImageLicenseUrl(name) ?: return false
    val suppliedUrl = url.trim().lowercase().substringBefore('#').substringBefore('?')
    return suppliedUrl == canonicalUrl
}

/** Image rights shown alongside the photo, respecting per-photo overrides. */
fun MealRecipe.recipeImageCredit(): String = when {
    hasDzirielleHostedImage() -> attribution?.credit.orEmpty()
    imageSourcePageUrl.startsWith("https://commons.wikimedia.org/wiki/File:", ignoreCase = true) ->
        imageCredit.ifBlank { imageAuthor }
    isHttpsImageAtHost(imageUrl, "api.openverse.org", "/v1/images/") ->
        imageCredit.ifBlank { imageAuthor }
    id.startsWith("focus:") -> attribution?.credit.orEmpty()
    id.startsWith("unitools:") -> imageAuthor.ifBlank { attribution?.credit.orEmpty() }
    else -> ""
}

fun MealRecipe.recipeImageLicense(): String = when {
    imageSourcePageUrl.startsWith("https://commons.wikimedia.org/wiki/File:", ignoreCase = true) ->
        imageLicense
    isHttpsImageAtHost(imageUrl, "api.openverse.org", "/v1/images/") -> imageLicense
    id.startsWith("focus:") -> attribution?.license.orEmpty()
    id.startsWith("unitools:") -> imageLicense
    else -> ""
}

fun MealRecipe.recipeImageLicenseUrl(): String = when {
    imageSourcePageUrl.startsWith("https://commons.wikimedia.org/wiki/File:", ignoreCase = true) ->
        imageLicenseUrl
    isHttpsImageAtHost(imageUrl, "api.openverse.org", "/v1/images/") -> imageLicenseUrl
    id.startsWith("focus:") -> attribution?.licenseUrl.orEmpty()
    id.startsWith("unitools:") ->
        imageLicenseUrl.ifBlank { canonicalRecipeImageLicenseUrl(imageLicense).orEmpty() }
    else -> ""
}

fun MealRecipe.recipeImageSourceUrl(): String =
    imageSourcePageUrl.ifBlank { attribution?.sourceUrl.orEmpty() }

data class RecipeAttribution(
    val sourceTitle: String,
    val sourceUrl: String,
    val revisionUrl: String,
    val historyUrl: String,
    val credit: String,
    val sourceLicense: String,
    val license: String,
    val licenseUrl: String,
    val changes: String
)

const val MEALS_BACKGROUND_CLASSIC = "classic"
const val MEALS_BACKGROUND_PINK = "pink"
const val MEALS_BACKGROUND_BLUE = "blue"

data class AppPreferences(
    val weeklyBudgetDzd: Int = 15_000,
    val wilaya: String = "الجزائر",
    val householdServings: Int = 3,
    val allergyIds: Set<String> = emptySet(),
    val mealSlots: Set<String> = setOf("lunch", "dinner"),
    val dislikedIngredientIds: Set<String> = emptySet(),
    val dietPattern: String = "none",
    val maxCookingMinutes: Int = 60,
    val budgetBufferPercent: Int = 5,
    val hasCompletedSetup: Boolean = false,
    val commune: String = "",
    val shoppingChannel: String = "market",
    val preferredShop: String = "",
    // Append new preferences here to preserve existing positional constructor calls.
    val appLanguage: String = "ar-DZ",
    val kitchenBackground: String = MEALS_BACKGROUND_PINK,
    val meatPreference: String = "regular",
    val monthlyMeatGrams: Int = 3_000,
    val oilPreference: String = "regular"
)

data class PantryItem(
    val id: String,
    val ingredientId: String,
    val nameAr: String,
    val quantityBase: Double?,
    val unit: String,
    val isApproximate: Boolean,
    val note: String,
    val expiryAtEpochMillis: Long?,
    val categoryId: String = FoodTaxonomyIds.OTHER_CATEGORY,
    val subcategoryId: String = FoodTaxonomyIds.OTHER_SUBCATEGORY
)

data class ShoppingListItem(
    val id: String,
    val ingredientId: String?,
    val name: String,
    val requiredQuantity: Double,
    val pantryQuantity: Double,
    val purchaseQuantity: Double,
    val unit: String,
    val consumedValueDzd: Double?,
    val cashOutlayDzd: Double?,
    val priceLabel: String?,
    val sourceLabel: String?,
    val isApproximatePantry: Boolean,
    val checked: Boolean,
    val userAdded: Boolean,
    val category: String = "أخرى",
    val storeNote: String = "",
    val isUnavailable: Boolean = false,
    val actualSpentDzd: Double? = null,
    val categoryId: String = FoodTaxonomyIds.OTHER_CATEGORY,
    val subcategoryId: String = FoodTaxonomyIds.OTHER_SUBCATEGORY,
    /** Ephemeral verified market range; recalculated from immutable offers on each snapshot. */
    val marketCashOutlayMinDzd: Double? = null,
    val marketCashOutlayMaxDzd: Double? = null,
    val marketConsumedValueMinDzd: Double? = null,
    val marketConsumedValueMaxDzd: Double? = null,
    val marketPriceOfferCount: Int = 0,
    val marketPriceMinPackageCount: Int? = null,
    val marketPriceMaxPackageCount: Int? = null,
    val marketPriceWilaya: String? = null,
    val marketPriceStoreNames: List<String> = emptyList(),
    val marketPriceSourceNotices: List<String> = emptyList(),
    val marketPriceObservedAtEpochMillis: Long? = null,
    val marketPriceIncludesLimitedAvailability: Boolean = false,
    /** Plan-scoped adjustment; not a shared household stock count. */
    val alreadyHaveQuantity: Double = 0.0,
    val alreadyHaveEnough: Boolean = false
)

data class BasketSummary(
    val items: List<ShoppingListItem> = emptyList(),
    val consumedValueDzd: Double = 0.0,
    val purchaseOutlayDzd: Double = 0.0,
    val unknownPriceCount: Int = 0,
    val hasDemoPrices: Boolean = false,
    val actualSpendDzd: Double = 0.0,
    /** Present only when at least one line uses a verified local market range. */
    val purchaseOutlayMinDzd: Double? = null,
    val purchaseOutlayMaxDzd: Double? = null
)

object MealSlots {
    val ids: List<String> = listOf("breakfast", "lunch", "dinner")
}

data class PlannedMeal(
    val id: String,
    val weekStartIso: String,
    val dayIndex: Int,
    val mealSlot: String,
    val recipeId: String?,
    val servings: Int,
    val locked: Boolean,
    val isCooked: Boolean = false,
    val isUserEdited: Boolean = false
)

data class PriceObservationSummary(
    val id: String,
    val ingredientId: String,
    val ingredientNameAr: String,
    val packQuantityBase: Double,
    val unit: String,
    val packPriceDzd: Double,
    val sourceLabel: String,
    val locationLabel: String,
    val observedAtEpochMillis: Long?,
    val priceKind: String,
    val confidenceLabel: String,
    val isStale: Boolean = false,
    val categoryId: String = FoodTaxonomyIds.OTHER_CATEGORY,
    val subcategoryId: String = FoodTaxonomyIds.OTHER_SUBCATEGORY
)

data class PlanRunSummary(
    val engineVersion: String,
    val generatedAtEpochMillis: Long,
    val priceSnapshotAtEpochMillis: Long,
    val budgetDzd: Int,
    val wilaya: String,
    val mealSlots: Set<String>
)

data class MealsSnapshot(
    val recipes: List<MealRecipe> = emptyList(),
    val ingredients: List<IngredientMaster> = emptyList(),
    val plannedMeals: List<PlannedMeal> = emptyList(),
    val pantry: List<PantryItem> = emptyList(),
    val shoppingItems: List<ShoppingListItem> = emptyList(),
    val priceObservations: List<PriceObservationSummary> = emptyList(),
    val favoriteRecipeIds: Set<String> = emptySet(),
    val planRun: PlanRunSummary? = null,
    val basket: BasketSummary = BasketSummary(),
    val foodCategories: List<FoodCategory> = emptyList(),
    val foodSubcategories: List<FoodSubcategory> = emptyList()
)

/** Applies the dataset's linear, sublinear/damped or fixed ingredient rule. */
fun scaleRecipeIngredientQuantity(quantity: Double, servingsFactor: Double, scalingRule: String): Double {
    require(servingsFactor > 0.0) { "servingsFactor must be positive" }
    val appliedFactor = when (scalingRule.trim().lowercase()) {
        "fixed" -> 1.0
        "sublinear", "damped" -> sqrt(servingsFactor)
        else -> servingsFactor
    }
    return quantity * appliedFactor
}


data class RecipeImageCandidate(
    val title: String,
    val imageUrl: String,
    val sourcePageUrl: String,
    val author: String,
    val credit: String,
    val license: String,
    val licenseUrl: String,
    val width: Int,
    val height: Int
)
