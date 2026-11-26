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
        assertTrue(PlanCuisinePolicy.allowsForGeneration(recipe(category = "طبق رئيسي")))
        assertTrue(PlanCuisinePolicy.allowsForGeneration(recipe(category = "حساء")))
        assertTrue(PlanCuisinePolicy.allowsForGeneration(recipe(category = "شوربة")))
        assertTrue(PlanCuisinePolicy.allowsForGeneration(recipe(category = "Soup")))
        assertTrue(
            PlanCuisinePolicy.allowsForGeneration(
                recipe(category = "Family recipe", categories = listOf("Plats principaux"))
            )
        )
        assertTrue(
            PlanCuisinePolicy.allowsForGeneration(
                recipe(category = "طبق رئيسي", categories = listOf("طبق جانبي"))
            )
        )
        assertTrue(
            PlanCuisinePolicy.allowsForGeneration(recipe(category = "طبق رئيسي ومرافق"))
        )
        assertFalse(
            PlanCuisinePolicy.allowsForGeneration(
                recipe(category = "تقليدي", categories = listOf("وصفة جزائرية"))
            )
        )
    }

    @Test
    fun generatorRejectsDessertsAndSweetsFromCourseMetadata() {
        assertFalse(PlanCuisinePolicy.allowsForGeneration(recipe(category = "حلويات صغيرة")))
        assertFalse(PlanCuisinePolicy.allowsForGeneration(recipe(category = "حلويات جافة صغيرة")))
        assertFalse(PlanCuisinePolicy.allowsForGeneration(recipe(category = "Dessert")))
        assertFalse(
            PlanCuisinePolicy.allowsForGeneration(
                recipe(category = "طبق رئيسي", categories = listOf("حلوى للقهوة"))
            )
        )
        assertFalse(
            PlanCuisinePolicy.allowsForGeneration(
                recipe(category = "Plat principal", nameFr = "Gâteau au chocolat")
            )
        )
        assertFalse(
            PlanCuisinePolicy.allowsForGeneration(
                recipe(category = "طبق رئيسي", nameAr = "كيكة الشوكولاتة")
            )
        )
        assertFalse(
            PlanCuisinePolicy.allowsForGeneration(
                recipe(category = "Plat principal", nameFr = "Gâteaux algériens")
            )
        )
    }

    @Test
    fun sweetProfiledAlgerianMainDishesAreNotMistakenForDesserts() {
        assertTrue(
            PlanCuisinePolicy.allowsForGeneration(
                recipe(
                    nameAr = "لحم حلو بالبرقوق المجفف والمشمش والزبيب",
                    nameFr = "Lham Lahlou aux pruneaux, abricots et raisins secs",
                    category = "طبق رئيسي"
                )
            )
        )
    }

    @Test
    fun generatorRejectsOtherNonMainCoursesAndRequiresEligibleCourseLabel() {
        listOf(
            "مقبلات",
            "مقبّلات ساخنة",
            "سلطات ومقبلات",
            "خبز",
            "وجبة خفيفة",
            "فطور وخبز",
            "مرافق"
        ).forEach { category ->
            assertFalse(
                "Must not generate category: $category",
                PlanCuisinePolicy.allowsForGeneration(recipe(category = category))
            )
        }
    }

    @Test
    fun foreignCuisineRecipesRemainOutsideAutomaticGeneration() {
        assertFalse(
            PlanCuisinePolicy.allowsForGeneration(
                recipe(id = "foreign-recipe", category = "Main course")
            )
        )
    }

    private fun recipe(
        id: String = "dz-algerian-dzirielle-test",
        nameAr: String = "طبق",
        nameFr: String = "Plat",
        category: String,
        categories: List<String> = emptyList(),
        tags: List<String> = emptyList()
    ) = MealRecipe(
        id = id,
        nameAr = nameAr,
        nameFr = nameFr,
        descriptionAr = "",
        descriptionFr = "",
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
