package dz.meals.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanCuisinePolicyTest {
    @Test
    fun onlyTheDedicatedAlgerianCatalogueMatchesTheCuisineGate() {
        assertTrue(PlanCuisinePolicy.allowsRecipeId("dz-algerian-dzirielle-recipe-001"))
        assertFalse(PlanCuisinePolicy.allowsRecipeId("couscous-vegetables"))
        assertFalse(PlanCuisinePolicy.allowsRecipeId("orc-123"))
        assertFalse(PlanCuisinePolicy.allowsRecipeId("focus-123"))
        assertFalse(PlanCuisinePolicy.allowsRecipeId("dz-algerian-other-recipe-001"))
        assertFalse(PlanCuisinePolicy.allowsRecipeId("other-dz-algerian-dzirielle-recipe-001"))
    }

    @Test
    fun generatorRequiresAnExplicitMainDishCourseType() {
        assertTrue(
            PlanCuisinePolicy.allowsMainDish(
                recipe(category = "طبق رئيسي")
            )
        )
        assertTrue(
            PlanCuisinePolicy.allowsMainDish(
                recipe(category = "Family recipe", categories = listOf("Plats principaux"))
            )
        )
        assertTrue(
            PlanCuisinePolicy.allowsMainDish(
                recipe(category = "تقليدي", tags = listOf("أطباق مالحة"))
            )
        )
        assertFalse(
            PlanCuisinePolicy.allowsMainDish(
                recipe(category = "تقليدي", categories = listOf("وصفة جزائرية"))
            )
        )
    }

    @Test
    fun generatorRejectsDessertsAndSweetsEvenWhenOtherMetadataSaysMainDish() {
        assertFalse(
            PlanCuisinePolicy.allowsMainDish(
                recipe(category = "حلويات")
            )
        )
        assertFalse(
            PlanCuisinePolicy.allowsMainDish(
                recipe(category = "Dessert")
            )
        )
        assertFalse(
            PlanCuisinePolicy.allowsMainDish(
                recipe(
                    category = "Plat principal",
                    nameFr = "Gâteau au chocolat"
                )
            )
        )
        assertFalse(
            PlanCuisinePolicy.allowsMainDish(
                recipe(
                    category = "طبق رئيسي",
                    nameAr = "كيكة الشوكولاتة"
                )
            )
        )
        assertFalse(
            PlanCuisinePolicy.allowsMainDish(
                recipe(category = "Plat", categories = listOf("Entrées"))
            )
        )
    }

    @Test
    fun foreignCuisineRecipesRemainOutsideAutomaticGeneration() {
        assertFalse(
            PlanCuisinePolicy.allowsMainDish(
                recipe(id = "foreign-recipe", category = "Main course")
            )
        )
    }

    private fun recipe(
        id: String = "dz-algerian-dzirielle-test",
        nameAr: String = "طبق",
        nameFr: String = "Plat",
        descriptionAr: String = "",
        descriptionFr: String = "",
        category: String,
        categories: List<String> = emptyList(),
        tags: List<String> = emptyList()
    ) = MealRecipe(
        id = id,
        nameAr = nameAr,
        nameFr = nameFr,
        descriptionAr = descriptionAr,
        descriptionFr = descriptionFr,
        category = category,
        region = "",
        durationMinutes = 30,
        baseServings = 2,
        difficulty = "",
        allergens = emptySet(),
        ingredients = emptyList(),
        stepsAr = emptyList(),
        stepsFr = emptyList(),
        tags = tags,
        categories = categories
    )
}
