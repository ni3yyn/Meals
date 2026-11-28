package dz.meals.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanningPeriodRulesTest {
    @Test
    fun weeklyRangeAlwaysStartsMondayAndContainsSevenDays() {
        val range = resolvePlanningDateRange(
            PlanningPeriodType.WEEK,
            "2026-11-28" // Saturday
        )

        assertEquals("WEEK:2026-11-23", range.periodId)
        assertEquals(LocalDate.parse("2026-11-23"), range.startDate)
        assertEquals(LocalDate.parse("2026-11-29"), range.endDate)
        assertEquals(7, range.dayCount)
        assertTrue(range.contains(LocalDate.parse("2026-11-29")))
        assertFalse(range.contains(LocalDate.parse("2026-11-30")))
    }

    @Test
    fun monthRangeCoversTwentyEightDays() {
        val range = resolvePlanningDateRange(
            PlanningPeriodType.MONTH,
            "2027-02-18"
        )

        assertEquals("MONTH:2027-02-01", range.periodId)
        assertEquals(LocalDate.parse("2027-02-01"), range.startDate)
        assertEquals(LocalDate.parse("2027-02-28"), range.endDate)
        assertEquals(28, range.dayCount)
    }

    @Test
    fun leapYearFebruaryContainsTwentyNineDays() {
        val range = resolvePlanningDateRange(
            PlanningPeriodType.MONTH,
            "2028-02-29"
        )

        assertEquals(LocalDate.parse("2028-02-01"), range.startDate)
        assertEquals(LocalDate.parse("2028-02-29"), range.endDate)
        assertEquals(29, range.dayCount)
    }

    @Test
    fun thirtyDayMonthUsesTheCalendarEnd() {
        val range = resolvePlanningDateRange(
            PlanningPeriodType.MONTH,
            "2027-04-12"
        )

        assertEquals(LocalDate.parse("2027-04-01"), range.startDate)
        assertEquals(LocalDate.parse("2027-04-30"), range.endDate)
        assertEquals(30, range.dayCount)
    }

    @Test
    fun thirtyOneDayMonthUsesTheCalendarEnd() {
        val range = resolvePlanningDateRange(
            PlanningPeriodType.MONTH,
            "2027-01-31"
        )

        assertEquals(LocalDate.parse("2027-01-01"), range.startDate)
        assertEquals(LocalDate.parse("2027-01-31"), range.endDate)
        assertEquals(31, range.dayCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedSelectedDates() {
        resolvePlanningDateRange(PlanningPeriodType.MONTH, "not-a-date")
    }

    @Test
    fun weeklySlotBuilderPreservesLegacyIdsAndExactlySevenDates() {
        val range = resolvePlanningDateRange(PlanningPeriodType.WEEK, "2026-11-28")
        val slots = buildPlanningPeriodSlots(range, listOf("breakfast", "lunch", "dinner"))

        assertEquals(21, slots.size)
        assertEquals("2026-11-23-0-breakfast", slots.first().id)
        assertEquals(LocalDate.parse("2026-11-23"), slots.first().date)
        assertEquals(6, slots.last().dayIndex)
        assertEquals(LocalDate.parse("2026-11-29"), slots.last().date)
        assertEquals(21, slots.map { it.id }.toSet().size)
    }

    @Test
    fun monthlySlotBuilderCoversEveryCalendarDateWithoutWeekBoundaries() {
        val range = resolvePlanningDateRange(PlanningPeriodType.MONTH, "2028-02-29")
        val slots = buildPlanningPeriodSlots(range, listOf("lunch", "dinner"))

        assertEquals(58, slots.size)
        assertEquals(LocalDate.parse("2028-02-01"), slots.first().date)
        assertEquals(LocalDate.parse("2028-02-29"), slots.last().date)
        assertEquals(0, slots.first().dayIndex)
        assertEquals(28, slots.last().dayIndex)
        assertEquals("MONTH:2028-02-01-2028-02-01-lunch", slots.first().id)
        assertEquals(58, slots.map { it.id }.toSet().size)
    }

    @Test
    fun slotBuilderDeduplicatesSelectedMealSlotsAndKeepsTheirOrder() {
        val range = resolvePlanningDateRange(PlanningPeriodType.MONTH, "2027-04-12")
        val slots = buildPlanningPeriodSlots(
            range,
            listOf("dinner", "lunch", "dinner", "")
        )

        assertEquals(60, slots.size)
        assertEquals(listOf("dinner", "lunch"), slots.take(2).map { it.mealSlot })
    }

    @Test(expected = IllegalArgumentException::class)
    fun slotBuilderRejectsAnIncompleteCalendarPeriod() {
        buildPlanningPeriodSlots(
            PlanningDateRange(
                periodId = "MONTH:2027-02-01",
                type = PlanningPeriodType.MONTH,
                startDate = LocalDate.parse("2027-02-01"),
                endDate = LocalDate.parse("2027-02-27")
            ),
            listOf("lunch")
        )
    }

    @Test
    fun alreadyHaveEnoughMakesPurchaseNeedZero() {
        assertEquals(
            0.0,
            calculatePurchaseNeed(
                requiredQuantity = 2_500.0,
                adjustment = PlanIngredientAdjustment(PlanIngredientAdjustment.Type.ENOUGH)
            ),
            0.0
        )
    }

    @Test
    fun someQuantityIsSubtractedAndClampedAtZero() {
        assertEquals(
            1_500.0,
            calculatePurchaseNeed(
                requiredQuantity = 2_500.0,
                adjustment = PlanIngredientAdjustment(
                    PlanIngredientAdjustment.Type.SOME,
                    availableQuantity = 1_000.0
                )
            ),
            0.0
        )
        assertEquals(
            0.0,
            calculatePurchaseNeed(
                requiredQuantity = 2_500.0,
                adjustment = PlanIngredientAdjustment(
                    PlanIngredientAdjustment.Type.SOME,
                    availableQuantity = 3_000.0
                )
            ),
            0.0
        )
    }

    @Test
    fun noAdjustmentLeavesTheFullRecipeDemand() {
        assertEquals(250.0, calculatePurchaseNeed(250.0, null), 0.0)
    }

    @Test
    fun validatesOnlyWellFormedPlanAdjustments() {
        assertTrue(
            validatePlanIngredientAdjustment(
                PlanIngredientAdjustment(PlanIngredientAdjustment.Type.ENOUGH)
            )
        )
        assertTrue(
            validatePlanIngredientAdjustment(
                PlanIngredientAdjustment(
                    PlanIngredientAdjustment.Type.SOME,
                    availableQuantity = 1.0
                )
            )
        )
        assertFalse(
            validatePlanIngredientAdjustment(
                PlanIngredientAdjustment(
                    PlanIngredientAdjustment.Type.SOME,
                    availableQuantity = 0.0
                )
            )
        )
        assertFalse(
            validatePlanIngredientAdjustment(
                PlanIngredientAdjustment(
                    PlanIngredientAdjustment.Type.SOME,
                    availableQuantity = Double.NaN
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun purchaseNeedRejectsNegativeRequiredQuantity() {
        calculatePurchaseNeed(-1.0, null)
    }
}
