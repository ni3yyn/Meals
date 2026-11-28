package dz.meals.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketProductPriceEstimatorTest {
    private val now = 1_800_000_000_000L
    private val reviewedAt = now - 10_000L

    private fun evidence(
        productId: String = "product-1",
        listingId: String = "listing-1",
        ingredientId: String = "ingredient-rice",
        packageQuantity: Double? = 1_000.0,
        packageUnit: String = "g",
        mappingStatus: String = "VERIFIED",
        mappingReviewedAt: Long? = reviewedAt,
        foodScopeReviewNeeded: Boolean = false,
        price: Double? = 200.0,
        minPrice: Double? = null,
        maxPrice: Double? = null,
        currency: String = "DZD",
        observedAt: Long? = now - 60_000L,
        priceStatus: String = "observed_unverified",
        availability: String = "https://schema.org/InStock",
        store: String = "Market A",
        wilaya: String = "Laghouat",
        notice: String = "Indicative retailer price"
    ) = MarketProductPriceEvidence(
        productId = productId,
        sourceListingId = listingId,
        ingredientId = ingredientId,
        productName = "Rice",
        packageQuantity = packageQuantity,
        packageUnit = packageUnit,
        mappingStatus = mappingStatus,
        mappingReviewedAtEpochMillis = mappingReviewedAt,
        foodScopeReviewNeeded = foodScopeReviewNeeded,
        priceDzd = price,
        priceMinDzd = minPrice,
        priceMaxDzd = maxPrice,
        currencyCode = currency,
        observedAtEpochMillis = observedAt,
        priceStatus = priceStatus,
        availabilityRaw = availability,
        storeName = store,
        source = "test",
        wilaya = wilaya,
        sourceNotice = notice
    )

    @Test
    fun roundsWholePackagesAndPreservesObservedPriceRanges() {
        val estimate = estimateMarketProductPrice(
            ingredientId = "ingredient-rice",
            requiredQuantity = 1_500.0,
            requiredUnit = "g",
            selectedWilaya = "Laghouat",
            evidence = listOf(
                evidence(
                    price = null,
                    minPrice = 200.0,
                    maxPrice = 240.0,
                    store = "Market A"
                ),
                evidence(
                    productId = "product-2",
                    listingId = "listing-2",
                    packageQuantity = 500.0,
                    packageUnit = "g",
                    price = 90.0,
                    store = "Market B"
                )
            ),
            nowEpochMillis = now
        )

        requireNotNull(estimate)
        assertEquals(2, estimate.offerCount)
        assertEquals(270.0, estimate.minCashOutlayDzd, 0.001)
        assertEquals(480.0, estimate.maxCashOutlayDzd, 0.001)
        assertEquals(270.0, estimate.minConsumedValueDzd, 0.001)
        assertEquals(360.0, estimate.maxConsumedValueDzd, 0.001)
        assertEquals(2, estimate.minPackageCount)
        assertEquals(3, estimate.maxPackageCount)
        assertEquals(listOf("Market A", "Market B"), estimate.storeNames)
    }

    @Test
    fun requiresAnExplicitWilayaAndNeverSubstitutesAnotherRegion() {
        val rows = listOf(evidence(wilaya = "El Tarf"))

        assertNull(
            estimateMarketProductPrice(
                "ingredient-rice", 500.0, "g", null, rows, now
            )
        )
        assertNull(
            estimateMarketProductPrice(
                "ingredient-rice", 500.0, "g", "Oran", rows, now
            )
        )
    }

    @Test
    fun supportsArabicAliasesForWilayasPresentInTheCatalogue() {
        val estimate = estimateMarketProductPrice(
            "ingredient-rice",
            500.0,
            "g",
            "ولاية الأغواط",
            listOf(evidence(wilaya = "Laghouat")),
            now
        )

        requireNotNull(estimate)
        assertEquals("Laghouat", estimate.wilaya)
    }

    @Test
    fun excludesOutOfStockAndUnknownAvailability() {
        val rows = listOf(
            evidence(listingId = "out", availability = "https://schema.org/OutOfStock"),
            evidence(listingId = "unknown", availability = ""),
            evidence(listingId = "available", availability = "https://schema.org/InStock")
        )

        val estimate = estimateMarketProductPrice(
            "ingredient-rice", 500.0, "g", "Laghouat", rows, now
        )

        requireNotNull(estimate)
        assertEquals(1, estimate.offerCount)
    }

    @Test
    fun limitedAvailabilityCanContributeButIsExplicitlyFlagged() {
        val estimate = estimateMarketProductPrice(
            "ingredient-rice",
            500.0,
            "g",
            "Laghouat",
            listOf(evidence(availability = "https://schema.org/LimitedAvailability")),
            now
        )

        requireNotNull(estimate)
        assertTrue(estimate.includesLimitedAvailability)
    }

    @Test
    fun excludesUnverifiedMappingsAndFoodScopeReviewProducts() {
        val rows = listOf(
            evidence(listingId = "suggested", mappingStatus = "SUGGESTED"),
            evidence(listingId = "unreviewed", mappingReviewedAt = null),
            evidence(listingId = "food-scope", foodScopeReviewNeeded = true),
            evidence(listingId = "rejected", mappingStatus = "REJECTED")
        )

        assertNull(
            estimateMarketProductPrice(
                "ingredient-rice", 500.0, "g", "Laghouat", rows, now
            )
        )
    }

    @Test
    fun excludesMappingsReviewedAfterThePlanPriceSnapshot() {
        val rows = listOf(
            evidence(mappingReviewedAt = now + 1L)
        )

        assertNull(
            estimateMarketProductPrice(
                "ingredient-rice", 500.0, "g", "Laghouat", rows, now
            )
        )
    }

    @Test
    fun excludesStaleFutureDatedAndUnknownPriceStatuses() {
        val rows = listOf(
            evidence(listingId = "stale", observedAt = now - DEFAULT_MAX_PRICE_AGE_MILLIS - 1),
            evidence(listingId = "future", observedAt = now + 1),
            evidence(listingId = "unknown-status", priceStatus = "verified")
        )

        assertNull(
            estimateMarketProductPrice(
                "ingredient-rice", 500.0, "g", "Laghouat", rows, now
            )
        )
    }

    @Test
    fun convertsKilogramsToGramsAndLitresToMillilitres() {
        val mass = estimateMarketProductPrice(
            "ingredient-rice",
            1_500.0,
            "g",
            "Laghouat",
            listOf(evidence(packageQuantity = 1.0, packageUnit = "kg", price = 220.0)),
            now
        )
        val volume = estimateMarketProductPrice(
            "ingredient-oil",
            1_500.0,
            "ml",
            "Blida",
            listOf(
                evidence(
                    ingredientId = "ingredient-oil",
                    packageQuantity = 1.0,
                    packageUnit = "L",
                    wilaya = "Blida",
                    price = 190.0
                )
            ),
            now
        )

        requireNotNull(mass)
        requireNotNull(volume)
        assertEquals(2, mass.minPackageCount)
        assertEquals(440.0, mass.minCashOutlayDzd, 0.001)
        assertEquals(2, volume.minPackageCount)
        assertEquals(380.0, volume.minCashOutlayDzd, 0.001)
    }

    @Test
    fun doesNotConvertMassToVolumeOrUseUnknownUnits() {
        val massToVolume = estimateMarketProductPrice(
            "ingredient-rice",
            500.0,
            "g",
            "Laghouat",
            listOf(evidence(packageQuantity = 1.0, packageUnit = "L")),
            now
        )
        val unknownUnit = estimateMarketProductPrice(
            "ingredient-rice",
            2.0,
            "cup",
            "Laghouat",
            listOf(evidence(packageQuantity = 1.0, packageUnit = "kg")),
            now
        )

        assertNull(massToVolume)
        assertNull(unknownUnit)
    }

    @Test
    fun excludesNonDzdAndInvalidPriceRanges() {
        val rows = listOf(
            evidence(listingId = "currency", currency = "EUR"),
            evidence(listingId = "negative", price = -10.0),
            evidence(listingId = "inverted-range", price = null, minPrice = 300.0, maxPrice = 200.0)
        )

        assertNull(
            estimateMarketProductPrice(
                "ingredient-rice", 500.0, "g", "Laghouat", rows, now
            )
        )
    }

    @Test
    fun usesOnlyTheLatestObservationForEachRetailListing() {
        val rows = listOf(
            evidence(
                listingId = "same-listing",
                observedAt = now - 120_000L,
                price = 100.0,
                availability = "https://schema.org/InStock"
            ),
            evidence(
                productId = "product-1",
                listingId = "same-listing",
                observedAt = now - 60_000L,
                price = 200.0,
                availability = "https://schema.org/OutOfStock"
            )
        )

        assertNull(
            estimateMarketProductPrice(
                "ingredient-rice", 500.0, "g", "Laghouat", rows, now
            )
        )
    }

    @Test
    fun futureDatedOfferDoesNotHideLastValidHistoricalOffer() {
        val rows = listOf(
            evidence(
                listingId = "same-listing",
                observedAt = now - 60_000L,
                price = 150.0,
                availability = "https://schema.org/InStock"
            ),
            evidence(
                listingId = "same-listing",
                observedAt = now + 60_000L,
                price = 999.0,
                availability = "https://schema.org/InStock"
            )
        )

        val estimate = estimateMarketProductPrice(
            "ingredient-rice", 500.0, "g", "Laghouat", rows, now
        )

        requireNotNull(estimate)
        assertEquals(1, estimate.offerCount)
        assertEquals(150.0, estimate.minCashOutlayDzd, 0.001)
    }

    @Test
    fun ignoresInvalidRequestedQuantitiesAndBlankIngredients() {
        val rows = listOf(evidence())

        assertNull(estimateMarketProductPrice("", 1.0, "g", "Laghouat", rows, now))
        assertNull(estimateMarketProductPrice("ingredient-rice", 0.0, "g", "Laghouat", rows, now))
        assertNull(estimateMarketProductPrice("ingredient-rice", Double.NaN, "g", "Laghouat", rows, now))
    }
}
