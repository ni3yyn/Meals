package dz.meals.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ingredients")
data class IngredientEntity(
    @PrimaryKey val id: String,
    val nameAr: String,
    val nameFr: String,
    val category: String,
    val baseUnit: String,
    val allergensCsv: String,
    val aliasesCsv: String
)

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val nameAr: String,
    val nameFr: String,
    val descriptionAr: String,
    val descriptionFr: String,
    val category: String,
    val region: String,
    val durationMinutes: Int,
    val baseServings: Int,
    val difficulty: String,
    val allergensCsv: String
)

@Entity(
    tableName = "recipe_ingredients",
    primaryKeys = ["recipeId", "ingredientId", "unit"],
    indices = [Index("ingredientId")]
)
data class RecipeIngredientEntity(
    val recipeId: String,
    val ingredientId: String,
    val quantityBase: Double,
    val unit: String,
    val prepNote: String,
    val optional: Boolean
)

@Entity(tableName = "recipe_steps", primaryKeys = ["recipeId", "stepNumber"])
data class RecipeStepEntity(
    val recipeId: String,
    val stepNumber: Int,
    val instructionAr: String,
    val instructionFr: String
)

@Entity(
    tableName = "plan_meals",
    indices = [Index("weekStartIso"), Index("recipeId")]
)
data class PlanMealEntity(
    @PrimaryKey val id: String,
    val weekStartIso: String,
    val dayIndex: Int,
    val mealSlot: String,
    val recipeId: String?,
    val servings: Int,
    val locked: Boolean
)

@Entity(
    tableName = "pantry_items",
    indices = [Index(value = ["ingredientId"], unique = true)]
)
data class PantryItemEntity(
    @PrimaryKey val id: String,
    val ingredientId: String,
    val quantityBase: Double?,
    val unit: String,
    val isApproximate: Boolean,
    val note: String,
    val expiryAtEpochMillis: Long?
)

@Entity(
    tableName = "price_observations",
    indices = [Index("ingredientId"), Index("observedAtEpochMillis")]
)
data class PriceObservationEntity(
    @PrimaryKey val id: String,
    val ingredientId: String,
    val packQuantityBase: Double,
    val unit: String,
    val packPriceDzd: Double,
    val sourceLabel: String,
    val locationLabel: String,
    val observedAtEpochMillis: Long?,
    val priceKind: String,
    val confidenceLabel: String
)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val ingredientId: String?,
    val customName: String?,
    val requiredQuantity: Double,
    val pantryQuantity: Double,
    val purchaseQuantity: Double,
    val unit: String,
    val consumedValueDzd: Double?,
    val cashOutlayDzd: Double?,
    val priceLabel: String?,
    val sourceLabel: String?,
    val isApproximatePantry: Boolean,
    val checked: Boolean,
    val userAdded: Boolean,
    val updatedAtEpochMillis: Long
)


@Entity(tableName = "favorite_recipes")
data class FavoriteRecipeEntity(
    @PrimaryKey val recipeId: String,
    val addedAtEpochMillis: Long
)
