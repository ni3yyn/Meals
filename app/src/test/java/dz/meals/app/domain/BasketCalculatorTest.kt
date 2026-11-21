package dz.meals.app.domain

import dz.meals.app.data.MealRecipe
import dz.meals.app.data.PlannedMeal
import dz.meals.app.data.RecipeIngredient
import dz.meals.app.data.SeedRecipes
import dz.meals.app.data.local.PantryItemEntity
import dz.meals.app.data.local.PriceObservationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BasketCalculatorTest {
    private val recipe = MealRecipe(
        id = "potato-soup",
        nameAr = "شوربة بطاطا",
        nameFr = "Soupe de pommes de terre",
        descriptionAr = "",
        descriptionFr = "",
        category = "اقتصادي",
        region = "وطني",
        durationMinutes = 25,
        baseServings = 2,
        difficulty = "سهل",
        allergens = emptySet(),
        ingredients = listOf(RecipeIngredient("potato", 200.0, "g")),
        stepsAr = emptyList(),
        stepsFr = emptyList()
    )

    private val plan = PlannedMeal(
        id = "meal-1",
        weekStartIso = "2026-11-16",
        dayIndex = 0,
        mealSlot = "lunch",
        recipeId = "potato-soup",
        servings = 4,
        locked = false
    )

    @Test
    fun scalesIngredientQuantityForHousehold() {
        assertEquals(400.0, scaleIngredientQuantity(200.0, 2, 4), 0.0001)
    }

    @Test
    fun exactPantryStockIsDeductedAndPurchaseRoundsToWholePack() {
        val pantry = listOf(PantryItemEntity("potato", "potato", 100.0, "g", false, "", null))
        val prices = listOf(
            PriceObservationEntity(
                id = "demo-potato",
                ingredientId = "potato",
                packQuantityBase = 500.0,
                unit = "g",
                packPriceDzd = 100.0,
                sourceLabel = "demo",
                locationLabel = "غير محدد",
                observedAtEpochMillis = null,
                priceKind = "demo",
                confidenceLabel = "غير موثق"
            )
        )

        val result = calculateBasket(listOf(plan), listOf(recipe), pantry, prices)
        val item = result.items.single()

        assertEquals(400.0, item.requiredQuantity, 0.0001)
        assertEquals(100.0, item.pantryQuantity, 0.0001)
        assertEquals(500.0, item.purchaseQuantity, 0.0001)
        assertEquals(100.0, item.cashOutlayDzd!!, 0.0001)
        assertEquals(80.0, result.consumedValueDzd, 0.0001)
    }

    @Test
    fun approximatePantryIsVisibleButNeverDeducted() {
        val pantry = listOf(PantryItemEntity("potato", "potato", null, "g", true, "some left", null))
        val result = calculateBasket(listOf(plan), listOf(recipe), pantry, emptyList())
        val item = result.items.single()

        assertEquals(0.0, item.pantryQuantity, 0.0001)
        assertEquals(400.0, item.purchaseQuantity, 0.0001)
        assertTrue(item.isApproximatePantry)
        assertEquals(1, result.unknownPriceCount)
    }

    @Test
    fun seededCatalogueHasConsistentUnitsAndAllergenMetadata() {
        SeedRecipes.validateCatalogue()
    }
}
