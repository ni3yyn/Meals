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
 * One date/meal-slot position in a selected planning period.
 *
 * WEEK IDs deliberately retain the legacy weekStart-dayIndex-slot format so
 * existing weekly meal rows keep their identity during the engine extraction.
 * MONTH IDs use the canonical period and ISO date instead of a week-relative key.
 */
data class PlanningPeriodSlot(
    val id: String,
    val date: LocalDate,
    val dayIndex: Int,
    val mealSlot: String
)

/** Build every selected slot exactly once, in chronological date/slot order. */
fun buildPlanningPeriodSlots(
    period: PlanningDateRange,
    mealSlots: List<String>
): List<PlanningPeriodSlot> {
    val canonicalRange = resolvePlanningDateRange(period.type, period.startDate.toString())
    require(
        period.periodId == canonicalRange.periodId &&
            period.startDate == canonicalRange.startDate &&
            period.endDate == canonicalRange.endDate
    ) {
        "Slots can only be built for a complete canonical planning period."
    }

    val selectedSlots = mealSlots
        .filter { it.isNotBlank() }
        .distinct()

    return (0 until period.dayCount).flatMap { dayIndex ->
        val date = period.startDate.plusDays(dayIndex.toLong())
        selectedSlots.map { mealSlot ->
            val id = if (period.type == PlanningPeriodType.WEEK) {
                "${period.startDate}-${dayIndex}-${mealSlot}"
            } else {
                "${period.periodId}-${date}-${mealSlot}"
            }
            PlanningPeriodSlot(
                id = id,
                date = date,
                dayIndex = dayIndex,
                mealSlot = mealSlot
            )
        }
    }
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


/**
 * Meat allowance assigned to the part of a planning period that overlaps one
 * calendar month. Weekly plans receive a proportional share of the monthly cap;
 * an explicit weekly cap, when supplied, is split across the selected seven dates.
 * Monthly plans always receive the complete monthly cap.
 */
data class PeriodMeatAllowance(
    val month: YearMonth,
    val overlapDays: Int,
    val denominatorDays: Int,
    val allowedGrams: Double,
    val basis: Basis
) {
    enum class Basis {
        MONTHLY_CAP,
        WEEKLY_CAP
    }
}

fun calculatePeriodMeatAllowances(
    period: PlanningDateRange,
    monthlyLimitGrams: Int,
    weeklyLimitGrams: Int? = null
): List<PeriodMeatAllowance> {
    val canonicalRange = resolvePlanningDateRange(period.type, period.startDate.toString())
    require(
        period.periodId == canonicalRange.periodId &&
            period.startDate == canonicalRange.startDate &&
            period.endDate == canonicalRange.endDate
    ) {
        "Meat allowances can only be calculated for a complete canonical planning period."
    }

    val monthlyLimit = monthlyLimitGrams.coerceIn(250, 20_000).toDouble()
    val weeklyLimit = weeklyLimitGrams?.coerceIn(250, 20_000)?.toDouble()
    val overlapDaysByMonth = (0 until period.dayCount)
        .map { offset -> YearMonth.from(period.startDate.plusDays(offset.toLong())) }
        .groupingBy { it }
        .eachCount()

    return overlapDaysByMonth.entries
        .sortedBy { it.key }
        .map { (month, overlapDays) ->
            when {
                period.type == PlanningPeriodType.MONTH -> PeriodMeatAllowance(
                    month = month,
                    overlapDays = overlapDays,
                    denominatorDays = month.lengthOfMonth(),
                    allowedGrams = monthlyLimit,
                    basis = PeriodMeatAllowance.Basis.MONTHLY_CAP
                )
                weeklyLimit != null -> PeriodMeatAllowance(
                    month = month,
                    overlapDays = overlapDays,
                    denominatorDays = period.dayCount,
                    allowedGrams = weeklyLimit * overlapDays / period.dayCount,
                    basis = PeriodMeatAllowance.Basis.WEEKLY_CAP
                )
                else -> PeriodMeatAllowance(
                    month = month,
                    overlapDays = overlapDays,
                    denominatorDays = month.lengthOfMonth(),
                    allowedGrams = monthlyLimit * overlapDays / month.lengthOfMonth(),
                    basis = PeriodMeatAllowance.Basis.MONTHLY_CAP
                )
            }
        }
}
