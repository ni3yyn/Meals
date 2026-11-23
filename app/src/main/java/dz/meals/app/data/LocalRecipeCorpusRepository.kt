package dz.meals.app.data

import android.content.Context
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class LocalCorpusIngredient(
    val name: String,
    val note: String,
    val quantity: String?,
    val unit: String?
)

data class LocalCorpusProvenance(
    val source: String,
    val title: String,
    val url: String,
    val revisionUrl: String,
    val historyUrl: String,
    val credit: String,
    val sourceLicense: String,
    val license: String,
    val licenseUrl: String,
    val changes: String
)

data class LocalCorpusRecipe(
    val key: String,
    val id: String,
    val language: String,
    val name: String,
    val summary: String,
    val category: String,
    val cuisine: String,
    val servings: Int?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val ingredients: List<LocalCorpusIngredient>,
    val steps: List<String>,
    val tags: List<String>,
    val allergens: List<String>,
    val sourceLanguage: String,
    val machineTranslated: Boolean,
    val translatedFrom: String?,
    val provenance: LocalCorpusProvenance
)

data class LocalCorpusIngredientTranslation(
    val name: String,
    val note: String
)

data class LocalCorpusTranslation(
    val name: String,
    val summary: String,
    val ingredients: List<LocalCorpusIngredientTranslation>,
    val steps: List<String>
)

/**
 * Debug-only corpus adapter. Source files are intentionally kept outside Git
 * and copied into debug APK assets by the local Gradle task.
 *
 * The MyMemory endpoint is a temporary, keyless test provider. Do not use this
 * direct-from-device integration in production; move it behind a server-side
 * adapter before release.
 */
class LocalRecipeCorpusRepository(context: Context) {
    private val appContext = context.applicationContext
    private val translationCacheDir = File(appContext.filesDir, "recipe-corpus-translations")
    private val httpClient = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun loadRecipes(): List<LocalCorpusRecipe> = withContext(Dispatchers.IO) {
        val metadataById = readJsonLines("data/recipes.jsonl").associateBy { it.text("id") }
        if (metadataById.isEmpty()) {
            throw IOException("ملف بيانات الوصفات فارغ أو غير صالح.")
        }

        buildList {
            listOf("en", "pl").forEach { language ->
                readJsonLines("data/text/" + language + ".jsonl").forEach { textRecord ->
                    val id = textRecord.text("id")
                    val metadata = metadataById[id]
                    if (metadata != null) {
                        add(mergeRecipe(metadata, textRecord, language))
                    }
                }
            }
        }.sortedWith(compareBy<LocalCorpusRecipe> { it.language }.thenBy { it.name.lowercase() })
    }

    fun hasCachedTranslation(recipe: LocalCorpusRecipe): Boolean =
        cacheFile(recipe).isFile

    suspend fun loadCachedTranslation(recipe: LocalCorpusRecipe): LocalCorpusTranslation? =
        withContext(Dispatchers.IO) {
            val file = cacheFile(recipe)
            if (!file.isFile) return@withContext null
            runCatching { parseTranslation(JSONObject(file.readText(Charsets.UTF_8))) }.getOrNull()
        }

    suspend fun translateAndCache(recipe: LocalCorpusRecipe): LocalCorpusTranslation =
        withContext(Dispatchers.IO) {
            loadCachedTranslation(recipe)?.let { return@withContext it }

            val translated = LocalCorpusTranslation(
                name = translateText(recipe.name, recipe.language),
                summary = translateText(recipe.summary, recipe.language),
                ingredients = recipe.ingredients.map { ingredient ->
                    LocalCorpusIngredientTranslation(
                        name = translateText(ingredient.name, recipe.language),
                        note = translateText(ingredient.note, recipe.language)
                    )
                },
                steps = recipe.steps.map { step -> translateText(step, recipe.language) }
            )

            val output = JSONObject()
                .put("schemaVersion", 1)
                .put("provider", "MyMemory")
                .put("sourceLanguage", recipe.language)
                .put("targetLanguage", "ar")
                .put("name", translated.name)
                .put("summary", translated.summary)
                .put("ingredients", JSONArray().apply {
                    translated.ingredients.forEach { item ->
                        put(JSONObject().put("name", item.name).put("note", item.note))
                    }
                })
                .put("steps", JSONArray(translated.steps))

            translationCacheDir.mkdirs()
            val file = cacheFile(recipe)
            val temporaryFile = File(translationCacheDir, file.name + ".tmp")
            temporaryFile.writeText(output.toString(), Charsets.UTF_8)
            if (file.exists() && !file.delete()) {
                temporaryFile.delete()
                throw IOException("تعذر استبدال الترجمة المحلية القديمة.")
            }
            if (!temporaryFile.renameTo(file)) {
                temporaryFile.copyTo(file, overwrite = true)
                temporaryFile.delete()
            }
            translated
        }

    private fun readJsonLines(assetPath: String): List<JSONObject> {
        return try {
            appContext.assets.open(assetPath).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() }.map { line -> JSONObject(line) }.toList()
            }
        } catch (error: Exception) {
            throw IOException(
                "بيانات الوصفات غير موجودة داخل نسخة Debug. شغّل: python tools/download_recipe_corpus.py ثم أعد بناء التطبيق.",
                error
            )
        }
    }

    private fun mergeRecipe(
        metadata: JSONObject,
        textRecord: JSONObject,
        language: String
    ): LocalCorpusRecipe {
        val id = textRecord.text("id")
        val amounts = metadata.optJSONArray("ingredients") ?: JSONArray()
        val localizedIngredients = textRecord.optJSONArray("ingredients") ?: JSONArray()
        val ingredients = (0 until localizedIngredients.length()).map { index ->
            val localized = localizedIngredients.optJSONObject(index) ?: JSONObject()
            val amount = amounts.optJSONObject(index) ?: JSONObject()
            LocalCorpusIngredient(
                name = localized.text("name"),
                note = localized.text("note"),
                quantity = amount.jsonScalar("qty"),
                unit = amount.text("unit").takeIf { it.isNotBlank() }
            )
        }

        val times = metadata.optJSONObject("time_min") ?: JSONObject()
        val provenance = metadata.optJSONObject("provenance") ?: JSONObject()
        val sourceLanguage = metadata.text("source_lang").ifBlank { language }
        return LocalCorpusRecipe(
            key = language + ":" + id,
            id = id,
            language = language,
            name = textRecord.text("name"),
            summary = textRecord.text("summary"),
            category = metadata.text("category"),
            cuisine = metadata.text("cuisine"),
            servings = metadata.intOrNull("servings"),
            prepMinutes = times.intOrNull("prep"),
            cookMinutes = times.intOrNull("cook"),
            ingredients = ingredients,
            steps = textRecord.stringArray("steps"),
            tags = metadata.stringArray("tags"),
            allergens = metadata.stringArray("allergens"),
            sourceLanguage = sourceLanguage,
            machineTranslated = textRecord.optBoolean("machine_translated", false),
            translatedFrom = textRecord.text("translated_from").takeIf { it.isNotBlank() },
            provenance = LocalCorpusProvenance(
                source = provenance.text("source"),
                title = provenance.text("title"),
                url = provenance.text("url"),
                revisionUrl = provenance.text("revision_url"),
                historyUrl = provenance.text("history_url"),
                credit = provenance.text("credit").ifBlank { provenance.text("attribution") },
                sourceLicense = provenance.text("source_license"),
                license = provenance.text("license"),
                licenseUrl = provenance.text("license_url"),
                changes = provenance.text("changes")
            )
        )
    }

    private fun cacheFile(recipe: LocalCorpusRecipe): File {
        val safeId = recipe.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(translationCacheDir, recipe.language + "-" + safeId + ".json")
    }

    private fun parseTranslation(json: JSONObject): LocalCorpusTranslation {
        val ingredients = json.optJSONArray("ingredients") ?: JSONArray()
        return LocalCorpusTranslation(
            name = json.text("name"),
            summary = json.text("summary"),
            ingredients = (0 until ingredients.length()).map { index ->
                val ingredient = ingredients.optJSONObject(index) ?: JSONObject()
                LocalCorpusIngredientTranslation(
                    name = ingredient.text("name"),
                    note = ingredient.text("note")
                )
            },
            steps = json.stringArray("steps")
        )
    }

    private fun translateText(sourceText: String, sourceLanguage: String): String {
        if (sourceText.isBlank()) return sourceText
        val tokens = mutableListOf<String>()
        val protectedText = NUMBER_PATTERN.replace(sourceText) { match ->
            tokens += match.value
            "MIDATINUMTOKEN" + (tokens.lastIndex) + "END"
        }

        val translated = splitForApi(protectedText).joinToString(" ") { chunk ->
            translateChunk(chunk, sourceLanguage)
        }

        var restored = translated
        tokens.forEachIndexed { index, original ->
            val marker = "MIDATINUMTOKEN" + index + "END"
            if (!restored.contains(marker, ignoreCase = true)) {
                throw IOException("تعذّر الحفاظ على رقم أو وحدة قياس أثناء الترجمة؛ أُلغيت العملية لحماية مقادير الوصفة.")
            }
            restored = restored.replace(Regex(Regex.escape(marker), RegexOption.IGNORE_CASE), original)
        }
        return restored.trim()
    }

    private fun translateChunk(text: String, sourceLanguage: String): String {
        val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
        val encodedLanguagePair = URLEncoder.encode(sourceLanguage + "|ar", StandardCharsets.UTF_8.name())
        val url = MY_MEMORY_ENDPOINT +
            "?q=" + encodedText +
            "&langpair=" + encodedLanguagePair +
            "&mt=1"

        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("خدمة الترجمة أعادت HTTP " + response.code + ". جرّب لاحقاً.")
            }
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val status = json.optInt("responseStatus", 0)
            if (status != 200) {
                val detail = json.optString("responseDetails").takeIf { it.isNotBlank() }
                throw IOException(detail ?: "خدمة الترجمة رفضت الطلب (رمز " + status + ").")
            }
            val translated = json.optJSONObject("responseData")?.optString("translatedText").orEmpty()
            if (translated.isBlank()) {
                throw IOException("لم تُرجع خدمة الترجمة نصاً لهذه الفقرة.")
            }
            return Html.fromHtml(translated, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        }
    }

    private fun splitForApi(text: String, maxUtf8Bytes: Int = 430): List<String> {
        val result = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            if (remaining.toByteArray(StandardCharsets.UTF_8).size <= maxUtf8Bytes) {
                result += remaining
                break
            }

            var end = remaining.length
            while (end > 1 &&
                remaining.substring(0, end).toByteArray(StandardCharsets.UTF_8).size > maxUtf8Bytes
            ) {
                end--
            }
            val preferredBreak = remaining.lastIndexOf(' ', end).takeIf { it > 0 }
            if (preferredBreak != null) end = preferredBreak
            if (end <= 0) throw IOException("تعذر تقسيم نص الوصفة إلى أجزاء مناسبة لخدمة الترجمة.")

            result += remaining.substring(0, end).trim()
            remaining = remaining.substring(end).trimStart()
        }
        return result
    }

    private fun JSONObject.text(key: String): String =
        if (isNull(key)) "" else optString(key).trim()

    private fun JSONObject.intOrNull(key: String): Int? {
        if (isNull(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value.toInt()
            else -> value?.toString()?.toIntOrNull()
        }
    }

    private fun JSONObject.jsonScalar(key: String): String? {
        if (isNull(key)) return null
        val value = opt(key) ?: return null
        return when (value) {
            is Number -> value.toString().removeSuffix(".0")
            else -> value.toString().takeIf { it.isNotBlank() }
        }
    }

    private fun JSONObject.stringArray(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() && it != "null" }
        }
    }

    private companion object {
        const val MY_MEMORY_ENDPOINT = "https://api.mymemory.translated.net/get"
        val NUMBER_PATTERN = Regex(
            """(?<![\p{L}\p{N}])\d+(?:[.,]\d+)?(?:\s?(?:°\s?[CF]|%|g|kg|mg|ml|l|tsp|tbsp|cups?|minutes?|mins?|hours?|hrs?|seconds?|secs?))?(?![\p{L}\p{N}])""",
            RegexOption.IGNORE_CASE
        )
    }
}
