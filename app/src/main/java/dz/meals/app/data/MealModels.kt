package dz.meals.app.data

data class Ingredient(val id: String, val name: String, val quantity: Double, val unit: String)

data class MealRecipe(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val durationMinutes: Int,
    val servings: Int,
    val estimatedCostDzd: Int,
    val ingredients: List<Ingredient>
)

data class AppPreferences(
    val weeklyBudgetDzd: Int = 15_000,
    val wilaya: String = "الجزائر",
    val plannedRecipeIds: Set<String> = emptySet(),
    val checkedIngredientIds: Set<String> = emptySet()
)

data class ShoppingListItem(
    val ingredientId: String,
    val name: String,
    val quantity: Double,
    val unit: String
)
