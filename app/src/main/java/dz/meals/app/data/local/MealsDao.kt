package dz.meals.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPrices(items: List<PriceObservationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPriceObservation(item: PriceObservationEntity)

    @Query("SELECT * FROM ingredients ORDER BY nameAr")
    suspend fun getIngredients(): List<IngredientEntity>

    @Query("SELECT * FROM recipes ORDER BY nameAr")
    suspend fun getRecipes(): List<RecipeEntity>

    @Query("SELECT * FROM plan_runs WHERE weekStartIso = :weekStartIso LIMIT 1")
    suspend fun getPlanRun(weekStartIso: String): PlanRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlanRun(item: PlanRunEntity)

    @Query("SELECT * FROM recipe_ingredients ORDER BY recipeId")
    suspend fun getRecipeIngredients(): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_steps ORDER BY recipeId, stepNumber")
    suspend fun getRecipeSteps(): List<RecipeStepEntity>

    @Query("SELECT * FROM plan_meals WHERE weekStartIso = :weekStartIso ORDER BY dayIndex, mealSlot")
    suspend fun getPlanMeals(weekStartIso: String): List<PlanMealEntity>

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

    @Query("SELECT * FROM pantry_items ORDER BY ingredientId")
    suspend fun getPantryItems(): List<PantryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPantryItem(item: PantryItemEntity)

    @Query("DELETE FROM pantry_items WHERE id = :id")
    suspend fun deletePantryItem(id: String)

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
}
