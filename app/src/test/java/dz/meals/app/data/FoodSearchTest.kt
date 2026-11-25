package dz.meals.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSearchTest {

    @Test
    fun arabicSearchIgnoresVocalizationAndNormalizesHamzaForms() {
        assertTrue(foodMatchesSearch(listOf("إِبْرِيق"), "ابريق"))
        assertTrue(foodMatchesSearch(listOf("مُطَيِّشَة"), "مطيشة"))
        assertTrue(foodMatchesSearch(listOf("كُسْبَرَة"), "كسبرة"))
    }

    @Test
    fun latinSearchIgnoresCommonFrenchAccentsAndCase() {
        assertTrue(foodMatchesSearch(listOf("Café"), "cafe"))
        assertTrue(foodMatchesSearch(listOf("Crème fraîche"), "CREME fraiche"))
        assertTrue(foodMatchesSearch(listOf("Œufs"), "oeufs"))
    }

    @Test
    fun searchCanMatchAcrossCanonicalNamesAndDarijaAliases() {
        val fields = listOf("Tomate", "مطيشة", "maticha", "طماطم")
        assertTrue(foodMatchesSearch(fields, "tomate"))
        assertTrue(foodMatchesSearch(fields, "MATÏCHA"))
        assertTrue(foodMatchesSearch(fields, "tomate مطي"))
    }

    @Test
    fun everySearchTermMustExistAndBlankQueriesDoNotMatchEverything() {
        assertFalse(foodMatchesSearch(listOf("طماطم", "tomato"), "طماطم دجاج"))
        assertFalse(foodMatchesSearch(listOf("طماطم", "tomato"), "   "))
    }
    @Test
    fun catalogueIndexNormalizesEveryLocaleAndAliasForSearch() = runBlocking {
        val tomato = IngredientMaster(
            id = "tomato",
            nameAr = "مُطَيِّشَة",
            nameFr = "Tomate",
            nameEn = "Tomato",
            baseUnit = "kg",
            allergens = emptySet(),
            aliases = listOf("maticha", "طماطم"),
            categoryId = "vegetables",
            subcategoryId = "fresh"
        )
        val chicken = IngredientMaster(
            id = "chicken",
            nameAr = "دجاج",
            nameFr = "Poulet",
            nameEn = "Chicken",
            baseUnit = "kg",
            allergens = emptySet(),
            aliases = listOf("djaj"),
            categoryId = "meat",
            subcategoryId = "poultry"
        )
        val index = buildFoodCatalogueSearchIndex(
            ingredients = listOf(chicken, tomato),
            categories = listOf(
                FoodCategory("vegetables", "خضر", "Vegetables", "Légumes", 1),
                FoodCategory("meat", "لحوم", "Meat", "Viandes", 2)
            ),
            subcategories = listOf(
                FoodSubcategory("fresh", "vegetables", "طازج", "Fresh", "Frais", 1),
                FoodSubcategory("poultry", "meat", "دواجن", "Poultry", "Volaille", 1)
            ),
        )

        assertEquals(listOf("chicken", "tomato"), index.entries.map { it.ingredient.id })
        assertEquals(1, index.categoryCounts["vegetables"])
        assertEquals(listOf("tomato"), index.ingredientsByCategory["vegetables"]?.map { it.id })

        assertEquals(listOf("tomato"), searchFoodCatalogue(index, "maticha").items.map { it.id })
        assertEquals(listOf("tomato"), searchFoodCatalogue(index, "TOMATE").items.map { it.id })
        assertEquals(listOf("tomato"), searchFoodCatalogue(index, "tom").items.map { it.id })
        assertEquals(listOf("tomato"), searchFoodCatalogue(index, "طماطم طازج").items.map { it.id })
        assertEquals(0, searchFoodCatalogue(index, "طماطم دجاج").totalCount)
    }

    @Test
    fun catalogueSearchCapsRenderedRowsAndSignalsMoreCandidates() = runBlocking {
        val foods = (1..100).map { number ->
            IngredientMaster(
                id = "food-$number",
                nameAr = "منتج $number",
                nameFr = "Produit $number",
                nameEn = "Product $number",
                baseUnit = "piece",
                allergens = emptySet(),
                aliases = emptyList(),
                categoryId = "other",
                subcategoryId = "misc"
            )
        }
        val index = buildFoodCatalogueSearchIndex(
            ingredients = foods,
            categories = listOf(FoodCategory("other", "أخرى", "Other", "Autres", 1)),
            subcategories = listOf(FoodSubcategory("misc", "other", "متنوع", "Misc", "Divers", 1)),
        )

        val result = searchFoodCatalogue(index, "product", resultLimit = 12)

        assertEquals(100, result.totalCount)
        assertEquals(12, result.items.size)
        assertTrue(result.hasMore)
        assertEquals(12, result.items.map { it.id }.distinct().size)
    }

    @Test
    fun invertedIndexSearchHandlesFourThousandCatalogueItemsAndPartialNames() = runBlocking {
        val target = IngredientMaster(
            id = "target-tomato",
            nameAr = "طماطم",
            nameFr = "Tomate",
            nameEn = "Tomato",
            baseUnit = "kg",
            allergens = emptySet(),
            aliases = listOf("maticha", "مطيشة"),
            categoryId = "vegetables",
            subcategoryId = "fresh"
        )
        val backgroundFoods = (1..3_999).map { number ->
            val suffix = number.toString().padStart(4, '0')
            IngredientMaster(
                id = "food-$suffix",
                nameAr = "مكوّن $suffix",
                nameFr = "Ingredient $suffix",
                nameEn = "Food $suffix",
                baseUnit = "piece",
                allergens = emptySet(),
                aliases = listOf("catalogueitem$suffix"),
                categoryId = "other",
                subcategoryId = "misc"
            )
        }
        val index = buildFoodCatalogueSearchIndex(
            ingredients = backgroundFoods + target,
            categories = listOf(
                FoodCategory("vegetables", "خضر", "Vegetables", "Légumes", 1),
                FoodCategory("other", "أخرى", "Other", "Autres", 2)
            ),
            subcategories = listOf(
                FoodSubcategory("fresh", "vegetables", "طازج", "Fresh", "Frais", 1),
                FoodSubcategory("misc", "other", "متنوع", "Misc", 1)
            )
        )

        val aliasResult = searchFoodCatalogue(index, "maticha", resultLimit = 40)
        val partialNameResult = searchFoodCatalogue(index, "tom", resultLimit = 40)

        assertEquals(listOf("target-tomato"), aliasResult.items.map { it.id })
        assertEquals(listOf("target-tomato"), partialNameResult.items.map { it.id })
        assertEquals(1, aliasResult.totalCount)
        assertFalse(aliasResult.hasMore)
        assertEquals(4_000, index.entries.size)
        assertEquals(3_999, index.categoryCounts["other"])
    }

}
