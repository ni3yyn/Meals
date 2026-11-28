package dz.meals.app.data

import android.content.Context
import dz.meals.app.data.local.MealsDatabase
import dz.meals.app.data.local.PlanMarketPriceSnapshotEntity
import dz.meals.app.domain.PlanningPeriodId
import dz.meals.app.domain.PlanningPeriodType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MonthlyPlanGenerationTest {

    @Test
    fun generatesWholeCalendarMonthAndPreservesProtectedMealsOnRegeneration() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "monthly-plan-${UUID.randomUUID()}.db"
        val recipePreferencesName = "monthly-recipe-catalogue-${UUID.randomUUID()}"
        val database = MealsDatabase.createForTesting(context, databaseName)
        val recipePreferences = context.getSharedPreferences(
            recipePreferencesName,
            Context.MODE_PRIVATE
        )

        try {
            val repository = MealsRepository(context, database)
            repository.ensureInitialized()

            // The same bundled, curated Algerian recipe collection used in
            // production is installed into this isolated Room database.
            AlgerianRecipeCatalogueInstaller(
                context,
                databaseOverride = database,
                preferencesOverride = recipePreferences
            ).install()
            repository.invalidateCatalogueCache()

            val month = YearMonth.of(2027, 1)
            val options = MonthlyGenerationOptions(
                monthlyBudgetDzd = 250_000,
                householdServings = 2,
                allergyIds = emptySet(),
                selectedMealSlots = setOf("lunch"),
                meatPreference = "regular"
            )
            val firstResult = repository.generateMonthlyPlan(
                month = month,
                monthlyBudgetDzd = options.monthlyBudgetDzd,
                householdServings = options.householdServings,
                allergyIds = options.allergyIds,
                selectedWilaya = "الجزائر",
                selectedMealSlots = options.selectedMealSlots,
                meatPreference = options.meatPreference
            )

            assertTrue("Monthly generation failed: ${firstResult.message}", firstResult.success)

            val dao = database.mealsDao()
            val periodId = "MONTH:2027-01-01"
            val period = dao.getPlanningPeriod(periodId)
            assertNotNull(period)
            assertEquals(PlanningPeriodType.MONTH.storageValue, period!!.periodType)
            assertEquals("2027-01-01", period.startDateIso)
            assertEquals("2027-01-31", period.endDateIso)
            assertTrue(period.marketPriceSnapshotCaptured)

            val firstRows = dao.getPlanningPeriodMeals(periodId)
            assertEquals(31, firstRows.size)
            assertEquals(31, firstRows.map { it.dateIso }.distinct().size)
            assertEquals(
                (1..31).map { day -> "2027-01-${day.toString().padStart(2, '0')}" },
                firstRows.map { it.dateIso }.distinct().sorted()
            )
            assertTrue(firstRows.all { it.mealSlot == "lunch" && it.recipeId != null })
            assertTrue(firstRows.all { it.id.startsWith("$periodId-") })

            // Monthly data must not be smuggled into the week-keyed tables.
            assertTrue(dao.getPlanMeals("2027-01-01").isEmpty())
            assertNull(dao.getPlanRun("2027-01-01"))

            val lockedBefore = firstRows[0].copy(locked = true)
            val cookedBefore = firstRows[1].copy(isCooked = true)
            val editedBefore = firstRows[2].copy(isUserEdited = true)
            dao.upsertPlanningPeriodMeals(
                listOf(lockedBefore, cookedBefore, editedBefore)
            )

            val secondResult = repository.generateMonthlyPlan(
                month = month,
                monthlyBudgetDzd = options.monthlyBudgetDzd,
                householdServings = options.householdServings,
                allergyIds = options.allergyIds,
                selectedWilaya = "الجزائر",
                selectedMealSlots = options.selectedMealSlots,
                meatPreference = options.meatPreference
            )
            assertTrue("Monthly regeneration failed: ${secondResult.message}", secondResult.success)

            val regeneratedById = dao.getPlanningPeriodMeals(periodId).associateBy { it.id }
            assertEquals(31, regeneratedById.size)
            val preservedIds = setOf(lockedBefore.id, cookedBefore.id, editedBefore.id)
            assertTrue(
                "Regeneration with unchanged preferences should explore a new eligible recipe when alternatives exist.",
                firstRows.any { old ->
                    old.id !in preservedIds && regeneratedById[old.id]?.recipeId != old.recipeId
                }
            )
            for (preservedRow in listOf(lockedBefore, cookedBefore, editedBefore)) {
                val after = regeneratedById[preservedRow.id]
                assertNotNull(after)
                assertEquals(preservedRow.recipeId, after!!.recipeId)
                assertEquals(preservedRow.locked, after.locked)
                assertEquals(preservedRow.isCooked, after.isCooked)
                assertEquals(preservedRow.isUserEdited, after.isUserEdited)
                assertEquals(preservedRow.dateIso, after.dateIso)
                assertEquals(preservedRow.mealSlot, after.mealSlot)
            }


            // A period snapshot restores date-keyed meals, period-local adjustments,
            // editable shopping state and the immutable product-offer evidence.
            val initialSnapshot = repository.snapshot(
                PlanningPeriodId(periodId),
                selectedWilaya = "الجزائر"
            )
            assertEquals(periodId, initialSnapshot.planningPeriod?.id)
            assertEquals("MONTH", initialSnapshot.planningPeriod?.periodType)
            assertEquals("2027-01-01", initialSnapshot.planningPeriod?.startDateIso)
            assertEquals("2027-01-31", initialSnapshot.planningPeriod?.endDateIso)
            assertEquals(31, initialSnapshot.plannedMeals.size)
            assertTrue(dao.getPlanPriceSnapshots(periodId).isNotEmpty())

            val targetLine = initialSnapshot.shoppingItems.first {
                it.ingredientId != null &&
                    it.requiredQuantity > 0.0 &&
                    it.purchaseQuantity > 0.0 &&
                    it.unit.trim().lowercase() in setOf("g", "kg", "mg", "ml", "l", "cl", "piece", "pieces", "pc", "pcs", "unit", "units")
            }
            val targetIngredientId = requireNotNull(targetLine.ingredientId)
            val amountAlreadyHave = (targetLine.requiredQuantity * 0.2)
                .coerceAtMost(targetLine.requiredQuantity - 0.0001)
                .coerceAtLeast(0.0001)
            val persistedShoppingLine = dao.getShoppingItems(periodId)
                .first { it.ingredientId == targetIngredientId && it.unit == targetLine.unit }
            dao.upsertShoppingItems(
                listOf(
                    persistedShoppingLine.copy(
                        checked = true,
                        purchaseQuantityOverride = (targetLine.purchaseQuantity * 0.8).coerceAtLeast(0.001),
                        storeNote = "اختبار حفظ الفترة",
                        actualSpentDzd = 75.0
                    )
                )
            )

            val capturedPeriod = requireNotNull(dao.getPlanningPeriod(periodId))
            val evidenceTime = (capturedPeriod.priceSnapshotAtEpochMillis - 1L).coerceAtLeast(1L)
            dao.upsertPlanMarketPriceSnapshots(
                listOf(
                    PlanMarketPriceSnapshotEntity(
                        periodId = periodId,
                        sourceListingId = "snapshot-source-${targetIngredientId}",
                        productId = "snapshot-product-${targetIngredientId}",
                        ingredientId = targetIngredientId,
                        productName = "منتج اختبار",
                        packageQuantity = 1.0,
                        packageUnit = targetLine.unit,
                        mappingStatus = "VERIFIED",
                        mappingReviewedAtEpochMillis = evidenceTime,
                        priceDzd = 120.0,
                        priceMinDzd = null,
                        priceMaxDzd = null,
                        currencyCode = "DZD",
                        observedAtEpochMillis = evidenceTime,
                        priceStatus = "indicative_unverified",
                        availabilityRaw = "https://schema.org/InStock",
                        storeName = "متجر اختبار",
                        source = "snapshot-test",
                        wilaya = "الجزائر",
                        sourceNotice = "بيانات اختبار"
                    )
                )
            )

            val recipeAssignmentsBeforeAdjustment = dao.getPlanningPeriodMeals(periodId)
                .associate { it.id to it.recipeId }
            val partialSnapshot = repository.updatePlanIngredientAdjustment(
                periodId = PlanningPeriodId(periodId),
                ingredientId = targetIngredientId,
                unit = targetLine.unit,
                alreadyHaveAll = false,
                alreadyHaveQuantity = amountAlreadyHave
            )
            assertNotNull(partialSnapshot)
            val partialShoppingLine = partialSnapshot!!.shoppingItems
                .first { it.ingredientId == targetIngredientId && it.unit == targetLine.unit }
            assertEquals(amountAlreadyHave, partialShoppingLine.alreadyHaveQuantity, 0.0001)
            assertEquals(false, partialShoppingLine.alreadyHaveEnough)
            assertEquals(
                (targetLine.requiredQuantity - amountAlreadyHave).coerceAtLeast(0.0),
                partialShoppingLine.purchaseQuantity,
                0.0001
            )
            assertNotNull(partialShoppingLine.marketCashOutlayMinDzd)
            assertEquals(true, partialShoppingLine.checked)
            assertEquals("اختبار حفظ الفترة", partialShoppingLine.storeNote)
            assertEquals(75.0, partialShoppingLine.actualSpentDzd!!, 0.0001)
            assertNull(
                dao.getShoppingItems(periodId)
                    .first { it.ingredientId == targetIngredientId && it.unit == targetLine.unit }
                    .purchaseQuantityOverride
            )
            assertTrue(partialSnapshot.marketPriceEvidence.any {
                it.productId == "snapshot-product-${targetIngredientId}" &&
                    it.sourceListingId == "snapshot-source-${targetIngredientId}" &&
                    it.mappingStatus == "VERIFIED"
            })
            assertEquals(recipeAssignmentsBeforeAdjustment, dao.getPlanningPeriodMeals(periodId)
                .associate { it.id to it.recipeId })

            // Invalid input must not replace the last valid plan-local adjustment.
            val rejectedSnapshot = repository.updatePlanIngredientAdjustment(
                periodId = PlanningPeriodId(periodId),
                ingredientId = targetIngredientId,
                unit = targetLine.unit,
                alreadyHaveAll = false,
                alreadyHaveQuantity = Double.NaN
            )
            assertNull(rejectedSnapshot)
            assertEquals(
                amountAlreadyHave,
                dao.getPlanIngredientAdjustment(periodId, targetIngredientId, targetLine.unit)
                    ?.availableQuantity ?: -1.0,
                0.0001
            )

            // "Already have enough" makes the remaining purchase and estimate zero.
            val enoughSnapshot = repository.updatePlanIngredientAdjustment(
                periodId = PlanningPeriodId(periodId),
                ingredientId = targetIngredientId,
                unit = targetLine.unit,
                alreadyHaveAll = true,
                alreadyHaveQuantity = 0.0
            )
            assertNotNull(enoughSnapshot)
            val enoughShoppingLine = enoughSnapshot!!.shoppingItems
                .first { it.ingredientId == targetIngredientId && it.unit == targetLine.unit }
            assertEquals(true, enoughShoppingLine.alreadyHaveEnough)
            assertEquals(0.0, enoughShoppingLine.purchaseQuantity, 0.0001)
            assertEquals(0.0, enoughShoppingLine.cashOutlayDzd!!, 0.0001)
            assertEquals(recipeAssignmentsBeforeAdjustment, dao.getPlanningPeriodMeals(periodId)
                .associate { it.id to it.recipeId })

            // An explicit zero partial amount clears the adjustment and restores
            // full recipe demand; it does not carry stock from this period.
            val clearedSnapshot = repository.updatePlanIngredientAdjustment(
                periodId = PlanningPeriodId(periodId),
                ingredientId = targetIngredientId,
                unit = targetLine.unit,
                alreadyHaveAll = false,
                alreadyHaveQuantity = 0.0
            )
            assertNotNull(clearedSnapshot)
            val clearedShoppingLine = clearedSnapshot!!.shoppingItems
                .first { it.ingredientId == targetIngredientId && it.unit == targetLine.unit }
            assertEquals(false, clearedShoppingLine.alreadyHaveEnough)
            assertEquals(0.0, clearedShoppingLine.alreadyHaveQuantity, 0.0001)
            assertEquals(
                targetLine.requiredQuantity,
                clearedShoppingLine.purchaseQuantity,
                0.0001
            )
            assertNull(dao.getPlanIngredientAdjustment(periodId, targetIngredientId, targetLine.unit))
            assertNull(dao.getPlanIngredientAdjustment("MONTH:2027-02-01", targetIngredientId, targetLine.unit))
            assertEquals(recipeAssignmentsBeforeAdjustment, dao.getPlanningPeriodMeals(periodId)
                .associate { it.id to it.recipeId })

            // No month slot can escape the selected calendar boundaries.
            assertFalse(
                regeneratedById.values.any {
                    LocalDate.parse(it.dateIso).isBefore(month.atDay(1)) ||
                        LocalDate.parse(it.dateIso).isAfter(month.atEndOfMonth())
                }
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            recipePreferences.edit().clear().commit()
        }
    }

    private data class MonthlyGenerationOptions(
        val monthlyBudgetDzd: Int,
        val householdServings: Int,
        val allergyIds: Set<String>,
        val selectedMealSlots: Set<String>,
        val meatPreference: String
    )
}
