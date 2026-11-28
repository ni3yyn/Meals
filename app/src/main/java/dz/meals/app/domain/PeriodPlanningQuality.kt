package dz.meals.app.domain

import kotlin.math.ceil

/**
 * A bounded retry budget keeps whole-period candidate scoring predictable.
 * Larger horizons cost more per pass because the complete basket is rescored.
 */
fun periodSearchAttempts(slotCount: Int): Int {
    require(slotCount > 0) { "A planning period must contain at least one selected slot." }
    return when {
        slotCount >= 72 -> 2
        slotCount >= 42 -> 3
        slotCount >= 28 -> 4
        slotCount >= 18 -> 6
        slotCount >= 12 -> 8
        else -> 10
    }
}

/**
 * Score the use of shared pantry staples across different meal recipes.
 *
 * A lower score is better. Reusing a limited number of non-protein staples
 * lowers shopping fragmentation, while very high-frequency staples receive
 * a small saturation penalty so the period does not become monotonous.
 * Protein and oil categories are excluded by the repository caller.
 */
fun scorePeriodIngredientReuse(
    mealIngredientIds: List<Set<String>>,
    maxRewardedSharedIngredients: Int = 12
): Double {
    require(maxRewardedSharedIngredients >= 0) {
        "The reusable-ingredient reward cap cannot be negative."
    }
    if (mealIngredientIds.size < 2 || maxRewardedSharedIngredients == 0) return 0.0

    val usageCounts = mealIngredientIds
        .flatMap { ingredients -> ingredients.filter { it.isNotBlank() }.toSet() }
        .groupingBy { it }
        .eachCount()
    val reusedIngredientCount = usageCounts.values.count { it >= 2 }
    val reward = reusedIngredientCount.coerceAtMost(maxRewardedSharedIngredients) * 120.0
    val saturationThreshold = ceil(mealIngredientIds.size * 0.75).toInt().coerceAtLeast(2)
    val saturationPenalty = usageCounts.values.sumOf { useCount ->
        (useCount - saturationThreshold).coerceAtLeast(0) * 35.0
    }
    return -reward + saturationPenalty
}
