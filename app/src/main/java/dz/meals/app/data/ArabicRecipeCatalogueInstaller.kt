package dz.meals.app.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import androidx.room.withTransaction
import dz.meals.app.data.local.IngredientEntity
import dz.meals.app.data.local.MealsDatabase
import dz.meals.app.data.local.RecipeAttributionEntity
import dz.meals.app.data.local.RecipeEntity
import dz.meals.app.data.local.RecipeIngredientEntity
import dz.meals.app.data.local.RecipeStepEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.IOException
import java.security.MessageDigest

/**
 * Streams the downloaded JSON into Room in small transactions.
 * Batch writes are repeatable, so an interrupted first import can safely retry.
 */
class ArabicRecipeCatalogueInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val database = MealsDatabase.getInstance(appContext)
    private val dao = database.mealsDao()
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun isInstalled(): Boolean = withContext(Dispatchers.IO) {
        preferences.getString(INSTALLED_REVISION_KEY, null) == ArabicRecipeCatalogueDownloader.REVISION &&
            dao.countArabicCatalogueRecipes() == ArabicRecipeCatalogueDownloader.EXPECTED_RECIPE_COUNT
    }

    suspend fun install(
        file: File,
        onProgress: (completedRecipes: Int, totalRecipes: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() <= 0L) {
            throw IOException("ملف مكتبة الوصفات غير موجود أو فارغ.")
        }

        val batch = ArrayList<RecipeBundle>(BATCH_SIZE)
        var rootComplete = false
        var translatedCount = 0
        var expectedCount = 0
        var importedCount = 0
        var recipesArraySeen = false
        val uniqueIds = HashSet<String>()

        suspend fun flushBatch() {
            if (batch.isEmpty()) return
            val recipeIds = batch.map { it.recipe.id }
            database.withTransaction {
                dao.deleteRecipeIngredientsForRecipes(recipeIds)
                dao.deleteRecipeStepsForRecipes(recipeIds)
                dao.upsertCatalogueIngredients(batch.flatMap { it.ingredients }.distinctBy { it.id })
                dao.upsertCatalogueRecipes(batch.map { it.recipe })
                dao.upsertCatalogueRecipeIngredients(batch.flatMap { it.recipeIngredients })
                dao.upsertCatalogueRecipeSteps(batch.flatMap { it.steps })
                dao.upsertRecipeAttributions(batch.map { it.attribution })
            }
            batch.clear()
            withContext(Dispatchers.Main.immediate) {
                onProgress(importedCount, expectedCount)
            }
        }

        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { buffered ->
            JsonReader(buffered).use { reader ->
                reader.isLenient = false
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "complete" -> rootComplete = reader.nextBoolean()
                        "translatedCount" -> translatedCount = readInt(reader)
                        "expectedCount" -> expectedCount = readInt(reader)
                        "recipes" -> {
                            recipesArraySeen = true
                            if (!rootComplete ||
                                expectedCount != ArabicRecipeCatalogueDownloader.EXPECTED_RECIPE_COUNT ||
                                translatedCount != expectedCount
                            ) {
                                throw IOException("مكتبة الوصفات غير مكتملة؛ لن نثبت نسخة جزئية.")
                            }

                            reader.beginArray()
                            while (reader.hasNext()) {
                                val source = readRecipe(reader)
                                if (source.id.isBlank() || !uniqueIds.add(source.id)) {
                                    throw IOException("تحتوي مكتبة الوصفات على معرّف مفقود أو مكرر.")
                                }
                                batch += source.toBundle()
                                importedCount++
                                if (batch.size >= BATCH_SIZE) flushBatch()
                            }
                            reader.endArray()
                            flushBatch()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }

        if (!recipesArraySeen || importedCount != expectedCount ||
            importedCount != ArabicRecipeCatalogueDownloader.EXPECTED_RECIPE_COUNT
        ) {
            throw IOException("عدد الوصفات داخل الملف لا يطابق بيانات النسخة المتوقعة.")
        }

        preferences.edit()
            .putString(INSTALLED_REVISION_KEY, ArabicRecipeCatalogueDownloader.REVISION)
            .apply()
        withContext(Dispatchers.Main.immediate) {
            onProgress(importedCount, expectedCount)
        }
        importedCount
    }

    private fun readRecipe(reader: JsonReader): SourceRecipe {
        var id = ""
        var name = ""
        var summary = ""
        var category = ""
        var cuisine = ""
        var servings: Int? = null
        var prepMinutes: Int? = null
        var cookMinutes: Int? = null
        var allergens = emptyList<String>()
        var tags = emptyList<String>()
        var ingredients = emptyList<SourceIngredient>()
        var steps = emptyList<String>()
        var provenance = SourceProvenance()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = readString(reader)
                "name" -> name = readString(reader)
                "summary" -> summary = readString(reader)
                "category" -> category = readString(reader)
                "cuisine" -> cuisine = readString(reader)
                "servings" -> servings = readNullableInt(reader)
                "prepMinutes" -> prepMinutes = readNullableInt(reader)
                "cookMinutes" -> cookMinutes = readNullableInt(reader)
                "allergens" -> allergens = readStringArray(reader)
                "tags" -> tags = readStringArray(reader)
                "ingredients" -> ingredients = readIngredients(reader)
                "steps" -> steps = readStringArray(reader)
                "provenance" -> provenance = readProvenance(reader)
                "sourceUrl" -> provenance = provenance.copy(sourceUrl = readString(reader))
                "sourceRevisionUrl" -> provenance = provenance.copy(revisionUrl = readString(reader))
                "sourceHistoryUrl" -> provenance = provenance.copy(historyUrl = readString(reader))
                "sourceCredit" -> provenance = provenance.copy(credit = readString(reader))
                "license" -> provenance = provenance.copy(license = readString(reader))
                "licenseUrl" -> provenance = provenance.copy(licenseUrl = readString(reader))
                "sourceLicense" -> provenance = provenance.copy(sourceLicense = readString(reader))
                "changes" -> provenance = provenance.copy(changes = readString(reader))
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (name.isBlank() || steps.isEmpty()) {
            throw IOException("إحدى الوصفات تفتقد العنوان أو خطوات التحضير.")
        }

        return SourceRecipe(
            id = id,
            name = name,
            summary = summary,
            category = category.ifBlank { "تقليدي" },
            cuisine = cuisine,
            servings = servings,
            prepMinutes = prepMinutes,
            cookMinutes = cookMinutes,
            allergens = allergens,
            tags = tags,
            ingredients = ingredients,
            steps = steps,
            provenance = provenance
        )
    }

    private fun readIngredients(reader: JsonReader): List<SourceIngredient> {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        val result = ArrayList<SourceIngredient>()
        reader.beginArray()
        while (reader.hasNext()) {
            var name = ""
            var note = ""
            var quantity = ""
            var unit = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "name" -> name = readString(reader)
                    "note" -> note = readString(reader)
                    "quantity" -> quantity = readString(reader)
                    "unit" -> unit = readString(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (name.isNotBlank()) result += SourceIngredient(name, note, quantity, unit)
        }
        reader.endArray()
        return result
    }

    private fun readProvenance(reader: JsonReader): SourceProvenance {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return SourceProvenance()
        }
        var sourceTitle = ""
        var sourceUrl = ""
        var revisionUrl = ""
        var historyUrl = ""
        var credit = ""
        var sourceLicense = ""
        var license = ""
        var licenseUrl = ""
        var changes = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "title" -> sourceTitle = readString(reader)
                "url" -> sourceUrl = readString(reader)
                "revision_url" -> revisionUrl = readString(reader)
                "history_url" -> historyUrl = readString(reader)
                "credit", "attribution" -> credit = readString(reader)
                "source_license" -> sourceLicense = readString(reader)
                "license" -> license = readString(reader)
                "license_url" -> licenseUrl = readString(reader)
                "changes" -> changes = readString(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return SourceProvenance(sourceTitle, sourceUrl, revisionUrl, historyUrl, credit, sourceLicense, license, licenseUrl, changes)
    }

    private fun readStringArray(reader: JsonReader): List<String> {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        val result = ArrayList<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            val value = readString(reader)
            if (value.isNotBlank()) result += value
        }
        reader.endArray()
        return result
    }

    private fun readString(reader: JsonReader): String = when (reader.peek()) {
        JsonToken.NULL -> {
            reader.nextNull()
            ""
        }
        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
        JsonToken.BOOLEAN -> reader.nextBoolean().toString()
        else -> {
            reader.skipValue()
            ""
        }
    }

    private fun readInt(reader: JsonReader): Int = readString(reader).toIntOrNull() ?: 0

    private fun readNullableInt(reader: JsonReader): Int? =
        readString(reader).toIntOrNull()?.takeIf { it > 0 }

    private fun SourceRecipe.toBundle(): RecipeBundle {
        val recipeId = RECIPE_ID_PREFIX + id
        val ingredientEntities = ArrayList<IngredientEntity>()
        val recipeIngredients = ArrayList<RecipeIngredientEntity>()

        ingredients.forEach { source ->
            val measure = normalizeMeasure(source.quantity, source.unit)
            val ingredientId = ingredientId(source.name, measure.unit)
            ingredientEntities += IngredientEntity(
                id = ingredientId,
                nameAr = source.name,
                nameFr = source.name,
                category = "مكوّن",
                baseUnit = measure.unit,
                allergensCsv = "",
                aliasesCsv = ""
            )

            val preparationNote = buildList {
                if (measure.quantityBase == null &&
                    source.quantity.isNotBlank() &&
                    measure.unit != "حسب الحاجة"
                ) add(source.quantity)
                if (measure.note.isNotBlank()) add(measure.note)
                if (source.note.isNotBlank()) add(source.note)
            }.joinToString(" · ")

            recipeIngredients += RecipeIngredientEntity(
                recipeId = recipeId,
                ingredientId = ingredientId,
                quantityBase = measure.quantityBase ?: 0.0,
                unit = measure.unit,
                prepNote = preparationNote,
                optional = false
            )
        }

        val recipe = RecipeEntity(
            id = recipeId,
            nameAr = name,
            nameFr = name,
            descriptionAr = summary,
            descriptionFr = summary,
            category = category.trim().ifBlank { "تقليدي" },
            region = cuisine,
            durationMinutes = ((prepMinutes ?: 0) + (cookMinutes ?: 0)).coerceAtLeast(0),
            baseServings = (servings ?: 2).coerceIn(1, 30),
            difficulty = "غير محدد",
            allergensCsv = normalizeAllergens(allergens).distinct().joinToString(","),
            tagsCsv = tags.distinct().joinToString("|")
        )
        val recipeSteps = steps.mapIndexed { index, step ->
            RecipeStepEntity(recipeId, index + 1, step, step)
        }
        val attribution = RecipeAttributionEntity(
            recipeId = recipeId,
            sourceTitle = provenance.sourceTitle.ifBlank { "Open Recipe Corpus" },
            sourceUrl = provenance.sourceUrl,
            revisionUrl = provenance.revisionUrl,
            historyUrl = provenance.historyUrl,
            credit = provenance.credit.ifBlank { "Open Recipe Corpus contributors" },
            sourceLicense = provenance.sourceLicense,
            license = provenance.license.ifBlank { "CC BY-SA 4.0" },
            licenseUrl = provenance.licenseUrl.ifBlank { DEFAULT_LICENSE_URL },
            changes = provenance.changes.ifBlank { "Machine-translated from Polish into Arabic." }
        )
        return RecipeBundle(recipe, ingredientEntities, recipeIngredients, recipeSteps, attribution)
    }

    private fun normalizeMeasure(quantity: String, sourceUnit: String): NormalizedMeasure {
        val amount = parseQuantity(quantity)
        return when (sourceUnit.trim().lowercase()) {
            "kg" -> NormalizedMeasure(amount?.times(1000.0), "g")
            "l" -> NormalizedMeasure(amount?.times(1000.0), "ml")
            "pcs", "pc" -> NormalizedMeasure(amount, "piece")
            "g" -> NormalizedMeasure(amount, "g")
            "ml" -> NormalizedMeasure(amount, "ml")
            "tbsp" -> NormalizedMeasure(amount, "ملعقة كبيرة")
            "tsp" -> NormalizedMeasure(amount, "ملعقة صغيرة")
            "to_taste" -> NormalizedMeasure(null, "حسب الحاجة")
            "pinch" -> NormalizedMeasure(amount, "رشة")
            "clove" -> NormalizedMeasure(amount, "فص")
            "bunch" -> NormalizedMeasure(amount, "ربطة")
            "slice" -> NormalizedMeasure(amount, "شريحة")
            "" -> NormalizedMeasure(
                amount,
                if (quantity.isBlank()) "حسب الحاجة" else "piece"
            )
            else -> NormalizedMeasure(amount, sourceUnit.trim())
        }
    }

    private fun normalizeAllergens(values: List<String>): List<String> =
        values.map { value ->
            when (value.trim().lowercase()) {
                "لبن", "الحليب", "حليب" -> "milk"
                "الغولتين", "الغلوتين" -> "gluten"
                "بيضة", "بيض", "البيض" -> "egg"
                "المكسرات", "مكسرات" -> "tree_nuts"
                "كرفس", "الكرفس" -> "celery"
                "الكبريتيت", "كبريتيت" -> "sulphites"
                "سمكة", "السمك", "سمك" -> "fish"
                "الخردل", "خردل" -> "mustard"
                "القشريات", "قشريات" -> "crustaceans"
                "فول الصويا", "الصويا" -> "soy"
                "سمسم", "السمسم" -> "sesame"
                "الرخويات", "رخويات" -> "molluscs"
                "الفول السوداني", "فول سوداني" -> "peanuts"
                else -> value.trim()
            }
        }.filter { it.isNotBlank() }

    private fun ingredientId(name: String, unit: String): String {
        val normalized = name.trim().lowercase().replace(Regex("\\s+"), " ") +
            "|" + unit.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        val suffix = digest.take(10).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return INGREDIENT_ID_PREFIX + suffix
    }

    private fun parseQuantity(raw: String): Double? {
        val value = raw.trim().replace(',', '.')
        if (value.isBlank()) return null
        value.toDoubleOrNull()?.let { return it.takeIf { number -> number > 0.0 } }
        val fraction = Regex("""^(\d+)\s*/\s*(\d+)$""").matchEntire(value) ?: return null
        val numerator = fraction.groupValues[1].toDoubleOrNull() ?: return null
        val denominator = fraction.groupValues[2].toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        return (numerator / denominator).takeIf { it > 0.0 }
    }

    private data class NormalizedMeasure(
        val quantityBase: Double?,
        val unit: String,
        val note: String = ""
    )

    private data class RecipeBundle(
        val recipe: RecipeEntity,
        val ingredients: List<IngredientEntity>,
        val recipeIngredients: List<RecipeIngredientEntity>,
        val steps: List<RecipeStepEntity>,
        val attribution: RecipeAttributionEntity
    )

    private data class SourceRecipe(
        val id: String,
        val name: String,
        val summary: String,
        val category: String,
        val cuisine: String,
        val servings: Int?,
        val prepMinutes: Int?,
        val cookMinutes: Int?,
        val allergens: List<String>,
        val tags: List<String>,
        val ingredients: List<SourceIngredient>,
        val steps: List<String>,
        val provenance: SourceProvenance
    )

    private data class SourceIngredient(
        val name: String,
        val note: String,
        val quantity: String,
        val unit: String
    )

    private data class SourceProvenance(
        val sourceTitle: String = "",
        val sourceUrl: String = "",
        val revisionUrl: String = "",
        val historyUrl: String = "",
        val credit: String = "",
        val sourceLicense: String = "",
        val license: String = "",
        val licenseUrl: String = "",
        val changes: String = ""
    )

    companion object {
        const val RECIPE_ID_PREFIX = "orc-"
        const val INGREDIENT_ID_PREFIX = "orc-ing-"
        private const val DEFAULT_LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
        private const val BATCH_SIZE = 80
        private const val PREFERENCES_NAME = "arabic_recipe_catalogue"
        private const val INSTALLED_REVISION_KEY = "installed_revision"
    }
}
