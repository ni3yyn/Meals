package dz.meals.app.domain

import dz.meals.app.data.BasketSummary
import dz.meals.app.data.MealRecipe
import dz.meals.app.data.PlannedMeal
import dz.meals.app.data.ShoppingListItem
import dz.meals.app.data.local.PantryItemEntity
import dz.meals.app.data.local.PriceObservationEntity
import kotlin.math.ceil

private data class RequirementKey(val ingredientId: String, val unit: String)

private const val MAX_PRICE_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L

fun shouldPreserveMeal(
    meal: PlannedMeal,
    fillEmptyOnly: Boolean
): Boolean =
    meal.locked || meal.isCooked || meal.isUserEdited || (fillEmptyOnly && meal.recipeId != null)

fun pricesForSavedPlan(
    savedPriceSnapshot: List<PriceObservationEntity>,
    latestObservations: List<PriceObservationEntity>,
    snapshotAtEpochMillis: Long
): List<PriceObservationEntity> =
    if (snapshotAtEpochMillis > 0L) savedPriceSnapshot else latestObservations

fun exactPantryDeduction(requiredQuantity: Double, availableQuantity: Double?, approximate: Boolean): Double {
    if (approximate || availableQuantity == null || requiredQuantity <= 0.0 || availableQuantity <= 0.0) return 0.0
    return minOf(requiredQuantity, availableQuantity)
}

fun isRecipeAllowed(
    recipe: MealRecipe,
    ingredientCategories: Map<String, String>,
    allergyIds: Set<String>,
    dietPattern: String
): Boolean {
    if (recipe.allergens.intersect(allergyIds).isNotEmpty()) return false
    return when (dietPattern) {
        "vegetarian" -> recipe.ingredients.none {
            ingredientCategories[it.ingredientId] == "لحوم ودواجن"
        }
        "vegan" -> recipe.ingredients.none {
            ingredientCategories[it.ingredientId] in setOf("لحوم ودواجن", "ألبان وبيض")
        }
        else -> true
    }
}

fun softPreferencePenalty(
    recipe: MealRecipe,
    dislikedIngredientIds: Set<String>,
    maxCookingMinutes: Int
): Double {
    val dislikedPenalty = recipe.ingredients
        .filter { it.ingredientId in dislikedIngredientIds }
        .sumOf { it.quantityBase.coerceAtMost(500.0) } * 2.0
    val timePenalty = (recipe.durationMinutes - maxCookingMinutes.coerceIn(10, 240))
        .coerceAtLeast(0) * 25.0
    return dislikedPenalty + timePenalty
}

fun findPinnedDietConflict(
    pinnedMeals: List<PlannedMeal>,
    recipes: List<MealRecipe>,
    ingredientCategories: Map<String, String>,
    dietPattern: String
): String? {
    val recipesById = recipes.associateBy { it.id }
    return pinnedMeals.firstNotNullOfOrNull { meal ->
        val recipe = meal.recipeId?.let(recipesById::get) ?: return@firstNotNullOfOrNull null
        recipe.id.takeIf { !isRecipeAllowed(recipe, ingredientCategories, emptySet(), dietPattern) }
    }
}

fun findLockedAllergyConflict(
    lockedMeals: List<PlannedMeal>,
    recipes: List<MealRecipe>,
    allergyIds: Set<String>
): String? {
    if (allergyIds.isEmpty()) return null
    val recipesById = recipes.associateBy { it.id }
    return lockedMeals.firstNotNullOfOrNull { meal ->
        val recipe = meal.recipeId?.let(recipesById::get) ?: return@firstNotNullOfOrNull null
        recipe.id.takeIf { recipe.allergens.intersect(allergyIds).isNotEmpty() }
    }
}

fun calculateBasket(
    plannedMeals: List<PlannedMeal>,
    recipes: List<MealRecipe>,
    pantry: List<PantryItemEntity>,
    prices: List<PriceObservationEntity>,
    checkedItemIds: Set<String> = emptySet(),
    selectedWilaya: String? = null,
    nowEpochMillis: Long = System.currentTimeMillis()
): BasketSummary {
    val recipesById = recipes.associateBy { it.id }
    val required = linkedMapOf<RequirementKey, Double>()

    plannedMeals.forEach { meal ->
        val recipe = meal.recipeId?.let(recipesById::get) ?: return@forEach
        val factor = meal.servings.toDouble() / recipe.baseServings.coerceAtLeast(1)
        recipe.ingredients.filterNot { it.optional }.forEach { ingredient ->
            val key = RequirementKey(ingredient.ingredientId, ingredient.unit)
            required[key] = (required[key] ?: 0.0) + ingredient.quantityBase * factor
        }
    }

    val pantryByKey = pantry.groupBy { RequirementKey(it.ingredientId, it.unit) }
    val priceByIngredient = prices.groupBy { it.ingredientId }
    val items = required.map { (key, amount) ->
        val pantryRows = pantryByKey[key].orEmpty()
        val exactStock = pantryRows.filter { !it.isApproximate && it.quantityBase != null }
            .sumOf { it.quantityBase ?: 0.0 }
        val approximateStock = pantryRows.any { it.isApproximate || it.quantityBase == null }
        val pantryUsed = minOf(amount, exactStock)
        val netRequired = (amount - pantryUsed).coerceAtLeast(0.0)
        val compatiblePrices = priceByIngredient[key.ingredientId]
            .orEmpty()
            .filter { it.unit == key.unit && it.packQuantityBase > 0.0 && it.packPriceDzd > 0.0 }
        val freshPrices = compatiblePrices.filter { observation ->
            if (observation.priceKind == "demo") {
                true
            } else {
                val observedAt = observation.observedAtEpochMillis
                observedAt != null &&
                    nowEpochMillis >= observedAt &&
                    nowEpochMillis - observedAt <= MAX_PRICE_AGE_MILLIS
            }
        }
        val relevantPrices = if (selectedWilaya.isNullOrBlank()) {
            freshPrices
        } else {
            freshPrices.filter {
                it.priceKind == "demo" ||
                    it.locationLabel.equals(selectedWilaya, ignoreCase = true) ||
                    it.locationLabel.equals("وطني", ignoreCase = true)
            }
        }
        val price = relevantPrices.maxWithOrNull(
            compareBy<PriceObservationEntity> {
                when {
                    it.priceKind == "demo" -> 1
                    !selectedWilaya.isNullOrBlank() && it.locationLabel.equals(selectedWilaya, ignoreCase = true) -> 3
                    it.locationLabel.equals("وطني", ignoreCase = true) -> 2
                    else -> 0
                }
            }.thenBy { it.observedAtEpochMillis ?: 0L }
        )

        val consumedValue = price?.let { amount / it.packQuantityBase * it.packPriceDzd }
        val packs = price?.let { if (netRequired <= 0.0) 0.0 else ceil(netRequired / it.packQuantityBase) }
        val purchaseQuantity = if (price != null && packs != null) packs * price.packQuantityBase else netRequired
        val cashOutlay = if (price != null && packs != null) packs * price.packPriceDzd else null
        val id = key.ingredientId + "::" + key.unit

        ShoppingListItem(
            id = id,
            ingredientId = key.ingredientId,
            name = key.ingredientId,
            requiredQuantity = amount,
            pantryQuantity = pantryUsed,
            purchaseQuantity = purchaseQuantity,
            unit = key.unit,
            consumedValueDzd = consumedValue,
            cashOutlayDzd = cashOutlay,
            priceLabel = price?.priceKind?.let { if (it == "demo") "قيمة توضيحية" else "ملاحظة سعر" },
            sourceLabel = price?.sourceLabel,
            isApproximatePantry = approximateStock,
            checked = id in checkedItemIds,
            userAdded = false
        )
    }

    val priced = items.filter { it.cashOutlayDzd != null }
    return BasketSummary(
        items = items,
        consumedValueDzd = items.sumOf { it.consumedValueDzd ?: 0.0 },
        purchaseOutlayDzd = priced.sumOf { it.cashOutlayDzd ?: 0.0 },
        unknownPriceCount = items.count { it.cashOutlayDzd == null && it.purchaseQuantity > 0.0 },
        hasDemoPrices = prices.any { it.priceKind == "demo" }
    )
}

fun scaleIngredientQuantity(quantity: Double, baseServings: Int, targetServings: Int): Double {
    require(baseServings > 0) { "baseServings must be positive" }
    require(targetServings > 0) { "targetServings must be positive" }
    return quantity * targetServings.toDouble() / baseServings.toDouble()
}
