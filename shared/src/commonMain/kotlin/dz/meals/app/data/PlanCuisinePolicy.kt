package dz.meals.app.data

/**
 * Central cuisine gate for recipes that may enter a meal plan.
 *
 * TEMPORARY DEFAULT: Midati currently plans from the dedicated Algerian
 * Dzirielle collection only. When cuisine selection is added to Settings,
 * replace this fixed matcher with the selected cuisine IDs and keep the
 * generator, manual planner and add-to-plan entry points calling this policy.
 */
object PlanCuisinePolicy {
    const val ALGERIAN_RECIPE_ID_PREFIX = "dz-algerian-dzirielle-"

    fun allows(recipe: MealRecipe): Boolean = allowsRecipeId(recipe.id)

    fun allowsRecipeId(recipeId: String): Boolean =
        recipeId.startsWith(ALGERIAN_RECIPE_ID_PREFIX)
}
