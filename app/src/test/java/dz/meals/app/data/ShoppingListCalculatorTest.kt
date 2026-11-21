package dz.meals.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingListCalculatorTest {
    @Test
    fun combinesSameIngredientAndUnitAcrossRecipes() {
        val recipes = listOf(
            MealRecipe("one", "One", "", "", 1, 1, 100, listOf(Ingredient("tomato", "طماطم", 2.0, "حبة"))),
            MealRecipe("two", "Two", "", "", 1, 1, 100, listOf(Ingredient("tomato", "طماطم", 3.0, "حبة")))
        )

        val result = buildShoppingList(recipes)

        assertEquals(1, result.size)
        assertEquals(5.0, result.single().quantity, 0.0)
    }

    @Test
    fun keepsDifferentUnitsSeparate() {
        val recipe = MealRecipe(
            "one", "One", "", "", 1, 1, 100,
            listOf(
                Ingredient("oil", "زيت", 1.0, "ملعقة"),
                Ingredient("oil", "زيت", 100.0, "مل")
            )
        )

        assertEquals(2, buildShoppingList(listOf(recipe)).size)
    }
}
