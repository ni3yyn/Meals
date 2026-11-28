package dz.meals.app.domain

import dz.meals.app.data.IngredientDemand
import dz.meals.app.data.local.MarketPriceObservationEntity
import dz.meals.app.data.local.MarketProductEntity
import dz.meals.app.data.local.MarketProductIngredientMatchEntity
import dz.meals.app.data.local.MarketProductSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductPriceEstimatorTest {
    private val now = 1_800_000_000_000L
    private val reviewedAt = now - 10_000L

    private fun demand(quantity: Double = 1_500.0, unit: String = "g") = IngredientDemand(
        ingredientId = "ingredient-rice",
        quantity = quantity,
        unit = unit,
        contributions = emptyList()
    )

    private fun product(
        id: String,
        quantity: Double? = 1_000.0,
        unit: String = "g",
        foodScopeReviewNeeded: Boolean = false,
        name: String = "Rice",
        brand: String? = null
    ) = MarketProductEntity(
        id = id,
        identityKey = "identity-$id",
        name = name,
        nameVariantsJson = "[]",
        brand = brand,
        gtin = null,
        imageUrl = "",
        imageUrlsJson = "[]",
        description = "",
        packageQuantity = quantity,
        packageUnit = unit,
        categoryNamesJson = "[]",
        sourceCount = 1,
        foodScopeReviewNeeded = foodScopeReviewNeeded,
        foodScopeReviewReason = "",
        sourceRowCount = 1
    )

    private fun match(
        productId: String,
        ingredientId: String = "ingredient-rice",
        status: String = "VERIFIED",
        confidence: Double? = 1.0,
        reviewed: Long? = reviewedAt
    ) = MarketProductIngredientMatchEntity(
        productId = productId,
        ingredientId = ingredientId,
        status = status,
        matchMethod = "human_review",
        confidenceScore = confidence,
        rationale = "Reviewed product identity",
        algorithmVersion = "test-v1",
        reviewedAtEpochMillis = reviewed,
        updatedAtEpochMillis = reviewedAt
    )

    private fun listing(
        productId: String,
        id: String = "listing-$productId",
        source: String = id,
        store: String = "Store $id",
        wilaya: String = "Laghouat",
        city: String = "Laghouat",
        storeUrl: String = "https://example.test/$id"
    ) = MarketProductSourceEntity(
        id = id,
        productId = productId,
        source = source,
        storeName = store,
        storeUrl = storeUrl,
        city = city,
        wilaya = wilaya,
        storeAddress = "",
        sourceProductKey = "key-$id",
        sourceProductId = "",
        sourceProductKeysJson = "[]",
        sourceProductIdsJson = "[]",
        sku = null,
        gtinRaw = null,
        rawBrand = null,
        mpn = null,
        manufacturer = null,
        productUrl = "",
        alternateUrlsJson = "[]",
        alternateLocaleUrlsJson = "[]",
        categorySlugPathsJson = "[]",
        variationSkusJson = "[]",
        enrichmentMetadataJson = "{}"
    )

    private fun observation(
        productId: String,
        listingId: String = "listing-$productId",
        price: Double? = 200.0,
        minPrice: Double? = null,
        maxPrice: Double? = null,
        observedAt: Long? = now - 60_000L,
        currency: String = "DZD",
        priceStatus: String = "observed_unverified",
        availability: String = "https://schema.org/InStock"
    ) = MarketPriceObservationEntity(
        id = "observation-$listingId",
        productId = productId,
        sourceListingId = listingId,
        priceDzd = price,
        priceMinDzd = minPrice,
        priceMaxDzd = maxPrice,
        priceDisplayRaw = "",
        currencyCode = currency,
        observedAtEpochMillis = observedAt,
        priceSource = "retailer",
        priceStatus = priceStatus,
        availabilityRaw = availability,
        stockQuantityRaw = "",
        locationHint = "",
        sourceNotice = "Observed retailer price"
    )

    private fun estimate(
        demands: List<IngredientDemand> = listOf(demand()),
        matches: List<MarketProductIngredientMatchEntity>,
        products: List<MarketProductEntity>,
        listings: List<MarketProductSourceEntity>,
        observations: List<MarketPriceObservationEntity>,
        locality: ProductPriceLocality? = ProductPriceLocality(wilaya = "Laghouat"),
        trust: Map<String, Double> = emptyMap(),
        maxAge: Long = DEFAULT_MAX_PRICE_AGE_MILLIS
    ) = ProductPriceEstimator.estimate(
        demands = demands,
        approvedMatches = matches,
        products = products,
        sourceListings = listings,
        observations = observations,
        locality = locality,
        sourceTrustScores = trust,
        nowEpochMillis = now,
        maxObservationAgeMillis = maxAge
    )

    @Test
    fun returnsDistinctPackageOptionsAndWholePackCashOutlay() {
        val products = listOf(
            product("large", quantity = 1_000.0, brand = "Brand A"),
            product("small", quantity = 500.0, brand = "Brand B"),
            product("family", quantity = 2_000.0, brand = "Brand C")
        )
        val listings = products.map { listing(it.id) }
        val observations = listOf(
            observation("large", price = 200.0),
            observation("small", price = 100.0),
            observation("family", price = 360.0)
        )

        val result = estimate(
            matches = products.map { match(it.id) },
            products = products,
            listings = listings,
            observations = observations,
            trust = mapOf("listing-large" to 1.0, "listing-small" to 1.0, "listing-family" to 1.0)
        )

        val estimate = result.ingredientEstimates.single()
        assertEquals(3, estimate.packageOptions.size)
        assertEquals(mapOf("large" to 2, "small" to 3, "family" to 1), estimate.packageOptions.associate { it.productId to it.packagesRequired })
        assertEquals(300.0, estimate.lowDzd!!, 0.001)
        assertEquals(360.0, estimate.typicalDzd!!, 0.001)
        assertEquals(400.0, estimate.highDzd!!, 0.001)
        assertEquals(300.0, result.lowDzd!!, 0.001)
        assertEquals(360.0, result.typicalDzd!!, 0.001)
        assertEquals(400.0, result.highDzd!!, 0.001)
        assertEquals(0, result.missingIngredientCount)
    }

    @Test
    fun onlyHumanVerifiedAndReviewedMappingsAreEligible() {
        val products = listOf(product("suggested"), product("unreviewed"), product("late"))
        val matches = listOf(
            match("suggested", status = "SUGGESTED"),
            match("unreviewed", reviewed = null),
            match("late", reviewed = now + 1L)
        )
        val listings = products.map { listing(it.id) }
        val observations = products.map { observation(it.id) }

        val result = estimate(
            matches = matches,
            products = products,
            listings = listings,
            observations = observations
        )
        val estimate = result.ingredientEstimates.single()

        assertTrue(estimate.packageOptions.isEmpty())
        assertNull(estimate.typicalDzd)
        assertTrue(ProductPriceMissingReason.NO_APPROVED_PRODUCT_MATCH in estimate.missingPriceReasons)
    }

    @Test
    fun excludesFoodScopeReviewProductsEvenWhenTheMappingIsVerified() {
        val result = estimate(
            matches = listOf(match("uncertain")),
            products = listOf(product("uncertain", foodScopeReviewNeeded = true)),
            listings = listOf(listing("uncertain")),
            observations = listOf(observation("uncertain"))
        )
        val estimate = result.ingredientEstimates.single()

        assertNull(estimate.lowDzd)
        assertTrue(ProductPriceMissingReason.PRODUCT_REQUIRES_FOOD_REVIEW in estimate.missingPriceReasons)
    }

    @Test
    fun filtersCrossWilayaOffersAndDoesNotBorrowFromAnotherRegion() {
        val products = listOf(product("local"), product("other"))
        val listings = listOf(
            listing("local", wilaya = "Laghouat"),
            listing("other", wilaya = "Blida")
        )
        val result = estimate(
            matches = products.map { match(it.id) },
            products = products,
            listings = listings,
            observations = products.map { observation(it.id) },
            locality = ProductPriceLocality(wilaya = "ولاية الأغواط")
        )
        val localEstimate = result.ingredientEstimates.single()

        assertEquals(listOf("local"), localEstimate.packageOptions.map { it.productId })
        assertEquals(1, localEstimate.confidence.independentSourceCount)
        assertEquals(ProductPriceConfidenceLevel.LOW, localEstimate.confidence.level)

        val noLocalOffer = estimate(
            matches = listOf(match("other")),
            products = listOf(product("other")),
            listings = listOf(listing("other", wilaya = "Blida")),
            observations = listOf(observation("other")),
            locality = ProductPriceLocality(wilaya = "Djelfa")
        ).ingredientEstimates.single()
        assertNull(noLocalOffer.typicalDzd)
        assertTrue(
            ProductPriceMissingReason.NO_LOCAL_RETAILER_LISTING in noLocalOffer.missingPriceReasons
        )
    }

    @Test
    fun supportsOptionalLocalityButMarksNonLocalCoverageAsWeaker() {
        val result = ProductPriceEstimator.estimate(
            demands = listOf(demand()),
            approvedMatches = listOf(match("one")),
            products = listOf(product("one")),
            sourceListings = listOf(listing("one", wilaya = "Blida")),
            observations = listOf(observation("one")),
            locality = null,
            sourceTrustScores = mapOf("listing-one" to 1.0),
            nowEpochMillis = now
        )
        val estimate = result.ingredientEstimates.single()

        assertEquals(1, estimate.packageOptions.size)
        assertEquals(0.65, estimate.confidence.localityRelevance, 0.001)
        assertEquals(ProductPriceConfidenceLevel.LOW, estimate.confidence.level)
    }

    @Test
    fun weightsTypicalEstimateBySourceTrustAndKeepsObservedRange() {
        val products = listOf(product("low"), product("mid"), product("high"))
        val listings = products.map { listing(it.id) }
        val observations = listOf(
            observation("low", price = 100.0),
            observation("mid", price = 200.0),
            observation("high", price = 300.0)
        )
        val result = estimate(
            matches = products.map { match(it.id) },
            products = products,
            listings = listings,
            observations = observations,
            trust = mapOf(
                "listing-low" to 0.05,
                "listing-mid" to 0.10,
                "listing-high" to 1.0
            )
        )
        val estimate = result.ingredientEstimates.single()

        assertEquals(200.0, estimate.lowDzd!!, 0.001)
        assertEquals(600.0, estimate.highDzd!!, 0.001)
        assertEquals(600.0, estimate.typicalDzd!!, 0.001)
        assertEquals(1.0, estimate.confidence.sourceTrustCoverage, 0.001)
    }

    @Test
    fun oneIndependentSourceIsAlwaysLowCoverage() {
        val result = estimate(
            matches = listOf(match("one")),
            products = listOf(product("one")),
            listings = listOf(listing("one")),
            observations = listOf(observation("one")),
            trust = mapOf("listing-one" to 1.0)
        )
        val confidence = result.ingredientEstimates.single().confidence

        assertEquals(1, confidence.independentSourceCount)
        assertTrue(confidence.independentSourceCoverage < 0.5)
        assertEquals(ProductPriceConfidenceLevel.LOW, confidence.level)
    }

    @Test
    fun missingPriceNeverBecomesZeroAndProducesAnExplicitReason() {
        val result = estimate(
            matches = listOf(match("one")),
            products = listOf(product("one")),
            listings = listOf(listing("one")),
            observations = listOf(observation("one", price = null))
        )
        val estimate = result.ingredientEstimates.single()

        assertNull(estimate.lowDzd)
        assertNull(estimate.typicalDzd)
        assertNull(estimate.highDzd)
        assertNull(result.typicalDzd)
        assertEquals(1, result.missingIngredientCount)
        assertTrue(ProductPriceMissingReason.MISSING_PRICE in estimate.missingPriceReasons)
    }

    @Test
    fun filtersStaleFutureUnavailableAndNonDzdObservations() {
        val cases = listOf(
            observation("stale", observedAt = now - DEFAULT_MAX_PRICE_AGE_MILLIS - 1L),
            observation("future", observedAt = now + 1L),
            observation("out", availability = "https://schema.org/OutOfStock"),
            observation("currency", currency = "EUR")
        )
        val products = listOf(
            product("stale"),
            product("future"),
            product("out"),
            product("currency")
        )
        val result = estimate(
            matches = products.map { match(it.id) },
            products = products,
            listings = products.map { listing(it.id) },
            observations = cases
        )
        val reasons = result.ingredientEstimates.single().missingPriceReasons

        assertTrue(result.ingredientEstimates.single().packageOptions.isEmpty())
        assertTrue(ProductPriceMissingReason.STALE_OBSERVATION in reasons)
        assertTrue(ProductPriceMissingReason.FUTURE_DATED_OBSERVATION in reasons)
        assertTrue(ProductPriceMissingReason.UNAVAILABLE_PRODUCT in reasons)
        assertTrue(ProductPriceMissingReason.NON_DZD_CURRENCY in reasons)
    }

    @Test
    fun convertsPackageKilogramsToDemandGrams() {
        val result = estimate(
            demands = listOf(demand(1_500.0, "g")),
            matches = listOf(match("one")),
            products = listOf(product("one", quantity = 1.0, unit = "kg")),
            listings = listOf(listing("one")),
            observations = listOf(observation("one", price = 220.0))
        )
        val option = result.ingredientEstimates.single().packageOptions.single()

        assertEquals(1_000.0, option.packageQuantityInDemandUnit, 0.001)
        assertEquals(2, option.packagesRequired)
        assertEquals(440.0, option.cashOutlayMinDzd, 0.001)
    }

    @Test
    fun refusesIncompatiblePackageUnitsAndInvalidDemands() {
        val incompatible = estimate(
            demands = listOf(demand(500.0, "g")),
            matches = listOf(match("one")),
            products = listOf(product("one", quantity = 1.0, unit = "L")),
            listings = listOf(listing("one")),
            observations = listOf(observation("one"))
        ).ingredientEstimates.single()
        assertNull(incompatible.typicalDzd)
        assertTrue(
            ProductPriceMissingReason.INCOMPATIBLE_PACKAGE_UNIT in incompatible.missingPriceReasons
        )

        val invalid = estimate(
            demands = listOf(demand(Double.NaN)),
            matches = emptyList(),
            products = emptyList(),
            listings = emptyList(),
            observations = emptyList()
        ).ingredientEstimates.single()
        assertNull(invalid.typicalDzd)
        assertTrue(ProductPriceMissingReason.INVALID_DEMAND in invalid.missingPriceReasons)
    }

    @Test
    fun requiresCompatibleProductAndListingIds() {
        val result = estimate(
            matches = listOf(match("one")),
            products = listOf(product("one")),
            listings = listOf(listing("one")),
            observations = listOf(observation("different-product", listingId = "listing-one"))
        )
        val estimate = result.ingredientEstimates.single()

        assertTrue(estimate.packageOptions.isEmpty())
        assertTrue(ProductPriceMissingReason.NO_PRICE_OBSERVATION in estimate.missingPriceReasons)
    }
}
