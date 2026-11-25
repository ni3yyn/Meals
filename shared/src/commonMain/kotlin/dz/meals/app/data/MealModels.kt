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

data class AppPreferences(
    val weeklyBudgetDzd: Int = 15_000,
    val wilaya: String = "الجزائر",
    val householdServings: Int = 3,
    val allergyIds: Set<String> = emptySet(),
    val mealSlots: Set<String> = setOf("breakfast", "lunch", "dinner"),
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
    val kitchenBackground: String = MEALS_BACKGROUND_PINK
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
    val subcategoryId: String = FoodTaxonomyIds.OTHER_SUBCATEGORY
)

data class BasketSummary(
    val items: List<ShoppingListItem> = emptyList(),
    val consumedValueDzd: Double = 0.0,
    val purchaseOutlayDzd: Double = 0.0,
    val unknownPriceCount: Int = 0,
    val hasDemoPrices: Boolean = false,
    val actualSpendDzd: Double = 0.0
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
