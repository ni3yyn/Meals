package dz.meals.app.data

/**
 * Assigns imported recipe ingredients to useful shopping categories.
 *
 * The source catalogue does not provide a dependable category for each
 * ingredient, so classify from its stable source key and Arabic/English name.
 * Unknown items remain in "أخرى" rather than collapsing the whole basket into
 * the meaningless "مكوّن" bucket.
 */
internal object IngredientCategoryClassifier {
    private val seafood = setOf(
        "fish", "salmon", "tuna", "sardine", "shrimp", "prawn", "seafood",
        "cod", "anchovy", "mussel", "clam", "squid", "calamari",
        "poisson", "thon", "saumon", "crevette", "calmar", "moule",
        "سمك", "تونة", "سردين", "جمبري", "قمرون", "روبيان", "حبار", "كلمار", "محار"
    )
    private val meat = setOf(
        "chicken", "poultry", "beef", "lamb", "mutton", "veal", "turkey",
        "duck", "meat", "mince", "sausage", "poulet", "boeuf", "bœuf",
        "agneau", "mouton", "veau", "dinde", "canard",
        "دجاج", "لحم", "لحوم", "غنم", "خروف", "بقر", "عجل", "ديك رومي", "بط", "كبدة", "كبد", "سجق"
    )
    private val dairy = setOf(
        "milk", "cheese", "yogurt", "yoghurt", "butter", "cream", "egg", "eggs",
        "lait", "fromage", "yaourt", "beurre", "creme", "oeuf", "œuf",
        "حليب", "لبن", "جبن", "زبدة", "قشدة", "كريمة", "بيض", "ياغورت", "زبادي"
    )
    private val grains = setOf(
        "semolina", "flour", "wheat", "rice", "pasta", "noodle", "bread",
        "couscous", "oat", "barley", "bulgur", "cereal", "cornmeal",
        "farine", "semoule", "ble", "riz", "pate", "pain", "avoine", "orge", "boulgour", "mais",
        "قمح", "سميد", "دقيق", "فرينة", "أرز", "رز", "معكرونة", "مقرونة", "كسكس", "شوفان", "شعير", "خبز", "نشا"
    )
    private val nutsAndSeeds = setOf(
        "almond", "walnut", "peanut", "pistachio", "cashew", "hazelnut",
        "pecan", "sesame", "chia", "flax", "linseed", "noisette", "amande",
        "pistache", "cajou", "sesame", "graine",
        "لوز", "جوز", "فستق", "كاجو", "بندق", "سمسم", "بذور", "فول سوداني"
    )
    private val legumes = setOf(
        "chickpea", "chickpeas", "lentil", "lentils", "bean", "beans",
        "kidney bean", "broad bean", "fava", "pulse", "pea", "peas",
        "pois chiche", "pois", "lentille", "haricot", "feve",
        "حمص", "عدس", "لوبيا", "فاصوليا", "فول", "جلبانة", "بازلاء"
    )
    private val spicesAndHerbs = setOf(
        "cumin", "paprika", "black pepper", "white pepper", "peppercorn",
        "cinnamon", "turmeric", "ginger", "coriander", "parsley", "mint",
        "thyme", "rosemary", "basil", "saffron", "clove", "cardamom",
        "curry", "chili", "chilli", "seasoning", "spice", "herb", "anise",
        "poivre", "cannelle", "curcuma", "gingembre", "coriandre", "persil",
        "menthe", "thym", "romarin", "basilic", "safran", "girofle",
        "cardamome", "زعفران", "كمون", "فلفل أسود", "قرفة", "كركم", "زنجبيل",
        "كزبرة", "معدنوس", "بقدونس", "نعناع", "زعتر", "إكليل الجبل", "قرنفل", "هيل", "بهارات", "توابل"
    )
    private val pantryBasics = setOf(
        "oil", "olive oil", "vinegar", "salt", "sugar", "honey", "jam",
        "molasses", "cocoa", "chocolate", "tomato paste", "concentrate",
        "ketchup", "mayonnaise", "mustard", "broth", "stock", "sauce",
        "huile", "vinaigre", "sel", "sucre", "miel", "confiture",
        "melasse", "cacao", "chocolat", "concentre", "moutarde", "bouillon",
        "زيت", "خل", "ملح", "سكر", "عسل", "مربى", "دبس", "كاكاو",
        "شوكولاتة", "طماطم مصبرة", "مركز طماطم", "صلصة", "كاتشب", "مايونيز", "خردل", "مرق"
    )
    private val fruit = setOf(
        "apple", "banana", "orange", "lemon", "lime", "strawberry", "berry",
        "grape", "date", "fig", "apricot", "peach", "pear", "mango",
        "pineapple", "kiwi", "avocado", "pomme", "banane", "citron",
        "fraise", "raisin", "datte", "figue", "abricot", "peche", "poire",
        "mangue", "ananas", "avocat",
        "تفاح", "موز", "برتقال", "ليمون", "فراولة", "توت", "عنب", "تمر", "تين",
        "مشمش", "خوخ", "إجاص", "كمثرى", "مانجو", "أناناس", "كيوي", "أفوكادو", "رمان"
    )
    private val vegetables = setOf(
        "tomato", "potato", "carrot", "onion", "garlic", "zucchini",
        "courgette", "eggplant", "aubergine", "cucumber", "lettuce",
        "spinach", "cabbage", "broccoli", "cauliflower", "bell pepper",
        "green pepper", "poivron", "tomate", "pomme de terre", "carotte",
        "oignon", "ail", "aubergine", "concombre", "laitue", "epinard",
        "chou", "brocoli", "chou fleur", "celeri", "poireau", "champignon",
        "potiron", "radis", "betterave", "navet", "artichaut", "gombo",
        "طماطم", "مطيشة", "بطاطا", "جزر", "بصل", "ثوم", "كوسة", "قرعة",
        "باذنجان", "خيار", "خس", "سبانخ", "ملفوف", "كرنب", "زهرة", "قرنبيط",
        "فلفل", "فلفلة", "كرفس", "كراث", "فطر", "مشروم", "يقطين", "قرع",
        "فجل", "شمندر", "لفت", "خرشوف", "هليون", "بامية"
    )

    fun classify(sourceId: String, nameAr: String, nameEn: String): String {
        val value = normalize("$sourceId $nameAr $nameEn")
        return when {
            hasAny(value, seafood) -> "أسماك ومأكولات بحرية"
            hasAny(value, meat) -> "لحوم ودواجن"
            hasAny(value, dairy) -> "ألبان وبيض"
            hasAny(value, grains) -> "حبوب ومخبوزات"
            hasAny(value, nutsAndSeeds) -> "مكسرات وبذور"
            hasAny(value, legumes) -> "بقوليات"
            hasAny(value, spicesAndHerbs) -> "توابل وأعشاب"
            hasAny(value, pantryBasics) -> "مواد أساسية وصلصات"
            hasAny(value, fruit) -> "فواكه"
            hasAny(value, vegetables) -> "خضروات"
            else -> "أخرى"
        }
    }

    private fun hasAny(value: String, terms: Set<String>): Boolean =
        terms.any { term ->
            val normalizedTerm = normalize(term)
            " $value ".contains(" $normalizedTerm ")
        }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[\u064B-\u065F\u0670]"), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
}
