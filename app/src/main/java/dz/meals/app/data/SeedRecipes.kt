package dz.meals.app.data

object SeedRecipes {
    val ingredients = listOf(
        IngredientMaster("semolina", "سميد متوسط", "Semoule moyenne", "حبوب ومخبوزات", "g", setOf("gluten"), listOf("سميد", "semoule", "smid")),
        IngredientMaster("chicken", "دجاج", "Poulet", "لحوم ودواجن", "g", emptySet(), listOf("دجاجة", "poulet")),
        IngredientMaster("carrot", "جزر", "Carotte", "خضروات", "piece", emptySet(), listOf("زرودية", "carotte", "zrodiya")),
        IngredientMaster("zucchini", "قرعة خضراء", "Courgette", "خضروات", "piece", emptySet(), listOf("كوسة", "courgette", "qar3a")),
        IngredientMaster("chickpeas", "حمص", "Pois chiches", "بقوليات", "g", emptySet(), listOf("حمص يابس", "pois chiche", "homs")),
        IngredientMaster("tomato", "طماطم", "Tomate", "خضروات", "g", emptySet(), listOf("مطيشة", "tomate", "maticha")),
        IngredientMaster("lentils", "عدس", "Lentilles", "بقوليات", "g", emptySet(), listOf("عدس يابس", "lentille", "3dess")),
        IngredientMaster("onion", "بصل", "Oignon", "خضروات", "g", emptySet(), listOf("بصلة", "oignon", "bsal")),
        IngredientMaster("garlic", "ثوم", "Ail", "خضروات", "clove", emptySet(), listOf("ثومة", "ail", "thoum")),
        IngredientMaster("oil", "زيت", "Huile", "مواد أساسية", "ml", emptySet(), listOf("زيت المائدة", "huile")),
        IngredientMaster("eggs", "بيض", "Œufs", "ألبان وبيض", "piece", setOf("egg"), listOf("بيضات", "oeuf", "œuf", "bayd")),
        IngredientMaster("pepper", "فلفل حلو", "Poivron", "خضروات", "piece", emptySet(), listOf("فلفلة", "poivron", "felfel")),
        IngredientMaster("pasta", "معكرونة", "Pâtes", "حبوب ومخبوزات", "g", setOf("gluten"), listOf("مقرونة", "pates", "pasta")),
        IngredientMaster("tomato-paste", "طماطم مصبرة", "Concentré de tomate", "مواد أساسية", "g", emptySet(), listOf("عكري", "concentré tomate")),
        IngredientMaster("potato", "بطاطا", "Pomme de terre", "خضروات", "g", emptySet(), listOf("اطاطبلا", "pomme de terre", "patate", "batata")),
        IngredientMaster("cheese", "جبن", "Fromage", "ألبان وبيض", "g", setOf("milk"), listOf("فرماج", "fromage", "jben"))
    )

    fun validateCatalogue() {
        require(ingredients.map { it.id }.distinct().size == ingredients.size) { "Ingredient IDs must be unique" }
        val ingredientById = ingredients.associateBy { it.id }
        require(all.isNotEmpty()) { "Recipe catalogue must not be empty" }
        all.forEach { recipe ->
            require(recipe.baseServings > 0) { "Recipe ${recipe.id} must have positive base servings" }
            require(recipe.durationMinutes > 0) { "Recipe ${recipe.id} must have positive preparation time" }
            recipe.ingredients.forEach { item ->
                val master = requireNotNull(ingredientById[item.ingredientId]) { "Unknown ingredient ${item.ingredientId} in ${recipe.id}" }
                require(item.unit == master.baseUnit) { "Unit mismatch for ${item.ingredientId} in ${recipe.id}: expected ${master.baseUnit}, got ${item.unit}" }
                require(item.quantityBase > 0.0) { "Non-positive quantity for ${item.ingredientId} in ${recipe.id}" }
                require(recipe.allergens.containsAll(master.allergens)) { "Missing allergen tags for ${item.ingredientId} in ${recipe.id}" }
            }
        }
    }

    val all = listOf(
        MealRecipe(
            id = "couscous-vegetables", nameAr = "كسكس بالخضرة", nameFr = "Couscous aux légumes",
            descriptionAr = "كسكس جزائري بالخضار والحمص وقطع الدجاج.",
            descriptionFr = "Couscous familial aux légumes, pois chiches et poulet.",
            category = "تقليدي", region = "وطني", durationMinutes = 55, baseServings = 4, difficulty = "متوسط",
            allergens = setOf("gluten"),
            ingredients = listOf(
                RecipeIngredient("semolina", 500.0, "g"),
                RecipeIngredient("chicken", 400.0, "g"),
                RecipeIngredient("carrot", 2.0, "piece"),
                RecipeIngredient("zucchini", 2.0, "piece"),
                RecipeIngredient("chickpeas", 120.0, "g"),
                RecipeIngredient("tomato", 250.0, "g")
            ),
            stepsAr = listOf("انقع الحمص مسبقاً واغسله جيداً.", "حضّر مرق الدجاج بالخضار والطماطم والتوابل حتى تنضج.", "بلّل الكسكس واطهه على البخار حتى يصبح خفيفاً ومفلفلاً.", "قدّم الكسكس مع المرق والخضار والدجاج والحمص."),
            stepsFr = listOf("Faites tremper les pois chiches à l’avance.", "Préparez le bouillon avec le poulet, les légumes et les épices.", "Humidifiez la semoule et faites-la cuire à la vapeur.", "Servez avec le bouillon, les légumes et le poulet.")
        ),
        MealRecipe(
            id = "lentil-stew", nameAr = "عدس بالخضرة", nameFr = "Lentilles aux légumes",
            descriptionAr = "طبق دافئ واقتصادي بالعدس والطماطم والجزر.",
            descriptionFr = "Un plat économique de lentilles, tomate et carotte.",
            category = "اقتصادي", region = "وطني", durationMinutes = 35, baseServings = 3, difficulty = "سهل",
            allergens = emptySet(),
            ingredients = listOf(
                RecipeIngredient("lentils", 250.0, "g"),
                RecipeIngredient("onion", 120.0, "g"),
                RecipeIngredient("carrot", 1.0, "piece"),
                RecipeIngredient("tomato", 200.0, "g"),
                RecipeIngredient("garlic", 2.0, "clove"),
                RecipeIngredient("oil", 20.0, "ml")
            ),
            stepsAr = listOf("اغسل العدس وصفّه.", "شوّح البصل والثوم في قليل من الزيت.", "أضف الجزر والطماطم والعدس والماء والتوابل.", "اتركه على نار هادئة حتى ينضج العدس ويثخن المرق."),
            stepsFr = listOf("Rincez et égouttez les lentilles.", "Faites revenir l’oignon et l’ail dans un peu d’huile.", "Ajoutez carotte, tomate, lentilles, eau et épices.", "Laissez mijoter jusqu’à cuisson complète.")
        ),
        MealRecipe(
            id = "shakshuka", nameAr = "شكشوكة", nameFr = "Chakchouka",
            descriptionAr = "بيض مطهو مع الطماطم والفلفل والبصل.",
            descriptionFr = "Œufs mijotés dans une sauce tomate et poivron.",
            category = "سريع", region = "وطني", durationMinutes = 20, baseServings = 2, difficulty = "سهل",
            allergens = setOf("egg"),
            ingredients = listOf(
                RecipeIngredient("eggs", 4.0, "piece"),
                RecipeIngredient("tomato", 300.0, "g"),
                RecipeIngredient("pepper", 1.0, "piece"),
                RecipeIngredient("onion", 100.0, "g"),
                RecipeIngredient("oil", 15.0, "ml")
            ),
            stepsAr = listOf("قطّع الطماطم والفلفل والبصل.", "اطه الخضار في الزيت حتى تتكوّن صلصة كثيفة.", "اصنع فراغات صغيرة وأضف البيض.", "غطّ المقلاة حتى ينضج البيض حسب الرغبة."),
            stepsFr = listOf("Coupez les légumes.", "Faites cuire dans l’huile jusqu’à obtenir une sauce.", "Ajoutez les œufs dans de petits creux.", "Couvrez jusqu’à la cuisson souhaitée.")
        ),
        MealRecipe(
            id = "tomato-pasta", nameAr = "معكرونة بصلصة الطماطم", nameFr = "Pâtes à la tomate",
            descriptionAr = "وجبة يومية بسيطة بمكونات متوفرة.",
            descriptionFr = "Des pâtes faciles avec une sauce tomate maison.",
            category = "اقتصادي", region = "وطني", durationMinutes = 25, baseServings = 3, difficulty = "سهل",
            allergens = setOf("gluten"),
            ingredients = listOf(
                RecipeIngredient("pasta", 300.0, "g"),
                RecipeIngredient("tomato", 250.0, "g"),
                RecipeIngredient("onion", 100.0, "g"),
                RecipeIngredient("garlic", 2.0, "clove"),
                RecipeIngredient("tomato-paste", 30.0, "g"),
                RecipeIngredient("oil", 15.0, "ml")
            ),
            stepsAr = listOf("اسلق المعكرونة في ماء مملح واحتفظ بقليل من ماء السلق.", "شوّح البصل والثوم ثم أضف الطماطم والمصبرة.", "اترك الصلصة تتسبّك وأضف قليلاً من ماء السلق.", "اخلط المعكرونة مع الصلصة وقدّمها ساخنة."),
            stepsFr = listOf("Faites cuire les pâtes dans l’eau salée.", "Faites revenir l’oignon et l’ail puis ajoutez tomate et concentré.", "Laissez réduire la sauce.", "Mélangez les pâtes avec la sauce.")
        ),
        MealRecipe(
            id = "chicken-potatoes", nameAr = "دجاج بالبطاطا في الفرن", nameFr = "Poulet aux pommes de terre au four",
            descriptionAr = "صينية دجاج وبطاطا بتوابل منزلية.",
            descriptionFr = "Un plat familial au four avec poulet et pommes de terre.",
            category = "عائلي", region = "وطني", durationMinutes = 50, baseServings = 4, difficulty = "سهل",
            allergens = emptySet(),
            ingredients = listOf(
                RecipeIngredient("chicken", 600.0, "g"),
                RecipeIngredient("potato", 700.0, "g"),
                RecipeIngredient("onion", 120.0, "g"),
                RecipeIngredient("garlic", 3.0, "clove"),
                RecipeIngredient("oil", 25.0, "ml")
            ),
            stepsAr = listOf("سخّن الفرن إلى 200 درجة مئوية.", "تبّل الدجاج والبطاطا والبصل والثوم بالزيت والتوابل.", "رتّب المكونات في صينية وأضف ربع كوب ماء.", "اطه حتى ينضج الدجاج وتتحمّر البطاطا مع التقليب عند الحاجة."),
            stepsFr = listOf("Préchauffez le four à 200 °C.", "Assaisonnez le poulet et les légumes.", "Placez dans un plat avec un peu d’eau.", "Faites cuire jusqu’à ce que le poulet soit cuit et les pommes de terre dorées.")
        ),
        MealRecipe(
            id = "vegetable-omelette", nameAr = "عجة بالخضرة", nameFr = "Omelette aux légumes",
            descriptionAr = "عجة خفيفة بالبيض والقرعة والجبن.",
            descriptionFr = "Une omelette rapide avec courgette et fromage.",
            category = "سريع", region = "وطني", durationMinutes = 15, baseServings = 2, difficulty = "سهل",
            allergens = setOf("egg", "milk"),
            ingredients = listOf(
                RecipeIngredient("eggs", 3.0, "piece"),
                RecipeIngredient("zucchini", 1.0, "piece"),
                RecipeIngredient("onion", 70.0, "g"),
                RecipeIngredient("cheese", 50.0, "g")
            ),
            stepsAr = listOf("ابشر القرعة وقطّع البصل.", "اطه الخضار في مقلاة حتى تلين.", "اخفق البيض واسكبه فوق الخضار.", "أضف الجبن واطه العجة على نار هادئة."),
            stepsFr = listOf("Râpez la courgette et coupez l’oignon.", "Faites revenir les légumes.", "Battez les œufs et versez-les sur les légumes.", "Ajoutez le fromage et faites cuire doucement.")
        )
    )
}
