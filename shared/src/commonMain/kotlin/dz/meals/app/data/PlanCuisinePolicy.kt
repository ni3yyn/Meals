package dz.meals.app.data

/**
 * Shared classification helpers for the curated Algerian recipe collection.
 *
 * TEMPORARY DEFAULT: automatic plan generation currently uses the dedicated
 * Algerian Dzirielle collection. Cuisine selection will later come from
 * Settings. Manual recipe selection and add-to-plan remain unrestricted.
 */
object PlanCuisinePolicy {
    const val ALGERIAN_RECIPE_ID_PREFIX = "dz-algerian-dzirielle-"

    private val mainDishMarkers = listOf(
        "main dish",
        "main dishes",
        "main course",
        "main courses",
        "main meal",
        "main meals",
        "mains",
        "plat",
        "plats",
        "plat principal",
        "plats principaux",
        "plat de resistance",
        "plats de resistance",
        "plat sale",
        "plats sales",
        "recette salee",
        "recettes salees",
        "طبق",
        "الطبق الرئيسي",
        "اطباق",
        "الاطباق",
        "طبق رئيسي",
        "اطباق رئيسية",
        "الاطباق الرئيسية",
        "وجبة رئيسية",
        "وجبات رئيسية",
        "اطباق مالحة",
        "وصفات مالحة",
        "اكلات مالحة"
    )

    private val nonMainDishMarkers = listOf(
        // Desserts and sweet dishes.
        "dessert", "desserts", "sweet", "sweets", "sucre",
        "patisserie", "gateau", "tarte", "cookie", "biscuit",
        "confiserie", "douceur", "chocolat", "pudding", "brownie",
        "cake", "muffin", "glace", "sorbet", "viennoiserie",
        "crepe sucree",
        "حلويات", "حلوى", "تحلية", "تحليات", "حلاوة", "حلو",
        "حلوة", "كعك", "كيك", "قاطو", "طورطة", "تارت",
        "بسكويت", "بسكوي", "شوكولاتة", "شكولاطة",

        // Other courses and drinks are not main dishes.
        "entree", "starter", "appetizer", "salade", "soup", "soupe",
        "boisson", "drink", "juice", "jus", "smoothie", "cocktail",
        "سلطة", "سلطات", "شوربة", "حساء", "مقبلات",
        "مشروب", "مشروبات", "عصير", "عصائر"
    )

    fun allows(recipe: MealRecipe): Boolean = allowsRecipeId(recipe.id)

    fun allowsRecipeId(recipeId: String): Boolean =
        recipeId.startsWith(ALGERIAN_RECIPE_ID_PREFIX)

    /**
     * Automatic generation is deliberately fail-closed: a recipe must carry
     * an explicit main-dish type in its course metadata. A generic label such
     * as "traditional" or "quick" is not enough to qualify it.
     */
    fun allowsMainDish(recipe: MealRecipe): Boolean {
        if (!allows(recipe)) return false

        val courseMetadata = listOf(recipe.category) + recipe.categories + recipe.tags
        val searchableText = listOf(
            recipe.nameAr,
            recipe.nameFr,
            recipe.nativeName,
            recipe.descriptionAr,
            recipe.descriptionFr
        )

        if ((courseMetadata + searchableText).any(::containsNonMainDishMarker)) {
            return false
        }

        return courseMetadata.any(::containsMainDishMarker)
    }

    private fun containsMainDishMarker(value: String): Boolean =
        containsAnyPhrase(value, mainDishMarkers)

    private fun containsNonMainDishMarker(value: String): Boolean =
        containsAnyPhrase(value, nonMainDishMarkers)

    private fun containsAnyPhrase(value: String, markers: List<String>): Boolean {
        val normalized = " ${normalize(value)} "
        return markers.any { marker ->
            val normalizedMarker = normalize(marker)
            normalizedMarker.isNotEmpty() && normalized.contains(" $normalizedMarker ")
        }
    }

    private fun normalize(value: String): String = buildString(value.length) {
        value.lowercase().forEach { character ->
            when (character) {
                'أ', 'إ', 'آ' -> append('ا')
                'ى' -> append('ي')
                'é', 'è', 'ê', 'ë' -> append('e')
                'à', 'â', 'ä' -> append('a')
                'ù', 'û', 'ü' -> append('u')
                'ô', 'ö' -> append('o')
                'î', 'ï' -> append('i')
                'ç' -> append('c')
                'œ' -> append("oe")
                'æ' -> append("ae")
                '\u064B', '\u064C', '\u064D', '\u064E', '\u064F',
                '\u0650', '\u0651', '\u0652', '\u0653', '\u0654',
                '\u0655', '\u0656', '\u0657', '\u0658', '\u0659',
                '\u065A', '\u065B', '\u065C', '\u065D', '\u065E',
                '\u065F', '\u0670' -> Unit
                else -> append(character)
            }
        }
    }
        .replace('-', ' ')
        .replace('_', ' ')
        .replace('/', ' ')
        .replace('\\', ' ')
        .replace(',', ' ')
        .replace(';', ' ')
        .replace(':', ' ')
        .replace('.', ' ')
        .replace('!', ' ')
        .replace('?', ' ')
        .replace('(', ' ')
        .replace(')', ' ')
        .replace('[', ' ')
        .replace(']', ' ')
        .replace('{', ' ')
        .replace('}', ' ')
        .replace('\u2019', ' ')
        .replace('\'', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}
