package dz.meals.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MealsDao {
    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun recipeCount(): Int

    @Query("SELECT recipeId FROM favorite_recipes")
    suspend fun getFavoriteRecipeIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavoriteRecipe(item: FavoriteRecipeEntity)

    @Query("DELETE FROM favorite_recipes WHERE recipeId = :recipeId")
    suspend fun removeFavoriteRecipe(recipeId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIngredients(items: List<IngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipes(items: List<RecipeEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeIngredients(items: List<RecipeIngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeSteps(items: List<RecipeStepEntity>)

    @Upsert
    suspend fun upsertFoodCategories(items: List<FoodCategoryEntity>)

    @Upsert
    suspend fun upsertFoodSubcategories(items: List<FoodSubcategoryEntity>)

    @Query("SELECT COUNT(*) FROM food_categories")
    suspend fun countFoodCategories(): Int

    @Query("SELECT COUNT(*) FROM food_subcategories")
    suspend fun countFoodSubcategories(): Int

    @Query("SELECT * FROM food_categories ORDER BY sortOrder, id")
    suspend fun getFoodCategories(): List<FoodCategoryEntity>

    @Query("SELECT * FROM food_subcategories ORDER BY categoryId, sortOrder, id")
    suspend fun getFoodSubcategories(): List<FoodSubcategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoodItems(items: List<FoodItemEntity>)

    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun countFoodItems(): Int

    @Query("SELECT * FROM food_items ORDER BY nameAr")
    suspend fun getFoodItems(): List<FoodItemEntity>

    @Query("SELECT * FROM food_items WHERE id = :id LIMIT 1")
    suspend fun getFoodItem(id: String): FoodItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogueIngredients(items: List<IngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogueRecipes(items: List<RecipeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogueRecipeIngredients(items: List<RecipeIngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogueRecipeSteps(items: List<RecipeStepEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecipeAttributions(items: List<RecipeAttributionEntity>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId IN (:recipeIds)")
    suspend fun deleteRecipeIngredientsForRecipes(recipeIds: List<String>)

    @Query("DELETE FROM recipe_steps WHERE recipeId IN (:recipeIds)")
    suspend fun deleteRecipeStepsForRecipes(recipeIds: List<String>)

    @Query("SELECT COUNT(*) FROM recipes WHERE id LIKE 'orc-%' OR id LIKE 'focus-%' OR id LIKE 'unitools-%'")
    suspend fun countArabicCatalogueRecipes(): Int

    @Query("SELECT COUNT(*) FROM recipes WHERE id LIKE 'dz-algerian-dzirielle-%'")
    suspend fun countAlgerianCatalogueRecipes(): Int

    @Query("SELECT COUNT(*) FROM recipes WHERE id LIKE 'unitools-%'")
    suspend fun countUnitoolsRecipes(): Int

    /**
     * Imported recipe ingredients with no seed/non-catalogue recipe usage.
     * Keep these hidden until the pinned catalogue is fully installed.
     */
    @Query("""
        SELECT DISTINCT imported.ingredientId
        FROM recipe_ingredients AS imported
        WHERE (
            imported.recipeId LIKE 'orc-%' OR
            imported.recipeId LIKE 'focus-%' OR
            imported.recipeId LIKE 'unitools-%'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM recipe_ingredients AS seedUsage
            WHERE seedUsage.ingredientId = imported.ingredientId
              AND seedUsage.recipeId NOT LIKE 'orc-%'
              AND seedUsage.recipeId NOT LIKE 'focus-%'
              AND seedUsage.recipeId NOT LIKE 'unitools-%'
        )
    """)
    suspend fun getUnpublishedArabicCatalogueIngredientIds(): List<String>


    @Query("DELETE FROM recipe_ingredients WHERE recipeId LIKE 'orc-%'")
    suspend fun deleteLegacyOpenRecipeIngredients()

    @Query("DELETE FROM recipe_steps WHERE recipeId LIKE 'orc-%'")
    suspend fun deleteLegacyOpenRecipeSteps()

    @Query("DELETE FROM recipe_attributions WHERE recipeId LIKE 'orc-%'")
    suspend fun deleteLegacyOpenRecipeAttributions()

    @Query("DELETE FROM favorite_recipes WHERE recipeId LIKE 'orc-%'")
    suspend fun deleteLegacyOpenRecipeFavorites()

    @Query("UPDATE plan_meals SET recipeId = NULL WHERE recipeId LIKE 'orc-%'")
    suspend fun detachLegacyOpenRecipePlans()

    @Query("DELETE FROM recipes WHERE id LIKE 'orc-%'")
    suspend fun deleteLegacyOpenRecipes()

    @Query("""
        DELETE FROM ingredients
        WHERE NOT EXISTS (
              SELECT 1 FROM recipe_ingredients
              WHERE recipe_ingredients.ingredientId = ingredients.id
          )
          AND NOT EXISTS (
              SELECT 1 FROM pantry_items
              WHERE pantry_items.ingredientId = ingredients.id
          )
          AND NOT EXISTS (
              SELECT 1 FROM price_observations
              WHERE price_observations.ingredientId = ingredients.id
          )
          AND NOT EXISTS (
              SELECT 1 FROM shopping_items
              WHERE shopping_items.ingredientId = ingredients.id
          )
          AND NOT EXISTS (
              SELECT 1 FROM plan_price_snapshots
              WHERE plan_price_snapshots.ingredientId = ingredients.id
          )
          AND NOT EXISTS (
              SELECT 1 FROM cooked_meal_deductions
              WHERE cooked_meal_deductions.ingredientId = ingredients.id
          )
    """)
    suspend fun deleteUnusedLegacyOpenRecipeIngredients()


    @Query("SELECT * FROM recipe_attributions")
    suspend fun getRecipeAttributions(): List<RecipeAttributionEntity>

    @Query("SELECT * FROM recipe_image_overrides")
    suspend fun getRecipeImageOverrides(): List<RecipeImageOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecipeImageOverride(item: RecipeImageOverrideEntity)

    @Query("DELETE FROM recipe_image_overrides WHERE recipeId = :recipeId")
    suspend fun removeRecipeImageOverride(recipeId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPrices(items: List<PriceObservationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPriceObservation(item: PriceObservationEntity)

    @Query("""
        UPDATE ingredients
        SET categoryId = COALESCE(
                (SELECT food_items.categoryId FROM food_items WHERE food_items.id = ingredients.id),
                'foodcat-other'
            ),
            subcategoryId = COALESCE(
                (SELECT food_items.subcategoryId FROM food_items WHERE food_items.id = ingredients.id),
                'foodsub-other-general'
            )
    """)
    suspend fun syncIngredientTaxonomyFromFoodItems(): Int

    @Query("SELECT * FROM ingredients ORDER BY nameAr")
    suspend fun getIngredients(): List<IngredientEntity>

    @Query("SELECT * FROM recipes ORDER BY nameAr")
    suspend fun getRecipes(): List<RecipeEntity>

    @Query("SELECT * FROM plan_runs WHERE weekStartIso = :weekStartIso LIMIT 1")
    suspend fun getPlanRun(weekStartIso: String): PlanRunEntity?

    @Query("SELECT * FROM planning_periods WHERE id = :periodId LIMIT 1")
    suspend fun getPlanningPeriod(periodId: String): PlanningPeriodEntity?

    @Query("""
        SELECT * FROM planning_periods
        WHERE periodType = :periodType
            AND startDateIso <= :rangeEndIso
            AND endDateIso >= :rangeStartIso
        ORDER BY startDateIso, id
    """)
    suspend fun getPlanningPeriodsOverlappingRange(
        periodType: String,
        rangeStartIso: String,
        rangeEndIso: String
    ): List<PlanningPeriodEntity>

    @Upsert
    suspend fun upsertPlanningPeriod(period: PlanningPeriodEntity)

    @Upsert
    suspend fun upsertPlanningPeriods(periods: List<PlanningPeriodEntity>)

    @Query("DELETE FROM planning_periods WHERE id = :periodId")
    suspend fun deletePlanningPeriod(periodId: String)

    @Query("""
        SELECT * FROM planning_period_meals
        WHERE periodId = :periodId
        ORDER BY dateIso, mealSlot, id
    """)
    suspend fun getPlanningPeriodMeals(periodId: String): List<PlanningPeriodMealEntity>

    @Query("""
        SELECT * FROM planning_period_meals
        WHERE dateIso BETWEEN :startDateIso AND :endDateIso
        ORDER BY dateIso, mealSlot, id
    """)
    suspend fun getPlanningPeriodMealsInDateRange(
        startDateIso: String,
        endDateIso: String
    ): List<PlanningPeriodMealEntity>

    @Upsert
    suspend fun upsertPlanningPeriodMeal(meal: PlanningPeriodMealEntity)

    @Upsert
    suspend fun upsertPlanningPeriodMeals(meals: List<PlanningPeriodMealEntity>)

    @Query("DELETE FROM planning_period_meals WHERE periodId = :periodId")
    suspend fun deletePlanningPeriodMeals(periodId: String)

    @Query("""
        SELECT * FROM plan_ingredient_adjustments
        WHERE periodId = :periodId
        ORDER BY ingredientId, unit
    """)
    suspend fun getPlanIngredientAdjustments(periodId: String): List<PlanIngredientAdjustmentEntity>

    @Query("""
        SELECT * FROM plan_ingredient_adjustments
        WHERE periodId = :periodId AND ingredientId = :ingredientId AND unit = :unit
        LIMIT 1
    """)
    suspend fun getPlanIngredientAdjustment(
        periodId: String,
        ingredientId: String,
        unit: String
    ): PlanIngredientAdjustmentEntity?

    @Upsert
    suspend fun upsertPlanIngredientAdjustment(adjustment: PlanIngredientAdjustmentEntity)

    @Query("""
        DELETE FROM plan_ingredient_adjustments
        WHERE periodId = :periodId AND ingredientId = :ingredientId AND unit = :unit
    """)
    suspend fun deletePlanIngredientAdjustment(periodId: String, ingredientId: String, unit: String)

    @Query("DELETE FROM plan_ingredient_adjustments WHERE periodId = :periodId")
    suspend fun deletePlanIngredientAdjustments(periodId: String)

    @Query("""
        SELECT * FROM plan_market_price_snapshots
        WHERE periodId = :periodId
        ORDER BY ingredientId, storeName, sourceListingId
    """)
    suspend fun getPlanMarketPriceSnapshots(periodId: String): List<PlanMarketPriceSnapshotEntity>

    @Upsert
    suspend fun upsertPlanMarketPriceSnapshots(snapshots: List<PlanMarketPriceSnapshotEntity>)

    @Query("DELETE FROM plan_market_price_snapshots WHERE periodId = :periodId")
    suspend fun deletePlanMarketPriceSnapshots(periodId: String)


    @Query("SELECT * FROM plan_price_snapshots WHERE weekStartIso = :weekStartIso")
    suspend fun getPlanPriceSnapshots(weekStartIso: String): List<PlanPriceSnapshotEntity>

    @Query("DELETE FROM plan_price_snapshots WHERE weekStartIso = :weekStartIso")
    suspend fun deletePlanPriceSnapshots(weekStartIso: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanPriceSnapshots(items: List<PlanPriceSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlanRun(item: PlanRunEntity)

    @Query("SELECT * FROM recipe_ingredients ORDER BY recipeId")
    suspend fun getRecipeIngredients(): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_steps ORDER BY recipeId, stepNumber")
    suspend fun getRecipeSteps(): List<RecipeStepEntity>

    @Query("SELECT * FROM plan_meals WHERE weekStartIso = :weekStartIso ORDER BY dayIndex, mealSlot")
    suspend fun getPlanMeals(weekStartIso: String): List<PlanMealEntity>

    @Query("SELECT * FROM plan_meals WHERE weekStartIso BETWEEN :firstWeekStartIso AND :lastWeekStartIso")
    suspend fun getPlanMealsForWeekStartsInRange(
        firstWeekStartIso: String,
        lastWeekStartIso: String
    ): List<PlanMealEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCookedMealDeduction(item: CookedMealDeductionEntity)

    @Query("SELECT * FROM cooked_meal_deductions WHERE mealId = :mealId")
    suspend fun getCookedMealDeductions(mealId: String): List<CookedMealDeductionEntity>

    @Query("DELETE FROM cooked_meal_deductions WHERE mealId = :mealId")
    suspend fun deleteCookedMealDeductions(mealId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlanMeal(item: PlanMealEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlanMeals(items: List<PlanMealEntity>)

    @Query("DELETE FROM plan_meals WHERE weekStartIso = :weekStartIso")
    suspend fun deleteWeek(weekStartIso: String)

    @Query("DELETE FROM plan_meals WHERE weekStartIso = :weekStartIso AND mealSlot IN (:mealSlots)")
    suspend fun deletePlanSlots(weekStartIso: String, mealSlots: List<String>)

    @Query("SELECT * FROM pantry_items ORDER BY ingredientId, unit, id")
    suspend fun getPantryItems(): List<PantryItemEntity>

    @Query("SELECT * FROM pantry_items WHERE id = :id LIMIT 1")
    suspend fun getPantryItem(id: String): PantryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPantryItem(item: PantryItemEntity)

    @Query("DELETE FROM pantry_items WHERE id = :id")
    suspend fun deletePantryItem(id: String)

    @Query("DELETE FROM pantry_items WHERE ingredientId = :ingredientId AND unit = :unit")
    suspend fun deletePantryItemsForIngredientUnit(ingredientId: String, unit: String)

    @Query("SELECT * FROM price_observations")
    suspend fun getPriceObservations(): List<PriceObservationEntity>

    @Query("SELECT * FROM shopping_items WHERE weekStartIso = :weekStartIso ORDER BY userAdded, customName, ingredientId")
    suspend fun getShoppingItems(weekStartIso: String): List<ShoppingItemEntity>

    @Query("UPDATE shopping_items SET weekStartIso = :weekStartIso WHERE weekStartIso = ''")
    suspend fun assignUnscopedShoppingItemsToWeek(weekStartIso: String): Int

    @Query("UPDATE shopping_items SET id = weekStartIso || '::' || id WHERE userAdded = 0 AND weekStartIso <> '' AND instr(id, '::') = 0")
    suspend fun scopeLegacyGeneratedShoppingItemIds(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShoppingItems(items: List<ShoppingItemEntity>)

    @Query("DELETE FROM shopping_items WHERE userAdded = 0 AND weekStartIso = :weekStartIso")
    suspend fun deleteGeneratedShoppingItems(weekStartIso: String)

    @Query("UPDATE shopping_items SET checked = :checked, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun setShoppingItemChecked(id: String, checked: Boolean, updatedAt: Long)

    @Query("SELECT * FROM shopping_items WHERE id = :id LIMIT 1")
    suspend fun getShoppingItem(id: String): ShoppingItemEntity?

    @Query("SELECT * FROM shopping_items WHERE id = :id AND userAdded = 1 LIMIT 1")
    suspend fun getCustomShoppingItem(id: String): ShoppingItemEntity?

    @Query("DELETE FROM shopping_items WHERE id = :id AND userAdded = 1")
    suspend fun deleteCustomShoppingItem(id: String)

    @Upsert
    suspend fun upsertMarketProducts(items: List<MarketProductEntity>)

    @Upsert
    suspend fun upsertMarketProductSources(items: List<MarketProductSourceEntity>)

    @Upsert
    suspend fun upsertMarketPriceObservations(items: List<MarketPriceObservationEntity>)

    @Query("SELECT COUNT(*) FROM market_products")
    suspend fun countMarketProducts(): Int

    @Query("SELECT COUNT(*) FROM market_product_sources")
    suspend fun countMarketProductSources(): Int

    @Query("SELECT COUNT(*) FROM market_price_observations")
    suspend fun countMarketPriceObservations(): Int

    @Query("SELECT * FROM market_products ORDER BY name COLLATE NOCASE, id LIMIT :limit")
    suspend fun getMarketProducts(limit: Int): List<MarketProductEntity>

    @Query("""
        SELECT * FROM market_products
        WHERE name LIKE '%' || :query || '%'
           OR IFNULL(brand, '') LIKE '%' || :query || '%'
           OR gtin = :query
        ORDER BY name COLLATE NOCASE, id
        LIMIT :limit
    """)
    suspend fun searchMarketProducts(query: String, limit: Int): List<MarketProductEntity>

    @Query("SELECT * FROM market_product_sources WHERE productId = :productId ORDER BY storeName, source")
    suspend fun getMarketProductSources(productId: String): List<MarketProductSourceEntity>

    @Query("SELECT * FROM market_price_observations WHERE productId = :productId ORDER BY observedAtEpochMillis DESC")
    suspend fun getMarketPriceObservations(productId: String): List<MarketPriceObservationEntity>

    @Query("SELECT * FROM market_price_observations ORDER BY observedAtEpochMillis DESC")
    suspend fun getAllMarketPriceObservations(): List<MarketPriceObservationEntity>

    @Query("""
        SELECT
            mapping.productId AS productId,
            listing.id AS sourceListingId,
            mapping.ingredientId AS ingredientId,
            product.name AS productName,
            product.packageQuantity AS packageQuantity,
            product.packageUnit AS packageUnit,
            mapping.status AS mappingStatus,
            mapping.reviewedAtEpochMillis AS mappingReviewedAtEpochMillis,
            product.foodScopeReviewNeeded AS foodScopeReviewNeeded,
            price.priceDzd AS priceDzd,
            price.priceMinDzd AS priceMinDzd,
            price.priceMaxDzd AS priceMaxDzd,
            price.currencyCode AS currencyCode,
            price.observedAtEpochMillis AS observedAtEpochMillis,
            price.priceStatus AS priceStatus,
            price.availabilityRaw AS availabilityRaw,
            listing.storeName AS storeName,
            listing.source AS source,
            listing.wilaya AS wilaya,
            price.sourceNotice AS sourceNotice
        FROM market_product_ingredient_matches AS mapping
        INNER JOIN market_products AS product
            ON product.id = mapping.productId
        INNER JOIN market_product_sources AS listing
            ON listing.productId = product.id
        INNER JOIN market_price_observations AS price
            ON price.productId = product.id
            AND price.sourceListingId = listing.id
        WHERE mapping.status = 'VERIFIED'
            AND product.foodScopeReviewNeeded = 0
        ORDER BY price.observedAtEpochMillis DESC, price.id
    """)
    suspend fun getVerifiedMarketProductPriceRows(): List<VerifiedMarketProductPriceRow>

    @Upsert
    suspend fun upsertMarketProductIngredientMatches(items: List<MarketProductIngredientMatchEntity>)

    @Query("SELECT * FROM market_product_ingredient_matches WHERE productId = :productId LIMIT 1")
    suspend fun getMarketProductIngredientMatch(productId: String): MarketProductIngredientMatchEntity?

    @Query("""
        SELECT * FROM market_product_ingredient_matches
        WHERE status = 'VERIFIED'
        ORDER BY ingredientId, productId
    """)
    suspend fun getVerifiedMarketProductIngredientMatches(): List<MarketProductIngredientMatchEntity>

    @Query("SELECT COUNT(*) FROM market_product_ingredient_matches")
    suspend fun countMarketProductIngredientMatches(): Int

    @Query("DELETE FROM market_product_ingredient_matches")
    suspend fun deleteAllMarketProductIngredientMatches()

}
