package dz.meals.app.data

/**
 * One recipe-line contribution to a plan-wide ingredient requirement.
 * periodAnchorIso is the weekly Monday or monthly first day; dayIndex resolves
 * the exact calendar date. ingredientLineIndex identifies the recipe source row.
 */
data class IngredientDemandContribution(
    val ingredientId: String,
    val mealId: String,
    val recipeId: String,
    val periodAnchorIso: String,
    val dayIndex: Int,
    val mealSlot: String,
    val servings: Int,
    val recipeBaseServings: Int,
    val ingredientLineIndex: Int,
    val sourceQuantity: Double,
    val sourceUnit: String,
    val scaledQuantity: Double,
    val demandUnit: String
)

data class IngredientDemand(
    val ingredientId: String,
    val quantity: Double,
    val unit: String,
    val contributions: List<IngredientDemandContribution>
)

private data class IngredientDemandKey(val ingredientId: String, val unitFamily: String)
private data class IngredientUnitDefinition(
    val familyKey: String,
    val canonicalUnit: String,
    val multiplierToCanonical: Double
)
private data class CalculatedIngredientLine(
    val key: IngredientDemandKey,
    val contribution: IngredientDemandContribution
)

/**
 * Deterministic, side-effect-free demand calculation for weekly and monthly plans.
 * Optional and unquantified ingredients stay visible in recipes but do not create
 * misleading shopping requirements.
 */
object IngredientDemandCalculator {
    fun calculate(plannedMeals: List<PlannedMeal>, recipes: List<MealRecipe>): List<IngredientDemand> {
        if (plannedMeals.isEmpty() || recipes.isEmpty()) return emptyList()
        val recipesById = recipes.associateBy { it.id }
        val lines = buildList {
            plannedMeals.sortedWith(
                compareBy<PlannedMeal> { it.weekStartIso }
                    .thenBy { it.dayIndex }
                    .thenBy { it.mealSlot }
                    .thenBy { it.id }
                    .thenBy { it.recipeId.orEmpty() }
            ).forEach { meal ->
                val recipeId = meal.recipeId ?: return@forEach
                val recipe = recipesById[recipeId] ?: return@forEach
                val baseServings = recipe.baseServings.coerceAtLeast(1)
                val servings = meal.servings.coerceAtLeast(1)
                val factor = servings.toDouble() / baseServings.toDouble()
                if (!factor.isFinite() || factor <= 0.0) return@forEach

                recipe.ingredients.forEachIndexed { lineIndex, ingredient ->
                    if (ingredient.optional ||
                        ingredient.ingredientId.isBlank() ||
                        !ingredient.quantityBase.isFinite() ||
                        ingredient.quantityBase <= 0.0
                    ) return@forEachIndexed

                    val unit = ingredientUnitDefinition(ingredient.unit)
                    val scaledSource = scaleRecipeIngredientQuantity(
                        ingredient.quantityBase,
                        factor,
                        ingredient.scalingRule
                    )
                    val canonicalQuantity = scaledSource * unit.multiplierToCanonical
                    if (!canonicalQuantity.isFinite() || canonicalQuantity <= 0.0) {
                        return@forEachIndexed
                    }
                    val contribution = IngredientDemandContribution(
                        ingredientId = ingredient.ingredientId,
                        mealId = meal.id,
                        recipeId = recipe.id,
                        periodAnchorIso = meal.weekStartIso,
                        dayIndex = meal.dayIndex,
                        mealSlot = meal.mealSlot,
                        servings = servings,
                        recipeBaseServings = baseServings,
                        ingredientLineIndex = lineIndex,
                        sourceQuantity = ingredient.quantityBase,
                        sourceUnit = ingredient.unit.trim(),
                        scaledQuantity = canonicalQuantity,
                        demandUnit = unit.canonicalUnit
                    )
                    add(
                        CalculatedIngredientLine(
                            IngredientDemandKey(ingredient.ingredientId, unit.familyKey),
                            contribution
                        )
                    )
                }
            }
        }
        if (lines.isEmpty()) return emptyList()

        return lines.groupBy { it.key }.entries
            .sortedWith(compareBy({ it.key.ingredientId }, { it.key.unitFamily }))
            .map { (key, group) ->
                val ordered = group.map { it.contribution }.sortedWith(
                    compareBy<IngredientDemandContribution> { it.periodAnchorIso }
                        .thenBy { it.dayIndex }
                        .thenBy { it.mealSlot }
                        .thenBy { it.mealId }
                        .thenBy { it.recipeId }
                        .thenBy { it.ingredientLineIndex }
                        .thenBy { it.sourceUnit }
                        .thenBy { it.sourceQuantity }
                )
                val total = ordered.sumOf { it.scaledQuantity }
                IngredientDemand(
                    ingredientId = key.ingredientId,
                    quantity = total,
                    unit = ordered.first().demandUnit,
                    contributions = ordered
                )
            }
            .filter { it.quantity.isFinite() && it.quantity > 0.0 }
    }
}

/** Convert quantities only within a known compatible unit dimension. */
fun convertIngredientQuantity(quantity: Double, fromUnit: String, toUnit: String): Double? {
    if (!quantity.isFinite()) return null
    val from = ingredientUnitDefinition(fromUnit)
    val to = ingredientUnitDefinition(toUnit)
    if (from.familyKey != to.familyKey) return null
    val converted = quantity * from.multiplierToCanonical / to.multiplierToCanonical
    return converted.takeIf { it.isFinite() && it >= 0.0 }
}

private fun ingredientUnitDefinition(rawUnit: String): IngredientUnitDefinition {
    val normalized = rawUnit.trim().lowercase()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ")
    return when (normalized) {
        "g", "gram", "grams", "gramme", "grammes", "غ", "غرام", "غرامات", "جرام", "جرامات" ->
            IngredientUnitDefinition("mass", "g", 1.0)
        "kg", "kilogram", "kilograms", "kilogramme", "kilogrammes", "كغ", "كيلوغرام", "كيلوغرامات", "كيلو" ->
            IngredientUnitDefinition("mass", "g", 1_000.0)
        "mg", "milligram", "milligrams", "ملغ" ->
            IngredientUnitDefinition("mass", "g", 0.001)
        "ml", "milliliter", "milliliters", "millilitre", "millilitres", "مل", "ملل" ->
            IngredientUnitDefinition("volume", "ml", 1.0)
        "l", "liter", "liters", "litre", "litres", "ل", "لتر", "لترات" ->
            IngredientUnitDefinition("volume", "ml", 1_000.0)
        "cl" -> IngredientUnitDefinition("volume", "ml", 10.0)
        "tsp", "teaspoon", "teaspoons", "ملعقة صغيرة", "م ص" ->
            IngredientUnitDefinition("volume", "ml", 5.0)
        "tbsp", "tablespoon", "tablespoons", "ملعقة كبيرة", "م ك" ->
            IngredientUnitDefinition("volume", "ml", 15.0)
        "piece", "pieces", "pc", "pcs", "unit", "units", "each", "قطعة", "قطعه", "حبة", "حبه", "وحدة", "وحدات" ->
            IngredientUnitDefinition("count", "piece", 1.0)
        else -> IngredientUnitDefinition("custom:$normalized", normalized, 1.0)
    }
}
