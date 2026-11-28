package dz.meals.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Reviewable bridge from one exact retail product variant to one canonical
 * recipe ingredient identity. Only VERIFIED rows may participate in pricing.
 *
 * Product variants remain independent rows, so multiple brands and pack sizes
 * can safely map to the same ingredient without merging their identities.
 */
@Entity(
    tableName = "market_product_ingredient_matches",
    foreignKeys = [
        ForeignKey(
            entity = MarketProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("ingredientId"),
        Index(value = ["status", "ingredientId"])
    ]
)
data class MarketProductIngredientMatchEntity(
    @PrimaryKey val productId: String,
    val ingredientId: String,
    val status: String,
    val matchMethod: String,
    val confidenceScore: Double?,
    val rationale: String,
    val algorithmVersion: String,
    val reviewedAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long
) {
    companion object {
        const val STATUS_SUGGESTED = "SUGGESTED"
        const val STATUS_VERIFIED = "VERIFIED"
        const val STATUS_REJECTED = "REJECTED"

        fun isSupportedStatus(value: String): Boolean =
            value == STATUS_SUGGESTED ||
                value == STATUS_VERIFIED ||
                value == STATUS_REJECTED
    }
}
