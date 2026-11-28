package dz.meals.app.domain

import dz.meals.app.data.IngredientDemand
import dz.meals.app.data.convertIngredientQuantity
import dz.meals.app.data.local.MarketPriceObservationEntity
import dz.meals.app.data.local.MarketProductEntity
import dz.meals.app.data.local.MarketProductIngredientMatchEntity
import dz.meals.app.data.local.MarketProductSourceEntity
import java.text.Normalizer
import kotlin.math.ceil
import kotlin.math.round

/**
 * Optional locality context for market-price estimation.
 *
 * When Wilaya is supplied, listings from other Wilayas are excluded. A city
 * refines ranking inside that Wilaya but does not discard an otherwise local
 * listing whose city metadata is absent or less specific.
 */
data class ProductPriceLocality(
    val wilaya: String? = null,
    val city: String? = null
) {
    internal val normalizedWilaya: String? = normalizeProductPriceLocation(wilaya)
    internal val normalizedCity: String? = normalizeProductPriceLocation(city)
    internal val isSpecified: Boolean
        get() = normalizedWilaya != null || normalizedCity != null
}

enum class ProductPriceConfidenceLevel {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Confidence is deliberately explainable instead of being a single opaque score.
 * Independent-source coverage caps confidence: one retailer/source is always
 * low-coverage even when its listing and price are otherwise excellent.
 */
data class ProductPriceConfidence(
    val score: Double,
    val level: ProductPriceConfidenceLevel,
    val matchQuality: Double,
    val sourceTrust: Double,
    val sourceTrustCoverage: Double,
    val freshness: Double,
    val independentSourceCoverage: Double,
    val packageCertainty: Double,
    val localityRelevance: Double,
    val independentSourceCount: Int,
    val eligibleOfferCount: Int
)

enum class ProductPriceMissingReason {
    INVALID_DEMAND,
    INVALID_REFERENCE_TIME,
    NO_APPROVED_PRODUCT_MATCH,
    PRODUCT_NOT_FOUND,
    PRODUCT_REQUIRES_FOOD_REVIEW,
    NO_RETAILER_LISTING,
    NO_LOCAL_RETAILER_LISTING,
    NO_PRICE_OBSERVATION,
    FUTURE_DATED_OBSERVATION,
    OBSERVATION_DATE_MISSING,
    STALE_OBSERVATION,
    NON_DZD_CURRENCY,
    UNKNOWN_PRICE_STATUS,
    UNAVAILABLE_PRODUCT,
    UNKNOWN_AVAILABILITY,
    MISSING_PRICE,
    INVALID_PRICE_RANGE,
    UNKNOWN_PACKAGE_SIZE,
    INCOMPATIBLE_PACKAGE_UNIT,
    PACKAGE_COUNT_OVERFLOW
}

/** One real product/listing option; variants and package sizes are never merged. */
data class ProductPricePackageOption(
    val productId: String,
    val productName: String,
    val brand: String?,
    val sourceListingId: String,
    val source: String,
    val storeName: String,
    val storeUrl: String,
    val city: String,
    val wilaya: String,
    val packageQuantity: Double,
    val packageUnit: String,
    val packageQuantityInDemandUnit: Double,
    val packagesRequired: Int,
    val packagePriceMinDzd: Double,
    val packagePriceMaxDzd: Double,
    val cashOutlayMinDzd: Double,
    val cashOutlayMaxDzd: Double,
    val consumedValueMinDzd: Double,
    val consumedValueMaxDzd: Double,
    val observedAtEpochMillis: Long,
    val sourceNotice: String,
    val isLimitedAvailability: Boolean
)

data class IngredientProductPriceEstimate(
    val ingredientId: String,
    val requiredQuantity: Double,
    val unit: String,
    val lowDzd: Double?,
    val typicalDzd: Double?,
    val highDzd: Double?,
    val packageOptions: List<ProductPricePackageOption>,
    val confidence: ProductPriceConfidence,
    /** Reasons describe excluded or missing evidence, even when other offers remain usable. */
    val missingPriceReasons: Set<ProductPriceMissingReason>,
    val latestObservedAtEpochMillis: Long?,
    val includesLimitedAvailability: Boolean
)

data class ProductPriceEstimationResult(
    val ingredientEstimates: List<IngredientProductPriceEstimate>,
    /** Totals are null unless every demanded ingredient has a supported estimate. */
    val lowDzd: Double?,
    val typicalDzd: Double?,
    val highDzd: Double?,
    val missingIngredientCount: Int
)

private data class EligibleProductOffer(
    val option: ProductPricePackageOption,
    val matchQuality: Double,
    val sourceTrust: Double,
    val hasExplicitSourceTrust: Boolean,
    val freshness: Double,
    val localityRelevance: Double,
    val availabilityReliability: Double,
    val typicalCashOutlayDzd: Double,
    val weight: Double
)

/**
 * Product-price estimation is deliberately independent of meal/recipe selection.
 *
 * Inputs are supplied as separate domain records so the calculation can be
 * reused for a weekly or monthly plan without querying Room or changing meals.
 * Only human-verified mappings, current DZD observations, available listings,
 * and known compatible package units can produce a priced option.
 */
object ProductPriceEstimator {
    fun estimate(
        demands: List<IngredientDemand>,
        approvedMatches: List<MarketProductIngredientMatchEntity>,
        products: List<MarketProductEntity>,
        sourceListings: List<MarketProductSourceEntity>,
        observations: List<MarketPriceObservationEntity>,
        locality: ProductPriceLocality? = null,
        sourceTrustScores: Map<String, Double> = emptyMap(),
        nowEpochMillis: Long = System.currentTimeMillis(),
        maxObservationAgeMillis: Long = DEFAULT_MAX_PRICE_AGE_MILLIS
    ): ProductPriceEstimationResult {
        val normalizedTrustScores = sourceTrustScores.entries.mapNotNull { (source, score) ->
            val normalizedSource = normalizeProductPriceLocation(source)
            if (normalizedSource == null || !score.isFinite() || score !in 0.0..1.0) {
                null
            } else {
                normalizedSource to score
            }
        }.toMap()

        val productsById = products.associateBy { it.id }
        val listingsByProduct = sourceListings
            .filter { it.id.isNotBlank() && it.productId.isNotBlank() }
            .groupBy { it.productId }
        val observationsByListing = observations
            .filter { it.id.isNotBlank() && it.sourceListingId.isNotBlank() }
            .groupBy { it.sourceListingId }

        val estimates = demands
            .sortedWith(compareBy<IngredientDemand> { it.ingredientId }.thenBy { it.unit })
            .map { demand ->
                estimateIngredient(
                    demand = demand,
                    approvedMatches = approvedMatches,
                    productsById = productsById,
                    listingsByProduct = listingsByProduct,
                    observationsByListing = observationsByListing,
                    locality = locality?.takeIf { it.isSpecified },
                    sourceTrustScores = normalizedTrustScores,
                    nowEpochMillis = nowEpochMillis,
                    maxObservationAgeMillis = maxObservationAgeMillis
                )
            }

        val missingCount = estimates.count { it.typicalDzd == null }
        val allPriced = estimates.isNotEmpty() && missingCount == 0
        return ProductPriceEstimationResult(
            ingredientEstimates = estimates,
            lowDzd = estimates.sumRoundedIf(allPriced) { requireNotNull(it.lowDzd) },
            typicalDzd = estimates.sumRoundedIf(allPriced) { requireNotNull(it.typicalDzd) },
            highDzd = estimates.sumRoundedIf(allPriced) { requireNotNull(it.highDzd) },
            missingIngredientCount = missingCount
        )
    }

    private fun estimateIngredient(
        demand: IngredientDemand,
        approvedMatches: List<MarketProductIngredientMatchEntity>,
        productsById: Map<String, MarketProductEntity>,
        listingsByProduct: Map<String, List<MarketProductSourceEntity>>,
        observationsByListing: Map<String, List<MarketPriceObservationEntity>>,
        locality: ProductPriceLocality?,
        sourceTrustScores: Map<String, Double>,
        nowEpochMillis: Long,
        maxObservationAgeMillis: Long
    ): IngredientProductPriceEstimate {
        val reasons = linkedSetOf<ProductPriceMissingReason>()
        if (demand.ingredientId.isBlank() ||
            !demand.quantity.isFinite() ||
            demand.quantity <= 0.0 ||
            demand.unit.isBlank()
        ) {
            reasons += ProductPriceMissingReason.INVALID_DEMAND
            return emptyEstimate(demand, reasons)
        }
        if (nowEpochMillis <= 0L || maxObservationAgeMillis < 0L) {
            reasons += ProductPriceMissingReason.INVALID_REFERENCE_TIME
            return emptyEstimate(demand, reasons)
        }

        val matches = approvedMatches
            .asSequence()
            .filter { it.ingredientId == demand.ingredientId }
            .filter {
                it.status.equals(MarketProductIngredientMatchEntity.STATUS_VERIFIED, ignoreCase = true) &&
                    (it.reviewedAtEpochMillis ?: 0L) in 1L..nowEpochMillis
            }
            .sortedBy { it.productId }
            .toList()
        if (matches.isEmpty()) {
            reasons += ProductPriceMissingReason.NO_APPROVED_PRODUCT_MATCH
            return emptyEstimate(demand, reasons)
        }

        val validProducts = mutableListOf<Pair<MarketProductEntity, MarketProductIngredientMatchEntity>>()
        matches.forEach { match ->
            val product = productsById[match.productId]
            when {
                product == null -> reasons += ProductPriceMissingReason.PRODUCT_NOT_FOUND
                product.foodScopeReviewNeeded -> reasons += ProductPriceMissingReason.PRODUCT_REQUIRES_FOOD_REVIEW
                else -> validProducts += product to match
            }
        }
        if (validProducts.isEmpty()) return emptyEstimate(demand, reasons)

        val productListings = validProducts.flatMap { (product, match) ->
            listingsByProduct[product.id].orEmpty().map { listing -> Triple(product, match, listing) }
        }
        if (productListings.isEmpty()) {
            reasons += ProductPriceMissingReason.NO_RETAILER_LISTING
            return emptyEstimate(demand, reasons)
        }

        val localListings = productListings.filter { (_, _, listing) ->
            matchesLocality(listing, locality)
        }
        if (localListings.isEmpty()) {
            reasons += ProductPriceMissingReason.NO_LOCAL_RETAILER_LISTING
            return emptyEstimate(demand, reasons)
        }

        val offers = localListings.mapNotNull { (product, match, listing) ->
            val listingObservations = observationsByListing[listing.id]
                .orEmpty()
                .filter { it.productId == product.id && it.sourceListingId == listing.id }
            if (listingObservations.isEmpty()) {
                reasons += ProductPriceMissingReason.NO_PRICE_OBSERVATION
                return@mapNotNull null
            }

            val datedObservations = listingObservations.filter { observation ->
                (observation.observedAtEpochMillis ?: Long.MAX_VALUE) <= nowEpochMillis
            }
            if (datedObservations.isEmpty()) {
                reasons += ProductPriceMissingReason.FUTURE_DATED_OBSERVATION
                return@mapNotNull null
            }
            val latest = datedObservations.maxWithOrNull(
                compareBy<MarketPriceObservationEntity> { it.observedAtEpochMillis ?: Long.MIN_VALUE }
                    .thenBy { it.id }
            ) ?: return@mapNotNull null

            val observedAt = latest.observedAtEpochMillis
            if (observedAt == null || observedAt <= 0L) {
                reasons += ProductPriceMissingReason.OBSERVATION_DATE_MISSING
                return@mapNotNull null
            }
            val age = nowEpochMillis - observedAt
            if (age < 0L || age > maxObservationAgeMillis) {
                reasons += ProductPriceMissingReason.STALE_OBSERVATION
                return@mapNotNull null
            }
            if (!latest.currencyCode.equals(CURRENCY_DZD, ignoreCase = true)) {
                reasons += ProductPriceMissingReason.NON_DZD_CURRENCY
                return@mapNotNull null
            }
            if (latest.priceStatus.trim().lowercase() !in ELIGIBLE_PRICE_STATUSES) {
                reasons += ProductPriceMissingReason.UNKNOWN_PRICE_STATUS
                return@mapNotNull null
            }

            val availability = latest.availabilityRaw.trim()
            val isLimited = availability.equals(AVAILABILITY_LIMITED, ignoreCase = true)
            when {
                availability.equals(AVAILABILITY_IN_STOCK, ignoreCase = true) -> Unit
                isLimited -> Unit
                availability.equals(AVAILABILITY_OUT_OF_STOCK, ignoreCase = true) ->
                    reasons += ProductPriceMissingReason.UNAVAILABLE_PRODUCT
                else -> reasons += ProductPriceMissingReason.UNKNOWN_AVAILABILITY
            }
            if (availability != AVAILABILITY_IN_STOCK && !isLimited) return@mapNotNull null

            val packageQuantity = product.packageQuantity
                ?.takeIf { it.isFinite() && it > 0.0 }
            if (packageQuantity == null) {
                reasons += ProductPriceMissingReason.UNKNOWN_PACKAGE_SIZE
                return@mapNotNull null
            }
            val packageQuantityInDemandUnit = convertIngredientQuantity(
                packageQuantity,
                product.packageUnit,
                demand.unit
            )
            if (packageQuantityInDemandUnit == null || !packageQuantityInDemandUnit.isFinite() ||
                packageQuantityInDemandUnit <= 0.0
            ) {
                reasons += ProductPriceMissingReason.INCOMPATIBLE_PACKAGE_UNIT
                return@mapNotNull null
            }

            val priceMin = latest.priceMinDzd ?: latest.priceDzd ?: latest.priceMaxDzd
            val priceMax = latest.priceMaxDzd ?: latest.priceDzd ?: latest.priceMinDzd
            if (priceMin == null || priceMax == null) {
                reasons += ProductPriceMissingReason.MISSING_PRICE
                return@mapNotNull null
            }
            if (!priceMin.isFinite() || !priceMax.isFinite() ||
                priceMin <= 0.0 || priceMax < priceMin
            ) {
                reasons += ProductPriceMissingReason.INVALID_PRICE_RANGE
                return@mapNotNull null
            }

            val packageCountValue = ceil(demand.quantity / packageQuantityInDemandUnit)
            if (!packageCountValue.isFinite() || packageCountValue !in 1.0..Int.MAX_VALUE.toDouble()) {
                reasons += ProductPriceMissingReason.PACKAGE_COUNT_OVERFLOW
                return@mapNotNull null
            }
            val packageCount = packageCountValue.toInt()
            val consumedFraction = demand.quantity / packageQuantityInDemandUnit
            val cashMin = packageCount * priceMin
            val cashMax = packageCount * priceMax
            val consumedMin = consumedFraction * priceMin
            val consumedMax = consumedFraction * priceMax
            if (listOf(cashMin, cashMax, consumedMin, consumedMax).any { !it.isFinite() }) {
                reasons += ProductPriceMissingReason.INVALID_PRICE_RANGE
                return@mapNotNull null
            }

            val normalizedSource = normalizeProductPriceLocation(listing.source)
            val explicitTrust = normalizedSource?.let(sourceTrustScores::get)
            val sourceTrust = explicitTrust ?: DEFAULT_UNVERIFIED_SOURCE_TRUST
            val matchQuality = (match.confidenceScore ?: DEFAULT_VERIFIED_MATCH_QUALITY)
                .coerceIn(0.0, 1.0)
            val freshness = freshnessScore(age, maxObservationAgeMillis)
            val localityRelevance = localityRelevance(listing, locality)
            val availabilityReliability = if (isLimited) LIMITED_AVAILABILITY_RELIABILITY else 1.0
            val weight = (
                matchQuality *
                    sourceTrust.coerceAtLeast(MIN_WEIGHT) *
                    freshness *
                    localityRelevance *
                    availabilityReliability
                ).coerceAtLeast(MIN_WEIGHT)

            EligibleProductOffer(
                option = ProductPricePackageOption(
                    productId = product.id,
                    productName = product.name,
                    brand = product.brand,
                    sourceListingId = listing.id,
                    source = listing.source,
                    storeName = listing.storeName,
                    storeUrl = listing.storeUrl,
                    city = listing.city,
                    wilaya = listing.wilaya,
                    packageQuantity = packageQuantity,
                    packageUnit = product.packageUnit,
                    packageQuantityInDemandUnit = packageQuantityInDemandUnit,
                    packagesRequired = packageCount,
                    packagePriceMinDzd = roundConsumerDzd(priceMin),
                    packagePriceMaxDzd = roundConsumerDzd(priceMax),
                    cashOutlayMinDzd = roundConsumerDzd(cashMin),
                    cashOutlayMaxDzd = roundConsumerDzd(cashMax),
                    consumedValueMinDzd = roundConsumerDzd(consumedMin),
                    consumedValueMaxDzd = roundConsumerDzd(consumedMax),
                    observedAtEpochMillis = observedAt,
                    sourceNotice = latest.sourceNotice,
                    isLimitedAvailability = isLimited
                ),
                matchQuality = matchQuality,
                sourceTrust = sourceTrust,
                hasExplicitSourceTrust = explicitTrust != null,
                freshness = freshness,
                localityRelevance = localityRelevance,
                availabilityReliability = availabilityReliability,
                typicalCashOutlayDzd = (cashMin + cashMax) / 2.0,
                weight = weight
            )
        }.sortedWith(
            compareBy<EligibleProductOffer> { it.option.productName.lowercase() }
                .thenBy { it.option.packageQuantityInDemandUnit }
                .thenBy { it.option.storeName.lowercase() }
                .thenBy { it.option.sourceListingId }
        )

        if (offers.isEmpty()) return emptyEstimate(demand, reasons)

        val distinctSources = offers.map { normalizeProductPriceLocation(it.option.source) }
            .filterNotNull()
            .distinct()
            .size
        val sourceCoverage = (distinctSources / REQUIRED_INDEPENDENT_SOURCE_COUNT.toDouble())
            .coerceIn(0.0, 1.0)
        val confidence = calculateConfidence(offers, sourceCoverage, distinctSources)
        val typical = weightedMedian(offers)
        return IngredientProductPriceEstimate(
            ingredientId = demand.ingredientId,
            requiredQuantity = demand.quantity,
            unit = demand.unit,
            lowDzd = roundConsumerDzd(offers.minOf { it.option.cashOutlayMinDzd }),
            typicalDzd = roundConsumerDzd(typical),
            highDzd = roundConsumerDzd(offers.maxOf { it.option.cashOutlayMaxDzd }),
            packageOptions = offers.map { it.option },
            confidence = confidence,
            missingPriceReasons = reasons,
            latestObservedAtEpochMillis = offers.maxOfOrNull { it.option.observedAtEpochMillis },
            includesLimitedAvailability = offers.any { it.option.isLimitedAvailability }
        )
    }

    private fun calculateConfidence(
        offers: List<EligibleProductOffer>,
        sourceCoverage: Double,
        independentSourceCount: Int
    ): ProductPriceConfidence {
        fun average(selector: (EligibleProductOffer) -> Double): Double =
            offers.map(selector).average().coerceIn(0.0, 1.0)

        val matchQuality = average { it.matchQuality }
        val sourceTrust = average { it.sourceTrust }
        val freshness = average { it.freshness }
        val packageCertainty = 1.0 // Every returned option has a measured, compatible package size.
        val localityRelevance = average { it.localityRelevance }
        val availabilityReliability = average { it.availabilityReliability }
        val sourceTrustCoverage = offers.count { it.hasExplicitSourceTrust } / offers.size.toDouble()
        val evidenceQuality = (
            matchQuality * 0.25 +
                sourceTrust * 0.20 +
                freshness * 0.20 +
                packageCertainty * 0.10 +
                localityRelevance * 0.15 +
                availabilityReliability * 0.10
            ).coerceIn(0.0, 1.0)
        val score = (evidenceQuality * sourceCoverage).coerceIn(0.0, 1.0)
        val level = when {
            score >= HIGH_CONFIDENCE_THRESHOLD -> ProductPriceConfidenceLevel.HIGH
            score >= MEDIUM_CONFIDENCE_THRESHOLD -> ProductPriceConfidenceLevel.MEDIUM
            else -> ProductPriceConfidenceLevel.LOW
        }
        return ProductPriceConfidence(
            score = score,
            level = level,
            matchQuality = matchQuality,
            sourceTrust = sourceTrust,
            sourceTrustCoverage = sourceTrustCoverage,
            freshness = freshness,
            independentSourceCoverage = sourceCoverage,
            packageCertainty = packageCertainty,
            localityRelevance = localityRelevance,
            independentSourceCount = independentSourceCount,
            eligibleOfferCount = offers.size
        )
    }

    private fun weightedMedian(offers: List<EligibleProductOffer>): Double {
        val ordered = offers.sortedWith(
            compareBy<EligibleProductOffer> { it.typicalCashOutlayDzd }
                .thenBy { it.option.sourceListingId }
        )
        val totalWeight = ordered.sumOf { it.weight }
        if (!totalWeight.isFinite() || totalWeight <= 0.0) {
            return ordered[ordered.size / 2].typicalCashOutlayDzd
        }
        val midpoint = totalWeight / 2.0
        var accumulated = 0.0
        ordered.forEach { offer ->
            accumulated += offer.weight
            if (accumulated >= midpoint) return offer.typicalCashOutlayDzd
        }
        return ordered.last().typicalCashOutlayDzd
    }

    private fun matchesLocality(
        listing: MarketProductSourceEntity,
        locality: ProductPriceLocality?
    ): Boolean {
        if (locality == null) return true
        val wantedWilaya = locality.normalizedWilaya
        val wantedCity = locality.normalizedCity
        if (wantedWilaya != null) {
            return normalizeProductPriceLocation(listing.wilaya) == wantedWilaya
        }
        return wantedCity != null && normalizeProductPriceLocation(listing.city) == wantedCity
    }

    private fun localityRelevance(
        listing: MarketProductSourceEntity,
        locality: ProductPriceLocality?
    ): Double {
        if (locality == null) return NO_LOCALITY_RELEVANCE
        val wantedCity = locality.normalizedCity ?: return 1.0
        val actualCity = normalizeProductPriceLocation(listing.city)
        return when {
            actualCity == wantedCity -> 1.0
            actualCity == null -> UNKNOWN_CITY_RELEVANCE
            else -> SAME_WILAYA_OTHER_CITY_RELEVANCE
        }
    }

    private fun freshnessScore(ageMillis: Long, maxAgeMillis: Long): Double {
        if (maxAgeMillis == 0L) return 1.0
        val ageRatio = (ageMillis.toDouble() / maxAgeMillis.toDouble()).coerceIn(0.0, 1.0)
        return (1.0 - ageRatio * MAX_FRESHNESS_DECAY).coerceIn(MIN_FRESHNESS_SCORE, 1.0)
    }

    private fun emptyEstimate(
        demand: IngredientDemand,
        reasons: Set<ProductPriceMissingReason>
    ) = IngredientProductPriceEstimate(
        ingredientId = demand.ingredientId,
        requiredQuantity = demand.quantity,
        unit = demand.unit,
        lowDzd = null,
        typicalDzd = null,
        highDzd = null,
        packageOptions = emptyList(),
        confidence = ProductPriceConfidence(
            score = 0.0,
            level = ProductPriceConfidenceLevel.LOW,
            matchQuality = 0.0,
            sourceTrust = 0.0,
            sourceTrustCoverage = 0.0,
            freshness = 0.0,
            independentSourceCoverage = 0.0,
            packageCertainty = 0.0,
            localityRelevance = 0.0,
            independentSourceCount = 0,
            eligibleOfferCount = 0
        ),
        missingPriceReasons = reasons,
        latestObservedAtEpochMillis = null,
        includesLimitedAvailability = false
    )

    private fun List<IngredientProductPriceEstimate>.sumRoundedIf(
        shouldSum: Boolean,
        selector: (IngredientProductPriceEstimate) -> Double
    ): Double? = if (shouldSum) roundConsumerDzd(sumOf(selector)) else null

    private fun roundConsumerDzd(value: Double): Double =
        (round(value / DZD_DISPLAY_INCREMENT) * DZD_DISPLAY_INCREMENT)
            .takeIf { it.isFinite() } ?: value
}

private fun normalizeProductPriceLocation(value: String?): String? {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return null
    val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFKD).lowercase()
    val withoutMarks = buildString(decomposed.length) {
        decomposed.forEach { character ->
            val type = Character.getType(character)
            if (type != Character.NON_SPACING_MARK.toInt() &&
                type != Character.COMBINING_SPACING_MARK.toInt() &&
                type != Character.ENCLOSING_MARK.toInt()
            ) append(character)
        }
    }
    val normalized = withoutMarks
        .replace("أ", "ا")
        .replace("إ", "ا")
        .replace("آ", "ا")
        .replace("ٱ", "ا")
        .replace("ى", "ي")
        .replace("ة", "ه")
        .replace("ؤ", "و")
        .replace("ئ", "ي")
        .replace("ـ", "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return null
    return when (normalized) {
        "الاغواط" -> "laghouat"
        "البليده" -> "blida"
        "الطارف" -> "el tarf"
        else -> normalized
    }
}

private const val CURRENCY_DZD = "DZD"
private const val AVAILABILITY_IN_STOCK = "https://schema.org/InStock"
private const val AVAILABILITY_LIMITED = "https://schema.org/LimitedAvailability"
private const val AVAILABILITY_OUT_OF_STOCK = "https://schema.org/OutOfStock"
private val ELIGIBLE_PRICE_STATUSES = setOf("indicative_unverified", "observed_unverified")
private const val DEFAULT_UNVERIFIED_SOURCE_TRUST = 0.5
private const val DEFAULT_VERIFIED_MATCH_QUALITY = 0.75
private const val LIMITED_AVAILABILITY_RELIABILITY = 0.75
private const val MIN_WEIGHT = 0.01
private const val REQUIRED_INDEPENDENT_SOURCE_COUNT = 3
private const val NO_LOCALITY_RELEVANCE = 0.65
private const val UNKNOWN_CITY_RELEVANCE = 0.85
private const val SAME_WILAYA_OTHER_CITY_RELEVANCE = 0.70
private const val MAX_FRESHNESS_DECAY = 0.75
private const val MIN_FRESHNESS_SCORE = 0.25
private const val DZD_DISPLAY_INCREMENT = 5.0
private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.50
private const val HIGH_CONFIDENCE_THRESHOLD = 0.75
