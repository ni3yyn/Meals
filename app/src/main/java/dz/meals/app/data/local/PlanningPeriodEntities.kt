package dz.meals.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Generic planning-period header. Legacy week-keyed tables remain untouched;
 * this aggregate is the forward-compatible owner for new weekly/monthly plans.
 */
@Entity(tableName = "planning_periods", indices = [Index(value = ["periodType", "startDate", "endDate"])])
data class PlanningPeriodEntity(
    @PrimaryKey val id: String,
    val periodType: String,
    val startDate: String,
    val endDate: String,
    val budgetDzd: Int,
    val wilaya: String,
    val mealSlotsCsv: String,
    val engineVersion: String,
    val generatedAtEpochMillis: Long,
    val priceSnapshotAtEpochMillis: Long,
    val status: String = "ACTIVE"
)

/**
 * Calendar-date based meal rows. A month is represented by its actual dates,
 * not artificial seven-day buckets.
 */
@Entity(
    tableName = "planning_period_meals",
    foreignKeys = [
        ForeignKey(entity = PlanningPeriodEntity::class, parentColumns = ["id"], childColumns = ["periodId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("periodId"), Index("recipeId"), Index(value = ["periodId", "calendarDate", "mealSlot"], unique = true)]
)
data class PlanningPeriodMealEntity(
    @PrimaryKey val id: String,
    val periodId: String,
    val calendarDate: String,
    val mealSlot: String,
    val recipeId: String?,
    val servings: Int,
    val locked: Boolean,
    val isCooked: Boolean,
    val isUserEdited: Boolean
)

/**
 * A plan-local declaration of what the household already has.
 * This is deliberately not a stock ledger and never carries to another plan.
 */
@Entity(
    tableName = "plan_ingredient_adjustments",
    foreignKeys = [
        ForeignKey(entity = PlanningPeriodEntity::class, parentColumns = ["id"], childColumns = ["periodId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = IngredientEntity::class, parentColumns = ["id"], childColumns = ["ingredientId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("periodId"), Index("ingredientId"), Index(value = ["periodId", "ingredientId", "unit"], unique = true)]
)
data class PlanIngredientAdjustmentEntity(
    @PrimaryKey val id: String,
    val periodId: String,
    val ingredientId: String,
    val unit: String,
    val alreadyHaveQuantity: Double,
    val alreadyHaveEnough: Boolean,
    val updatedAtEpochMillis: Long
)

/**
 * Reviewed bridge between a retail product and the canonical recipe ingredient.
 * Candidate generation never writes REVIEWED mappings.
 */
@Entity(
    tableName = "market_product_ingredient_matches",
    foreignKeys = [
        ForeignKey(entity = MarketProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = IngredientEntity::class, parentColumns = ["id"], childColumns = ["ingredientId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index("productId"), Index("ingredientId"),
        Index(value = ["productId", "ingredientId"], unique = true),
        Index(value = ["reviewStatus", "matchMethod"])
    ]
)
data class MarketProductIngredientMatchEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val ingredientId: String,
    val reviewStatus: String,
    val matchMethod: String,
    val confidence: Double,
    val evidenceJson: String,
    val reviewedAtEpochMillis: Long?,
    val reviewedBy: String?
)
