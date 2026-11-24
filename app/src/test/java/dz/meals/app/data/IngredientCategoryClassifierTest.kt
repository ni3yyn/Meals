package dz.meals.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientCategoryClassifierTest {
    @Test
    fun classifiesArabicEnglishAndFrenchIngredients() {
        assertEquals("خضروات", IngredientCategoryClassifier.classify("tomato", "طماطم", "Tomato"))
        assertEquals("لحوم ودواجن", IngredientCategoryClassifier.classify("chicken", "دجاج", "Chicken"))
        assertEquals("بقوليات", IngredientCategoryClassifier.classify("lentil", "عدس", "Lentils"))
        assertEquals("ألبان وبيض", IngredientCategoryClassifier.classify("oeuf", "بيض", "Egg"))
        assertEquals("حبوب ومخبوزات", IngredientCategoryClassifier.classify("semoule", "سميد", "Semolina"))
        assertEquals("توابل وأعشاب", IngredientCategoryClassifier.classify("cumin", "كمون", "Cumin"))
        assertEquals("فواكه", IngredientCategoryClassifier.classify("banana", "موز", "Banane"))
        assertEquals("مواد أساسية وصلصات", IngredientCategoryClassifier.classify("olive-oil", "زيت الزيتون", "Olive oil"))
    }

    @Test
    fun unknownIngredientIsKeptInAnHonestOtherCategory() {
        assertEquals("أخرى", IngredientCategoryClassifier.classify("unknown-item-42", "مكوّن غريب", "Unknown item"))
    }
}
