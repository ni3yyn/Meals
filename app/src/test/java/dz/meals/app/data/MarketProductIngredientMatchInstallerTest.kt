package dz.meals.app.data

import android.content.Context
import dz.meals.app.BuildConfig
import dz.meals.app.data.local.FoodCategoryEntity
import dz.meals.app.data.local.FoodSubcategoryEntity
import dz.meals.app.data.local.IngredientEntity
import dz.meals.app.data.local.MarketProductEntity
import dz.meals.app.data.local.MealsDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarketProductIngredientMatchInstallerTest {

    @Test
    fun installsSuggestionsAndVerifiedRowsButExposesOnlyVerifiedMappings() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "product-ingredient-map-${UUID.randomUUID()}.db"
        val preferences = context.getSharedPreferences("product-ingredient-map-$databaseName", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val database = MealsDatabase.createForTesting(context, databaseName)

        try {
            val dao = database.mealsDao()
            seedCatalogueRows(dao, foodScopeReviewNeeded = false)
            val json = mappingPayload(
                verifiedProductId = "product-verified",
                suggestedProductId = "product-suggested"
            )
            val installer = MarketProductIngredientMatchInstaller(
                context = context,
                databaseOverride = database,
                preferencesOverride = preferences,
                mappingJsonOverride = json
            )

            assertEquals(2, installer.installIfNeeded())
            assertEquals(2, dao.countMarketProductIngredientMatches())
            val verified = dao.getVerifiedMarketProductIngredientMatches()
            assertEquals(1, verified.size)
            assertEquals("product-verified", verified.single().productId)
            assertEquals("ingredient-salt", verified.single().ingredientId)
            assertEquals("SUGGESTED", dao.getMarketProductIngredientMatch("product-suggested")?.status)
            assertEquals(0, installer.installIfNeeded())
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun foodScopeReviewFlagBlocksVerifiedMappingImport() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "product-ingredient-review-${UUID.randomUUID()}.db"
        val preferences = context.getSharedPreferences("product-ingredient-review-$databaseName", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val database = MealsDatabase.createForTesting(context, databaseName)

        try {
            val dao = database.mealsDao()
            seedCatalogueRows(dao, foodScopeReviewNeeded = true)
            val payload = JSONObject()
                .put("format", "midati-product-ingredient-matches")
                .put("formatVersion", 1)
                .put("sourceCatalogueRevision", BuildConfig.MARKET_PRODUCT_BASE_CATALOGUE_REVISION)
                .put("catalogueProductCount", 1)
                .put("generatedAtEpochMillis", 100L)
                .put(
                    "mappings",
                    JSONArray().put(
                        JSONObject()
                            .put("productId", "product-verified")
                            .put("ingredientId", "ingredient-salt")
                            .put("status", "VERIFIED")
                            .put("matchMethod", "MANUAL")
                            .put("confidenceScore", 1.0)
                            .put("rationale", "Reviewer confirmed the product is salt.")
                            .put("algorithmVersion", "test-v1")
                            .put("reviewedAtEpochMillis", 90L)
                            .put("updatedAtEpochMillis", 100L)
                    )
                ).toString()

            val installer = MarketProductIngredientMatchInstaller(
                context = context,
                databaseOverride = database,
                preferencesOverride = preferences,
                mappingJsonOverride = payload
            )
            assertEquals(0, installer.installIfNeeded())
            assertEquals(0, dao.countMarketProductIngredientMatches())
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            preferences.edit().clear().commit()
        }
    }

    private suspend fun seedCatalogueRows(
        dao: dz.meals.app.data.local.MealsDao,
        foodScopeReviewNeeded: Boolean
    ) {
        dao.upsertFoodCategories(
            listOf(FoodCategoryEntity("foodcat-test", "اختبار", "Test", "Test", 0))
        )
        dao.upsertFoodSubcategories(
            listOf(FoodSubcategoryEntity("foodsub-test", "foodcat-test", "عام", "General", "Général", 0))
        )
        dao.insertIngredients(
            listOf(
                IngredientEntity(
                    id = "ingredient-salt",
                    nameAr = "ملح",
                    nameFr = "Sel",
                    categoryId = "foodcat-test",
                    subcategoryId = "foodsub-test",
                    baseUnit = "g",
                    allergensCsv = "",
                    aliasesCsv = "sel|salt"
                )
            )
        )
        dao.upsertMarketProducts(
            listOf(
                product("product-verified", foodScopeReviewNeeded),
                product("product-suggested", false)
            )
        )
    }

    private fun product(id: String, needsReview: Boolean) = MarketProductEntity(
        id = id,
        identityKey = "test|$id",
        name = "Sel fin",
        nameVariantsJson = "[]",
        brand = null,
        gtin = null,
        imageUrl = "",
        imageUrlsJson = "[]",
        description = "Salt",
        packageQuantity = 1.0,
        packageUnit = "kg",
        categoryNamesJson = "[]",
        sourceCount = 1,
        foodScopeReviewNeeded = needsReview,
        foodScopeReviewReason = if (needsReview) "Needs review" else "",
        sourceRowCount = 1
    )

    private fun mappingPayload(
        verifiedProductId: String,
        suggestedProductId: String
    ): String {
        fun mapping(productId: String, status: String, reviewedAt: Long?): JSONObject =
            JSONObject()
                .put("productId", productId)
                .put("ingredientId", "ingredient-salt")
                .put("status", status)
                .put("matchMethod", if (status == "VERIFIED") "MANUAL" else "FUZZY_NAME")
                .put("confidenceScore", 0.99)
                .put("rationale", "Salt product candidate.")
                .put("algorithmVersion", "test-v1")
                .put("reviewedAtEpochMillis", reviewedAt ?: JSONObject.NULL)
                .put("updatedAtEpochMillis", 100L)

        return JSONObject()
            .put("format", "midati-product-ingredient-matches")
            .put("formatVersion", 1)
            .put("sourceCatalogueRevision", BuildConfig.MARKET_PRODUCT_BASE_CATALOGUE_REVISION)
            .put("catalogueProductCount", 2)
            .put("generatedAtEpochMillis", 100L)
            .put(
                "mappings",
                JSONArray()
                    .put(mapping(verifiedProductId, "VERIFIED", 90L))
                    .put(mapping(suggestedProductId, "SUGGESTED", null))
            )
            .toString()
    }
}
