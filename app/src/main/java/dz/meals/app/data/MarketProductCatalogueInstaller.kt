package dz.meals.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import dz.meals.app.BuildConfig
import dz.meals.app.data.local.MarketPriceObservationEntity
import dz.meals.app.data.local.MarketProductEntity
import dz.meals.app.data.local.MarketProductSourceEntity
import dz.meals.app.data.local.MealsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Installs the bundled, source-attributed retail catalogue into its own Room
 * tables. Generic food identities and recipe ingredient pricing are untouched.
 */
class MarketProductCatalogueInstaller internal constructor(
    context: Context,
    databaseOverride: MealsDatabase? = null,
    preferencesOverride: SharedPreferences? = null
) {
    private val appContext = context.applicationContext
    private val database = databaseOverride ?: MealsDatabase.getInstance(appContext)
    private val dao = database.mealsDao()
    private val preferences = preferencesOverride
        ?: appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun installIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (
            preferences.getString(INSTALLED_REVISION_KEY, null) == REVISION &&
            dao.countMarketProducts() == EXPECTED_PRODUCT_COUNT &&
            dao.countMarketProductSources() == EXPECTED_SOURCE_COUNT &&
            dao.countMarketPriceObservations() == EXPECTED_PRICE_COUNT
        ) {
            return@withContext 0
        }

        val manifest = readAssetJson(MANIFEST_ASSET)
        validateManifest(manifest)
        val products = ArrayList<MarketProductEntity>(EXPECTED_PRODUCT_COUNT)
        val sources = ArrayList<MarketProductSourceEntity>(EXPECTED_SOURCE_COUNT)
        val prices = ArrayList<MarketPriceObservationEntity>(EXPECTED_PRICE_COUNT)

        val parts = manifest.optJSONArray("parts")
            ?: throw IOException("ملف دليل المنتجات لا يحتوي على أجزاء البيانات.")
        if (parts.length() != EXPECTED_PART_COUNT) {
            throw IOException("عدد أجزاء دليل المنتجات غير مطابق للنسخة المعتمدة.")
        }

        for (index in 0 until parts.length()) {
            val path = parts.optString(index).trim()
            if (!path.startsWith("market-products/parts/") || !path.endsWith(".json")) {
                throw IOException("مسار جزء غير صالح داخل دليل المنتجات.")
            }
            val part = readAssetJson(path)
            if (part.optString("format") != PART_FORMAT ||
                part.optInt("formatVersion", -1) != FORMAT_VERSION ||
                part.optInt("partIndex", -1) != index + 1
            ) {
                throw IOException("جزء غير صالح من دليل المنتجات عند الموضع ${index + 1}.")
            }
            products += parseProducts(part.requiredArray("products"))
            sources += parseSources(part.requiredArray("listings"))
            prices += parsePrices(part.requiredArray("priceObservations"))
        }

        validatePayload(manifest, products, sources, prices)

        database.withTransaction {
            products.chunked(INSERT_BATCH_SIZE).forEach { dao.upsertMarketProducts(it) }
            sources.chunked(INSERT_BATCH_SIZE).forEach { dao.upsertMarketProductSources(it) }
            prices.chunked(INSERT_BATCH_SIZE).forEach { dao.upsertMarketPriceObservations(it) }

            if (dao.countMarketProducts() != EXPECTED_PRODUCT_COUNT ||
                dao.countMarketProductSources() != EXPECTED_SOURCE_COUNT ||
                dao.countMarketPriceObservations() != EXPECTED_PRICE_COUNT
            ) {
                throw IOException("لم يكتمل تثبيت دليل المنتجات والأسعار.")
            }
        }

        if (!preferences.edit().putString(INSTALLED_REVISION_KEY, REVISION).commit()) {
            throw IOException("تعذر حفظ إصدار دليل المنتجات المثبت.")
        }
        products.size
    }

    private fun validateManifest(root: JSONObject) {
        if (root.optString("format") != FORMAT ||
            root.optInt("formatVersion", -1) != FORMAT_VERSION
        ) throw IOException("تنسيق دليل المنتجات غير مدعوم.")

        val target = root.optJSONObject("target")
            ?: throw IOException("ملف دليل المنتجات لا يحدد قاعدة البيانات المستهدفة.")
        if (target.optString("repository") != TARGET_REPOSITORY ||
            target.optInt("roomSchemaVersion", -1) != ROOM_SCHEMA_VERSION
        ) throw IOException("دليل المنتجات لا يطابق مخطط Midati الحالي.")

        val counts = root.optJSONObject("counts")
            ?: throw IOException("أعداد دليل المنتجات مفقودة.")
        if (counts.optInt("products", -1) != EXPECTED_PRODUCT_COUNT ||
            counts.optInt("sourceListings", -1) != EXPECTED_SOURCE_COUNT ||
            counts.optInt("priceObservations", -1) != EXPECTED_PRICE_COUNT ||
            counts.optInt("rawRows", -1) != EXPECTED_RAW_ROW_COUNT
        ) throw IOException("أعداد دليل المنتجات لا تطابق النسخة المعتمدة.")
    }

    private fun validatePayload(
        manifest: JSONObject,
        products: List<MarketProductEntity>,
        sources: List<MarketProductSourceEntity>,
        prices: List<MarketPriceObservationEntity>
    ) {
        val counts = manifest.getJSONObject("counts")
        if (products.size != counts.optInt("products") ||
            sources.size != counts.optInt("sourceListings") ||
            prices.size != counts.optInt("priceObservations")
        ) throw IOException("عدد السجلات المقروءة لا يطابق بيان دليل المنتجات.")

        val productIds = products.map { it.id }.toSet()
        val sourceIds = sources.map { it.id }.toSet()
        val priceIds = prices.map { it.id }.toSet()
        val identityKeys = products.map { it.identityKey }.toSet()

        if (productIds.size != products.size || products.any {
                !UUID_V4.matches(it.id) || it.identityKey.isBlank() || it.name.isBlank()
            } || identityKeys.size != products.size
        ) throw IOException("توجد هويات منتجات مفقودة أو مكررة.")

        if (sourceIds.size != sources.size || sources.any {
                !UUID_V4.matches(it.id) || it.productId !in productIds ||
                    it.source.isBlank() || it.sourceProductKey.isBlank()
            } || sources.map { "${it.source}|${it.sourceProductKey}" }.toSet().size != sources.size
        ) throw IOException("توجد بيانات متجر أو مصدر غير صالحة أو مكررة.")

        if (priceIds.size != prices.size || prices.any {
                !UUID_V4.matches(it.id) || it.productId !in productIds ||
                    it.sourceListingId !in sourceIds ||
                    (it.priceDzd == null && it.priceMinDzd == null && it.priceMaxDzd == null) ||
                    listOfNotNull(it.priceDzd, it.priceMinDzd, it.priceMaxDzd).any { value ->
                        !value.isFinite() || value < 0.0
                    } ||
                    (it.priceMinDzd != null && it.priceMaxDzd != null &&
                        it.priceMinDzd > it.priceMaxDzd)
            }
        ) throw IOException("توجد ملاحظات أسعار غير صالحة.")

        val pricesByListing = prices.associateBy { it.sourceListingId }
        if (sources.any { source ->
                val associatedPrice = pricesByListing[source.id]
                associatedPrice == null || associatedPrice.productId != source.productId
            } || pricesByListing.size != prices.size
        ) throw IOException("توجد علاقة غير صالحة بين المنتج والمتجر والسعر.")

        val invalidGtin = products.firstOrNull { it.gtin != null && !isValidGtin(it.gtin) }
        if (invalidGtin != null) throw IOException("GTIN غير صالح للمنتج ${invalidGtin.id}.")
    }

    private fun parseProducts(array: JSONArray): List<MarketProductEntity> =
        array.objects().map { item ->
            MarketProductEntity(
                id = item.requiredText("id"),
                identityKey = item.requiredText("identityKey"),
                name = item.requiredText("name"),
                nameVariantsJson = item.arrayJson("nameVariants"),
                brand = item.nullableText("brand"),
                gtin = item.nullableText("gtin"),
                imageUrl = item.textOrEmpty("imageUrl"),
                imageUrlsJson = item.arrayJson("imageUrls"),
                description = item.textOrEmpty("description"),
                packageQuantity = item.nullableDouble("packageQuantity"),
                packageUnit = item.textOrEmpty("packageUnit"),
                categoryNamesJson = item.arrayJson("categoryNames"),
                sourceCount = item.intOrDefault("sourceCount", 0),
                foodScopeReviewNeeded = item.optBoolean("foodScopeReviewNeeded", false),
                foodScopeReviewReason = item.textOrEmpty("foodScopeReviewReason"),
                sourceRowCount = item.intOrDefault("sourceRowCount", 1)
            )
        }

    private fun parseSources(array: JSONArray): List<MarketProductSourceEntity> =
        array.objects().map { item ->
            MarketProductSourceEntity(
                id = item.requiredText("id"),
                productId = item.requiredText("productId"),
                source = item.requiredText("source"),
                storeName = item.textOrEmpty("storeName"),
                storeUrl = item.textOrEmpty("storeUrl"),
                city = item.textOrEmpty("city"),
                wilaya = item.textOrEmpty("wilaya"),
                storeAddress = item.textOrEmpty("storeAddress"),
                sourceProductKey = item.requiredText("sourceProductKey"),
                sourceProductId = item.textOrEmpty("sourceProductId"),
                sourceProductKeysJson = item.arrayJson("sourceProductKeys"),
                sourceProductIdsJson = item.arrayJson("sourceProductIds"),
                sku = item.nullableText("sku"),
                gtinRaw = item.nullableText("gtinRaw"),
                rawBrand = item.nullableText("rawBrand"),
                mpn = item.nullableText("mpn"),
                manufacturer = item.nullableText("manufacturer"),
                productUrl = item.textOrEmpty("productUrl"),
                alternateUrlsJson = item.arrayJson("alternateUrls"),
                alternateLocaleUrlsJson = item.arrayJson("alternateLocaleUrls"),
                categorySlugPathsJson = item.arrayJson("categorySlugPaths"),
                variationSkusJson = item.arrayJson("variationSkus"),
                enrichmentMetadataJson = item.optJSONObject("enrichmentMetadata")?.toString().orEmpty()
            )
        }

    private fun parsePrices(array: JSONArray): List<MarketPriceObservationEntity> =
        array.objects().map { item ->
            MarketPriceObservationEntity(
                id = item.requiredText("id"),
                productId = item.requiredText("productId"),
                sourceListingId = item.requiredText("sourceListingId"),
                priceDzd = item.nullableDouble("priceDzd"),
                priceMinDzd = item.nullableDouble("priceMinDzd"),
                priceMaxDzd = item.nullableDouble("priceMaxDzd"),
                priceDisplayRaw = item.textOrEmpty("priceDisplayRaw"),
                currencyCode = item.textOrEmpty("currencyCode").ifBlank { "DZD" },
                observedAtEpochMillis = item.nullableLong("observedAtEpochMillis"),
                priceSource = item.textOrEmpty("priceSource"),
                priceStatus = item.textOrEmpty("priceStatus"),
                availabilityRaw = item.textOrEmpty("availabilityRaw"),
                stockQuantityRaw = item.textOrEmpty("stockQuantityRaw"),
                locationHint = item.textOrEmpty("locationHint"),
                sourceNotice = item.textOrEmpty("sourceNotice")
            )
        }

    private fun readAssetJson(path: String): JSONObject = try {
        appContext.assets.open(path).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    } catch (error: Exception) {
        throw IOException("تعذر قراءة دليل المنتجات المضمّن: $path", error)
    }

    private fun JSONArray.objects(): List<JSONObject> = buildList(length()) {
        for (index in 0 until length()) {
            add(optJSONObject(index) ?: throw IOException("سجل غير صالح داخل دليل المنتجات."))
        }
    }

    private fun JSONObject.requiredArray(key: String): JSONArray =
        optJSONArray(key) ?: throw IOException("قائمة $key مفقودة من دليل المنتجات.")

    private fun JSONObject.requiredText(key: String): String =
        textOrEmpty(key).takeIf { it.isNotBlank() }
            ?: throw IOException("حقل $key مطلوب في دليل المنتجات.")

    private fun JSONObject.textOrEmpty(key: String): String {
        val value = opt(key)
        return when (value) {
            null, JSONObject.NULL -> ""
            is String -> value.trim()
            is Number, is Boolean -> value.toString()
            else -> value.toString()
        }
    }

    private fun JSONObject.nullableText(key: String): String? =
        textOrEmpty(key).takeIf { it.isNotBlank() }

    private fun JSONObject.arrayJson(key: String): String =
        optJSONArray(key)?.toString() ?: "[]"

    private fun JSONObject.intOrDefault(key: String, defaultValue: Int): Int {
        val value = opt(key) ?: return defaultValue
        if (value === JSONObject.NULL) return defaultValue
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        } ?: throw IOException("قيمة عددية غير صالحة للحقل $key.")
    }

    private fun JSONObject.nullableDouble(key: String): Double? {
        val value = opt(key) ?: return null
        if (value === JSONObject.NULL) return null
        val number = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        } ?: throw IOException("قيمة عشرية غير صالحة للحقل $key.")
        if (!number.isFinite()) throw IOException("قيمة عشرية غير صالحة للحقل $key.")
        return number
    }

    private fun JSONObject.nullableLong(key: String): Long? {
        val value = opt(key) ?: return null
        if (value === JSONObject.NULL) return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        } ?: throw IOException("تاريخ غير صالح للحقل $key.")
    }

    companion object {
        const val EXPECTED_PRODUCT_COUNT = 1_803
        const val EXPECTED_SOURCE_COUNT = 1_803
        const val EXPECTED_PRICE_COUNT = 1_803
        const val EXPECTED_RAW_ROW_COUNT = 1_958
        private const val EXPECTED_PART_COUNT = 7
        private const val FORMAT = "midati-market-product-catalogue"
        private const val PART_FORMAT = "midati-market-product-catalogue-part"
        private const val FORMAT_VERSION = 1
        private const val ROOM_SCHEMA_VERSION = 19
        private const val TARGET_REPOSITORY = "ni3yyn/Meals"
        private const val MANIFEST_ASSET = "market-products/manifest.json"
        private const val PREFERENCES_NAME = "market_product_catalogue"
        private const val INSTALLED_REVISION_KEY = "installed_revision"
        private const val INSERT_BATCH_SIZE = 200
        private val UUID_V4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

        private fun isValidGtin(value: String): Boolean {
            if (!value.all { it.isDigit() } || value.length !in setOf(8, 12, 13, 14)) return false
            val digits = value.map { it.digitToInt() }
            val checkDigit = digits.last()
            val weightedSum = digits.dropLast(1).asReversed().mapIndexed { index, digit ->
                digit * if (index % 2 == 0) 3 else 1
            }.sum()
            return (10 - (weightedSum % 10)) % 10 == checkDigit
        }

        val REVISION: String get() = BuildConfig.MARKET_PRODUCT_CATALOGUE_REVISION
    }
}
