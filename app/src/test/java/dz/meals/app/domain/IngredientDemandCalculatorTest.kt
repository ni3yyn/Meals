package dz.meals.app.domain

import dz.meals.app.data.IngredientDemandCalculator
import dz.meals.app.data.MealRecipe
import dz.meals.app.data.PlannedMeal
import dz.meals.app.data.RecipeIngredient
import dz.meals.app.data.convertIngredientQuantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientDemandCalculatorTest {
    private fun recipe(id: String, ingredients: List<RecipeIngredient>, baseServings: Int = 2) = MealRecipe(
        id = id, nameAr = id, nameFr = id, descriptionAr = "", descriptionFr = "",
        category = "", region = "", durationMinutes = 10, baseServings = baseServings,
        difficulty = "", allergens = emptySet(), ingredients = ingredients,
        stepsAr = emptyList(), stepsFr = emptyList()
    )

    private fun meal(
        id: String, recipeId: String, dayIndex: Int, servings: Int = 2,
        slot: String = "lunch", anchor: String = "2027-01-01"
    ) = PlannedMeal(
        id = id, weekStartIso = anchor, dayIndex = dayIndex, mealSlot = slot,
        recipeId = recipeId, servings = servings, locked = false
    )

    @Test
    fun scalesRecipeIngredientsBySelectedHouseholdServings() {
        val recipe = recipe("soup", listOf(RecipeIngredient("potato", 200.0, "g")))
        val demand = IngredientDemandCalculator.calculate(
            listOf(meal("meal-a", "soup", 0, servings = 6)), listOf(recipe)
        ).single()
        assertEquals("potato", demand.ingredientId)
        assertEquals(600.0, demand.quantity, 0.0001)
        assertEquals("g", demand.unit)
    }

    @Test
    fun appliesLinearSublinearAndFixedScalingRules() {
        val recipe = recipe(
            "mixed-scaling",
            listOf(
                RecipeIngredient("linear", 10.0, "g", scalingRule = "linear"),
                RecipeIngredient("sublinear", 10.0, "g", scalingRule = "sublinear"),
                RecipeIngredient("fixed", 10.0, "g", scalingRule = "fixed")
            )
        )
        val demands = IngredientDemandCalculator.calculate(
            listOf(meal("meal-scale", recipe.id, 1, servings = 8)), listOf(recipe)
        ).associateBy { it.ingredientId }
        assertEquals(40.0, demands.getValue("linear").quantity, 0.0001)
        assertEquals(20.0, demands.getValue("sublinear").quantity, 0.0001)
        assertEquals(10.0, demands.getValue("fixed").quantity, 0.0001)
    }

    @Test
    fun optionalAndUnquantifiedIngredientsDoNotCreatePurchaseDemand() {
        val recipe = recipe(
            "optional-lines",
            listOf(
                RecipeIngredient("required", 100.0, "g"),
                RecipeIngredient("optional", 50.0, "g", optional = true),
                RecipeIngredient("to-taste", 0.0, "g"),
                RecipeIngredient("invalid", Double.NaN, "g"),
                RecipeIngredient("negative", -5.0, "g")
            )
        )
        val demands = IngredientDemandCalculator.calculate(
            listOf(meal("meal-options", recipe.id, 0)), listOf(recipe)
        )
        assertEquals(1, demands.size)
        assertEquals("required", demands.single().ingredientId)
        assertEquals(100.0, demands.single().quantity, 0.0001)
    }

    @Test
    fun aggregatesCompatibleUnitsIntoOneCanonicalDemand() {
        val flourG = recipe("flour-grams", listOf(RecipeIngredient("flour", 250.0, "g")))
        val flourKg = recipe("flour-kilos", listOf(RecipeIngredient("flour", 0.5, "kg")))
        val oilL = recipe("oil-litres", listOf(RecipeIngredient("oil", 0.25, "l")))
        val oilMl = recipe("oil-ml", listOf(RecipeIngredient("oil", 150.0, "ml")))
        val demands = IngredientDemandCalculator.calculate(
            listOf(
                meal("a", flourG.id, 0), meal("b", flourKg.id, 1),
                meal("c", oilL.id, 2), meal("d", oilMl.id, 3)
            ),
            listOf(flourG, flourKg, oilL, oilMl)
        ).associateBy { it.ingredientId }
        assertEquals(750.0, demands.getValue("flour").quantity, 0.0001)
        assertEquals("g", demands.getValue("flour").unit)
        assertEquals(400.0, demands.getValue("oil").quantity, 0.0001)
        assertEquals("ml", demands.getValue("oil").unit)
    }

    @Test
    fun refusesToMergeDifferentDimensionsOrUnreviewedCustomUnits() {
        val recipe = recipe(
            "incompatible-units",
            listOf(
                RecipeIngredient("food", 100.0, "g"),
                RecipeIngredient("food", 1.0, "piece"),
                RecipeIngredient("food", 2.0, "cup"),
                RecipeIngredient("food", 3.0, "bunch")
            )
        )
        val demands = IngredientDemandCalculator.calculate(
            listOf(meal("meal-units", recipe.id, 0)), listOf(recipe)
        )
        assertEquals(4, demands.size)
        assertTrue(demands.any { it.unit == "g" })
        assertTrue(demands.any { it.unit == "piece" })
        assertTrue(demands.any { it.unit == "cup" })
        assertTrue(demands.any { it.unit == "bunch" })
    }

    @Test
    fun traceabilityIdentifiesEveryContributingMealRecipeSlotAndSourceLine() {
        val first = recipe(
            "first-recipe",
            listOf(
                RecipeIngredient("potato", 100.0, "g"),
                RecipeIngredient("onion", 20.0, "g")
            )
        )
        val second = recipe("second-recipe", listOf(RecipeIngredient("potato", 50.0, "g")))
        val demand = IngredientDemandCalculator.calculate(
            listOf(
                meal("lunch-on-day-1", first.id, 0, slot = "lunch"),
                meal("dinner-on-day-3", second.id, 2, slot = "dinner", anchor = "2027-02-01")
            ),
            listOf(first, second)
        ).single { it.ingredientId == "potato" }
        assertEquals(150.0, demand.quantity, 0.0001)
        assertEquals(2, demand.contributions.size)
        assertEquals(listOf("lunch-on-day-1", "dinner-on-day-3"), demand.contributions.map { it.mealId })
        assertEquals(listOf(first.id, second.id), demand.contributions.map { it.recipeId })
        assertEquals(listOf("lunch", "dinner"), demand.contributions.map { it.mealSlot })
        assertEquals(listOf(0, 2), demand.contributions.map { it.dayIndex })
        assertEquals(listOf(0, 0), demand.contributions.map { it.ingredientLineIndex })
        assertEquals(listOf(100.0, 50.0), demand.contributions.map { it.sourceQuantity })
        assertEquals(listOf("2027-01-01", "2027-02-01"), demand.contributions.map { it.periodAnchorIso })
    }

    @Test
    fun outputAndContributionOrderDoNotDependOnInputCollectionOrder() {
        val a = recipe("a", listOf(RecipeIngredient("potato", 100.0, "g")))
        val b = recipe("b", listOf(RecipeIngredient("potato", 50.0, "kg")))
        val meals = listOf(meal("meal-b", b.id, 1), meal("meal-a", a.id, 0))
        val forward = IngredientDemandCalculator.calculate(meals, listOf(a, b))
        val reversed = IngredientDemandCalculator.calculate(meals.reversed(), listOf(b, a))
        assertEquals(forward, reversed)
    }

    @Test
    fun convertsOnlyCompatiblePackageUnits() {
        assertEquals(1_000.0, convertIngredientQuantity(1.0, "kg", "g")!!, 0.0001)
        assertEquals(1_000.0, convertIngredientQuantity(1.0, "l", "ml")!!, 0.0001)
        assertEquals(15.0, convertIngredientQuantity(1.0, "tbsp", "ml")!!, 0.0001)
        assertEquals(null, convertIngredientQuantity(1.0, "g", "ml"))
        assertEquals(null, convertIngredientQuantity(1.0, "cup", "ml"))
    }
}
