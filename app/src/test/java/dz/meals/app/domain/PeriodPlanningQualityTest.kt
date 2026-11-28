package dz.meals.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodPlanningQualityTest {

    @Test
    fun retryBudgetIsBoundedAndShrinksForLongPeriods() {
        assertEquals(10, periodSearchAttempts(1))
        assertEquals(10, periodSearchAttempts(11))
        assertEquals(8, periodSearchAttempts(12))
        assertEquals(6, periodSearchAttempts(18))
        assertEquals(4, periodSearchAttempts(28))
        assertEquals(3, periodSearchAttempts(42))
        assertEquals(2, periodSearchAttempts(72))
        assertTrue((1..93).all { periodSearchAttempts(it) in 2..10 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun retryBudgetRejectsAnEmptyPeriod() {
        periodSearchAttempts(0)
    }

    @Test
    fun sharedStaplesImproveThePeriodQualityScore() {
        val noReuse = scorePeriodIngredientReuse(
            listOf(
                setOf("onion", "garlic"),
                setOf("tomato", "pepper")
            )
        )
        val sharedStaples = scorePeriodIngredientReuse(
            listOf(
                setOf("onion", "garlic", "salt"),
                setOf("tomato", "onion", "salt")
            )
        )

        assertTrue(sharedStaples < noReuse)
        assertEquals(-240.0, sharedStaples, 0.0)
        assertEquals(0.0, noReuse, 0.0)
    }

    @Test
    fun ingredientReuseIsCappedAndDoesNotCountDuplicatesWithinOneRecipe() {
        val score = scorePeriodIngredientReuse(
            listOf(
                setOf("salt", "onion"),
                setOf("salt", "onion"),
                setOf("salt", "tomato"),
                setOf("salt", "pepper")
            ),
            maxRewardedSharedIngredients = 1
        )

        // Salt and onion are both reused, but the reward is capped at one.
        // Salt appears in every meal, so it receives the saturation penalty.
        assertEquals(-85.0, score, 0.0)
    }

    @Test
    fun aSingleMealCannotGainAReuseReward() {
        assertEquals(
            0.0,
            scorePeriodIngredientReuse(listOf(setOf("salt", "onion"))),
            0.0
        )
    }
}
