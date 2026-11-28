package dz.meals.app.domain

import java.text.Normalizer
import kotlin.math.ceil

/**
 * One immutable retailer offer joined to a human-reviewed product/ingredient mapping.
 *
 * This is deliberately a domain input rather than a Room entity so the estimator
 * can be tested without Android and can be reused by the weekly/monthly engines.
 */
data class MarketProductPriceEvidence(
    val productId: String,
    val sourceListingId: String,
    val ingredientId: String,
    val productName: String,
    val packageQuantity: Double?,
    val packageUnit: String,
    val mappingStatus: String,
    val mappingReviewedAtEpochMillis: Long?,
    val foodScopeReviewNeeded: Boolean,
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

/**
 * An observed offer range for one recipe ingredient in one selected Wilaya.
 *
 * Min/max are taken from real offers. They are not averages or national prices.
 * Cash outlay includes whole-package rounding; consumed value does not.
 */
data class MarketProductPriceEstimate(
    val ingredientId: String,
    val wilaya: String,
    val unit: String,
    val minCashOutlayDzd: Double,
    val maxCashOutlayDzd: Double,
    val minConsumedValueDzd: Double,
    val maxConsumedValueDzd: Double,
    val minPackageCount: Int,
    val maxPackageCount: Int,
    val offerCount: Int,
    val storeNames: List<String>,
    val sourceNotices: List<String>,
    val latestObservedAtEpochMillis: Long,
    val includesLimitedAvailability: Boolean
)

private data class UnitDescriptor(
    val family: String,
    /** Number of canonical base units represented by one displayed unit. */
    val baseMultiplier: Double
)

private data class EligibleOffer(
    val evidence: MarketProductPriceEvidence,
    val packageQuantityInRequestedUnit: Double,
    val priceMinDzd: Double,
    val priceMaxDzd: Double,
    val packageCount: Int,
    val cashMinDzd: Double,
    val cashMaxDzd: Double,
    val consumedMinDzd: Double,
    val consumedMaxDzd: Double
)

/**
 * Estimates the cost of buying a recipe quantity from retailer observations.
 *
 * Hard gates:
 * - the caller must pass a VERIFIED mapping with a review timestamp;
 * - food-scope-review products are excluded;
 * - selected Wilaya must be present and match the retailer listing Wilaya;
 * - only DZD observations that are fresh, explicitly available, and indicative
 *   or observed are considered;
 * - package size and recipe units must have a known compatible dimension.
 *
 * Out-of-stock, unknown-availability, stale, future-dated, unpriced, and
 * cross-Wilaya observations are excluded rather than silently substituted.
 */
fun estimateMarketProductPrice(
    ingredientId: String,
    requiredQuantity: Double,
    requiredUnit: String,
    selectedWilaya: String?,
    evidence: List<MarketProductPriceEvidence>,
    nowEpochMillis: Long = System.currentTimeMillis(),
    maxAgeMillis: Long = DEFAULT_MAX_PRICE_AGE_MILLIS
): MarketProductPriceEstimate? {
    val safeIngredientId = ingredientId.trim()
    if (safeIngredientId.isEmpty() ||
        !requiredQuantity.isFinite() ||
        requiredQuantity <= 0.0 ||
        nowEpochMillis <= 0L ||
        maxAgeMillis < 0L
    ) return null

    // No Wilaya means no local price estimate. We never substitute a different region.
    val requestedWilaya = normalizeWilaya(selectedWilaya) ?: return null
    val targetUnit = describeUnit(requiredUnit) ?: return null

    val latestByListing = evidence
        .asSequence()
        .filter { it.ingredientId == safeIngredientId }
        .filter { it.sourceListingId.isNotBlank() }
        .groupBy { it.sourceListingId }
        .values
        .mapNotNull { rows ->
            rows.maxByOrNull { it.observedAtEpochMillis ?: Long.MIN_VALUE }
        }

    val eligibleOffers = latestByListing.mapNotNull { row ->
        if (!isVerifiedMapping(row) ||
            normalizeWilaya(row.wilaya) != requestedWilaya ||
            !isEligibleObservation(row, nowEpochMillis, maxAgeMillis)
        ) return@mapNotNull null

        val packageQuantity = row.packageQuantity
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return@mapNotNull null
        val packageUnit = describeUnit(row.packageUnit) ?: return@mapNotNull null
        if (packageUnit.family != targetUnit.family) return@mapNotNull null

        val packageQuantityInTargetUnit =
            packageQuantity * packageUnit.baseMultiplier / targetUnit.baseMultiplier
        if (!packageQuantityInTargetUnit.isFinite() || packageQuantityInTargetUnit <= 0.0) {
            return@mapNotNull null
        }

        val minPrice = row.priceMinDzd ?: row.priceDzd ?: row.priceMaxDzd
            ?: return@mapNotNull null
        val maxPrice = row.priceMaxDzd ?: row.priceDzd ?: row.priceMinDzd
            ?: return@mapNotNull null
        if (!minPrice.isFinite() || !maxPrice.isFinite() ||
            minPrice <= 0.0 || maxPrice < minPrice
        ) return@mapNotNull null

        val rawPackageCount = ceil(requiredQuantity / packageQuantityInTargetUnit)
        if (!rawPackageCount.isFinite() || rawPackageCount > Int.MAX_VALUE) {
            return@mapNotNull null
        }
        val packageCount = rawPackageCount.toInt().coerceAtLeast(1)
        val consumedFraction = requiredQuantity / packageQuantityInTargetUnit

        EligibleOffer(
            evidence = row,
            packageQuantityInRequestedUnit = packageQuantityInTargetUnit,
            priceMinDzd = minPrice,
            priceMaxDzd = maxPrice,
            packageCount = packageCount,
            cashMinDzd = packageCount * minPrice,
            cashMaxDzd = packageCount * maxPrice,
            consumedMinDzd = consumedFraction * minPrice,
            consumedMaxDzd = consumedFraction * maxPrice
        )
    }

    if (eligibleOffers.isEmpty()) return null

    val latestObservedAt = eligibleOffers.maxOf {
        requireNotNull(it.evidence.observedAtEpochMillis)
    }
    val wilayaLabel = eligibleOffers
        .firstOrNull { normalizeWilaya(it.evidence.wilaya) == requestedWilaya }
        ?.evidence?.wilaya
        ?.trim()
        .orEmpty()
        .ifBlank { requestedWilaya }

    return MarketProductPriceEstimate(
        ingredientId = safeIngredientId,
        wilaya = wilayaLabel,
        unit = requiredUnit.trim(),
        minCashOutlayDzd = eligibleOffers.minOf { it.cashMinDzd },
        maxCashOutlayDzd = eligibleOffers.maxOf { it.cashMaxDzd },
        minConsumedValueDzd = eligibleOffers.minOf { it.consumedMinDzd },
        maxConsumedValueDzd = eligibleOffers.maxOf { it.consumedMaxDzd },
        minPackageCount = eligibleOffers.minOf { it.packageCount },
        maxPackageCount = eligibleOffers.maxOf { it.packageCount },
        offerCount = eligibleOffers.size,
        storeNames = eligibleOffers.map { it.evidence.storeName.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted(),
        sourceNotices = eligibleOffers.map { it.evidence.sourceNotice.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted(),
        latestObservedAtEpochMillis = latestObservedAt,
        includesLimitedAvailability = eligibleOffers.any {
            it.evidence.availabilityRaw == AVAILABILITY_LIMITED
        }
    )
}

private fun isVerifiedMapping(row: MarketProductPriceEvidence): Boolean =
    row.mappingStatus.equals(MAPPING_VERIFIED, ignoreCase = true) &&
        (row.mappingReviewedAtEpochMillis ?: 0L) > 0L &&
        !row.foodScopeReviewNeeded

private fun isEligibleObservation(
    row: MarketProductPriceEvidence,
    nowEpochMillis: Long,
    maxAgeMillis: Long
): Boolean {
    if (!row.currencyCode.equals(CURRENCY_DZD, ignoreCase = true)) return false
    if (row.priceStatus !in ELIGIBLE_PRICE_STATUSES) return false
    if (row.availabilityRaw !in ELIGIBLE_AVAILABILITY) return false

    val observedAt = row.observedAtEpochMillis ?: return false
    if (observedAt <= 0L || observedAt > nowEpochMillis) return false
    return nowEpochMillis - observedAt <= maxAgeMillis
}

private fun describeUnit(value: String): UnitDescriptor? {
    val normalized = normalizeLabel(value)
    return when (normalized) {
        "g", "gram", "grams", "gramme", "grammes", "غ", "غرام", "غرامات" ->
            UnitDescriptor("MASS", 1.0)
        "kg", "kilogram", "kilograms", "kilogramme", "kilogrammes", "كغ", "كيلوغرام", "كيلو" ->
            UnitDescriptor("MASS", 1_000.0)
        "mg", "milligram", "milligrams", "ملغ" ->
            UnitDescriptor("MASS", 0.001)
        "ml", "milliliter", "milliliters", "millilitre", "millilitres", "مل", "ملل" ->
            UnitDescriptor("VOLUME", 1.0)
        "l", "liter", "liters", "litre", "litres", "ل", "لتر" ->
            UnitDescriptor("VOLUME", 1_000.0)
        "cl" -> UnitDescriptor("VOLUME", 10.0)
        "piece", "pieces", "pc", "pcs", "unit", "units", "unite", "unites", "قطعه", "حبه" ->
            UnitDescriptor("COUNT", 1.0)
        else -> null
    }
}

private fun normalizeWilaya(value: String?): String? {
    val normalized = value?.let(::normalizeLabel)
        ?.removePrefix("wilaya of ")
        ?.removePrefix("wilaya ")
        ?.removePrefix("ولايه ")
        ?.removePrefix("ولاية ")
        ?.trim()
        .orEmpty()
    if (normalized.isBlank()) return null

    return when (normalized) {
        "laghouat", "laghwat", "الاغواط" -> "laghouat"
        "blida", "البليده" -> "blida"
        "el tarf", "eltarf", "tarf", "الطارف" -> "el tarf"
        else -> normalized
    }
}

private fun normalizeLabel(value: String): String {
    val decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFKD).lowercase()
    val withoutCombiningMarks = buildString(decomposed.length) {
        decomposed.forEach { character ->
            val type = Character.getType(character)
            if (type != Character.NON_SPACING_MARK.toInt() &&
                type != Character.COMBINING_SPACING_MARK.toInt() &&
                type != Character.ENCLOSING_MARK.toInt()
            ) append(character)
        }
    }
    val normalized = withoutCombiningMarks
        .replace("أ", "ا")
        .replace("إ", "ا")
        .replace("آ", "ا")
        .replace("ٱ", "ا")
        .replace("ى", "ي")
        .replace("ة", "ه")
        .replace("ؤ", "و")
        .replace("ئ", "ي")
        .replace("ـ", "")
    return normalized.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

private const val MAPPING_VERIFIED = "VERIFIED"
private const val CURRENCY_DZD = "DZD"
private const val AVAILABILITY_IN_STOCK = "https://schema.org/InStock"
private const val AVAILABILITY_LIMITED = "https://schema.org/LimitedAvailability"
private val ELIGIBLE_AVAILABILITY = setOf(AVAILABILITY_IN_STOCK, AVAILABILITY_LIMITED)
private val ELIGIBLE_PRICE_STATUSES = setOf("indicative_unverified", "observed_unverified")

const val DEFAULT_MAX_PRICE_AGE_MILLIS = 30L * 24L * 60L * 60L * 1_000L
