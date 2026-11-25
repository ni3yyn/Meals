package dz.meals.app.data

import io.github.karloti.typeahead.TypeaheadRecord.TypeaheadMetadata
import io.github.karloti.typeahead.TypeaheadSearchEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val FOOD_SEARCH_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
private val FOOD_SEARCH_WHITESPACE = Regex("\\s+")

private const val FOOD_SEARCH_MAX_CANDIDATES = 100
private const val FOOD_SEARCH_TOP_VOCAB = 32

/**
 * Folds user search text without changing stored names or stable food IDs.
 * Supports Arabic vocalization/hamza variants, common Latin diacritics and
 * whitespace/punctuation differences. It intentionally does not translate.
 */
fun normalizeFoodSearchText(value: String): String {
    val folded = buildString(value.length) {
        for (character in value.lowercase()) {
            when (character) {
                in '\u064B'..'\u065F', '\u0670', '\u0640', '\u200C', '\u200D' -> Unit
                'أ', 'إ', 'آ', 'ٱ' -> append('ا')
                'ؤ' -> append('و')
                'ئ', 'ى', 'ی' -> append('ي')
                'ک' -> append('ك')
                'à', 'á', 'â', 'ä', 'ã', 'å' -> append('a')
                'æ' -> append("ae")
                'ç' -> append('c')
                'è', 'é', 'ê', 'ë' -> append('e')
                'ì', 'í', 'î', 'ï' -> append('i')
                'ñ' -> append('n')
                'ò', 'ó', 'ô', 'ö', 'õ' -> append('o')
                'œ' -> append("oe")
                'ù', 'ú', 'û', 'ü' -> append('u')
                'ý', 'ÿ' -> append('y')
                else -> append(character)
            }
        }
    }
    return folded
        .replace(FOOD_SEARCH_SEPARATORS, " ")
        .trim()
        .replace(FOOD_SEARCH_WHITESPACE, " ")
}

/** A normalized catalogue row indexed by the shared Typeahead engine. */
data class FoodSearchIndexEntry internal constructor(
    val ingredient: IngredientMaster,
    internal val normalizedSearchText: String
)

/**
 * One reusable in-memory inverted index supports both free-text search and
 * category browsing. The native engine is built once per catalogue revision.
 */
class FoodCatalogueSearchIndex internal constructor(
    val entries: List<FoodSearchIndexEntry>,
    val ingredientsByCategory: Map<String, List<IngredientMaster>>,
    val categoryCounts: Map<String, Int>,
    internal val searchEngine: TypeaheadSearchEngine<FoodSearchIndexEntry, String>,
    internal val maxCandidates: Int
)

data class FoodCatalogueSearchResult(
    val items: List<IngredientMaster>,
    val totalCount: Int,
    val hasMore: Boolean = false
)

/**
 * Builds the search document corpus and the KMP inverted index.
 *
 * This is suspendable because Typeahead performs vocabulary vectorization on
 * Dispatchers.Default. Call it from a background coroutine, never from a
 * composable calculation or the main thread.
 */
suspend fun buildFoodCatalogueSearchIndex(
    ingredients: List<IngredientMaster>,
    categories: List<FoodCategory>,
    subcategories: List<FoodSubcategory>
): FoodCatalogueSearchIndex {
    val categoriesById = categories.associateBy { it.id }
    val subcategoriesById = subcategories.associateBy { it.id }

    // Stable order here is only for deterministic category grouping. Search
    // ranking is supplied by the inverted index; the UI applies locale order
    // to the selected category's much smaller list.
    val catalogue = ingredients.sortedBy { it.nameAr.lowercase() }
    val entries = catalogue.map { food ->
        val category = categoriesById[food.categoryId]
        val subcategory = subcategoriesById[food.subcategoryId]
        val searchFields = buildList {
            add(food.nameAr)
            add(food.nameFr)
            add(food.nameEn)
            addAll(food.aliases)
            add(category?.nameAr.orEmpty())
            add(category?.nameFr.orEmpty())
            add(category?.nameEn.orEmpty())
            add(subcategory?.nameAr.orEmpty())
            add(subcategory?.nameFr.orEmpty())
            add(subcategory?.nameEn.orEmpty())
        }
        FoodSearchIndexEntry(
            ingredient = food,
            normalizedSearchText = normalizeFoodSearchText(searchFields.joinToString(" "))
        )
    }

    val engine = TypeaheadSearchEngine<FoodSearchIndexEntry, String>(
        textSelector = FoodSearchIndexEntry::normalizedSearchText,
        keySelector = { it.ingredient.id },
        metadata = TypeaheadMetadata(
            // Keep a bounded candidate pool: we display at most 40 rows, and
            // retain enough additional candidates for exact matching/ranking.
            maxResults = FOOD_SEARCH_MAX_CANDIDATES,
            topKVocab = FOOD_SEARCH_TOP_VOCAB,
            haveStore = true
        )
    )
    engine.addAll(entries)

    val ingredientsByCategory = catalogue.groupBy { it.categoryId }
    val categoryCounts = ingredientsByCategory.mapValues { (_, foods) -> foods.size }

    return FoodCatalogueSearchIndex(
        entries = entries,
        ingredientsByCategory = ingredientsByCategory,
        categoryCounts = categoryCounts,
        searchEngine = engine,
        maxCandidates = FOOD_SEARCH_MAX_CANDIDATES
    )
}

/**
 * Queries the prebuilt Typeahead inverted index, then applies Midati's exact
 * all-terms rule against the normalized document text. That preserves the
 * existing Arabic/French/English partial-name semantics while letting the
 * library do vocabulary retrieval and relevance ordering.
 *
 * The engine retains at most 100 candidates internally; the UI renders only
 * [resultLimit]. hasMore is conservative when the candidate pool is full.
 */
suspend fun searchFoodCatalogue(
    index: FoodCatalogueSearchIndex,
    query: String,
    resultLimit: Int = 40
): FoodCatalogueSearchResult {
    val normalizedQuery = normalizeFoodSearchText(query)
    if (normalizedQuery.length < 2) {
        return FoodCatalogueSearchResult(emptyList(), 0)
    }

    val terms = normalizedQuery.split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return FoodCatalogueSearchResult(emptyList(), 0)

    index.searchEngine.find(normalizedQuery)
    val rankedCandidates = index.searchEngine.storeResults?.value.orEmpty()

    // MiniSearch-style retrieval is performed by the inverted index; this
    // final check enforces Midati's established "all typed terms must occur"
    // behaviour and removes fuzzy near-misses from the visible results.
    val exactMatches = rankedCandidates.filter { (entry, _) ->
        terms.all { term -> term in entry.normalizedSearchText }
    }

    val safeLimit = resultLimit.coerceAtLeast(0)
    return FoodCatalogueSearchResult(
        items = exactMatches.take(safeLimit).map { it.first.ingredient },
        totalCount = exactMatches.size,
        hasMore = rankedCandidates.size >= index.maxCandidates
    )
}

/**
 * Compatibility helper for small one-off callers and existing tests.
 * Catalogue screens should use [buildFoodCatalogueSearchIndex] and
 * [searchFoodCatalogue] instead.
 */
fun foodMatchesSearch(fields: Iterable<String>, query: String): Boolean {
    val normalizedQuery = normalizeFoodSearchText(query)
    if (normalizedQuery.isBlank()) return false
    val terms = normalizedQuery.split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return false
    val normalizedIndex = normalizeFoodSearchText(fields.joinToString(" "))
    return terms.all { term -> term in normalizedIndex }
}


/**
 * Process-wide cache so navigating away from Shopping and back does not
 * reconstruct the typeahead engine (which owns a background coroutine scope).
 * Structural snapshots detect real catalogue changes without coupling the
 * cache to UI locale or screen lifetime.
 */
private object FoodCatalogueSearchIndexCache {
    private val mutex = Mutex()
    private var hasSnapshot = false
    private var cachedIngredients: List<IngredientMaster> = emptyList()
    private var cachedCategories: List<FoodCategory> = emptyList()
    private var cachedSubcategories: List<FoodSubcategory> = emptyList()
    private var cachedIndex: FoodCatalogueSearchIndex? = null

    suspend fun getOrBuild(
        ingredients: List<IngredientMaster>,
        categories: List<FoodCategory>,
        subcategories: List<FoodSubcategory>
    ): FoodCatalogueSearchIndex = mutex.withLock {
        val existing = cachedIndex
        if (
            hasSnapshot &&
            existing != null &&
            cachedIngredients == ingredients &&
            cachedCategories == categories &&
            cachedSubcategories == subcategories
        ) {
            return@withLock existing
        }

        val built = buildFoodCatalogueSearchIndex(
            ingredients = ingredients,
            categories = categories,
            subcategories = subcategories
        )
        cachedIngredients = ingredients.toList()
        cachedCategories = categories.toList()
        cachedSubcategories = subcategories.toList()
        cachedIndex = built
        hasSnapshot = true
        built
    }
}

/** Returns the same process-wide index while the underlying catalogue is unchanged. */
suspend fun getFoodCatalogueSearchIndex(
    ingredients: List<IngredientMaster>,
    categories: List<FoodCategory>,
    subcategories: List<FoodSubcategory>
): FoodCatalogueSearchIndex = FoodCatalogueSearchIndexCache.getOrBuild(
    ingredients = ingredients,
    categories = categories,
    subcategories = subcategories
)

