package dz.meals.app.domain

import dz.meals.app.data.FoodTaxonomyRef
import dz.meals.app.data.MealRecipe
import dz.meals.app.data.PlannedMeal
import dz.meals.app.data.RecipeIngredient
import dz.meals.app.data.SeedRecipes
import dz.meals.app.data.local.PantryItemEntity
import dz.meals.app.data.local.PriceObservationEntity
import dz.meals.app.domain.findLockedAllergyConflict
import dz.meals.app.domain.findPinnedDietConflict
import dz.meals.app.domain.isRecipeAllowed
import dz.meals.app.domain.isRecipeAllowedByMeatPreference
import dz.meals.app.domain.recipeMeatQuantityGrams
import dz.meals.app.domain.recipeOilQuantityPerServing
import dz.meals.app.domain.softPreferencePenalty
import dz.meals.app.domain.shouldPreserveMeal
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
    fun shoppingRequirementIgnoresHomeStockAndUsesTheFullRecipeQuantity() {
        val pantry = listOf(PantryItemEntity("potato", "potato", 100.0, "g", false, "", null))
        val prices = listOf(
            PriceObservationEntity(
                id = "demo-potato",
                ingredientId = "potato",
                packQuantityBase = 100.0,
                unit = "g",
                packPriceDzd = 20.0,
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
        assertEquals(0.0, item.pantryQuantity, 0.0001)
        assertEquals(400.0, item.purchaseQuantity, 0.0001)
        assertEquals(80.0, item.cashOutlayDzd!!, 0.0001)
        assertEquals(80.0, result.consumedValueDzd, 0.0001)
    }

    @Test
    fun approximatePantryIsVisibleButNeverDeducted() {
        val pantry = listOf(PantryItemEntity("potato", "potato", null, "g", true, "some left", null))
        val result = calculateBasket(listOf(plan), listOf(recipe), pantry, emptyList())
        val item = result.items.single()

        assertEquals(0.0, item.pantryQuantity, 0.0001)
        assertEquals(400.0, item.purchaseQuantity, 0.0001)
        assertTrue(!item.isApproximatePantry)
        assertEquals(1, result.unknownPriceCount)
    }

    @Test
    fun localWilayaPriceIsPreferredOverDemoEstimate() {
        val demo = PriceObservationEntity("demo-potato", "potato", 500.0, "g", 100.0, "demo", "غير مرتبطة بولاية", null, "demo", "غير موثق")
        val local = PriceObservationEntity("local-potato", "potato", 500.0, "g", 150.0, "سوق الحي", "الجلفة", 1_800_000_000_000L, "user_reported", "غير موثق")

        val result = calculateBasket(
            plannedMeals = listOf(plan),
            recipes = listOf(recipe),
            pantry = emptyList(),
            prices = listOf(demo, local),
            selectedWilaya = "الجلفة",
            nowEpochMillis = 1_800_000_000_000L
        )

        assertEquals(150.0, result.items.single().cashOutlayDzd!!, 0.0001)
        assertEquals("ملاحظة سعر", result.items.single().priceLabel)
    }

    @Test
    fun weekStartsOnMondayForAlgerianMealPlanning() {
        assertEquals("2026-11-16", dz.meals.app.data.currentWeekStartIso(java.time.LocalDate.parse("2026-11-21")))
        assertEquals("2026-11-23", dz.meals.app.data.currentWeekStartIso(java.time.LocalDate.parse("2026-11-24")))
    }

    @Test
    fun staleLocalPriceIsIgnoredAndDemoPriceIsClearlyUsedAsFallback() {
        val demo = PriceObservationEntity("demo-potato", "potato", 500.0, "g", 100.0, "demo", "غير مرتبطة بولاية", null, "demo", "غير موثق")
        val staleLocal = PriceObservationEntity("old-local", "potato", 500.0, "g", 40.0, "سوق قديم", "الجلفة", 1_700_000_000_000L, "user_reported", "غير موثق")

        val result = calculateBasket(
            plannedMeals = listOf(plan),
            recipes = listOf(recipe),
            pantry = emptyList(),
            prices = listOf(demo, staleLocal),
            selectedWilaya = "الجلفة",
            nowEpochMillis = 1_800_000_000_000L
        )

        assertEquals(100.0, result.items.single().cashOutlayDzd!!, 0.0001)
        assertEquals("قيمة توضيحية", result.items.single().priceLabel)
    }

    @Test
    fun lockedMealConflictingWithAllergyRuleBlocksWeekGeneration() {
        val glutenRecipe = SeedRecipes.all.first { it.id == "couscous-vegetables" }
        val lockedMeal = plan.copy(recipeId = glutenRecipe.id, locked = true)
        assertEquals(
            glutenRecipe.id,
            findLockedAllergyConflict(listOf(lockedMeal), listOf(glutenRecipe), setOf("gluten"))
        )
        assertEquals(
            null,
            findLockedAllergyConflict(listOf(lockedMeal), listOf(glutenRecipe), emptySet())
        )
    }

    @Test
    fun weeklyPlannerIncludesBreakfastLunchAndDinnerForSevenDays() {
        assertEquals(listOf("breakfast", "lunch", "dinner"), dz.meals.app.data.MealSlots.ids)
        assertEquals(21, dz.meals.app.data.MealSlots.ids.size * 7)
    }

    @Test
    fun preferencesDefaultToLunchAndDinnerAndBoundSettings() {
        val defaults = dz.meals.app.data.AppPreferences()
        assertEquals(setOf("lunch", "dinner"), defaults.mealSlots)
        assertEquals("none", defaults.dietPattern)
        assertTrue(defaults.budgetBufferPercent in 0..30)
        assertTrue(defaults.maxCookingMinutes in 10..240)
        assertEquals("regular", defaults.meatPreference)
        assertEquals(3_000, defaults.monthlyMeatGrams)
        assertEquals("regular", defaults.oilPreference)
        assertTrue(!defaults.hasCompletedSetup)
    }

    @Test
    fun vegetarianAndVeganFiltersUseCanonicalIngredientCategories() {
        val chickenRecipe = recipe.copy(
            ingredients = listOf(RecipeIngredient("chicken", 300.0, "g"))
        )
        val categories = mapOf(
            "chicken" to FoodTaxonomyRef("foodcat-poultry", "foodsub-poultry-chicken")
        )
        assertTrue(!isRecipeAllowed(chickenRecipe, categories, emptySet(), "vegetarian"))
        assertTrue(!isRecipeAllowed(chickenRecipe, categories, emptySet(), "vegan"))
        assertTrue(isRecipeAllowed(chickenRecipe, categories, emptySet(), "none"))
        val milkRecipe = recipe.copy(allergens = setOf("milk"))
        assertTrue(!isRecipeAllowed(milkRecipe, emptyMap(), setOf("milk"), "none"))
    }

    @Test
    fun householdMeatPreferencesUseCanonicalIngredientTaxonomy() {
        val chicken = recipe.copy(
            id = "chicken-meal",
            ingredients = listOf(RecipeIngredient("chicken", 300.0, "g"))
        )
        val beef = recipe.copy(
            id = "beef-meal",
            ingredients = listOf(RecipeIngredient("beef", 300.0, "g"))
        )
        val fish = recipe.copy(
            id = "fish-meal",
            ingredients = listOf(RecipeIngredient("fish", 300.0, "g"))
        )
        val taxonomy = mapOf(
            "chicken" to FoodTaxonomyRef("foodcat-poultry", "foodsub-poultry-chicken"),
            "beef" to FoodTaxonomyRef("foodcat-meat", "foodsub-meat-red"),
            "fish" to FoodTaxonomyRef("foodcat-seafood", "foodsub-seafood-fish")
        )

        assertTrue(!isRecipeAllowedByMeatPreference(chicken, taxonomy, "none"))
        assertTrue(!isRecipeAllowedByMeatPreference(fish, taxonomy, "none"))
        assertTrue(isRecipeAllowedByMeatPreference(chicken, taxonomy, "chicken_only"))
        assertTrue(!isRecipeAllowedByMeatPreference(beef, taxonomy, "chicken_only"))
        assertTrue(!isRecipeAllowedByMeatPreference(fish, taxonomy, "chicken_only"))
        assertTrue(isRecipeAllowedByMeatPreference(beef, taxonomy, "monthly_limit"))
        assertTrue(isRecipeAllowedByMeatPreference(beef, taxonomy, "regular"))
    }

    @Test
    fun monthlyMeatAllowanceScalesHouseholdQuantitiesAndRejectsUnknownUnits() {
        val chicken = recipe.copy(
            id = "chicken-meal",
            baseServings = 2,
            ingredients = listOf(RecipeIngredient("chicken", 300.0, "g"))
        )
        val beefInKilograms = recipe.copy(
            id = "beef-meal",
            baseServings = 2,
            ingredients = listOf(RecipeIngredient("beef", 0.25, "kg"))
        )
        val unknownUnit = beefInKilograms.copy(
            ingredients = listOf(RecipeIngredient("beef", 1.0, "piece"))
        )
        val taxonomy = mapOf(
            "chicken" to FoodTaxonomyRef("foodcat-poultry", "foodsub-poultry-chicken"),
            "beef" to FoodTaxonomyRef("foodcat-meat", "foodsub-meat-red")
        )

        assertEquals(600.0, recipeMeatQuantityGrams(chicken, 4, taxonomy)!!, 0.0001)
        assertEquals(500.0, recipeMeatQuantityGrams(beefInKilograms, 4, taxonomy)!!, 0.0001)
        assertTrue(recipeMeatQuantityGrams(unknownUnit, 4, taxonomy) == null)
    }

    @Test
    fun reducedOilPreferenceUsesMeasuredOilPerHouseholdServing() {
        val oilyRecipe = recipe.copy(
            baseServings = 2,
            ingredients = listOf(RecipeIngredient("olive-oil", 20.0, "ml"))
        )
        val taxonomy = mapOf(
            "olive-oil" to FoodTaxonomyRef("foodcat-oils", "foodsub-oils-olive")
        )

        assertEquals(10.0, recipeOilQuantityPerServing(oilyRecipe, 4, taxonomy)!!, 0.0001)
    }

    @Test
    fun cookingTimeAndDislikedIngredientsAreSoftRankingPenalties() {
        val baseline = softPreferencePenalty(recipe, emptySet(), 60)
        val withDisliked = softPreferencePenalty(recipe, setOf("potato"), 60)
        val withTimeLimit = softPreferencePenalty(recipe, emptySet(), 10)
        assertEquals(0.0, baseline, 0.0001)
        assertTrue(withDisliked > baseline)
        assertTrue(withTimeLimit > baseline)
    }

    @Test
    fun pinnedMealsThatConflictWithDietPatternBlockRegeneration() {
        val chicken = recipe.copy(
            id = "chicken",
            ingredients = listOf(RecipeIngredient("chicken", 250.0, "g"))
        )
        val pinned = plan.copy(recipeId = chicken.id, isUserEdited = true)
        assertEquals(
            chicken.id,
            findPinnedDietConflict(listOf(pinned), listOf(chicken), mapOf("chicken" to FoodTaxonomyRef("foodcat-poultry", "foodsub-poultry-chicken")), "vegetarian")
        )
        assertEquals(
            null,
            findPinnedDietConflict(listOf(pinned), listOf(chicken), mapOf("chicken" to FoodTaxonomyRef("foodcat-poultry", "foodsub-poultry-chicken")), "none")
        )
    }

    @Test
    fun regenerationPreservesLockedCookedAndManuallyEditedMeals() {
        assertTrue(shouldPreserveMeal(plan.copy(locked = true), false))
        assertTrue(shouldPreserveMeal(plan.copy(isCooked = true), false))
        assertTrue(shouldPreserveMeal(plan.copy(isUserEdited = true), false))
        assertTrue(!shouldPreserveMeal(plan, false))
        assertTrue(shouldPreserveMeal(plan.copy(recipeId = "existing"), true))
        assertTrue(!shouldPreserveMeal(plan.copy(recipeId = null), true))
    }

    @Test
    fun historicalSnapshotPriceRemainsAvailableWithHistoricalLabel() {
        val oldPrice = PriceObservationEntity(
            "old-potato", "potato", 500.0, "g", 100.0, "سوق سابق", "الجلفة",
            1_700_000_000_000L, "user_reported", "غير موثق"
        )
        val history = calculateBasket(
            plannedMeals = listOf(plan),
            recipes = listOf(recipe),
            pantry = emptyList(),
            prices = listOf(oldPrice),
            selectedWilaya = "الجلفة",
            nowEpochMillis = 1_800_000_000_000L,
            allowStaleSnapshotPrices = true
        )
        assertEquals(100.0, history.items.single().cashOutlayDzd!!, 0.0001)
        assertEquals("سعر تاريخي من لقطة الخطة", history.items.single().priceLabel)
    }

    @Test
    fun savedPlanKeepsItsPriceSnapshotWhenNewReportsArrive() {
        val oldPrice = PriceObservationEntity("old", "potato", 500.0, "g", 100.0, "old source", "الجلفة", 1_700_000_000_000L, "user_reported", "غير موثق")
        val newPrice = PriceObservationEntity("new", "potato", 500.0, "g", 160.0, "new source", "الجلفة", 1_800_000_000_000L, "user_reported", "غير موثق")
        assertEquals(
            listOf("old"),
            pricesForSavedPlan(listOf(oldPrice), listOf(oldPrice, newPrice), 1_700_000_000_000L).map { it.id }
        )
        assertEquals(
            listOf("old", "new"),
            pricesForSavedPlan(emptyList(), listOf(oldPrice, newPrice), 1_700_000_000_000L).map { it.id }
        )
        assertEquals(
            listOf("old"),
            pricesForSavedPlan(emptyList(), listOf(oldPrice), 1_700_000_000_000L).map { it.id }
        )
    }

    @Test
    fun cookedMealOnlyDeductsExactAvailablePantryQuantity() {
        assertEquals(150.0, exactPantryDeduction(200.0, 150.0, false), 0.0001)
        assertEquals(0.0, exactPantryDeduction(200.0, null, true), 0.0001)
        assertEquals(0.0, exactPantryDeduction(200.0, 150.0, true), 0.0001)
        assertEquals(0.0, exactPantryDeduction(0.0, 150.0, false), 0.0001)
    }

    @Test
    fun seededCatalogueHasConsistentUnitsAndAllergenMetadata() {
        SeedRecipes.validateCatalogue()
    }
}
