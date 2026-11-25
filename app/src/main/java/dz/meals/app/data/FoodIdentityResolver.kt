package dz.meals.app.data

import android.content.Context
import android.util.JsonReader
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Resolves legacy ingredient keys to stable food UUIDs.
 *
 * The generated crosswalk is bundled in the APK so migrations work offline.
 * IDs outside the crosswalk (such as user-created food keys) receive a
 * deterministic UUIDv5 rather than being dropped or randomly reassigned.
 */
class FoodIdentityResolver(context: Context) {
    private val appContext = context.applicationContext

    private val legacyMappings: Map<String, String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LinkedHashMap<String, String>().apply {
            putAll(SEED_MAPPINGS)
            putAll(readBundledMappings())
        }
    }

    fun canonicalId(legacyId: String): String {
        val key = legacyId.trim()
        require(key.isNotEmpty()) { "Ingredient identity cannot be blank" }

        SEED_MAPPINGS[key]?.let { return it }
        normalizeUuid(key)?.let { return it }
        legacyMappings[key]?.let { return it }
        return uuidV5(key)
    }

    private fun readBundledMappings(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        try {
            appContext.assets.open(MAPPING_ASSET).bufferedReader(Charsets.UTF_8).use { stream ->
                JsonReader(stream).use { reader ->
                    reader.isLenient = false
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "legacyRoomIngredientIdToFoodItemId" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val legacyId = reader.nextName()
                                    val canonicalId = reader.nextString()
                                    require(legacyId.isNotBlank()) { "Food crosswalk contains a blank legacy ID" }
                                    require(normalizeUuid(canonicalId) != null) {
                                        "Food crosswalk contains an invalid UUID for $legacyId"
                                    }
                                    result[legacyId] = canonicalId.lowercase()
                                }
                                reader.endObject()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }
        } catch (error: Exception) {
            throw IllegalStateException(
                "The bundled food identity crosswalk is missing or invalid; migration must not continue without it.",
                error
            )
        }

        require(result.size >= MIN_EXPECTED_LEGACY_MAPPINGS) {
            "The bundled food identity crosswalk is incomplete (${result.size} entries)."
        }
        return result
    }

    private fun normalizeUuid(value: String): String? {
        if (!UUID_PATTERN.matches(value)) return null
        return runCatching { UUID.fromString(value).toString() }.getOrNull()
    }

    private fun uuidV5(name: String): String {
        val namespace = UUID.fromString(UUID_NAMESPACE)
        val namespaceBytes = ByteBuffer.allocate(16)
            .putLong(namespace.mostSignificantBits)
            .putLong(namespace.leastSignificantBits)
            .array()
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(namespaceBytes)
        val hash = digest.digest(name.toByteArray(Charsets.UTF_8))
        hash[6] = ((hash[6].toInt() and 0x0f) or 0x50).toByte()
        hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte()
        val mostSignificantBits = ByteBuffer.wrap(hash, 0, 8).long
        val leastSignificantBits = ByteBuffer.wrap(hash, 8, 8).long
        return UUID(mostSignificantBits, leastSignificantBits).toString()
    }

    companion object {
        const val MAPPING_ASSET = "migration/recipe-ingredient-map.json"
        private const val MIN_EXPECTED_LEGACY_MAPPINGS = 1_261
        private const val UUID_NAMESPACE = "ed3f7bd4-4b12-5d6d-9eb1-2b6de75e1a55"
        private val UUID_PATTERN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )

        /** Stable IDs for the original 16 seeded foods. */
        val SEED_MAPPINGS = mapOf(
            "semolina" to "3c718b6b-0535-43d7-9f4f-1dd5ab860bae",
            "chicken" to "0fdd586e-27ec-46c1-b407-2b5925054e42",
            "carrot" to "e46fe74d-ff23-4838-9de7-91553aaf4343",
            "zucchini" to "95c23222-33e1-4774-b33c-80bb11e92bef",
            "chickpeas" to "ba7f4611-f247-4be4-89ea-41f970be51c1",
            "tomato" to "f04fe295-6527-4e4e-b9b2-68de6ed1dd29",
            "lentils" to "d3274082-ddb1-425d-9261-fbbb9230a28e",
            "onion" to "a5d374ee-c9f0-4443-8d9c-3d1f76987691",
            "garlic" to "d957b3b5-c887-4216-aff6-263403dbc9e1",
            "oil" to "aab650af-92cf-441f-95f7-16411177dfe2",
            "eggs" to "312802bc-f608-43a9-8644-54c9696143b5",
            "pepper" to "67462723-fb06-4dac-9e08-fc3484d289b9",
            "pasta" to "9375b0c5-6a10-45b9-86b0-68fe95c4fd83",
            "tomato-paste" to "d376b5b9-31c4-4007-bfac-309c1a7648a7",
            "potato" to "304f8039-efa2-46b2-8873-b5307afe954a",
            "cheese" to "33b76b9e-8eae-4aae-bd5e-d6a9f5959011"
        )
    }
}
