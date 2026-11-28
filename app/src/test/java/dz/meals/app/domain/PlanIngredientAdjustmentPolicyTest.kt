package dz.meals.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlanIngredientAdjustmentPolicyTest {
    @Test fun partialAtHomeAmountReducesOnlyPurchaseNeed() {
        val need = PlanIngredientAdjustmentPolicy.apply("salt", "g", 500.0, 180.0)
        assertEquals(320.0, need.purchaseQuantity, 0.0001)
        assertEquals(180.0, need.coveredQuantity, 0.0001)
    }

    @Test fun alreadyHaveEnoughSetsPurchaseNeedToZero() {
        val need = PlanIngredientAdjustmentPolicy.apply("oil", "ml", 900.0, 0.0, true)
        assertEquals(0.0, need.purchaseQuantity, 0.0)
        assertEquals(900.0, need.coveredQuantity, 0.0)
    }

    @Test fun excessAtHomeAmountIsClampedAndNeverCreatesNegativeNeed() {
        val need = PlanIngredientAdjustmentPolicy.apply("sugar", "g", 400.0, 700.0)
        assertEquals(0.0, need.purchaseQuantity, 0.0)
        assertEquals(400.0, need.coveredQuantity, 0.0)
    }

    @Test fun differentUnitsDoNotShareAdjustments() {
        val adjustment = PlanIngredientNeed("oil", "ml", 100.0, 50.0)
        assertNotNull(PlanIngredientAdjustmentPolicy.adjustmentFor("oil", "ml", listOf(adjustment)))
        assertNull(PlanIngredientAdjustmentPolicy.adjustmentFor("oil", "g", listOf(adjustment)))
        assertNull(PlanIngredientAdjustmentPolicy.adjustmentFor("salt", "ml", listOf(adjustment)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeAtHomeQuantity() {
        PlanIngredientAdjustmentPolicy.apply("salt", "g", 100.0, -1.0)
    }
}
