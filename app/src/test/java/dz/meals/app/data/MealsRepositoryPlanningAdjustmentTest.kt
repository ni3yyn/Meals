package dz.meals.app.data

import android.content.Context
import dz.meals.app.data.local.MealsDatabase
import dz.meals.app.data.local.ShoppingItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MealsRepositoryPlanningAdjustmentTest {

    @Test
    fun changingOrClearingPlanAdjustmentResetsGeneratedPurchaseOverride() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "plan-adjustment-${UUID.randomUUID()}.db"
        val database = MealsDatabase.createForTesting(context, databaseName)

        try {
            val repository = MealsRepository(context, database)
            val weekStartIso = "2026-11-23"

            // Snapshot initializes the production catalogue and creates the canonical
            // WEEK period before the plan-scoped adjustment is persisted.
            repository.snapshot(weekStartIso)
            val dao = database.mealsDao()
            val ingredient = dao.getIngredients().first()
            val unit = ingredient.baseUnit.ifBlank { "piece" }
            val shoppingItem = ShoppingItemEntity(
                id = "$weekStartIso::${ingredient.id}::$unit",
                ingredientId = ingredient.id,
                customName = ingredient.nameAr,
                requiredQuantity = 100.0,
                pantryQuantity = 0.0,
                purchaseQuantity = 100.0,
                unit = unit,
                consumedValueDzd = null,
                cashOutlayDzd = null,
                priceLabel = null,
                sourceLabel = null,
                isApproximatePantry = false,
                checked = false,
                userAdded = false,
                updatedAtEpochMillis = 1L,
                weekStartIso = weekStartIso,
                purchaseQuantityOverride = 40.0
            )
            dao.upsertShoppingItems(listOf(shoppingItem))
            val periodId = "WEEK:$weekStartIso"

            repository.updatePlanIngredientAdjustment(
                weekStartIso = weekStartIso,
                ingredientId = ingredient.id,
                unit = unit,
                adjustmentType = "SOME",
                availableQuantity = 25.0
            )

            assertEquals(
                "SOME",
                dao.getPlanIngredientAdjustment(periodId, ingredient.id, unit)?.adjustmentType
            )
            assertNull(dao.getShoppingItem(shoppingItem.id)?.purchaseQuantityOverride)

            // Clearing the adjustment must also clear any stale manual purchase
            // override so the next snapshot returns to its calculated quantity.
            dao.upsertShoppingItems(listOf(shoppingItem.copy(purchaseQuantityOverride = 17.0)))
            repository.updatePlanIngredientAdjustment(
                weekStartIso = weekStartIso,
                ingredientId = ingredient.id,
                unit = unit,
                adjustmentType = null
            )

            assertNull(dao.getPlanIngredientAdjustment(periodId, ingredient.id, unit))
            assertNull(dao.getShoppingItem(shoppingItem.id)?.purchaseQuantityOverride)

            // Invalid input is rejected before either persisted value is changed.
            dao.upsertShoppingItems(listOf(shoppingItem.copy(purchaseQuantityOverride = 13.0)))
            repository.updatePlanIngredientAdjustment(
                weekStartIso = weekStartIso,
                ingredientId = ingredient.id,
                unit = unit,
                adjustmentType = "SOME",
                availableQuantity = 30.0
            )
            dao.upsertShoppingItems(listOf(shoppingItem.copy(purchaseQuantityOverride = 11.0)))
            repository.updatePlanIngredientAdjustment(
                weekStartIso = weekStartIso,
                ingredientId = ingredient.id,
                unit = unit,
                adjustmentType = "SOME",
                availableQuantity = Double.NaN
            )

            assertNotNull(dao.getPlanIngredientAdjustment(periodId, ingredient.id, unit))
            assertEquals(
                30.0,
                dao.getPlanIngredientAdjustment(periodId, ingredient.id, unit)!!.availableQuantity,
                0.0001
            )
            assertEquals(
                11.0,
                dao.getShoppingItem(shoppingItem.id)?.purchaseQuantityOverride!!,
                0.0001
            )

            // The combined editor action clears the previous adjustment and
            // commits a newly typed purchase override in the same transaction.
            repository.updatePlanShoppingItemDetails(
                weekStartIso = weekStartIso,
                shoppingItemId = shoppingItem.id,
                ingredientId = ingredient.id,
                unit = unit,
                adjustmentType = null,
                availableQuantity = 0.0,
                purchaseQuantity = 23.0,
                storeNote = "سوق الحي",
                unavailable = true,
                actualSpentDzd = 55.0
            )

            assertNull(dao.getPlanIngredientAdjustment(periodId, ingredient.id, unit))
            val editedRow = dao.getShoppingItem(shoppingItem.id)!!
            assertEquals(23.0, editedRow.purchaseQuantityOverride!!, 0.0001)
            assertEquals("سوق الحي", editedRow.storeNote)
            assertEquals(true, editedRow.isUnavailable)
            assertEquals(55.0, editedRow.actualSpentDzd!!, 0.0001)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
