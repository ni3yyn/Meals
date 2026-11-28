package dz.meals.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

enum class PlanningPeriodType(val storageValue: String) {
    WEEK("WEEK"),
    MONTH("MONTH");

    companion object {
        fun fromStorage(value: String): PlanningPeriodType? =
            entries.firstOrNull { it.storageValue == value }
    }
}

data class PlanningDateRange(
    val periodId: String,
    val type: PlanningPeriodType,
    val startDate: LocalDate,
    val endDate: LocalDate
) {
    val dayCount: Int
        get() = (endDate.toEpochDay() - startDate.toEpochDay() + 1L).toInt()

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDate) && !date.isAfter(endDate)
}

/**
 * Resolve a selected date into its canonical planning period.
 *
 * Week periods always start on Monday. Month periods always start on day one
 * and extend through the actual last day of that calendar month.
 */
fun resolvePlanningDateRange(
    type: PlanningPeriodType,
    selectedDateIso: String
): PlanningDateRange {
    val selectedDate = try {
        LocalDate.parse(selectedDateIso)
    } catch (error: DateTimeParseException) {
        throw IllegalArgumentException("Invalid planning date: $selectedDateIso", error)
    }

    val start: LocalDate
    val end: LocalDate
    when (type) {
        PlanningPeriodType.WEEK -> {
            start = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            end = start.plusDays(6)
        }
        PlanningPeriodType.MONTH -> {
            val month = YearMonth.from(selectedDate)
            start = month.atDay(1)
            end = month.atEndOfMonth()
        }
    }

    val id = "${type.storageValue}:${start}"
    return PlanningDateRange(
        periodId = id,
        type = type,
        startDate = start,
        endDate = end
    )
}

data class PlanIngredientAdjustment(
    val type: Type,
    /** Approximate quantity already available, in the recipe aggregate's unit. */
    val availableQuantity: Double = 0.0
) {
    enum class Type {
        SOME,
        ENOUGH
    }
}

/**
 * Convert the UI's plan-only adjustment into the quantity still to buy.
 * The supplied quantity must already be in the same compatible unit as the
 * aggregated recipe requirement. This function never stores or carries stock.
 */
fun calculatePurchaseNeed(
    requiredQuantity: Double,
    adjustment: PlanIngredientAdjustment?
): Double {
    require(requiredQuantity.isFinite() && requiredQuantity >= 0.0) {
        "Required quantity must be finite and non-negative."
    }
    if (adjustment == null) return requiredQuantity

    return when (adjustment.type) {
        PlanIngredientAdjustment.Type.ENOUGH -> 0.0
        PlanIngredientAdjustment.Type.SOME -> {
            require(adjustment.availableQuantity.isFinite() && adjustment.availableQuantity >= 0.0) {
                "Available quantity must be finite and non-negative."
            }
            (requiredQuantity - adjustment.availableQuantity.coerceAtMost(requiredQuantity))
                .coerceAtLeast(0.0)
        }
    }
}

/** Validate rows before persisting a plan-scoped adjustment. */
fun validatePlanIngredientAdjustment(
    adjustment: PlanIngredientAdjustment
): Boolean = when (adjustment.type) {
    PlanIngredientAdjustment.Type.ENOUGH -> adjustment.availableQuantity == 0.0
    PlanIngredientAdjustment.Type.SOME ->
        adjustment.availableQuantity.isFinite() && adjustment.availableQuantity > 0.0
}
