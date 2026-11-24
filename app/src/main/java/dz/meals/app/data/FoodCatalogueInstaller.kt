package dz.meals.app.data

import android.content.Context
import androidx.room.withTransaction
import dz.meals.app.data.local.FoodItemEntity
import dz.meals.app.data.local.MealsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Installs the pinned generic food-identity catalogue into Room.
 *
 * This is deliberately separate from retailer/market products: these rows
 * describe foods independent of brand, barcode, retailer or package size.
 */
class FoodCatalogueInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val database = MealsDatabase.getInstance(appContext)
    private val dao = database.mealsDao()
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun installIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (
            preferences.getString(INSTALLED_REVISION_KEY, null) == REVISION &&
            dao.countFoodItems() == EXPECTED_ITEM_COUNT
        ) {
            return@withContext 0
        }

        val catalogue = readAssetJson(CATALOGUE_ASSET)
        val categories = readAssetJson(CATEGORIES_ASSET)
        val products = catalogue.optJSONArray("products")
            ?: throw IOException("ملف دليل المكونات لا يحتوي على قائمة المنتجات الغذائية.")
        if (products.length() != EXPECTED_ITEM_COUNT) {
            throw IOException(
                "عدد عناصر دليل المكونات غير متوقع: ${products.length()} بدلاً من $EXPECTED_ITEM_COUNT."
            )
        }

        val categoryNames = readCategoryNames(categories)
        val batch = ArrayList<FoodItemEntity>(BATCH_SIZE)
        val seenIds = HashSet<String>()
        var imported = 0

        database.withTransaction {
            dao.deleteAllFoodItems()
        }

        suspend fun flush() {
            if (batch.isEmpty()) return
            database.withTransaction {
                dao.upsertFoodItems(batch.toList())
            }
            imported += batch.size
            batch.clear()
        }

        for (index in 0 until products.length()) {
            val item = products.optJSONObject(index)
                ?: throw IOException("عنصر غير صالح في دليل المكونات عند الموضع ${index + 1}.")
            val id = item.optString("id").trim().lowercase()
            if (!UUID_PATTERN.matches(id) || !seenIds.add(id)) {
                throw IOException("معرّف مفقود أو مكرر في دليل المكونات عند الموضع ${index + 1}.")
            }

            val names = item.optJSONObject("names") ?: JSONObject()
            val aliases = item.optJSONObject("aliases") ?: JSONObject()
            val categoryId = item.stringOrEmpty("categoryId")
            val subcategoryId = item.stringOrEmpty("subcategoryId")
            val category = categoryNames[categoryId] ?: CategoryNames()
            val subcategory = categoryNames[subcategoryId] ?: CategoryNames()
            val acceptedUnits = item.optJSONArray("acceptedUnits").toStringValues()
            val recipeIngredientIds = item.optJSONArray("recipeIngredientIds").toStringValues()

            batch += FoodItemEntity(
                id = id,
                nameAr = names.stringOrEmpty("ar"),
                nameEn = names.stringOrEmpty("en"),
                nameFr = names.stringOrEmpty("fr"),
                aliasesArCsv = aliases.optJSONArray("ar").toStringValues().joinToString("|"),
                aliasesEnCsv = aliases.optJSONArray("en").toStringValues().joinToString("|"),
                aliasesFrCsv = aliases.optJSONArray("fr").toStringValues().joinToString("|"),
                categoryId = categoryId,
                subcategoryId = subcategoryId,
                categoryNameAr = category.ar,
                categoryNameEn = category.en,
                categoryNameFr = category.fr,
                subcategoryNameAr = subcategory.ar,
                acceptedUnitsCsv = acceptedUnits.joinToString("|"),
                imageUrl = item.stringOrEmpty("imageUrl"),
                quantity = if (item.isNull("quantity")) null else item.optDouble("quantity").takeIf { it.isFinite() },
                countryOfOrigin = item.stringOrEmpty("countryOfOrigin"),
                nutritionJson = item.optJSONObject("nutrition")?.toString().orEmpty(),
                sourceMappingsJson = item.optJSONObject("sourceMappings")?.toString().orEmpty(),
                recipeIngredientIdsCsv = recipeIngredientIds.joinToString("|"),
                sourceJson = item.toString()
            )
            if (batch.size >= BATCH_SIZE) flush()
        }
        flush()

        if (imported != EXPECTED_ITEM_COUNT || dao.countFoodItems() != EXPECTED_ITEM_COUNT) {
            throw IOException("لم يكتمل تثبيت دليل المكونات؛ لم نعتمد النسخة المحلية.")
        }

        preferences.edit()
            .putString(INSTALLED_REVISION_KEY, REVISION)
            .apply()
        imported
    }

    private fun readAssetJson(path: String): JSONObject =
        try {
            appContext.assets.open(path).bufferedReader(Charsets.UTF_8).use { reader ->
                JSONObject(reader.readText())
            }
        } catch (error: Exception) {
            throw IOException("تعذر قراءة ملف دليل المكونات: $path", error)
        }

    private fun readCategoryNames(root: JSONObject): Map<String, CategoryNames> {
        val result = HashMap<String, CategoryNames>()
        val categories = root.optJSONArray("categories") ?: return result
        for (index in 0 until categories.length()) {
            val category = categories.optJSONObject(index) ?: continue
            val id = category.stringOrEmpty("id")
            if (id.isNotBlank()) result[id] = category.optJSONObject("names").toCategoryNames()
            val children = category.optJSONArray("subcategories") ?: continue
            for (childIndex in 0 until children.length()) {
                val child = children.optJSONObject(childIndex) ?: continue
                val childId = child.stringOrEmpty("id")
                if (childId.isNotBlank()) result[childId] = child.optJSONObject("names").toCategoryNames()
            }
        }
        return result
    }

    private fun JSONObject.stringOrEmpty(key: String): String =
        if (isNull(key)) "" else optString(key).takeUnless { it == "null" }.orEmpty()

    private fun JSONObject?.toCategoryNames(): CategoryNames {
        val names = this ?: return CategoryNames()
        return CategoryNames(
            ar = names.stringOrEmpty("ar"),
            en = names.stringOrEmpty("en"),
            fr = names.stringOrEmpty("fr")
        )
    }

    private fun JSONArray?.toStringValues(): List<String> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank() && value != "null") add(value)
            }
        }
    }

    private data class CategoryNames(
        val ar: String = "",
        val en: String = "",
        val fr: String = ""
    )

    companion object {
        const val EXPECTED_ITEM_COUNT = 4_649
        const val REVISION = "1f27d99c58e91f22fb8feda2a50ef196e8ac95cb"
        private const val PREFERENCES_NAME = "generic_food_catalogue"
        private const val INSTALLED_REVISION_KEY = "installed_revision"
        private const val CATALOGUE_ASSET = "food/catalogue.json"
        private const val CATEGORIES_ASSET = "food/categories.json"
        private const val BATCH_SIZE = 200
        private val UUID_PATTERN = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        )
    }
}
