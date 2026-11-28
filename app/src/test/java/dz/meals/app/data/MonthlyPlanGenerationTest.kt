package dz.meals.app.data

import android.content.Context
import dz.meals.app.data.local.MealsDatabase
import dz.meals.app.data.local.PlanIngredientAdjustmentEntity
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
            dao.upsertPlanIngredientAdjustment(
                PlanIngredientAdjustmentEntity(
                    periodId = periodId,
                    ingredientId = targetIngredientId,
                    unit = targetLine.unit,
                    adjustmentType = "SOME",
                    availableQuantity = amountAlreadyHave,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )

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

            val restoredSnapshot = repository.snapshot(
                PlanningPeriodId(periodId),
                selectedWilaya = "الجزائر"
            )
            assertEquals(31, restoredSnapshot.plannedMeals.size)
            val restoredShoppingLine = restoredSnapshot.shoppingItems
                .first { it.ingredientId == targetIngredientId && it.unit == targetLine.unit }
            assertEquals(amountAlreadyHave, restoredShoppingLine.alreadyHaveQuantity, 0.0001)
            assertEquals(true, restoredShoppingLine.checked)
            assertEquals("اختبار حفظ الفترة", restoredShoppingLine.storeNote)
            assertEquals(75.0, restoredShoppingLine.actualSpentDzd!!, 0.0001)
            assertTrue(restoredSnapshot.marketPriceEvidence.any {
                it.productId == "snapshot-product-${targetIngredientId}" &&
                    it.sourceListingId == "snapshot-source-${targetIngredientId}" &&
                    it.mappingStatus == "VERIFIED"
            })
            assertNotNull(restoredShoppingLine.marketCashOutlayMinDzd)

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
