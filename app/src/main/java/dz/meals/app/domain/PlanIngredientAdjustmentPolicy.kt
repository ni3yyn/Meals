package dz.meals.app.domain

/**
 * User adjustments apply only to one saved planning period. They are not
 * household inventory and must never be copied to another period implicitly.
 */
data class PlanIngredientNeed(
    val ingredientId: String,
    val unit: String,
    val requiredQuantity: Double,
    val alreadyHaveQuantity: Double = 0.0,
    val alreadyHaveEnough: Boolean = false
) {
    val purchaseQuantity: Double
        get() {
            if (!requiredQuantity.isFinite() || requiredQuantity <= 0.0) return 0.0
            if (alreadyHaveEnough) return 0.0
            val available = if (alreadyHaveQuantity.isFinite()) alreadyHaveQuantity.coerceAtLeast(0.0) else 0.0
            return (requiredQuantity - available).coerceAtLeast(0.0)
        }

    val coveredQuantity: Double
        get() = (requiredQuantity.coerceAtLeast(0.0) - purchaseQuantity).coerceAtLeast(0.0)
}

object PlanIngredientAdjustmentPolicy {
    fun apply(
        ingredientId: String,
        unit: String,
        requiredQuantity: Double,
        alreadyHaveQuantity: Double = 0.0,
        alreadyHaveEnough: Boolean = false
    ): PlanIngredientNeed {
        require(ingredientId.isNotBlank()) { "ingredientId must not be blank" }
        require(unit.isNotBlank()) { "unit must not be blank" }
        require(requiredQuantity.isFinite() && requiredQuantity >= 0.0) { "requiredQuantity must be finite and non-negative" }
        require(alreadyHaveQuantity.isFinite() && alreadyHaveQuantity >= 0.0) { "alreadyHaveQuantity must be finite and non-negative" }
        return PlanIngredientNeed(
            ingredientId = ingredientId,
            unit = unit.trim(),
            requiredQuantity = requiredQuantity,
            alreadyHaveQuantity = alreadyHaveQuantity,
            alreadyHaveEnough = alreadyHaveEnough
        )
    }

    /** A stale adjustment cannot be applied to a different unit or ingredient. */
    fun adjustmentFor(
        ingredientId: String,
        unit: String,
        adjustments: List<PlanIngredientNeed>
    ): PlanIngredientNeed? = adjustments.firstOrNull {
        it.ingredientId == ingredientId && it.unit == unit
    }
}
