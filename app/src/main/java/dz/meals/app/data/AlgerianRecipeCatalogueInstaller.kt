package dz.meals.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import dz.meals.app.data.local.IngredientEntity
import dz.meals.app.data.local.MealsDatabase
import dz.meals.app.data.local.RecipeAttributionEntity
import dz.meals.app.data.local.RecipeEntity
import dz.meals.app.data.local.RecipeIngredientEntity
import dz.meals.app.data.local.RecipeStepEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Installs the separately curated Algerian recipe collection into the existing
 * Room v17 recipe tables. IDs are namespaced so this import never replaces the
 * built-in, Focus, UniTools, or general open-recipe catalogue rows.
 *
 * The payload is bundled with the app, so first installation works offline.
 * All table writes happen in one Room transaction; the completion revision is
 * recorded only after that transaction succeeds.
 */
class AlgerianRecipeCatalogueInstaller internal constructor(
    context: Context,
    databaseOverride: MealsDatabase? = null,
    preferencesOverride: SharedPreferences? = null
) {
    private val appContext = context.applicationContext
    private val database = databaseOverride ?: MealsDatabase.getInstance(appContext)
    private val dao = database.mealsDao()
    private val preferences = preferencesOverride
        ?: appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun isInstalled(): Boolean = withContext(Dispatchers.IO) {
        preferences.getString(INSTALLED_REVISION_KEY, null) == REVISION &&
            dao.countAlgerianCatalogueRecipes() == EXPECTED_RECIPE_COUNT
    }

    suspend fun install(
        onProgress: (completedRecipes: Int, totalRecipes: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        if (isInstalled()) return@withContext 0

        val root = readPayload()
        validatePayloadHeader(root)
        val entities = root.optJSONObject("entities")
            ?: throw IOException("ملف الوصفات الجزائرية لا يحتوي على جداول Room.")

        val ingredients = parseIngredients(entities.requiredArray("ingredients"))
        val recipes = parseRecipes(entities.requiredArray("recipes"))
        val recipeIngredients = parseRecipeIngredients(entities.requiredArray("recipe_ingredients"))
        val steps = parseSteps(entities.requiredArray("recipe_steps"))
        val attributions = parseAttributions(entities.requiredArray("recipe_attributions"))

        validateCounts(root, ingredients, recipes, recipeIngredients, steps, attributions)
        validateRelationships(ingredients, recipes, recipeIngredients, steps, attributions)

        val validCategoryPairs = dao.getFoodSubcategories()
            .map { it.categoryId to it.id }
            .toSet()
        val invalidIngredient = ingredients.firstOrNull {
            (it.categoryId to it.subcategoryId) !in validCategoryPairs
        }
        if (invalidIngredient != null) {
            throw IOException(
                "المكوّن \x24{invalidIngredient.id} يشير إلى تصنيف غير مثبت في Midati."
            )
        }

        val recipeIds = recipes.map { it.id }
        database.withTransaction {
            // Remove old children for only this collection before the upsert.
            // This makes future catalogue revisions safe when a recipe loses a row.
            dao.deleteRecipeIngredientsForRecipes(recipeIds)
            dao.deleteRecipeStepsForRecipes(recipeIds)

            ingredients.chunked(INSERT_BATCH_SIZE).forEach {
                dao.upsertCatalogueIngredients(it)
            }

            var imported = 0
            recipes.chunked(RECIPE_BATCH_SIZE).forEach { batch ->
                dao.upsertCatalogueRecipes(batch)
                imported += batch.size
                withContext(Dispatchers.Main.immediate) {
                    onProgress(imported, recipes.size)
                }
            }

            recipeIngredients.chunked(INSERT_BATCH_SIZE).forEach {
                dao.upsertCatalogueRecipeIngredients(it)
            }
            steps.chunked(INSERT_BATCH_SIZE).forEach {
                dao.upsertCatalogueRecipeSteps(it)
            }
            attributions.chunked(INSERT_BATCH_SIZE).forEach {
                dao.upsertRecipeAttributions(it)
            }

            val installedCount = dao.countAlgerianCatalogueRecipes()
            if (installedCount != EXPECTED_RECIPE_COUNT) {
                throw IOException(
                    "لم يكتمل تثبيت الوصفات الجزائرية: $installedCount من $EXPECTED_RECIPE_COUNT."
                )
            }
        }

        if (!preferences.edit().putString(INSTALLED_REVISION_KEY, REVISION).commit()) {
            throw IOException("تعذر حفظ إصدار مكتبة الوصفات الجزائرية المثبت.")
        }

        withContext(Dispatchers.Main.immediate) {
            onProgress(EXPECTED_RECIPE_COUNT, EXPECTED_RECIPE_COUNT)
        }
        EXPECTED_RECIPE_COUNT
    }

    private fun readPayload(): JSONObject = try {
        appContext.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText())
        }
    } catch (error: Exception) {
        throw IOException("تعذر قراءة ملف الوصفات الجزائرية المضمّن: $ASSET_PATH", error)
    }

    private fun validatePayloadHeader(root: JSONObject) {
        if (root.optString("format") != FORMAT ||
            root.optInt("formatVersion", -1) != FORMAT_VERSION
        ) {
            throw IOException("تنسيق ملف الوصفات الجزائرية غير مدعوم.")
        }

        val target = root.optJSONObject("target")
            ?: throw IOException("ملف الوصفات الجزائرية لا يحدد قاعدة البيانات المستهدفة.")
        if (target.optString("repository") != TARGET_REPOSITORY ||
            target.optInt("roomSchemaVersion", -1) != ROOM_SCHEMA_VERSION ||
            target.optString("collectionId") != COLLECTION_ID ||
            target.optString("recipeIdPrefix") != RECIPE_ID_PREFIX
        ) {
            throw IOException("ملف الوصفات الجزائرية لا يطابق مخطط Midati الحالي.")
        }

        val source = root.optJSONObject("source")
            ?: throw IOException("بيانات مصدر مكتبة الوصفات الجزائرية مفقودة.")
        if (source.optInt("recipeCount", -1) != EXPECTED_RECIPE_COUNT ||
            source.optString("sourceSha256") != REVISION
        ) {
            throw IOException("إصدار ملف الوصفات الجزائرية غير مطابق للنسخة المعتمدة.")
        }
    }

    private fun validateCounts(
        root: JSONObject,
        ingredients: List<IngredientEntity>,
        recipes: List<RecipeEntity>,
        recipeIngredients: List<RecipeIngredientEntity>,
        steps: List<RecipeStepEntity>,
        attributions: List<RecipeAttributionEntity>
    ) {
        val counts = root.optJSONObject("counts")
            ?: throw IOException("أعداد سجلات مكتبة الوصفات الجزائرية مفقودة.")
        val actual = mapOf(
            "recipes" to recipes.size,
            "ingredients" to ingredients.size,
            "recipe_ingredients" to recipeIngredients.size,
            "recipe_steps" to steps.size,
            "recipe_attributions" to attributions.size
        )
        actual.forEach { (key, count) ->
            if (counts.optInt(key, -1) != count) {
                throw IOException("عدد سجلات $key لا يطابق البيانات داخل الملف.")
            }
        }
        if (recipes.size != EXPECTED_RECIPE_COUNT) {
            throw IOException("عدد الوصفات الجزائرية يجب أن يساوي $EXPECTED_RECIPE_COUNT.")
        }
    }

    private fun validateRelationships(
        ingredients: List<IngredientEntity>,
        recipes: List<RecipeEntity>,
        recipeIngredients: List<RecipeIngredientEntity>,
        steps: List<RecipeStepEntity>,
        attributions: List<RecipeAttributionEntity>
    ) {
        val recipeIds = recipes.map { it.id }.toSet()
        val ingredientIds = ingredients.map { it.id }.toSet()

        if (recipeIds.size != recipes.size || recipes.any {
                !it.id.startsWith(RECIPE_ID_PREFIX) || it.nameAr.isBlank()
            }
        ) throw IOException("تحتوي مكتبة الوصفات الجزائرية على معرّفات أو أسماء غير صالحة.")

        if (ingredientIds.size != ingredients.size || ingredients.any {
                it.id.isBlank() || it.nameAr.isBlank() || it.baseUnit.isBlank()
            }
        ) throw IOException("تحتوي مكتبة الوصفات الجزائرية على مكوّنات غير صالحة.")

        if (recipeIngredients.any {
                it.recipeId !in recipeIds || it.ingredientId !in ingredientIds ||
                    it.unit.isBlank() || it.rowKey.isBlank()
            }
        ) throw IOException("توجد علاقة مكوّن بوصفة أو هوية مكوّن غير موجودة.")

        if (steps.any { it.recipeId !in recipeIds || it.stepNumber <= 0 || it.instructionAr.isBlank() }) {
            throw IOException("توجد خطوة تحضير مرتبطة بوصفة غير موجودة أو بلا تعليمات.")
        }

        if (attributions.size != recipes.size || attributions.any { it.recipeId !in recipeIds }) {
            throw IOException("بيانات مصدر الوصفات غير مكتملة أو مرتبطة بمعرّفات غير موجودة.")
        }
    }

    private fun parseRecipes(array: JSONArray): List<RecipeEntity> = array.objects().map { row ->
        RecipeEntity(
            id = row.requiredText("id"),
            nameAr = row.requiredText("nameAr"),
            nameFr = row.textOrEmpty("nameFr"),
            descriptionAr = row.textOrEmpty("descriptionAr"),
            descriptionFr = row.textOrEmpty("descriptionFr"),
            category = row.requiredText("category"),
            region = row.textOrEmpty("region"),
            durationMinutes = row.intOrDefault("durationMinutes", 0).coerceAtLeast(0),
            baseServings = row.intOrDefault("baseServings", 2).coerceIn(1, 30),
            difficulty = row.textOrEmpty("difficulty").ifBlank { "غير محدد" },
            allergensCsv = row.textOrEmpty("allergensCsv"),
            tagsCsv = row.textOrEmpty("tagsCsv"),
            categoriesCsv = row.textOrEmpty("categoriesCsv"),
            dietaryCsv = row.textOrEmpty("dietaryCsv"),
            caloriesPerServing = row.nullableDouble("caloriesPerServing"),
            proteinGramsPerServing = row.nullableDouble("proteinGramsPerServing"),
            carbsGramsPerServing = row.nullableDouble("carbsGramsPerServing"),
            fatGramsPerServing = row.nullableDouble("fatGramsPerServing"),
            imageUrl = row.textOrEmpty("imageUrl"),
            prepMinutes = row.nullableInt("prepMinutes"),
            cookMinutes = row.nullableInt("cookMinutes"),
            nativeName = row.textOrEmpty("nativeName"),
            countryCode = row.textOrEmpty("countryCode"),
            imageAuthor = row.textOrEmpty("imageAuthor"),
            imageLicense = row.textOrEmpty("imageLicense")
        )
    }

    private fun parseIngredients(array: JSONArray): List<IngredientEntity> = array.objects().map { row ->
        IngredientEntity(
            id = row.requiredText("id"),
            nameAr = row.requiredText("nameAr"),
            nameFr = row.textOrEmpty("nameFr"),
            categoryId = row.requiredText("categoryId"),
            subcategoryId = row.requiredText("subcategoryId"),
            baseUnit = row.requiredText("baseUnit"),
            allergensCsv = row.textOrEmpty("allergensCsv"),
            aliasesCsv = row.textOrEmpty("aliasesCsv")
        )
    }

    private fun parseRecipeIngredients(array: JSONArray): List<RecipeIngredientEntity> =
        array.objects().map { row ->
            RecipeIngredientEntity(
                recipeId = row.requiredText("recipeId"),
                ingredientId = row.requiredText("ingredientId"),
                quantityBase = row.doubleOrDefault("quantityBase", 0.0),
                unit = row.requiredText("unit"),
                prepNote = row.textOrEmpty("prepNote"),
                optional = row.optBoolean("optional", false),
                scalingRule = row.textOrEmpty("scalingRule").ifBlank { "linear" },
                rowKey = row.requiredText("rowKey")
            )
        }

    private fun parseSteps(array: JSONArray): List<RecipeStepEntity> = array.objects().map { row ->
        RecipeStepEntity(
            recipeId = row.requiredText("recipeId"),
            stepNumber = row.intOrDefault("stepNumber", 0),
            instructionAr = row.requiredText("instructionAr"),
            instructionFr = row.textOrEmpty("instructionFr"),
            durationMinutes = row.nullableInt("durationMinutes")
        )
    }

    private fun parseAttributions(array: JSONArray): List<RecipeAttributionEntity> =
        array.objects().map { row ->
            RecipeAttributionEntity(
                recipeId = row.requiredText("recipeId"),
                sourceTitle = row.textOrEmpty("sourceTitle"),
                sourceUrl = row.textOrEmpty("sourceUrl"),
                revisionUrl = row.textOrEmpty("revisionUrl"),
                historyUrl = row.textOrEmpty("historyUrl"),
                credit = row.textOrEmpty("credit"),
                sourceLicense = row.textOrEmpty("sourceLicense"),
                license = row.textOrEmpty("license"),
                licenseUrl = row.textOrEmpty("licenseUrl"),
                changes = row.textOrEmpty("changes")
            )
        }

    private fun JSONArray.objects(): List<JSONObject> = buildList(length()) {
        for (index in 0 until length()) {
            add(optJSONObject(index) ?: throw IOException("سجل غير صالح داخل ملف الوصفات الجزائرية."))
        }
    }

    private fun JSONObject.requiredArray(key: String): JSONArray =
        optJSONArray(key) ?: throw IOException("حقل $key مفقود من ملف الوصفات الجزائرية.")

    private fun JSONObject.requiredText(key: String): String =
        textOrEmpty(key).takeIf { it.isNotBlank() }
            ?: throw IOException("حقل $key مطلوب في ملف الوصفات الجزائرية.")

    private fun JSONObject.textOrEmpty(key: String): String {
        val value = opt(key)
        return when (value) {
            null, JSONObject.NULL -> ""
            is String -> value
            is Number, is Boolean -> value.toString()
            else -> value.toString()
        }
    }

    private fun JSONObject.intOrDefault(key: String, defaultValue: Int): Int {
        val value = opt(key) ?: return defaultValue
        if (value === JSONObject.NULL) return defaultValue
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: throw IOException("قيمة عددية غير صالحة للحقل $key.")
            else -> throw IOException("قيمة عددية غير صالحة للحقل $key.")
        }
    }

    private fun JSONObject.nullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return intOrDefault(key, 0)
    }

    private fun JSONObject.doubleOrDefault(key: String, defaultValue: Double): Double {
        val value = opt(key) ?: return defaultValue
        if (value === JSONObject.NULL) return defaultValue
        val number = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        } ?: throw IOException("قيمة كمية غير صالحة للحقل $key.")
        if (!number.isFinite()) throw IOException("قيمة كمية غير صالحة للحقل $key.")
        return number
    }

    private fun JSONObject.nullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return doubleOrDefault(key, 0.0)
    }

    companion object {
        const val ASSET_PATH = "recipes/algerian_room_v17.json"
        const val COLLECTION_ID = "dz-algerian-dzirielle"
        const val RECIPE_ID_PREFIX = "dz-algerian-dzirielle-"
        const val EXPECTED_RECIPE_COUNT = 187
        const val ROOM_SCHEMA_VERSION = 17
        const val REVISION = "99fca3fd4df7ff061d37779ecbb79210a980320da0f17d84cd6e0577b73c6dad"

        private const val FORMAT = "midati-room-entity-import"
        private const val FORMAT_VERSION = 1
        private const val TARGET_REPOSITORY = "ni3yyn/Meals"
        private const val INSERT_BATCH_SIZE = 200
        private const val RECIPE_BATCH_SIZE = 40
        private const val PREFERENCES_NAME = "algerian_recipe_catalogue"
        private const val INSTALLED_REVISION_KEY = "installed_revision"
    }
}
