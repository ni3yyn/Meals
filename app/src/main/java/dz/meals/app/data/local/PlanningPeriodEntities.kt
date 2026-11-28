package dz.meals.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Canonical planning container. WEEK remains Monday-starting; MONTH always
 * spans the complete calendar month. Existing weekStartIso rows are migrated
 * into WEEK periods without being reinterpreted as calendar months.
 */
@Entity(
    tableName = "planning_periods",
    indices = [
        Index(value = ["periodType", "startDateIso"]),
        Index(value = ["endDateIso"])
    ]
)
data class PlanningPeriodEntity(
    @PrimaryKey val id: String,
    val periodType: String,
    val startDateIso: String,
    val endDateIso: String,
    val budgetDzd: Int,
    val wilaya: String,
    val mealSlotsCsv: String,
    val engineVersion: String,
    val createdAtEpochMillis: Long,
    val priceSnapshotAtEpochMillis: Long,
    val householdServings: Int,
    val updatedAtEpochMillis: Long
)

/**
 * Date-keyed meal slot for either a week or a whole calendar month.
 * Meal identity is period + date + slot; it is not a day index limited to 0..6.
 */
@Entity(
    tableName = "planning_period_meals",
    foreignKeys = [
        ForeignKey(
            entity = PlanningPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("periodId"),
        Index("dateIso"),
        Index("recipeId"),
        Index(value = ["periodId", "dateIso", "mealSlot"], unique = true)
    ]
)
data class PlanningPeriodMealEntity(
    @PrimaryKey val id: String,
    val periodId: String,
    val dateIso: String,
    val mealSlot: String,
    val recipeId: String?,
    val servings: Int,
    val locked: Boolean,
    val isCooked: Boolean,
    val isUserEdited: Boolean
)

/**
 * Explicit, plan-only amount the household already has for one ingredient.
 * No row means no deduction; SOME uses availableQuantity; ENOUGH covers the
 * current plan's full requirement. The period foreign key prevents any
 * cross-plan carry-over.
 */
@Entity(
    tableName = "plan_ingredient_adjustments",
    primaryKeys = ["periodId", "ingredientId", "unit"],
    foreignKeys = [
        ForeignKey(
            entity = PlanningPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("ingredientId")]
)
data class PlanIngredientAdjustmentEntity(
    val periodId: String,
    val ingredientId: String,
    val unit: String,
    val adjustmentType: String,
    val availableQuantity: Double,
    val updatedAtEpochMillis: Long
)

/**
 * Immutable product-offer evidence captured for a plan at generation time.
 * It survives future catalogue refreshes or a later mapping-review change.
 */
@Entity(
    tableName = "plan_market_price_snapshots",
    primaryKeys = ["periodId", "sourceListingId"],
    foreignKeys = [
        ForeignKey(
            entity = PlanningPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("ingredientId"),
        Index("productId")
    ]
)
data class PlanMarketPriceSnapshotEntity(
    val periodId: String,
    val sourceListingId: String,
    val productId: String,
    val ingredientId: String,
    val productName: String,
    val packageQuantity: Double?,
    val packageUnit: String,
    val mappingStatus: String,
    val mappingReviewedAtEpochMillis: Long?,
    val priceDzd: Double?,
    val priceMinDzd: Double?,
    val priceMaxDzd: Double?,
    val currencyCode: String,
    val observedAtEpochMillis: Long?,
    val priceStatus: String,
    val availabilityRaw: String,
    val storeName: String,
    val source: String,
    val wilaya: String,
    val sourceNotice: String
)
