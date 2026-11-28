package dz.meals.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A branded/package-level retail item. This is intentionally separate from
 * generic food_items and recipe ingredient identities.
 */
@Entity(
    tableName = "market_products",
    indices = [
        Index("gtin"),
        Index("name"),
        Index(value = ["identityKey"], unique = true)
    ]
)
data class MarketProductEntity(
    @PrimaryKey val id: String,
    val identityKey: String,
    val name: String,
    val nameVariantsJson: String,
    val brand: String?,
    val gtin: String?,
    val imageUrl: String,
    val imageUrlsJson: String,
    val description: String,
    val packageQuantity: Double?,
    val packageUnit: String,
    val categoryNamesJson: String,
    val sourceCount: Int,
    val foodScopeReviewNeeded: Boolean,
    val foodScopeReviewReason: String,
    val sourceRowCount: Int
)

/**
 * Source-scoped retailer listing metadata. SKU values are never treated as
 * globally unique; their scope is the source/store that issued them.
 */
@Entity(
    tableName = "market_product_sources",
    foreignKeys = [
        ForeignKey(
            entity = MarketProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("productId"),
        Index("source"),
        Index("sku"),
        Index(value = ["source", "sourceProductKey"], unique = true)
    ]
)
data class MarketProductSourceEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val source: String,
    val storeName: String,
    val storeUrl: String,
    val city: String,
    val wilaya: String,
    val storeAddress: String,
    val sourceProductKey: String,
    val sourceProductId: String,
    val sourceProductKeysJson: String,
    val sourceProductIdsJson: String,
    val sku: String?,
    val gtinRaw: String?,
    val rawBrand: String?,
    val mpn: String?,
    val manufacturer: String?,
    val productUrl: String,
    val alternateUrlsJson: String,
    val alternateLocaleUrlsJson: String,
    val categorySlugPathsJson: String,
    val variationSkusJson: String,
    val enrichmentMetadataJson: String
)

/**
 * An immutable, source-attributed price snapshot. Ranges remain ranges; we do
 * not synthesize an average or overwrite one retailer with another.
 */
@Entity(
    tableName = "market_price_observations",
    foreignKeys = [
        ForeignKey(
            entity = MarketProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MarketProductSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceListingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("productId"),
        Index("sourceListingId"),
        Index("observedAtEpochMillis"),
        Index(value = ["sourceListingId", "observedAtEpochMillis"], unique = true)
    ]
)
data class MarketPriceObservationEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val sourceListingId: String,
    val priceDzd: Double?,
    val priceMinDzd: Double?,
    val priceMaxDzd: Double?,
    val priceDisplayRaw: String,
    val currencyCode: String,
    val observedAtEpochMillis: Long?,
    val priceSource: String,
    val priceStatus: String,
    val availabilityRaw: String,
    val stockQuantityRaw: String,
    val locationHint: String,
    val sourceNotice: String
)
