package dz.meals.app.domain

import dz.meals.app.data.FoodTaxonomyRef
import dz.meals.app.data.IngredientMaster
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
    fun planAdjustmentSomeDeductsOnlyTheQuantityEntered() {
        val price = PriceObservationEntity(
            "demo-potato", "potato", 100.0, "g", 20.0,
            "demo", "غير محدد", null, "demo", "غير موثق"
        )
        val result = calculateBasket(
            plannedMeals = listOf(plan),
            recipes = listOf(recipe),
            pantry = emptyList(),
            prices = listOf(price),
            planIngredientAdjustments = mapOf(
                ("potato" to "g") to PlanIngredientAdjustment(
                    PlanIngredientAdjustment.Type.SOME,
                    availableQuantity = 100.0
                )
            )
        )

        val item = result.items.single()
        assertEquals(400.0, item.requiredQuantity, 0.0001)
        assertEquals(300.0, item.purchaseQuantity, 0.0001)
        assertEquals(100.0, item.alreadyHaveQuantity, 0.0001)
        assertTrue(!item.alreadyHaveEnough)
        assertEquals(60.0, item.cashOutlayDzd!!, 0.0001)
        assertEquals(0, result.unknownPriceCount)
    }

    @Test
    fun planAdjustmentEnoughMakesPurchaseNeedAndCashOutlayZero() {
        val result = calculateBasket(
            plannedMeals = listOf(plan),
            recipes = listOf(recipe),
            pantry = emptyList(),
            prices = emptyList(),
            planIngredientAdjustments = mapOf(
                ("potato" to "g") to PlanIngredientAdjustment(
                    PlanIngredientAdjustment.Type.ENOUGH
                )
            )
        )

        val item = result.items.single()
        assertEquals(400.0, item.requiredQuantity, 0.0001)
        assertEquals(0.0, item.purchaseQuantity, 0.0001)
        assertTrue(item.alreadyHaveEnough)
        assertEquals(0.0, item.cashOutlayDzd!!, 0.0001)
        assertEquals(0, result.unknownPriceCount)
    }

    @Test
    fun planIngredientAdjustmentDoesNotLeakIntoAnotherPlanCalculation() {
        val adjusted = calculateBasket(
            plannedMeals = listOf(plan),
            recipes = listOf(recipe),
            pantry = emptyList(),
            prices = emptyList(),
            planIngredientAdjustments = mapOf(
                ("potato" to "g") to PlanIngredientAdjustment(
                    PlanIngredientAdjustment.Type.SOME,
                    availableQuantity = 100.0
                )
            )
        )
        val separatePlanWithoutAdjustment = calculateBasket(
            plannedMeals = listOf(plan.copy(weekStartIso = "2026-11-23")),
            recipes = listOf(recipe),
            pantry = emptyList(),
            prices = emptyList()
        )

        assertEquals(300.0, adjusted.items.single().purchaseQuantity, 0.0001)
        assertEquals(400.0, separatePlanWithoutAdjustment.items.single().purchaseQuantity, 0.0001)
        assertEquals(100.0, adjusted.items.single().alreadyHaveQuantity, 0.0001)
        assertEquals(0.0, separatePlanWithoutAdjustment.items.single().alreadyHaveQuantity, 0.0001)
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
    fun verifiedMarketProductRangeOverridesLegacyPriceAndRoundsWholePacks() {
        val legacy = PriceObservationEntity(
            "legacy-potato", "potato", 100.0, "g", 5.0,
            "legacy", "Laghouat", 1_800_000_000_000L, "user_reported", "unverified"
        )
        val evidence = MarketProductPriceEvidence(
            productId = "retail-potato",
            sourceListingId = "listing-potato",
            ingredientId = "potato",
            productName = "Potatoes 1 kg",
            packageQuantity = 1_000.0,
            packageUnit = "g",
            mappingStatus = "VERIFIED",
            mappingReviewedAtEpochMillis = 1_799_999_000_000L,
            foodScopeReviewNeeded = false,
            priceDzd = null,
            priceMinDzd = 200.0,
            priceMaxDzd = 240.0,
            currencyCode = "DZD",
            observedAtEpochMillis = 1_799_999_900_000L,
            priceStatus = "observed_unverified",
            availabilityRaw = "https://schema.org/InStock",
            storeName = "متجر محلي",
            source = "test",
            wilaya = "Laghouat",
            sourceNotice = "Indicative retailer price"
        )

        val result = calculateBasket(
            plannedMeals = listOf(plan),
            recipes = listOf(recipe),
            pantry = emptyList(),
            prices = listOf(legacy),
            selectedWilaya = "Laghouat",
            nowEpochMillis = 1_800_000_000_000L,
            marketProductPriceEvidence = listOf(evidence)
        )

        val item = result.items.single()
        assertEquals(400.0, item.purchaseQuantity, 0.0001)
        assertEquals(200.0, item.marketCashOutlayMinDzd!!, 0.0001)
        assertEquals(240.0, item.marketCashOutlayMaxDzd!!, 0.0001)
        assertEquals(240.0, item.cashOutlayDzd!!, 0.0001)
        assertEquals(80.0, item.marketConsumedValueMinDzd!!, 0.0001)
        assertEquals(96.0, item.marketConsumedValueMaxDzd!!, 0.0001)
        assertEquals(240.0, result.purchaseOutlayDzd, 0.0001)
        assertEquals(200.0, result.purchaseOutlayMinDzd!!, 0.0001)
        assertEquals(240.0, result.purchaseOutlayMaxDzd!!, 0.0001)
        assertEquals("سعر استرشادي غير مؤكد", item.priceLabel)
    }

    @Test
    fun productMarketEstimateRequiresAnExactSelectedWilaya() {
        val evidence = MarketProductPriceEvidence(
            productId = "retail-potato",
            sourceListingId = "listing-potato",
            ingredientId = "potato",
            productName = "Potatoes 1 kg",
            packageQuantity = 1_000.0,
            packageUnit = "g",
            mappingStatus = "VERIFIED",
            mappingReviewedAtEpochMillis = 1_799_999_000_000L,
            foodScopeReviewNeeded = false,
            priceDzd = 200.0,
            priceMinDzd = null,
            priceMaxDzd = null,
            currencyCode = "DZD",
            observedAtEpochMillis = 1_799_999_900_000L,
            priceStatus = "observed_unverified",
            availabilityRaw = "https://schema.org/InStock",
            storeName = "متجر محلي",
            source = "test",
            wilaya = "Laghouat",
            sourceNotice = ""
        )

        val result = calculateBasket(
            plannedMeals = listOf(plan),
            recipes = listOf(recipe),
            pantry = emptyList(),
            prices = emptyList(),
            selectedWilaya = null,
            nowEpochMillis = 1_800_000_000_000L,
            marketProductPriceEvidence = listOf(evidence)
        )

        assertEquals(1, result.unknownPriceCount)
        assertEquals(null, result.items.single().marketCashOutlayMinDzd)
    }

    @Test
    fun incompleteBasketDoesNotExposeACompleteMarketTotalRange() {
        val twoIngredientRecipe = recipe.copy(
            ingredients = listOf(
                RecipeIngredient("potato", 200.0, "g"),
                RecipeIngredient("carrot", 100.0, "g")
            )
        )
        val potatoEvidence = MarketProductPriceEvidence(
            productId = "retail-potato",
            sourceListingId = "listing-potato",
            ingredientId = "potato",
            productName = "Potatoes 1 kg",
            packageQuantity = 1_000.0,
            packageUnit = "g",
            mappingStatus = "VERIFIED",
            mappingReviewedAtEpochMillis = 1_799_999_000_000L,
            foodScopeReviewNeeded = false,
            priceDzd = 200.0,
            priceMinDzd = null,
            priceMaxDzd = null,
            currencyCode = "DZD",
            observedAtEpochMillis = 1_799_999_900_000L,
            priceStatus = "observed_unverified",
            availabilityRaw = "https://schema.org/InStock",
            storeName = "متجر محلي",
            source = "test",
            wilaya = "Laghouat",
            sourceNotice = ""
        )

        val result = calculateBasket(
            plannedMeals = listOf(plan),
            recipes = listOf(twoIngredientRecipe),
            pantry = emptyList(),
            prices = emptyList(),
            selectedWilaya = "Laghouat",
            nowEpochMillis = 1_800_000_000_000L,
            marketProductPriceEvidence = listOf(potatoEvidence)
        )

        assertEquals(1, result.unknownPriceCount)
        assertEquals(null, result.purchaseOutlayMinDzd)
        assertEquals(null, result.purchaseOutlayMaxDzd)
    }

    @Test
    fun weekStartsOnMondayForAlgerianMealPlanning() {
        assertEquals("2026-11-16", dz.meals.app.data.currentWeekStartIso(java.time.LocalDate.parse("2026-11-21")))
        assertEquals("2026-11-23", dz.meals.app.data.currentWeekStartIso(java.time.LocalDate.parse("2026-11-24")))
    }

    @Test
    fun missingWilayaNeverUsesAnotherNamedWilayasLegacyPrice() {
        val otherWilaya = PriceObservationEntity(
            "local-other-wilaya", "potato", 500.0, "g", 150.0,
            "سوق محلي", "Laghouat", 1_800_000_000_000L, "user_reported", "غير موثق"
        )

        val result = calculateBasket(
            plannedMeals = listOf(plan),
            recipes = listOf(recipe),
            pantry = emptyList(),
            prices = listOf(otherWilaya),
            selectedWilaya = null,
            nowEpochMillis = 1_800_000_000_000L
        )

        assertEquals(1, result.unknownPriceCount)
        assertEquals(null, result.items.single().cashOutlayDzd)
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
    fun basketUsesCanonicalDemandsAndConvertsLegacyPackageUnits() {
        val mixedUnitRecipe = recipe.copy(
            baseServings = 2,
            ingredients = listOf(
                RecipeIngredient("potato", 200.0, "g"),
                RecipeIngredient("potato", 0.1, "kg"),
                RecipeIngredient("oil", 1.0, "l"),
                RecipeIngredient("oil", 100.0, "ml"),
                RecipeIngredient("optional", 50.0, "g", optional = true)
            )
        )
        val prices = listOf(
            PriceObservationEntity(
                "potato-kilo", "potato", 1.0, "kg", 100.0,
                "local shop", "غير محدد", null, "demo", "illustrative"
            )
        )
        val result = calculateBasket(
            plannedMeals = listOf(plan.copy(servings = 4)),
            recipes = listOf(mixedUnitRecipe),
            pantry = emptyList(),
            prices = prices,
            planIngredientAdjustments = mapOf(
                ("potato" to "kg") to PlanIngredientAdjustment(
                    PlanIngredientAdjustment.Type.SOME,
                    availableQuantity = 0.1
                )
            )
        )
        val potatoDemand = result.ingredientDemands.single { it.ingredientId == "potato" }
        val potatoItem = result.items.single { it.ingredientId == "potato" }
        // 200 g + 0.1 kg per two servings scales to 600 g at four servings.
        assertEquals(600.0, potatoDemand.quantity, 0.0001)
        assertEquals("g", potatoDemand.unit)
        assertEquals(2, potatoDemand.contributions.size)
        assertEquals(100.0, potatoItem.alreadyHaveQuantity, 0.0001)
        assertEquals(1_000.0, potatoItem.purchaseQuantity, 0.0001)
        assertEquals(100.0, potatoItem.cashOutlayDzd!!, 0.0001)
        assertTrue(result.ingredientDemands.none { it.ingredientId == "optional" })
    }

    private fun ingredientMaster(
        id: String,
        nameAr: String,
        nameFr: String = ""
    ) = IngredientMaster(
        id = id,
        nameAr = nameAr,
        nameFr = nameFr,
        baseUnit = "g",
        allergens = emptySet(),
        aliases = emptyList(),
        categoryId = "foodcat-other",
        subcategoryId = "foodsub-other-general"
    )

    @Test
    fun noMeatPreferenceUsesWholeMeatAndOffalIngredientWordsOnly() {
        val meat = recipe.copy(
            id = "meat-meal",
            ingredients = listOf(RecipeIngredient("meat", 300.0, "g"))
        )
        val liver = recipe.copy(
            id = "liver-meal",
            ingredients = listOf(RecipeIngredient("liver", 150.0, "g"))
        )
        val tongue = recipe.copy(
            id = "tongue-meal",
            ingredients = listOf(RecipeIngredient("tongue", 150.0, "g"))
        )
        val chicken = recipe.copy(
            id = "chicken-meal",
            ingredients = listOf(RecipeIngredient("chicken", 300.0, "g"))
        )
        val fish = recipe.copy(
            id = "fish-meal",
            ingredients = listOf(RecipeIngredient("fish", 300.0, "g"))
        )
        val chickpeas = recipe.copy(
            id = "chickpeas-meal",
            ingredients = listOf(RecipeIngredient("chickpeas", 200.0, "g"))
        )
        val artichokeHearts = recipe.copy(
            id = "artichoke-hearts-meal",
            ingredients = listOf(RecipeIngredient("artichoke-hearts", 100.0, "g"))
        )
        val animalHeart = recipe.copy(
            id = "animal-heart-meal",
            ingredients = listOf(RecipeIngredient("animal-heart", 100.0, "g"))
        )
        val lambCut = recipe.copy(
            id = "lamb-cut-meal",
            ingredients = listOf(RecipeIngredient("lamb-cut", 300.0, "g"))
        )
        val sheepDairy = recipe.copy(
            id = "sheep-dairy-meal",
            ingredients = listOf(RecipeIngredient("sheep-dairy", 100.0, "g"))
        )

        // Deliberately give chickpeas and artichoke hearts misleading meat
        // categories: "none" must use the actual ingredient name, not taxonomy.
        val taxonomy = mapOf(
            "chicken" to FoodTaxonomyRef("foodcat-poultry", "foodsub-poultry-chicken"),
            "fish" to FoodTaxonomyRef("foodcat-seafood", "foodsub-seafood-fish"),
            "meat" to FoodTaxonomyRef("foodcat-meat", "foodsub-meat-red"),
            "chickpeas" to FoodTaxonomyRef("foodcat-meat", "foodsub-meat-red"),
            "artichoke-hearts" to FoodTaxonomyRef("foodcat-meat", "foodsub-meat-red")
        )
        val names = mapOf(
            "meat" to ingredientMaster("meat", "لحم مفروم", "viande hachée"),
            "liver" to ingredientMaster("liver", "كبدة دجاج", "foie de poulet"),
            "tongue" to ingredientMaster("tongue", "لسان عجل", "langue de veau"),
            "chicken" to ingredientMaster("chicken", "دجاج", "poulet"),
            "fish" to ingredientMaster("fish", "سمك", "poisson"),
            "chickpeas" to ingredientMaster("chickpeas", "الحمص", "pois chiches"),
            "artichoke-hearts" to ingredientMaster("artichoke-hearts", "قلوب خرشوف", "coeurs d'artichauts"),
            "animal-heart" to ingredientMaster("animal-heart", "قلب دجاج", "coeur de poulet"),
            "lamb-cut" to ingredientMaster("lamb-cut", "فخذ خروف", "gigot d'agneau"),
            "sheep-dairy" to ingredientMaster("sheep-dairy", "لبن غنم", "lait de mouton")
        )

        assertTrue(!isRecipeAllowedByMeatPreference(meat, taxonomy, "none", names))
        assertTrue(!isRecipeAllowedByMeatPreference(liver, taxonomy, "none", names))
        assertTrue(!isRecipeAllowedByMeatPreference(tongue, taxonomy, "none", names))
        assertTrue(!isRecipeAllowedByMeatPreference(animalHeart, taxonomy, "none", names))
        assertTrue(!isRecipeAllowedByMeatPreference(lambCut, taxonomy, "none", names))
        assertTrue(isRecipeAllowedByMeatPreference(sheepDairy, taxonomy, "none", names))
        assertTrue(isRecipeAllowedByMeatPreference(chicken, taxonomy, "none", names))
        assertTrue(isRecipeAllowedByMeatPreference(fish, taxonomy, "none", names))
        assertTrue(isRecipeAllowedByMeatPreference(chickpeas, taxonomy, "none", names))
        assertTrue(isRecipeAllowedByMeatPreference(artichokeHearts, taxonomy, "none", names))

        // A missing name never falls back to a broad category exclusion.
        assertTrue(isRecipeAllowedByMeatPreference(meat, taxonomy, "none"))
        assertTrue(isRecipeAllowedByMeatPreference(chicken, taxonomy, "chicken_only", names))
        assertTrue(!isRecipeAllowedByMeatPreference(meat, taxonomy, "chicken_only", names))
        assertTrue(!isRecipeAllowedByMeatPreference(fish, taxonomy, "chicken_only", names))
        assertTrue(isRecipeAllowedByMeatPreference(meat, taxonomy, "monthly_limit", names))
        assertTrue(isRecipeAllowedByMeatPreference(meat, taxonomy, "regular", names))
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
