package dz.meals.app.data

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
}
