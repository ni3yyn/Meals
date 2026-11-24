package dz.meals.app.data

import android.content.Context
import androidx.room.withTransaction
import dz.meals.app.BuildConfig
import dz.meals.app.data.local.FoodCategoryEntity
import dz.meals.app.data.local.FoodItemEntity
import dz.meals.app.data.local.FoodSubcategoryEntity
import dz.meals.app.data.local.MealsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Installs generic food identities and the normalized category taxonomy.
 * Localized category labels are persisted once in taxonomy tables; food rows
 * retain only stable category/subcategory IDs.
 */
class FoodCatalogueInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val database = MealsDatabase.getInstance(appContext)
    private val dao = database.mealsDao()
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun installIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (
            preferences.getString(INSTALLED_REVISION_KEY, null) == REVISION &&
            dao.countFoodItems() == EXPECTED_ITEM_COUNT &&
            dao.countFoodCategories() == EXPECTED_CATEGORY_COUNT &&
            dao.countFoodSubcategories() == EXPECTED_SUBCATEGORY_COUNT
        ) {
            return@withContext 0
        }

        val catalogue = readAssetJson(CATALOGUE_ASSET)
        val taxonomyJson = readAssetJson(CATEGORIES_ASSET)
        val products = catalogue.optJSONArray("products")
            ?: throw IOException("ملف دليل المكونات لا يحتوي على قائمة المنتجات الغذائية.")
        if (products.length() != EXPECTED_ITEM_COUNT) {
            throw IOException(
                "عدد عناصر دليل المكونات غير متوقع: ${products.length()} بدلاً من $EXPECTED_ITEM_COUNT."
            )
        }

        val taxonomy = readTaxonomy(taxonomyJson)
        val foodItems = ArrayList<FoodItemEntity>(products.length())
        val seenIds = HashSet<String>(products.length())

        for (index in 0 until products.length()) {
            val item = products.optJSONObject(index)
                ?: throw IOException("عنصر غير صالح في دليل المكونات عند الموضع ${index + 1}.")
            val id = item.optString("id").trim().lowercase()
            if (!UUID_PATTERN.matches(id) || !seenIds.add(id)) {
                throw IOException("معرّف مفقود أو مكرر في دليل المكونات عند الموضع ${index + 1}.")
            }

            val names = item.optJSONObject("names") ?: JSONObject()
            val arabicName = names.stringOrEmpty("ar")
            if (arabicName.isBlank()) {
                throw IOException("اسم عربي مفقود في عنصر دليل المكونات عند الموضع ${index + 1}.")
            }
            val aliases = item.optJSONObject("aliases") ?: JSONObject()
            val categoryId = item.stringOrEmpty("categoryId")
            val subcategoryId = item.stringOrEmpty("subcategoryId")
            if (taxonomy.subcategoryParents[subcategoryId] != categoryId) {
                throw IOException("Food $id references a missing or mismatched category/subcategory.")
            }

            foodItems += FoodItemEntity(
                id = id,
                nameAr = arabicName,
                nameEn = names.stringOrEmpty("en"),
                nameFr = names.stringOrEmpty("fr"),
                aliasesArCsv = aliases.optJSONArray("ar").toStringValues().joinToString("|"),
                aliasesEnCsv = aliases.optJSONArray("en").toStringValues().joinToString("|"),
                aliasesFrCsv = aliases.optJSONArray("fr").toStringValues().joinToString("|"),
                categoryId = categoryId,
                subcategoryId = subcategoryId,
                acceptedUnitsCsv = item.optJSONArray("acceptedUnits").toStringValues().joinToString("|"),
                imageUrl = item.stringOrEmpty("imageUrl"),
                quantity = if (item.isNull("quantity")) null else item.optDouble("quantity").takeIf { it.isFinite() },
                countryOfOrigin = item.stringOrEmpty("countryOfOrigin"),
                nutritionJson = item.optJSONObject("nutrition")?.toString().orEmpty(),
                sourceMappingsJson = item.optJSONObject("sourceMappings")?.toString().orEmpty(),
                recipeIngredientIdsCsv = item.optJSONArray("recipeIngredientIds").toStringValues().joinToString("|"),
                sourceJson = item.toString()
            )
        }

        if (seenIds.size != EXPECTED_ITEM_COUNT) {
            throw IOException("عدد هويات الطعام الفريدة في دليل المكونات غير صحيح.")
        }

        // Taxonomy and catalogue rows are one atomic install. No destructive
        // pre-clear: interrupted/retried imports keep existing food rows usable.
        database.withTransaction {
            dao.upsertFoodCategories(taxonomy.categories)
            dao.upsertFoodSubcategories(taxonomy.subcategories)
            foodItems.chunked(BATCH_SIZE).forEach { dao.upsertFoodItems(it) }

            if (dao.countFoodItems() != EXPECTED_ITEM_COUNT ||
                dao.countFoodCategories() != EXPECTED_CATEGORY_COUNT ||
                dao.countFoodSubcategories() != EXPECTED_SUBCATEGORY_COUNT
            ) {
                throw IOException("لم يكتمل تثبيت دليل المكونات والتصنيفات.")
            }
        }

        if (!preferences.edit().putString(INSTALLED_REVISION_KEY, REVISION).commit()) {
            throw IOException("تعذر حفظ إصدار دليل المكونات المثبت.")
        }
        foodItems.size
    }

    private fun readAssetJson(path: String): JSONObject =
        try {
            appContext.assets.open(path).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        } catch (error: Exception) {
            throw IOException("تعذر قراءة ملف دليل المكونات: $path", error)
        }

    private fun readTaxonomy(root: JSONObject): TaxonomySnapshot {
        val categories = root.optJSONArray("categories")
            ?: throw IOException("Canonical taxonomy has no categories array.")
        if (categories.length() != EXPECTED_CATEGORY_COUNT) {
            throw IOException("Canonical taxonomy must contain $EXPECTED_CATEGORY_COUNT categories.")
        }

        val categoryEntities = ArrayList<FoodCategoryEntity>(EXPECTED_CATEGORY_COUNT)
        val subcategoryEntities = ArrayList<FoodSubcategoryEntity>(EXPECTED_SUBCATEGORY_COUNT)
        val parents = HashMap<String, String>(EXPECTED_SUBCATEGORY_COUNT)
        val categoryIds = HashSet<String>()
        val subcategoryIds = HashSet<String>()

        for (categoryIndex in 0 until categories.length()) {
            val category = categories.optJSONObject(categoryIndex)
                ?: throw IOException("Invalid taxonomy category at index $categoryIndex.")
            val categoryId = category.stringOrEmpty("id")
            if (categoryId.isBlank() || !categoryIds.add(categoryId)) {
                throw IOException("Missing or duplicate taxonomy category ID: $categoryId.")
            }
            val names = category.optJSONObject("names")
                ?: throw IOException("Category $categoryId has no localized labels.")
            categoryEntities += FoodCategoryEntity(
                id = categoryId,
                nameAr = names.requiredName("ar", categoryId),
                nameEn = names.requiredName("en", categoryId),
                nameFr = names.requiredName("fr", categoryId),
                sortOrder = categoryIndex
            )

            val children = category.optJSONArray("subcategories")
                ?: throw IOException("Category $categoryId has no subcategories.")
            for (childIndex in 0 until children.length()) {
                val child = children.optJSONObject(childIndex)
                    ?: throw IOException("Invalid subcategory for $categoryId.")
                val childId = child.stringOrEmpty("id")
                if (childId.isBlank() || !subcategoryIds.add(childId)) {
                    throw IOException("Missing or duplicate taxonomy subcategory ID: $childId.")
                }
                val childNames = child.optJSONObject("names")
                    ?: throw IOException("Subcategory $childId has no localized labels.")
                subcategoryEntities += FoodSubcategoryEntity(
                    id = childId,
                    categoryId = categoryId,
                    nameAr = childNames.requiredName("ar", childId),
                    nameEn = childNames.requiredName("en", childId),
                    nameFr = childNames.requiredName("fr", childId),
                    sortOrder = childIndex
                )
                parents[childId] = categoryId
            }
        }

        if (subcategoryEntities.size != EXPECTED_SUBCATEGORY_COUNT) {
            throw IOException("Canonical taxonomy must contain $EXPECTED_SUBCATEGORY_COUNT subcategories.")
        }
        return TaxonomySnapshot(categoryEntities, subcategoryEntities, parents)
    }

    private fun JSONObject.requiredName(locale: String, owner: String): String =
        stringOrEmpty(locale).takeIf { it.isNotBlank() }
            ?: throw IOException("Missing $locale name for taxonomy record $owner.")

    private fun JSONObject.stringOrEmpty(key: String): String =
        if (isNull(key)) "" else optString(key).takeUnless { it == "null" }.orEmpty().trim()

    private fun JSONArray?.toStringValues(): List<String> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank() && value != "null") add(value)
            }
        }
    }

    private data class TaxonomySnapshot(
        val categories: List<FoodCategoryEntity>,
        val subcategories: List<FoodSubcategoryEntity>,
        val subcategoryParents: Map<String, String>
    )

    companion object {
        const val EXPECTED_ITEM_COUNT = 4_649
        const val EXPECTED_CATEGORY_COUNT = 22
        const val EXPECTED_SUBCATEGORY_COUNT = 82
        val REVISION: String get() = BuildConfig.FOOD_CATALOGUE_REVISION
        private const val PREFERENCES_NAME = "generic_food_catalogue"
        private const val INSTALLED_REVISION_KEY = "installed_revision"
        private const val CATALOGUE_ASSET = "food/catalogue.json"
        private const val CATEGORIES_ASSET = "food/categories.json"
        private const val BATCH_SIZE = 200
        private val UUID_PATTERN = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        )
    }
}
