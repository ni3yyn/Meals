package dz.meals.app.data

/**
 * Offline starter catalogue. Costs are illustrative and are labelled as demo estimates
 * in the interface until verified local price feeds are connected.
 */
object SeedRecipes {
    val all = listOf(
        MealRecipe(
            id = "couscous-vegetables", name = "كسكس بالخضرة",
            description = "كسكس جزائري بالخضار والحمص وقطع الدجاج.",
            category = "تقليدي", durationMinutes = 55, servings = 4, estimatedCostDzd = 1_050,
            ingredients = listOf(
                Ingredient("semolina", "كسكس متوسط", 500.0, "غ"),
                Ingredient("chicken", "دجاج", 400.0, "غ"),
                Ingredient("carrot", "جزر", 2.0, "حبة"),
                Ingredient("zucchini", "قرعة خضراء", 2.0, "حبة"),
                Ingredient("chickpeas", "حمص", 120.0, "غ"),
                Ingredient("tomato", "طماطم", 2.0, "حبة")
            )
        ),
        MealRecipe(
            id = "lentil-stew", name = "عدس بالخضرة",
            description = "طبق دافئ واقتصادي بالعدس والطماطم والجزر.",
            category = "اقتصادي", durationMinutes = 35, servings = 3, estimatedCostDzd = 520,
            ingredients = listOf(
                Ingredient("lentils", "عدس", 250.0, "غ"),
                Ingredient("onion", "بصل", 1.0, "حبة"),
                Ingredient("carrot", "جزر", 1.0, "حبة"),
                Ingredient("tomato", "طماطم", 2.0, "حبة"),
                Ingredient("garlic", "ثوم", 2.0, "فص"),
                Ingredient("oil", "زيت", 2.0, "ملعقة")
            )
        ),
        MealRecipe(
            id = "shakshuka", name = "شكشوكة",
            description = "بيض مطهو مع الطماطم والفلفل والبصل.",
            category = "سريع", durationMinutes = 20, servings = 2, estimatedCostDzd = 460,
            ingredients = listOf(
                Ingredient("eggs", "بيض", 4.0, "حبة"),
                Ingredient("tomato", "طماطم", 3.0, "حبة"),
                Ingredient("pepper", "فلفل", 1.0, "حبة"),
                Ingredient("onion", "بصل", 1.0, "حبة"),
                Ingredient("oil", "زيت", 1.0, "ملعقة")
            )
        ),
        MealRecipe(
            id = "tomato-pasta", name = "معكرونة بصلصة الطماطم",
            description = "وجبة يومية بسيطة بمكونات متوفرة.",
            category = "اقتصادي", durationMinutes = 25, servings = 3, estimatedCostDzd = 610,
            ingredients = listOf(
                Ingredient("pasta", "معكرونة", 300.0, "غ"),
                Ingredient("tomato", "طماطم", 4.0, "حبة"),
                Ingredient("onion", "بصل", 1.0, "حبة"),
                Ingredient("garlic", "ثوم", 2.0, "فص"),
                Ingredient("tomato-paste", "طماطم مصبرة", 2.0, "ملعقة")
            )
        ),
        MealRecipe(
            id = "chicken-potatoes", name = "دجاج بالبطاطا في الفرن",
            description = "صينية دجاج وبطاطا بتوابل منزلية.",
            category = "عائلي", durationMinutes = 50, servings = 4, estimatedCostDzd = 1_280,
            ingredients = listOf(
                Ingredient("chicken", "دجاج", 600.0, "غ"),
                Ingredient("potato", "بطاطا", 700.0, "غ"),
                Ingredient("onion", "بصل", 1.0, "حبة"),
                Ingredient("garlic", "ثوم", 3.0, "فص"),
                Ingredient("oil", "زيت", 2.0, "ملعقة")
            )
        ),
        MealRecipe(
            id = "vegetable-omelette", name = "عجة بالخضرة",
            description = "عجة خفيفة بالبيض والقرعة والجبن.",
            category = "سريع", durationMinutes = 15, servings = 2, estimatedCostDzd = 430,
            ingredients = listOf(
                Ingredient("eggs", "بيض", 3.0, "حبة"),
                Ingredient("zucchini", "قرعة خضراء", 1.0, "حبة"),
                Ingredient("onion", "بصل", 1.0, "حبة"),
                Ingredient("cheese", "جبن", 50.0, "غ")
            )
        )
    )
}
