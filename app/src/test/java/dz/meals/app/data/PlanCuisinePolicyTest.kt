package dz.meals.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanCuisinePolicyTest {
    @Test
    fun onlyTheDedicatedAlgerianCatalogueCanEnterMealPlans() {
        assertTrue(PlanCuisinePolicy.allowsRecipeId("dz-algerian-dzirielle-recipe-001"))
        assertFalse(PlanCuisinePolicy.allowsRecipeId("couscous-vegetables"))
        assertFalse(PlanCuisinePolicy.allowsRecipeId("orc-123"))
        assertFalse(PlanCuisinePolicy.allowsRecipeId("focus-123"))
        assertFalse(PlanCuisinePolicy.allowsRecipeId("dz-algerian-other-recipe-001"))
        assertFalse(PlanCuisinePolicy.allowsRecipeId("other-dz-algerian-dzirielle-recipe-001"))
    }
}
