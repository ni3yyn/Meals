package dz.meals.app.data

/**
 * Shared classification helpers for automatic plan generation.
 *
 * TEMPORARY DEFAULT: the generator currently uses the dedicated Algerian
 * Dzirielle collection. Cuisine selection will later come from Settings.
 * Manual recipe selection and add-to-plan remain unrestricted.
 */
object PlanCuisinePolicy {
    const val ALGERIAN_RECIPE_ID_PREFIX = "dz-algerian-dzirielle-"

    // These are the explicit main-course labels used by the Room v17 catalogue.
    private val eligibleCourseMarkers = listOf(
        "main dish",
        "main dishes",
        "main course",
        "main courses",
        "main meal",
        "main meals",
        "mains",
        "plat principal",
        "plats principaux",
        "plat de resistance",
        "plats de resistance",
        "طبق رئيسي",
        "الطبق الرئيسي",
        "اطباق رئيسية",
        "الاطباق الرئيسية",
        "وجبة رئيسية",
        "وجبات رئيسية",
        "soup",
        "soups",
        "soupe",
        "soupes",
        "حساء",
        "شوربة",
        "شوربات"
    )

    // Course labels that should never enter an automatically generated plan.
    private val nonMainDishMarkers = listOf(
        "dessert", "desserts", "sweet", "sweets", "sucre", "sucree", "sucres", "sucrees",
        "patisserie", "patisseries", "gateau", "gateaux", "tarte", "tartes",
        "cookie", "cookies", "biscuit", "biscuits", "confiserie", "confiseries",
        "douceur", "douceurs", "pudding", "brownie", "cake", "muffin",
        "glace", "glaces", "sorbet", "sorbets", "viennoiserie", "viennoiseries",
        "crepe sucree",
        "حلويات", "حلوى", "تحلية", "تحليات", "حلاوة", "حلوة",
        "كعك", "كيك", "قاطو", "طورطة", "تارت", "بسكويت", "بسكوي",
        "شوكولاتة", "شكولاطة",

        "entree", "entrees", "starter", "starters", "appetizer", "appetizers",
        "salade", "salades", "boisson", "boissons",
        "drink", "drinks", "juice", "juices", "jus", "smoothie", "cocktail",
        "سلطة", "سلطات", "مقبلات", "مشروب", "مشروبات",
        "عصير", "عصائر",
        "وجبة خفيفة", "وجبات خفيفة", "فطور", "خبز"
    )

    // Title-only checks catch a dessert whose course metadata was accidentally mislabeled.
    // Avoid the generic adjective "حلو" here: dishes such as لحم حلو are main courses.
    private val dessertTitleMarkers = listOf(
        "dessert", "desserts", "patisserie", "patisseries", "gateau", "gateaux",
        "tarte", "tartes", "cookie", "cookies", "biscuit", "biscuits",
        "confiserie", "confiseries", "pudding", "brownie", "cake", "muffin",
        "glace", "glaces", "sorbet", "sorbets", "viennoiserie", "viennoiseries", "حلويات", "حلوى", "حلوة", "كعك", "كيك",
        "قاطو", "طورطة", "تارت", "بسكويت", "بسكوي"
    )

    fun allows(recipe: MealRecipe): Boolean = allowsRecipeId(recipe.id)

    fun allowsRecipeId(recipeId: String): Boolean =
        recipeId.startsWith(ALGERIAN_RECIPE_ID_PREFIX)

    /**
     * Fail closed: an Algerian recipe must have an explicit main-dish or soup
     * course classification. Desserts, sweets, starters, salads, breads,
     * breakfasts and drinks are excluded from automatic generation.
     */
    fun allowsForGeneration(recipe: MealRecipe): Boolean {
        if (!allows(recipe)) return false

        val courseMetadata = listOf(recipe.category) + recipe.categories + recipe.tags
        if (courseMetadata.any(::containsNonMainDishMarker)) return false

        val dishNames = listOf(recipe.nameAr, recipe.nameFr, recipe.nativeName)
        if (dishNames.any { name -> containsAnyPhrase(name, dessertTitleMarkers) }) {
            return false
        }

        return courseMetadata.any(::containsEligibleCourseMarker)
    }

    private fun containsEligibleCourseMarker(value: String): Boolean =
        containsAnyPhrase(value, eligibleCourseMarkers)

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
