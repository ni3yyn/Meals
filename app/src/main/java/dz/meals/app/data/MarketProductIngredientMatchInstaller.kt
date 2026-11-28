package dz.meals.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import dz.meals.app.BuildConfig
import dz.meals.app.data.local.MarketProductIngredientMatchEntity
import dz.meals.app.data.local.MealsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Installs the separately reviewed product-to-ingredient bridge.
 *
 * The file is optional until the Colab review pipeline has produced it.
 * Candidate matches remain SUGGESTED; the estimator must query VERIFIED rows
 * exclusively. A mapping generated from another product-catalogue revision is
 * discarded rather than risking stale product identities.
 */
class MarketProductIngredientMatchInstaller internal constructor(
    context: Context,
    databaseOverride: MealsDatabase? = null,
    preferencesOverride: SharedPreferences? = null,
    private val mappingJsonOverride: String? = null
) {
    private val appContext = context.applicationContext
    private val database = databaseOverride ?: MealsDatabase.getInstance(appContext)
    private val dao = database.mealsDao()
    private val preferences = preferencesOverride
        ?: appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun installIfNeeded(): Int = withContext(Dispatchers.IO) {
        val json = mappingJsonOverride ?: readOptionalAsset() ?: run {
            if (preferences.getString(INSTALLED_REVISION_KEY, null) != null) {
                database.withTransaction { dao.deleteAllMarketProductIngredientMatches() }
                preferences.edit().remove(INSTALLED_REVISION_KEY).commit()
            }
            return@withContext 0
        }

        val root = try {
            JSONObject(json)
        } catch (error: Exception) {
            Log.e(TAG, "Ignoring invalid product-to-ingredient mapping JSON.", error)
            return@withContext 0
        }

        if (root.optString("format") != FORMAT ||
            root.optInt("formatVersion", -1) != FORMAT_VERSION
        ) {
            Log.e(TAG, "Ignoring unsupported product-to-ingredient mapping format.")
            return@withContext 0
        }

        if (root.optString("sourceCatalogueRevision") !=
            BuildConfig.MARKET_PRODUCT_BASE_CATALOGUE_REVISION
        ) {
            database.withTransaction { dao.deleteAllMarketProductIngredientMatches() }
            preferences.edit().remove(INSTALLED_REVISION_KEY).commit()
            Log.w(TAG, "Mapping was generated for a different product catalogue revision; mappings cleared.")
            return@withContext 0
        }

        if (root.optInt("catalogueProductCount", -1) != dao.countMarketProducts()) {
            Log.e(TAG, "Ignoring product-to-ingredient mapping with a mismatched product count.")
            return@withContext 0
        }

        val mappings = try {
            parseAndValidate(root)
        } catch (error: Exception) {
            Log.e(TAG, "Ignoring invalid product-to-ingredient mapping payload.", error)
            return@withContext 0
        }

        val installedRevision = BuildConfig.MARKET_PRODUCT_CATALOGUE_REVISION
        if (
            preferences.getString(INSTALLED_REVISION_KEY, null) == installedRevision &&
            dao.countMarketProductIngredientMatches() == mappings.size
        ) {
            return@withContext 0
        }

        database.withTransaction {
            dao.deleteAllMarketProductIngredientMatches()
            mappings.chunked(BATCH_SIZE).forEach { dao.upsertMarketProductIngredientMatches(it) }
            if (dao.countMarketProductIngredientMatches() != mappings.size) {
                throw IOException("Product-to-ingredient mapping install count mismatch.")
            }
        }

        if (!preferences.edit().putString(INSTALLED_REVISION_KEY, installedRevision).commit()) {
            throw IOException("Unable to persist product-to-ingredient mapping revision.")
        }
        mappings.size
    }

    private fun readOptionalAsset(): String? =
        try {
            appContext.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: FileNotFoundException) {
            null
        } catch (error: IOException) {
            throw IOException("Unable to read $ASSET_PATH.", error)
        }

    private suspend fun parseAndValidate(root: JSONObject): List<MarketProductIngredientMatchEntity> {
        val products = dao.getMarketProducts(MAX_CATALOGUE_PRODUCTS)
        val productIds = products.mapTo(HashSet()) { it.id }
        val productsNeedingFoodReview = products
            .filter { it.foodScopeReviewNeeded }
            .mapTo(HashSet()) { it.id }
        val ingredientIds = dao.getIngredients().mapTo(HashSet()) { it.id }
        val array = root.optJSONArray("mappings")
            ?: throw IOException("Mapping file does not contain a mappings array.")
        if (array.length() > MAX_MAPPING_COUNT) {
            throw IOException("Mapping file exceeds the supported record limit.")
        }

        val seenProducts = HashSet<String>(array.length())
        val result = ArrayList<MarketProductIngredientMatchEntity>(array.length())
        val generatedAt = root.optLong("generatedAtEpochMillis", 0L)

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: throw IOException("Invalid mapping at index $index.")
            val productId = item.optString("productId").trim()
            val ingredientId = item.optString("ingredientId").trim()
            val status = item.optString("status").trim().uppercase()
            val matchMethod = item.optString("matchMethod").trim()
            val algorithmVersion = item.optString("algorithmVersion").trim()
            val rationale = item.optString("rationale").trim()
            val confidence = if (item.isNull("confidenceScore")) null else item.optDouble(
                "confidenceScore",
                Double.NaN
            )
            val reviewedAt = if (item.isNull("reviewedAtEpochMillis")) null else item.optLong(
                "reviewedAtEpochMillis",
                -1L
            )
            val updatedAt = item.optLong("updatedAtEpochMillis", generatedAt)

            if (productId !in productIds || ingredientId !in ingredientIds) {
                throw IOException("Mapping references an unknown product or recipe ingredient.")
            }
            if (!seenProducts.add(productId)) {
                throw IOException("A market product may have only one mapping row.")
            }
            if (!MarketProductIngredientMatchEntity.isSupportedStatus(status)) {
                throw IOException("Unsupported mapping review status: $status.")
            }
            if (matchMethod.isBlank() || algorithmVersion.isBlank() || updatedAt <= 0L) {
                throw IOException("Mapping metadata is incomplete.")
            }
            if (confidence != null && (!confidence.isFinite() || confidence !in 0.0..1.0)) {
                throw IOException("Mapping confidence must be between 0 and 1.")
            }
            if (reviewedAt != null && reviewedAt <= 0L) {
                throw IOException("Mapping review timestamp is invalid.")
            }
            if (status == MarketProductIngredientMatchEntity.STATUS_VERIFIED &&
                (productId in productsNeedingFoodReview || reviewedAt == null)
            ) {
                throw IOException("Food-scope-uncertain products require review before verification.")
            }

            result += MarketProductIngredientMatchEntity(
                productId = productId,
                ingredientId = ingredientId,
                status = status,
                matchMethod = matchMethod,
                confidenceScore = confidence,
                rationale = rationale,
                algorithmVersion = algorithmVersion,
                reviewedAtEpochMillis = reviewedAt,
                updatedAtEpochMillis = updatedAt
            )
        }
        return result
    }

    companion object {
        private const val TAG = "MidatiProductMapping"
        private const val FORMAT = "midati-product-ingredient-matches"
        private const val FORMAT_VERSION = 1
        private const val ASSET_PATH = "market-products/ingredient-matches.json"
        private const val PREFERENCES_NAME = "market_product_ingredient_matches"
        private const val INSTALLED_REVISION_KEY = "installed_revision"
        private const val BATCH_SIZE = 200
        private const val MAX_CATALOGUE_PRODUCTS = 10_000
        private const val MAX_MAPPING_COUNT = 10_000
    }
}
