package dz.meals.app.data

private val FOOD_SEARCH_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
private val FOOD_SEARCH_WHITESPACE = Regex("\\s+")

/**
 * Folds search text without changing stored names or stable food IDs.
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

/**
 * An immutable, pre-normalized catalogue row. Build these once when the
 * catalogue changes, not while the user types.
 */
data class FoodSearchIndexEntry internal constructor(
    val ingredient: IngredientMaster,
    internal val normalizedSearchText: String
)

/**
 * One catalogue index supports search and category browsing. The grouped lists
 * preserve the localized alphabetical order of [entries].
 */
data class FoodCatalogueSearchIndex internal constructor(
    val entries: List<FoodSearchIndexEntry>,
    val ingredientsByCategory: Map<String, List<IngredientMaster>>,
    val categoryCounts: Map<String, Int>
)

data class FoodCatalogueSearchResult(
    val items: List<IngredientMaster>,
    val totalCount: Int
)

/**
 * Builds a reusable index over every supported locale, alias and taxonomy
 * label. Call from a background dispatcher for large catalogues.
 */
fun buildFoodCatalogueSearchIndex(
    ingredients: List<IngredientMaster>,
    categories: List<FoodCategory>,
    subcategories: List<FoodSubcategory>,
    localeTag: String
): FoodCatalogueSearchIndex {
    val categoriesById = categories.associateBy { it.id }
    val subcategoriesById = subcategories.associateBy { it.id }

    val sortedIngredients = ingredients.sortedBy { food ->
        when {
            localeTag.startsWith("en", ignoreCase = true) -> food.nameEn.ifBlank { food.nameAr }.lowercase()
            localeTag.startsWith("fr", ignoreCase = true) -> food.nameFr.ifBlank { food.nameAr }.lowercase()
            else -> food.nameAr.lowercase()
        }
    }

    val entries = sortedIngredients.map { food ->
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

    val ingredientsByCategory = sortedIngredients.groupBy { it.categoryId }
    val categoryCounts = ingredientsByCategory.mapValues { (_, foods) -> foods.size }

    return FoodCatalogueSearchIndex(
        entries = entries,
        ingredientsByCategory = ingredientsByCategory,
        categoryCounts = categoryCounts
    )
}

/**
 * Filters pre-normalized entries. Only [resultLimit] rows are retained for UI
 * composition, while [FoodCatalogueSearchResult.totalCount] remains exact.
 * Entries are already alphabetically sorted, so filtering needs no second sort.
 */
fun searchFoodCatalogue(
    index: FoodCatalogueSearchIndex,
    query: String,
    resultLimit: Int = 40
): FoodCatalogueSearchResult {
    val normalizedQuery = normalizeFoodSearchText(query)
    if (normalizedQuery.isBlank()) return FoodCatalogueSearchResult(emptyList(), 0)

    val terms = normalizedQuery.split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return FoodCatalogueSearchResult(emptyList(), 0)

    val safeLimit = resultLimit.coerceAtLeast(0)
    val matches = ArrayList<IngredientMaster>(minOf(safeLimit, index.entries.size))
    var totalCount = 0

    for (entry in index.entries) {
        if (terms.all { term -> term in entry.normalizedSearchText }) {
            totalCount += 1
            if (matches.size < safeLimit) matches.add(entry.ingredient)
        }
    }

    return FoodCatalogueSearchResult(items = matches, totalCount = totalCount)
}

/**
 * Compatibility helper for small one-off callers and existing tests.
 * Large catalogue screens should use [buildFoodCatalogueSearchIndex] once.
 */
fun foodMatchesSearch(fields: Iterable<String>, query: String): Boolean {
    val normalizedQuery = normalizeFoodSearchText(query)
    if (normalizedQuery.isBlank()) return false
    val terms = normalizedQuery.split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return false
    val normalizedIndex = normalizeFoodSearchText(fields.joinToString(" "))
    return terms.all { term -> term in normalizedIndex }
}
