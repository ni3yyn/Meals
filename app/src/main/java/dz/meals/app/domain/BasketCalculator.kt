package dz.meals.app.domain

import dz.meals.app.data.scaleRecipeIngredientQuantity

import dz.meals.app.data.BasketSummary
import dz.meals.app.data.FoodTaxonomyIds
import dz.meals.app.data.FoodTaxonomyRef
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
    if (snapshotAtEpochMillis > 0L && savedPriceSnapshot.isNotEmpty()) savedPriceSnapshot
    else latestObservations

fun exactPantryDeduction(requiredQuantity: Double, availableQuantity: Double?, approximate: Boolean): Double {
    if (approximate || availableQuantity == null || requiredQuantity <= 0.0 || availableQuantity <= 0.0) return 0.0
    return minOf(requiredQuantity, availableQuantity)
}

private val vegetarianExcludedCategoryIds = setOf(
    "foodcat-meat",
    "foodcat-poultry",
    "foodcat-seafood"
)

private val vegetarianExcludedSubcategoryIds = setOf(
    "foodsub-canned-fish",
    "foodsub-frozen-meat-fish",
    "foodsub-oils-animal"
)

private val veganExcludedCategoryIds = vegetarianExcludedCategoryIds + setOf(
    "foodcat-dairy",
    "foodcat-eggs"
)

private val veganExcludedSubcategoryIds = vegetarianExcludedSubcategoryIds + setOf(
    "foodsub-sweeteners-honey"
)

private fun FoodTaxonomyRef.isExcludedByVegetarianDiet(): Boolean =
    categoryId in vegetarianExcludedCategoryIds ||
        subcategoryId in vegetarianExcludedSubcategoryIds

private fun FoodTaxonomyRef.isExcludedByVeganDiet(): Boolean =
    categoryId in veganExcludedCategoryIds ||
        subcategoryId in veganExcludedSubcategoryIds

private val meatAndPoultryCategoryIds = setOf("foodcat-meat", "foodcat-poultry")
private val animalFleshCategoryIds = meatAndPoultryCategoryIds + "foodcat-seafood"

/** Household meat rules use canonical ingredient taxonomy, never recipe title matching. */
fun isRecipeAllowedByMeatPreference(
    recipe: MealRecipe,
    ingredientTaxonomy: Map<String, FoodTaxonomyRef>,
    meatPreference: String
): Boolean {
    if (meatPreference == "regular" || meatPreference == "monthly_limit") return true
    return recipe.ingredients.none { ingredient ->
        if (ingredient.optional || ingredient.quantityBase <= 0.0) return@none false
        val taxonomy = ingredientTaxonomy[ingredient.ingredientId] ?: return@none false
        when (meatPreference) {
            "none" -> taxonomy.categoryId in animalFleshCategoryIds
            "chicken_only" ->
                taxonomy.categoryId == "foodcat-meat" ||
                    taxonomy.categoryId == "foodcat-seafood" ||
                    (taxonomy.categoryId == "foodcat-poultry" &&
                        taxonomy.subcategoryId != "foodsub-poultry-chicken")
            else -> false
        }
    }
}

/**
 * Measurable monthly meat usage for a household-sized serving.
 * Returns null when a meat ingredient has an unknown unit, rather than
 * silently treating an unquantified ingredient as zero consumption.
 */
fun recipeMeatQuantityGrams(
    recipe: MealRecipe,
    servings: Int,
    ingredientTaxonomy: Map<String, FoodTaxonomyRef>
): Double? {
    val factor = servings.coerceAtLeast(1).toDouble() / recipe.baseServings.coerceAtLeast(1)
    var totalGrams = 0.0
    recipe.ingredients.filter { !it.optional && it.quantityBase > 0.0 }.forEach { ingredient ->
        val categoryId = ingredientTaxonomy[ingredient.ingredientId]?.categoryId
        if (categoryId == null || categoryId !in meatAndPoultryCategoryIds) return@forEach
        val scaled = scaleRecipeIngredientQuantity(ingredient.quantityBase, factor, ingredient.scalingRule)
        val grams = when (ingredient.unit.trim().lowercase()) {
            "g", "gram", "grams" -> scaled
            "kg", "kilogram", "kilograms" -> scaled * 1_000.0
            else -> return null
        }
        totalGrams += grams
    }
    return totalGrams
}

/** Returns measured oil/fat grams-or-ml per household serving; null means unquantified. */
fun recipeOilQuantityPerServing(
    recipe: MealRecipe,
    servings: Int,
    ingredientTaxonomy: Map<String, FoodTaxonomyRef>
): Double? {
    val factor = servings.coerceAtLeast(1).toDouble() / recipe.baseServings.coerceAtLeast(1)
    var total = 0.0
    recipe.ingredients.filter { !it.optional && it.quantityBase > 0.0 }.forEach { ingredient ->
        if (ingredientTaxonomy[ingredient.ingredientId]?.categoryId != "foodcat-oils") return@forEach
        val scaled = scaleRecipeIngredientQuantity(ingredient.quantityBase, factor, ingredient.scalingRule)
        val amount = when (ingredient.unit.trim().lowercase()) {
            "g", "gram", "grams", "ml", "milliliter", "milliliters" -> scaled
            "kg", "kilogram", "kilograms", "l", "liter", "liters" -> scaled * 1_000.0
            else -> return null
        }
        total += amount
    }
    return total / servings.coerceAtLeast(1)
}

fun isRecipeAllowed(
    recipe: MealRecipe,
    ingredientTaxonomy: Map<String, FoodTaxonomyRef>,
    allergyIds: Set<String>,
    dietPattern: String
): Boolean {
    if (recipe.allergens.intersect(allergyIds).isNotEmpty()) return false

    val communityRecipe = recipe.id.startsWith("orc-")
    val hasForbiddenForVegetarian = recipe.ingredients.any { ingredient ->
        ingredientTaxonomy[ingredient.ingredientId]
            ?.isExcludedByVegetarianDiet() == true
    }
    val hasForbiddenForVegan = recipe.ingredients.any { ingredient ->
        ingredientTaxonomy[ingredient.ingredientId]
            ?.isExcludedByVeganDiet() == true
    }

    return when (dietPattern) {
        "vegetarian" -> !hasForbiddenForVegetarian &&
            (!communityRecipe || "نباتي" in recipe.tags)
        "vegan" -> !hasForbiddenForVegan &&
            (!communityRecipe || "نباتي صرف" in recipe.tags)
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
    ingredientTaxonomy: Map<String, FoodTaxonomyRef>,
    dietPattern: String
): String? {
    val recipesById = recipes.associateBy { it.id }
    return pinnedMeals.firstNotNullOfOrNull { meal ->
        val recipe = meal.recipeId?.let(recipesById::get) ?: return@firstNotNullOfOrNull null
        recipe.id.takeIf { !isRecipeAllowed(recipe, ingredientTaxonomy, emptySet(), dietPattern) }
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
    @Suppress("UNUSED_PARAMETER") pantry: List<PantryItemEntity>,
    prices: List<PriceObservationEntity>,
    checkedItemIds: Set<String> = emptySet(),
    selectedWilaya: String? = null,
    nowEpochMillis: Long = System.currentTimeMillis(),
    allowStaleSnapshotPrices: Boolean = false,
    foodTaxonomyByIngredientId: Map<String, FoodTaxonomyRef> = emptyMap()
): BasketSummary {
    val recipesById = recipes.associateBy { it.id }
    val required = linkedMapOf<RequirementKey, Double>()

    plannedMeals.forEach { meal ->
        val recipe = meal.recipeId?.let(recipesById::get) ?: return@forEach
        val factor = meal.servings.toDouble() / recipe.baseServings.coerceAtLeast(1)
        // Unquantified "to taste" / free-text amounts stay visible in recipe details,
        // but cannot create misleading zero-quantity shopping requirements.
        recipe.ingredients.filter { !it.optional && it.quantityBase > 0.0 }.forEach { ingredient ->
            val key = RequirementKey(ingredient.ingredientId, ingredient.unit)
            required[key] = (required[key] ?: 0.0) + scaleRecipeIngredientQuantity(ingredient.quantityBase, factor, ingredient.scalingRule)
        }
    }

    // Midati lists what the household needs to buy; it does not assume a
    // manually maintained stock count is current. Keep the parameter for
    // compatibility with existing snapshots and call sites.
    val priceByIngredient = prices.groupBy { it.ingredientId }
    val items = required.map { (key, amount) ->
        val pantryUsed = 0.0
        val approximateStock = false
        val netRequired = amount
        val compatiblePrices = priceByIngredient[key.ingredientId]
            .orEmpty()
            .filter { it.unit == key.unit && it.packQuantityBase > 0.0 && it.packPriceDzd > 0.0 }
        val freshPrices = compatiblePrices.filter { observation ->
            if (allowStaleSnapshotPrices || observation.priceKind == "demo") {
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
        val isHistoricalPrice = allowStaleSnapshotPrices && price != null &&
            price.priceKind != "demo" && price.observedAtEpochMillis?.let { observedAt ->
                nowEpochMillis < observedAt || nowEpochMillis - observedAt > MAX_PRICE_AGE_MILLIS
            } == true
        val observationDate = price?.observedAtEpochMillis?.let { observedAt ->
            java.time.Instant.ofEpochMilli(observedAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .toString()
        }

        val taxonomy = foodTaxonomyByIngredientId[key.ingredientId]
            ?: FoodTaxonomyRef(FoodTaxonomyIds.OTHER_CATEGORY, FoodTaxonomyIds.OTHER_SUBCATEGORY)
        ShoppingListItem(
            id = id,
            ingredientId = key.ingredientId,
            name = key.ingredientId,
            categoryId = taxonomy.categoryId,
            subcategoryId = taxonomy.subcategoryId,
            requiredQuantity = amount,
            pantryQuantity = pantryUsed,
            purchaseQuantity = purchaseQuantity,
            unit = key.unit,
            consumedValueDzd = consumedValue,
            cashOutlayDzd = cashOutlay,
            priceLabel = when {
                price == null -> null
                price.priceKind == "demo" -> "قيمة توضيحية"
                isHistoricalPrice -> "سعر تاريخي من لقطة الخطة"
                else -> "ملاحظة سعر"
            },
            sourceLabel = price?.let {
                listOfNotNull(it.sourceLabel, it.locationLabel, observationDate).joinToString(" · ")
            },
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
