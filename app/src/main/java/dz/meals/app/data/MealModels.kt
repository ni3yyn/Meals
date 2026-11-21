package dz.meals.app.data

data class IngredientMaster(
    val id: String,
    val nameAr: String,
    val nameFr: String,
    val category: String,
    val baseUnit: String,
    val allergens: Set<String>,
    val aliases: List<String>
)

data class RecipeIngredient(
    val ingredientId: String,
    val quantityBase: Double,
    val unit: String,
    val prepNote: String = "",
    val optional: Boolean = false
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
    val stepsFr: List<String>
)

data class AppPreferences(
    val weeklyBudgetDzd: Int = 15_000,
    val wilaya: String = "الجزائر",
    val householdServings: Int = 3,
    val allergyIds: Set<String> = emptySet()
)

data class PantryItem(
    val id: String,
    val ingredientId: String,
    val nameAr: String,
    val quantityBase: Double?,
    val unit: String,
    val isApproximate: Boolean,
    val note: String,
    val expiryAtEpochMillis: Long?
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
    val category: String = "أخرى"
)

data class BasketSummary(
    val items: List<ShoppingListItem> = emptyList(),
    val consumedValueDzd: Double = 0.0,
    val purchaseOutlayDzd: Double = 0.0,
    val unknownPriceCount: Int = 0,
    val hasDemoPrices: Boolean = false
)

data class PlannedMeal(
    val id: String,
    val weekStartIso: String,
    val dayIndex: Int,
    val mealSlot: String,
    val recipeId: String?,
    val servings: Int,
    val locked: Boolean
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
    val confidenceLabel: String
)

data class MealsSnapshot(
    val recipes: List<MealRecipe> = emptyList(),
    val ingredients: List<IngredientMaster> = emptyList(),
    val plannedMeals: List<PlannedMeal> = emptyList(),
    val pantry: List<PantryItem> = emptyList(),
    val shoppingItems: List<ShoppingListItem> = emptyList(),
    val priceObservations: List<PriceObservationSummary> = emptyList(),
    val basket: BasketSummary = BasketSummary()
)
