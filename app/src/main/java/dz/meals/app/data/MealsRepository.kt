package dz.meals.app.data

import android.content.Context
import androidx.room.withTransaction
import dz.meals.app.data.local.CookedMealDeductionEntity
import dz.meals.app.data.local.FavoriteRecipeEntity
import dz.meals.app.data.local.FoodCategoryEntity
import dz.meals.app.data.local.FoodSubcategoryEntity
import dz.meals.app.data.local.FoodItemEntity
import dz.meals.app.data.local.IngredientEntity
import dz.meals.app.data.local.MealsDatabase
import dz.meals.app.data.local.MealsDao
import dz.meals.app.data.local.PantryItemEntity
import dz.meals.app.data.local.PlanMealEntity
import dz.meals.app.data.local.PlanPriceSnapshotEntity
import dz.meals.app.data.local.PlanRunEntity
import dz.meals.app.data.local.PriceObservationEntity
import dz.meals.app.data.local.RecipeEntity
import dz.meals.app.data.local.RecipeIngredientEntity
import dz.meals.app.data.local.RecipeStepEntity
import dz.meals.app.data.local.RecipeImageOverrideEntity
import dz.meals.app.data.local.ShoppingItemEntity
import dz.meals.app.domain.calculateBasket
import dz.meals.app.domain.exactPantryDeduction
import dz.meals.app.domain.findLockedAllergyConflict
import dz.meals.app.domain.findPinnedDietConflict
import dz.meals.app.domain.isRecipeAllowed
import dz.meals.app.domain.pricesForSavedPlan
import dz.meals.app.domain.softPreferencePenalty
import dz.meals.app.domain.shouldPreserveMeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.text.Normalizer
import java.time.LocalDate
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

class MealsRepository internal constructor(
    context: Context,
    databaseOverride: MealsDatabase? = null
) {
    private val appContext = context.applicationContext
    private val database = databaseOverride ?: MealsDatabase.getInstance(appContext)
    private val dao: MealsDao = database.mealsDao()
    private val foodIdentityResolver = FoodIdentityResolver(appContext)
    private val commonsClient = OkHttpClient.Builder()
        .callTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val initializationMutex = Mutex()
    private val catalogueCacheMutex = Mutex()
    private var cachedCatalogueRecipes: List<MealRecipe>? = null
    private var cachedCatalogueIngredients: List<IngredientMaster>? = null

    suspend fun ensureInitialized() = initializationMutex.withLock {
        SeedRecipes.validateCatalogue()
        FoodCatalogueInstaller(appContext).installIfNeeded()
        dao.assignUnscopedShoppingItemsToWeek(currentWeekStartIso())
        dao.scopeLegacyGeneratedShoppingItemIds()
        if (dao.recipeCount() > 0) return@withLock
        database.withTransaction {
            dao.insertIngredients(SeedRecipes.ingredients.map {
                IngredientEntity(
                    id = foodIdentityResolver.canonicalId(it.id),
                    nameAr = it.nameAr,
                    nameFr = it.nameFr,
                    categoryId = it.categoryId,
                    subcategoryId = it.subcategoryId,
                    baseUnit = it.baseUnit,
                    allergensCsv = it.allergens.sorted().joinToString(","),
                    aliasesCsv = it.aliases.joinToString("|")
                )
            })
            dao.insertRecipes(SeedRecipes.all.map { recipe ->
                RecipeEntity(
                    id = recipe.id,
                    nameAr = recipe.nameAr,
                    nameFr = recipe.nameFr,
                    descriptionAr = recipe.descriptionAr,
                    descriptionFr = recipe.descriptionFr,
                    category = recipe.category,
                    region = recipe.region,
                    durationMinutes = recipe.durationMinutes,
                    baseServings = recipe.baseServings,
                    difficulty = recipe.difficulty,
                    allergensCsv = recipe.allergens.sorted().joinToString(",")
                )
            })
            dao.insertRecipeIngredients(SeedRecipes.all.flatMap { recipe ->
                recipe.ingredients.mapIndexed { index, item ->
                    RecipeIngredientEntity(
                        recipeId = recipe.id,
                        ingredientId = foodIdentityResolver.canonicalId(item.ingredientId),
                        quantityBase = item.quantityBase,
                        unit = item.unit,
                        prepNote = item.prepNote,
                        optional = item.optional,
                        rowKey = "seed:$index"
                    )
                }
            })
            dao.insertRecipeSteps(SeedRecipes.all.flatMap { recipe ->
                recipe.stepsAr.mapIndexed { index, step ->
                    RecipeStepEntity(
                        recipeId = recipe.id,
                        stepNumber = index + 1,
                        instructionAr = step,
                        instructionFr = recipe.stepsFr.getOrElse(index) { "" }
                    )
                }
            })
            dao.insertPrices(demoPrices())
        }
    }

    suspend fun invalidateCatalogueCache() {
        catalogueCacheMutex.withLock {
            cachedCatalogueRecipes = null
            cachedCatalogueIngredients = null
        }
    }

    suspend fun snapshot(
        weekStartIso: String,
        selectedWilaya: String? = null,
        selectedMealSlots: Set<String> = MealSlots.ids.toSet()
    ): MealsSnapshot = withContext(Dispatchers.Default) {
        ensureInitialized()
        val ingredients = loadIngredients()
        val ingredientTaxonomyById = ingredients.associate { ingredient ->
            ingredient.id to FoodTaxonomyRef(ingredient.categoryId, ingredient.subcategoryId)
        }
        val foodCategories = dao.getFoodCategories().map { it.toModel() }
        val foodSubcategories = dao.getFoodSubcategories().map { it.toModel() }
        val recipes = loadRecipes()
        val savedRun = dao.getPlanRun(weekStartIso)
        val slotsForThisWeek = savedRun?.mealSlotsCsv
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: selectedMealSlots
        val planned = dao.getPlanMeals(weekStartIso)
            .filter { it.mealSlot in slotsForThisWeek }
            .map { it.toModel() }
        // Household stock is intentionally not loaded into the shopping flow.
        // Keep legacy Room rows intact for upgrade compatibility.
        val pantryEntities = emptyList<PantryItemEntity>()
        val pantry = emptyList<PantryItem>()
        val currentPrices = dao.getPriceObservations()
        val savedPriceSnapshot = if (savedRun != null) {
            dao.getPlanPriceSnapshots(weekStartIso).map { it.toPriceObservation() }
        } else emptyList()
        val prices = if (savedRun != null) {
            pricesForSavedPlan(savedPriceSnapshot, currentPrices, savedRun.priceSnapshotAtEpochMillis)
        } else currentPrices
        val planWilaya = savedRun?.wilaya?.takeIf { it.isNotBlank() } ?: selectedWilaya
        val existingShopping = dao.getShoppingItems(weekStartIso)
        // Basket rows frequently share ingredient/unit pairs. Build this lookup
        // once so each generated basket line does not rescan the full shopping list.
        val existingShoppingByIngredientAndUnit = existingShopping.groupBy {
            it.ingredientId to it.unit
        }
        val favoriteRecipeIds = dao.getFavoriteRecipeIds().toSet()
        val checkedItemIds = existingShopping
            .filter { it.checked && it.ingredientId != null }
            .mapNotNull { item ->
                item.ingredientId?.let { "$it::${item.unit}" }
                    ?: item.id.substringAfter("::").takeIf { it.isNotBlank() }
            }.toSet()
        val basket = calculateBasket(
            planned,
            recipes,
            pantryEntities,
            prices,
            checkedItemIds,
            planWilaya,
            allowStaleSnapshotPrices = savedRun != null && savedPriceSnapshot.isNotEmpty(),
            foodTaxonomyByIngredientId = ingredientTaxonomyById
        )
        val names = ingredients.associateBy { it.id }
        val autoItems = basket.items.map { line ->
            val scopedId = "$weekStartIso::${line.id}"
            // Reuse old rows by canonical ingredient and unit, not their legacy
            // embedded IDs. If aliases converge, fold their editable state together.
            val oldRows = existingShoppingByIngredientAndUnit[
                line.ingredientId to line.unit
            ].orEmpty()
            val old = oldRows.maxByOrNull { it.updatedAtEpochMillis }
            val overrides = oldRows.mapNotNull { it.purchaseQuantityOverride }
            val purchaseOverride = overrides.takeIf { it.isNotEmpty() }?.sum()
            val storeNotes = oldRows.map { it.storeNote.trim() }
                .filter { it.isNotEmpty() }.distinct().joinToString(" · ")
            val actualSpend = oldRows.mapNotNull { it.actualSpentDzd }.takeIf { it.isNotEmpty() }?.sum()
            ShoppingItemEntity(
                id = scopedId,
                ingredientId = line.ingredientId,
                customName = names[line.ingredientId]?.nameAr ?: line.name,
                requiredQuantity = line.requiredQuantity,
                pantryQuantity = line.pantryQuantity,
                purchaseQuantity = purchaseOverride ?: line.purchaseQuantity,
                unit = line.unit,
                consumedValueDzd = line.consumedValueDzd,
                cashOutlayDzd = if (purchaseOverride != null && line.purchaseQuantity > 0.0) {
                    line.cashOutlayDzd?.times(purchaseOverride / line.purchaseQuantity)
                } else line.cashOutlayDzd,
                priceLabel = if (purchaseOverride != null) "تقدير معدل يدوياً" else line.priceLabel,
                sourceLabel = line.sourceLabel,
                isApproximatePantry = line.isApproximatePantry,
                checked = oldRows.any { it.checked },
                userAdded = false,
                updatedAtEpochMillis = old?.updatedAtEpochMillis ?: System.currentTimeMillis(),
                weekStartIso = weekStartIso,
                purchaseQuantityOverride = purchaseOverride,
                storeNote = storeNotes,
                isUnavailable = oldRows.any { it.isUnavailable },
                actualSpentDzd = actualSpend
            )
        }
        database.withTransaction {
            dao.deleteGeneratedShoppingItems(weekStartIso)
            if (autoItems.isNotEmpty()) dao.upsertShoppingItems(autoItems)
        }
        val shoppingItems = dao.getShoppingItems(weekStartIso).map { it.toModel(names[it.ingredientId]) }
        MealsSnapshot(
            recipes = recipes,
            ingredients = ingredients,
            plannedMeals = planned,
            pantry = pantry,
            shoppingItems = shoppingItems,
            priceObservations = currentPrices.mapNotNull { observation ->
                val master = names[observation.ingredientId] ?: return@mapNotNull null
                PriceObservationSummary(
                    id = observation.id,
                    ingredientId = observation.ingredientId,
                    ingredientNameAr = master.nameAr,
                    packQuantityBase = observation.packQuantityBase,
                    unit = observation.unit,
                    packPriceDzd = observation.packPriceDzd,
                    sourceLabel = observation.sourceLabel,
                    locationLabel = observation.locationLabel,
                    observedAtEpochMillis = observation.observedAtEpochMillis,
                    priceKind = observation.priceKind,
                    confidenceLabel = observation.confidenceLabel,
                    categoryId = master.categoryId,
                    subcategoryId = master.subcategoryId,
                    isStale = observation.priceKind != "demo" &&
                        (observation.observedAtEpochMillis == null ||
                            System.currentTimeMillis() < observation.observedAtEpochMillis ||
                            System.currentTimeMillis() - observation.observedAtEpochMillis > 30L * 24L * 60L * 60L * 1000L)
                )
            }.sortedByDescending { it.observedAtEpochMillis ?: 0L },
            favoriteRecipeIds = favoriteRecipeIds,
            planRun = savedRun?.toModel(),
            basket = basket.copy(
                items = shoppingItems,
                purchaseOutlayDzd = shoppingItems.sumOf { it.cashOutlayDzd ?: 0.0 },
                unknownPriceCount = shoppingItems.count { it.cashOutlayDzd == null && it.purchaseQuantity > 0.0 },
                actualSpendDzd = shoppingItems.sumOf { it.actualSpentDzd ?: 0.0 }
            ),
            foodCategories = foodCategories,
            foodSubcategories = foodSubcategories
        )
    }

    suspend fun generateWeek(
        weekStartIso: String,
        weeklyBudgetDzd: Int,
        householdServings: Int,
        allergyIds: Set<String>,
        selectedWilaya: String? = null,
        selectedMealSlots: Set<String> = MealSlots.ids.toSet(),
        dislikedIngredientIds: Set<String> = emptySet(),
        dietPattern: String = "none",
        maxCookingMinutes: Int = 60,
        budgetBufferPercent: Int = 5,
        fillEmptyOnly: Boolean = false
    ): GenerationResult {
        ensureInitialized()
        val canonicalDislikedIngredientIds = dislikedIngredientIds
            .mapTo(linkedSetOf(), foodIdentityResolver::canonicalId)
        val recipes = loadRecipes()
        // Temporary default: only the separately curated Algerian collection can
        // enter generated plans. Settings will later supply the selected cuisines
        // through this centralized policy.
        // Automatic generation accepts only explicitly classified Algerian main dishes.
        val generatorRecipes = recipes.filter(PlanCuisinePolicy::allowsMainDish)
        val validSlots = selectedMealSlots.intersect(MealSlots.ids.toSet())
        if (validSlots.isEmpty()) {
            return GenerationResult(false, "اختر وجبة واحدة على الأقل لتخطيط الأسبوع.", 0, weeklyBudgetDzd)
        }
        val existingMeals = dao.getPlanMeals(weekStartIso)
            .filter { it.mealSlot in validSlots }
            .map { it.toModel() }
        val locked = existingMeals.filter { shouldPreserveMeal(it, fillEmptyOnly) }
        val lockedConflict = findLockedAllergyConflict(locked, recipes, allergyIds)
        if (lockedConflict != null) {
            return GenerationResult(
                success = false,
                message = "توجد وجبة مثبتة لا توافق قيود الحساسية الحالية. استبدلها أو غيّر قيود الطعام قبل إنشاء الخطة. لم نغيّر أي وجبة.",
                purchaseOutlayDzd = 0,
                budgetDzd = weeklyBudgetDzd
            )
        }
        val ingredientsById = loadIngredients().associateBy { it.id }
        val ingredientTaxonomyById = ingredientsById.mapValues { (_, ingredient) ->
            FoodTaxonomyRef(ingredient.categoryId, ingredient.subcategoryId)
        }
        val pinnedDietConflict = findPinnedDietConflict(locked, recipes, ingredientTaxonomyById, dietPattern)
        if (pinnedDietConflict != null) {
            return GenerationResult(
                success = false,
                message = "توجد وجبة مثبتة لا توافق النمط الغذائي الحالي. استبدلها أو غيّر النمط قبل إنشاء الخطة. لم نغيّر أي وجبة.",
                purchaseOutlayDzd = 0,
                budgetDzd = weeklyBudgetDzd
            )
        }
        val allowed = generatorRecipes.filter { recipe ->
            isRecipeAllowed(recipe, ingredientTaxonomyById, allergyIds, dietPattern)
        }
        val pantry = dao.getPantryItems()
        val prices = dao.getPriceObservations()
        if (allowed.isEmpty()) {
            return GenerationResult(false, "لا توجد أطباق رئيسية جزائرية متاحة بعد تطبيق قيود الحساسية والنمط الغذائي. لم نغيّر خطتك.", 0, weeklyBudgetDzd)
        }

        val slots = (0 until 7).flatMap { day ->
            MealSlots.ids.filter { it in validSlots }.map { slot -> "$weekStartIso-$day-$slot" to (day to slot) }
        }
        val targetBudget = weeklyBudgetDzd * (100 - budgetBufferPercent.coerceIn(0, 30)) / 100.0
        val chosen = locked.associateBy { it.id }.toMutableMap()
        val repeats = locked.mapNotNull { it.recipeId }.groupingBy { it }.eachCount().toMutableMap()

        slots.forEach { (id, position) ->
            if (id in chosen) return@forEach
            val (day, slot) = position
            val candidates = allowed
                .filter { (repeats[it.id] ?: 0) < 4 }
                .filter { recipe -> chosen.values.none { it.dayIndex == day && it.recipeId == recipe.id } }
            val candidate = candidates.minByOrNull { recipe ->
                val trial = chosen.values.toList() + PlannedMeal(
                    id, weekStartIso, day, slot, recipe.id, householdServings.coerceIn(1, 12), false
                )
                val basket = calculateBasket(
                    trial,
                    recipes,
                    pantry,
                    prices,
                    selectedWilaya = selectedWilaya,
                    foodTaxonomyByIngredientId = ingredientTaxonomyById
                )
                val repeatPenalty = (repeats[recipe.id] ?: 0) * 420.0
                val overBudgetPenalty = (basket.purchaseOutlayDzd - targetBudget).coerceAtLeast(0.0) * 3.0
                val preferencePenalty = softPreferencePenalty(recipe, canonicalDislikedIngredientIds, maxCookingMinutes)
                basket.purchaseOutlayDzd + repeatPenalty + overBudgetPenalty + preferencePenalty
            } ?: allowed.minBy { it.id }

            chosen[id] = PlannedMeal(
                id = id,
                weekStartIso = weekStartIso,
                dayIndex = day,
                mealSlot = slot,
                recipeId = candidate.id,
                servings = householdServings.coerceIn(1, 12),
                locked = false
            )
            repeats[candidate.id] = (repeats[candidate.id] ?: 0) + 1
        }

        val generatedAt = System.currentTimeMillis()
        val priceSnapshotAt = generatedAt
        database.withTransaction {
            dao.deletePlanPriceSnapshots(weekStartIso)
            if (prices.isNotEmpty()) {
                dao.insertPlanPriceSnapshots(prices.map { it.toPlanSnapshot(weekStartIso) })
            }
            dao.deletePlanSlots(weekStartIso, validSlots.toList())
            dao.upsertPlanMeals(chosen.values.sortedWith(compareBy<PlannedMeal> { it.dayIndex }.thenBy { it.mealSlot }).map { it.toEntity() })
            dao.upsertPlanRun(
                PlanRunEntity(
                    weekStartIso = weekStartIso,
                    engineVersion = "0.3.0",
                    generatedAtEpochMillis = generatedAt,
                    priceSnapshotAtEpochMillis = priceSnapshotAt,
                    budgetDzd = weeklyBudgetDzd,
                    wilaya = selectedWilaya.orEmpty(),
                    mealSlotsCsv = validSlots.sorted().joinToString(",")
                )
            )
        }
        val result = calculateBasket(
            chosen.values.toList(),
            recipes,
            pantry,
            prices,
            selectedWilaya = selectedWilaya,
            foodTaxonomyByIngredientId = ingredientTaxonomyById
        )
        val within = result.unknownPriceCount == 0 && result.purchaseOutlayDzd <= weeklyBudgetDzd
        return GenerationResult(
            success = true,
            message = if (within) "أنشأنا خطة أولية ضمن ميزانيتك التقديرية." else "أنشأنا خطة أولية، لكنها تتجاوز الميزانية أو تحتوي على أسعار غير متوفرة. راجع الوجبات قبل التسوق.",
            purchaseOutlayDzd = result.purchaseOutlayDzd.toInt(),
            budgetDzd = weeklyBudgetDzd
        )
    }

    suspend fun setMeal(weekStartIso: String, dayIndex: Int, mealSlot: String, recipeId: String?, servings: Int) {
        val id = "$weekStartIso-$dayIndex-$mealSlot"
        val old = dao.getPlanMeals(weekStartIso).firstOrNull { it.id == id }
        if (old?.isCooked == true) return
        dao.upsertPlanMeal(
            PlanMealEntity(
                id = id,
                weekStartIso = weekStartIso,
                dayIndex = dayIndex,
                mealSlot = mealSlot,
                recipeId = recipeId,
                servings = servings.coerceIn(1, 12),
                locked = old?.locked ?: false,
                isCooked = old?.isCooked ?: false,
                isUserEdited = true
            )
        )
    }

    suspend fun toggleMealLock(id: String, weekStartIso: String) {
        val meal = dao.getPlanMeals(weekStartIso).firstOrNull { it.id == id } ?: return
        dao.upsertPlanMeal(meal.copy(locked = !meal.locked))
    }

    suspend fun toggleMealCooked(id: String, weekStartIso: String) {
        val current = dao.getPlanMeals(weekStartIso).firstOrNull { it.id == id } ?: return
        // Cooking completion is a meal-planning status only. Midati no longer
        // maintains or deducts household stock behind the shopping list.
        dao.upsertPlanMeal(current.copy(isCooked = !current.isCooked))
    }

    suspend fun adjustMealServings(id: String, weekStartIso: String, delta: Int) {
        val meal = dao.getPlanMeals(weekStartIso).firstOrNull { it.id == id } ?: return
        if (meal.isCooked) return
        dao.upsertPlanMeal(
            meal.copy(
                servings = (meal.servings + delta).coerceIn(1, 12),
                isUserEdited = true
            )
        )
    }

    suspend fun addRecipeToNextSlot(weekStartIso: String, recipeId: String, servings: Int) {
        val current = dao.getPlanMeals(weekStartIso)
        val empty = (0 until 7).asSequence()
            .flatMap { day -> MealSlots.ids.asSequence().map { slot -> day to slot } }
            .firstOrNull { (day, slot) -> current.none { it.dayIndex == day && it.mealSlot == slot && it.recipeId != null } }
            ?: return
        setMeal(weekStartIso, empty.first, empty.second, recipeId, servings)
    }

    suspend fun updatePantry(
        ingredientId: String,
        quantityBase: Double?,
        isApproximate: Boolean,
        note: String = "",
        expiryAtEpochMillis: Long? = null
    ) {
        // The UI can supply either a canonical ingredient ID (new stock) or the
        // persisted pantry row ID (editing/removing an existing row).
        val existingRow = dao.getPantryItem(ingredientId)
        val canonicalIngredientId = existingRow?.ingredientId ?: foodIdentityResolver.canonicalId(ingredientId)
        // Most catalogue foods live in food_items, not the legacy ingredients
        // table. Resolve that canonical master row as a fallback so every one of
        // the 4,649 searchable foods can be added to pantry on first use.
        val ingredient = dao.getIngredients()
            .firstOrNull { it.id == canonicalIngredientId }
            ?.toModel()
            ?: dao.getFoodItem(canonicalIngredientId)?.toIngredientMaster()
            ?: return
        val targetUnit = existingRow?.unit ?: ingredient.baseUnit
        val pantryRowId = existingRow?.id ?: canonicalIngredientId

        // Replace merged alias rows atomically, retaining the edited row's unit
        // and ID so cooking deductions can still restore stock to that row.
        database.withTransaction {
            dao.deletePantryItemsForIngredientUnit(canonicalIngredientId, targetUnit)
            if (quantityBase != null || isApproximate) {
                dao.upsertPantryItem(
                    PantryItemEntity(
                        id = pantryRowId,
                        ingredientId = canonicalIngredientId,
                        quantityBase = if (isApproximate) null else quantityBase?.coerceAtLeast(0.0),
                        unit = targetUnit,
                        isApproximate = isApproximate,
                        note = note,
                        expiryAtEpochMillis = expiryAtEpochMillis
                    )
                )
            }
        }
    }

    suspend fun recordPriceObservation(
        ingredientId: String,
        packQuantityBase: Double,
        packPriceDzd: Double,
        selectedWilaya: String,
        shopContext: String
    ) {
        require(packQuantityBase > 0.0) { "Pack quantity must be positive" }
        require(packPriceDzd > 0.0) { "Price must be positive" }
        val canonicalIngredientId = foodIdentityResolver.canonicalId(ingredientId)
        val ingredient = loadIngredients().firstOrNull { it.id == canonicalIngredientId }
            ?: throw IllegalArgumentException("Unknown ingredient")
        val wilaya = selectedWilaya.trim()
        require(wilaya.isNotEmpty()) { "Wilaya is required" }
        val context = shopContext.trim().ifBlank { "نوع المحل غير محدد" }
        dao.upsertPriceObservation(
            PriceObservationEntity(
                id = "user-" + UUID.randomUUID().toString(),
                ingredientId = canonicalIngredientId,
                packQuantityBase = packQuantityBase,
                unit = ingredient.baseUnit,
                packPriceDzd = packPriceDzd,
                sourceLabel = "بلاغ محلي · " + context,
                locationLabel = wilaya,
                observedAtEpochMillis = System.currentTimeMillis(),
                priceKind = "user_reported",
                confidenceLabel = "غير موثّق"
            )
        )
    }

    suspend fun searchCommonsRecipeImages(
        recipe: MealRecipe,
        query: String
    ): List<RecipeImageCandidate> = withContext(Dispatchers.IO) {
        val typedTerm = query.trim().take(100)
        require(typedTerm.isNotBlank()) { "أدخلي اسم الطبق للبحث عن صورة." }
        val sourceName = recipe.nameFr.trim().takeIf {
            it.isNotBlank() && !it.equals(recipe.nameAr, ignoreCase = true) &&
                !typedTerm.contains(it, ignoreCase = true)
        }.orEmpty()
        val searchTerm = listOf(typedTerm, sourceName).filter { it.isNotBlank() }.distinct().joinToString(" ").take(140)

        val requestUrl = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", "$searchTerm filetype:bitmap")
            .addQueryParameter("gsrnamespace", "6")
            .addQueryParameter("gsrlimit", "18")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|extmetadata|size|mime")
            .addQueryParameter("iiurlwidth", "640")
            .addQueryParameter(
                "iiextmetadatafilter",
                "Artist|Credit|LicenseShortName|LicenseUrl|Copyrighted"
            )
            .build()
        val request = Request.Builder()
            .url(requestUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "Midati-Android/1.0 (https://github.com/ni3yyn/Meals)")
            .build()

        val body = commonsClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("تعذر البحث في Wikimedia Commons (HTTP ${response.code}).")
            }
            response.body?.string() ?: throw IOException("لم تصل نتائج البحث عن الصور.")
        }
        val pages = JSONObject(body).optJSONObject("query")?.optJSONArray("pages")
            ?: return@withContext emptyList()

        buildList {
            for (index in 0 until pages.length()) {
                val page = pages.optJSONObject(index) ?: continue
                val title = page.optString("title").trim()
                if (!title.startsWith("File:", ignoreCase = true)) continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val mime = info.optString("mime")
                if (!mime.startsWith("image/")) continue
                val width = info.optInt("width")
                val height = info.optInt("height")
                if (width < 320 || height < 220) continue

                val metadata = info.optJSONObject("extmetadata") ?: continue
                fun metadataValue(key: String): String =
                    metadata.optJSONObject(key)?.optString("value").orEmpty().stripCommonsMarkup()

                val license = metadataValue("LicenseShortName")
                val licenseUrl = metadataValue("LicenseUrl")
                if (!isReusableCommonsImageLicense(license, licenseUrl)) continue

                val artist = metadataValue("Artist")
                val credit = metadataValue("Credit")
                val author = artist.ifBlank { credit }
                if (author.isBlank()) continue

                // Never fall back to imageinfo.url: that is the full-resolution original.
                // A Commons result without a generated thumbnail is not safe to display.
                val imageUrl = info.optString("thumburl").trim()
                val sourcePageUrl = info.optString("descriptionurl")
                if (!imageUrl.startsWith("https://upload.wikimedia.org/")) continue
                if (!sourcePageUrl.startsWith("https://commons.wikimedia.org/wiki/File:")) continue

                add(
                    RecipeImageCandidate(
                        title = title.removePrefix("File:"),
                        imageUrl = imageUrl,
                        sourcePageUrl = sourcePageUrl,
                        author = author,
                        credit = credit.ifBlank { author },
                        license = license,
                        licenseUrl = licenseUrl,
                        width = width,
                        height = height
                    )
                )
            }
        }.take(18)
    }

    /**
     * Finds and saves a high-confidence Commons match without opening the manual picker.
     * If the filename does not meaningfully match one of the recipe's known names, no
     * override is written and the caller can offer manual search instead.
     */
    suspend fun automaticallyFindAndSaveRecipeImage(recipe: MealRecipe): RecipeImageCandidate {
        val query = sequenceOf(recipe.nativeName, recipe.nameFr, recipe.nameAr)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: throw IOException("لا يتوفر اسم مناسب للبحث عن صورة هذه الوصفة.")

        val candidates = searchCommonsRecipeImages(recipe, query)
        val bestMatch = candidates
            .map { candidate -> candidate to commonsRecipeImageMatchScore(recipe, candidate) }
            .filter { (_, score) -> score >= 0.5 }
            .maxByOrNull { (_, score) -> score }
            ?.first
            ?: throw IOException("لم نعثر على صورة مرخّصة يمكن مطابقتها بثقة. يمكنك البحث يدوياً.")

        saveRecipeImage(recipe.id, bestMatch)
        return bestMatch
    }

    suspend fun saveRecipeImage(recipeId: String, image: RecipeImageCandidate) {
        require(image.imageUrl.startsWith("https://upload.wikimedia.org/")) {
            "رابط الصورة غير موثوق."
        }
        require(image.sourcePageUrl.startsWith("https://commons.wikimedia.org/wiki/File:")) {
            "رابط مصدر الصورة غير صالح."
        }
        require(isReusableCommonsImageLicense(image.license, image.licenseUrl)) {
            "ترخيص هذه الصورة لا يسمح بإعادة استخدامها داخل التطبيق."
        }
        require(image.author.isNotBlank() && image.credit.isNotBlank()) {
            "تفتقد الصورة إلى بيانات النسب المطلوبة."
        }
        require(dao.getRecipes().any { it.id == recipeId }) { "الوصفة غير موجودة." }

        database.withTransaction {
            dao.upsertRecipeImageOverride(
                RecipeImageOverrideEntity(
                    recipeId = recipeId,
                    imageUrl = image.imageUrl,
                    sourcePageUrl = image.sourcePageUrl,
                    author = image.author,
                    credit = image.credit,
                    license = image.license,
                    licenseUrl = image.licenseUrl,
                    selectedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
        invalidateCatalogueCache()
    }

    suspend fun removeRecipeImageOverride(recipeId: String) {
        dao.removeRecipeImageOverride(recipeId)
        invalidateCatalogueCache()
    }

    suspend fun toggleFavorite(recipeId: String) {
        if (dao.getFavoriteRecipeIds().contains(recipeId)) {
            dao.removeFavoriteRecipe(recipeId)
        } else if (dao.getRecipes().any { it.id == recipeId }) {
            dao.upsertFavoriteRecipe(
                FavoriteRecipeEntity(
                    recipeId = recipeId,
                    addedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun toggleShoppingItem(id: String, checked: Boolean) {
        dao.setShoppingItemChecked(id, checked, System.currentTimeMillis())
    }

    suspend fun addCatalogueShoppingItem(
        weekStartIso: String,
        ingredientId: String
    ) {
        ensureInitialized()
        val canonicalId = foodIdentityResolver.canonicalId(ingredientId)
        val ingredient = loadIngredients().firstOrNull { it.id == canonicalId } ?: return
        val unit = ingredient.baseUnit.trim().ifBlank { "piece" }
        val existing = dao.getShoppingItems(weekStartIso).firstOrNull {
            it.ingredientId == canonicalId && it.unit == unit
        }
        if (existing != null) return

        // Match the generated row's stable key. If a plan later needs this food,
        // regeneration upgrades this manual line in place instead of duplicating it.
        val id = "$weekStartIso::$canonicalId::$unit"
        dao.upsertShoppingItems(
            listOf(
                ShoppingItemEntity(
                    id = id,
                    ingredientId = canonicalId,
                    customName = ingredient.nameAr,
                    requiredQuantity = 1.0,
                    pantryQuantity = 0.0,
                    purchaseQuantity = 1.0,
                    unit = unit,
                    consumedValueDzd = null,
                    cashOutlayDzd = null,
                    priceLabel = null,
                    sourceLabel = "أضيف يدوياً من قاموس Midati",
                    isApproximatePantry = false,
                    checked = false,
                    userAdded = true,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    weekStartIso = weekStartIso,
                    purchaseQuantityOverride = null
                )
            )
        )
    }

    suspend fun addCustomShoppingItem(
        weekStartIso: String,
        name: String,
        quantity: Double,
        unit: String,
        estimatedPriceDzd: Double? = null
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || quantity <= 0.0 || (estimatedPriceDzd != null && estimatedPriceDzd < 0.0)) return
        val id = "custom-" + UUID.randomUUID().toString()
        dao.upsertShoppingItems(
            listOf(
                ShoppingItemEntity(
                    id = id,
                    ingredientId = null,
                    customName = cleanName,
                    requiredQuantity = quantity,
                    pantryQuantity = 0.0,
                    purchaseQuantity = quantity,
                    unit = unit.trim().ifBlank { "حبة" },
                    consumedValueDzd = null,
                    cashOutlayDzd = estimatedPriceDzd,
                    priceLabel = if (estimatedPriceDzd != null) "سعر يدوي" else null,
                    sourceLabel = if (estimatedPriceDzd != null) "سعر أدخله المستخدم · غير موثّق" else null,
                    isApproximatePantry = false,
                    checked = false,
                    userAdded = true,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    weekStartIso = weekStartIso
                )
            )
        )
    }

    suspend fun updateShoppingItemDetails(
        id: String,
        purchaseQuantity: Double?,
        storeNote: String,
        unavailable: Boolean,
        actualSpentDzd: Double?
    ) {
        val current = dao.getShoppingItem(id) ?: return
        if (purchaseQuantity != null && purchaseQuantity <= 0.0) return
        if (actualSpentDzd != null && actualSpentDzd < 0.0) return
        dao.upsertShoppingItems(
            listOf(
                current.copy(
                    purchaseQuantityOverride = purchaseQuantity,
                    purchaseQuantity = purchaseQuantity ?: current.purchaseQuantity,
                    cashOutlayDzd = if (purchaseQuantity != null && current.purchaseQuantity > 0.0) {
                        current.cashOutlayDzd?.times(purchaseQuantity / current.purchaseQuantity)
                    } else current.cashOutlayDzd,
                    priceLabel = if (purchaseQuantity != null) "تقدير معدل يدوياً" else current.priceLabel,
                    storeNote = storeNote.trim().take(100),
                    isUnavailable = unavailable,
                    actualSpentDzd = actualSpentDzd,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
        )
    }

    suspend fun updateCustomShoppingItem(id: String, name: String, quantity: Double, unit: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || quantity <= 0.0) return
        val current = dao.getCustomShoppingItem(id) ?: return
        dao.upsertShoppingItems(
            listOf(
                current.copy(
                    customName = cleanName,
                    requiredQuantity = quantity,
                    purchaseQuantity = quantity,
                    unit = unit.trim().ifBlank { "حبة" },
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
        )
    }

    suspend fun deleteCustomShoppingItem(id: String) {
        dao.deleteCustomShoppingItem(id)
    }

    private suspend fun loadIngredients(): List<IngredientMaster> = catalogueCacheMutex.withLock {
        cachedCatalogueIngredients ?: run {
            val catalogueInstalled = hasInstalledArabicRecipeCatalogue()
            val unpublishedIngredientIds = if (catalogueInstalled) {
                emptySet()
            } else {
                dao.getUnpublishedArabicCatalogueIngredientIds().toSet()
            }
            val merged = dao.getIngredients()
                .filter { ingredient -> ingredient.id !in unpublishedIngredientIds }
                .map { it.toModel() }
                .associateBy { it.id }
                .toMutableMap()
            val categoriesById = dao.getFoodCategories().associateBy { it.id }
            val subcategoriesById = dao.getFoodSubcategories().associateBy { it.id }
            dao.getFoodItems().forEach { food ->
                if (categoriesById[food.categoryId] == null) {
                    throw IllegalStateException(
                        "Food ${food.id} references missing category ${food.categoryId}."
                    )
                }
                val subcategory = subcategoriesById[food.subcategoryId]
                    ?: throw IllegalStateException(
                        "Food ${food.id} references missing subcategory ${food.subcategoryId}."
                    )
                if (subcategory.categoryId != food.categoryId) {
                    throw IllegalStateException(
                        "Food ${food.id} has a mismatched category/subcategory reference."
                    )
                }
                val catalogueMaster = food.toIngredientMaster()
                val localMaster = merged[food.id]
                merged[food.id] = catalogueMaster.copy(
                    // Keep curated allergen knowledge and Algerian/Darija aliases
                    // from the existing seed/master rows while replacing heuristic
                    // names and categories with the canonical catalogue metadata.
                    allergens = localMaster?.allergens.orEmpty(),
                    aliases = (catalogueMaster.aliases + localMaster?.aliases.orEmpty()).distinct()
                )
            }
            merged.values.sortedBy { it.nameAr }
                .also { cachedCatalogueIngredients = it }
        }
    }

    private suspend fun loadRecipes(): List<MealRecipe> = catalogueCacheMutex.withLock {
        cachedCatalogueRecipes ?: loadRecipesFromDatabase().also { cachedCatalogueRecipes = it }
    }

    private suspend fun hasInstalledArabicRecipeCatalogue(): Boolean =
        appContext.getSharedPreferences("arabic_recipe_catalogue", Context.MODE_PRIVATE).let { preferences ->
            preferences.getString("installed_revision", null) == ArabicRecipeCatalogueDownloader.REVISION &&
                preferences.getInt(
                    ArabicRecipeCatalogueInstaller.METADATA_MAPPING_VERSION_KEY,
                    0
                ) == ArabicRecipeCatalogueInstaller.METADATA_MAPPING_VERSION &&
                dao.countArabicCatalogueRecipes() == ArabicRecipeCatalogueDownloader.EXPECTED_RECIPE_COUNT
        }

    private suspend fun loadRecipesFromDatabase(): List<MealRecipe> {
        val ingredients = dao.getRecipeIngredients().groupBy { it.recipeId }
        val steps = dao.getRecipeSteps().groupBy { it.recipeId }
        val attributions = dao.getRecipeAttributions().associateBy { it.recipeId }
        val imageOverrides = dao.getRecipeImageOverrides().associateBy { it.recipeId }
        // Hide partially imported community rows until the entire pinned
        // catalogue revision has passed validation and has been committed.
        val catalogueInstalled = hasInstalledArabicRecipeCatalogue()
        return dao.getRecipes()
            .filter { recipe ->
                !(recipe.id.startsWith(ArabicRecipeCatalogueInstaller.RECIPE_ID_PREFIX) ||
                    recipe.id.startsWith(ArabicRecipeCatalogueInstaller.FOCUS_RECIPE_ID_PREFIX) ||
                    recipe.id.startsWith(ArabicRecipeCatalogueInstaller.UNITOOLS_RECIPE_ID_PREFIX)) || catalogueInstalled
            }
            .map { recipe ->
                val recipeSteps = steps[recipe.id].orEmpty().sortedBy { it.stepNumber }
                val imageOverride = imageOverrides[recipe.id]
                MealRecipe(
                    id = recipe.id,
                    nameAr = recipe.nameAr,
                    nameFr = recipe.nameFr,
                    descriptionAr = recipe.descriptionAr,
                    descriptionFr = recipe.descriptionFr,
                    category = recipe.category,
                    region = recipe.region,
                    durationMinutes = recipe.durationMinutes,
                    baseServings = recipe.baseServings,
                    difficulty = recipe.difficulty,
                    allergens = recipe.allergensCsv.split(",").filter { it.isNotBlank() }.toSet(),
                    ingredients = ingredients[recipe.id].orEmpty().map {
                        RecipeIngredient(it.ingredientId, it.quantityBase, it.unit, it.prepNote, it.optional, it.scalingRule)
                    },
                    stepsAr = recipeSteps.map { it.instructionAr },
                    stepsFr = recipeSteps.map { it.instructionFr },
                    attribution = attributions[recipe.id]?.let {
                        RecipeAttribution(
                            sourceTitle = it.sourceTitle,
                            sourceUrl = it.sourceUrl,
                            revisionUrl = it.revisionUrl,
                            historyUrl = it.historyUrl,
                            credit = it.credit,
                            sourceLicense = it.sourceLicense,
                            license = it.license,
                            licenseUrl = it.licenseUrl,
                            changes = it.changes
                        )
                    },
                    tags = recipe.tagsCsv.split("|").filter { it.isNotBlank() },
                    categories = recipe.categoriesCsv.split("|").filter { it.isNotBlank() },
                    dietary = recipe.dietaryCsv.split("|").filter { it.isNotBlank() },
                    prepMinutes = recipe.prepMinutes,
                    cookMinutes = recipe.cookMinutes,
                    caloriesPerServing = recipe.caloriesPerServing,
                    proteinGramsPerServing = recipe.proteinGramsPerServing,
                    carbsGramsPerServing = recipe.carbsGramsPerServing,
                    fatGramsPerServing = recipe.fatGramsPerServing,
                    imageUrl = imageOverride?.imageUrl ?: recipe.imageUrl,
                    nativeName = recipe.nativeName,
                    countryCode = recipe.countryCode,
                    imageAuthor = imageOverride?.author ?: recipe.imageAuthor,
                    imageLicense = imageOverride?.license ?: recipe.imageLicense,
                    stepMinutes = recipeSteps.map { it.durationMinutes },
                    imageSourcePageUrl = imageOverride?.sourcePageUrl.orEmpty(),
                    imageLicenseUrl = imageOverride?.licenseUrl.orEmpty(),
                    imageCredit = imageOverride?.credit ?: recipe.imageAuthor
                )
            }
    }

    private fun demoPrices(): List<PriceObservationEntity> {
        // Sample values to exercise package rounding only; not current or verified quotations.
        val samples = listOf(
            Triple("semolina", 1_000.0, 150.0),
            Triple("chicken", 1_000.0, 650.0),
            Triple("carrot", 1.0, 10.0),
            Triple("zucchini", 1.0, 45.0),
            Triple("chickpeas", 1_000.0, 220.0),
            Triple("tomato", 1_000.0, 140.0),
            Triple("lentils", 1_000.0, 260.0),
            Triple("onion", 1_000.0, 110.0),
            Triple("garlic", 1.0, 15.0),
            Triple("oil", 1_000.0, 240.0),
            Triple("eggs", 6.0, 150.0),
            Triple("pepper", 1.0, 65.0),
            Triple("pasta", 500.0, 95.0),
            Triple("tomato-paste", 140.0, 85.0),
            Triple("potato", 1_000.0, 90.0),
            Triple("cheese", 200.0, 180.0)
        )
        val units = SeedRecipes.ingredients.associate { it.id to it.baseUnit }
        return samples.map { (id, pack, price) ->
            PriceObservationEntity(
                id = "demo-$id",
                ingredientId = foodIdentityResolver.canonicalId(id),
                packQuantityBase = pack,
                unit = units.getValue(id),
                packPriceDzd = price,
                sourceLabel = "قيم تجريبية داخل التطبيق",
                locationLabel = "غير مرتبطة بولاية",
                observedAtEpochMillis = null,
                priceKind = "demo",
                confidenceLabel = "غير موثّق"
            )
        }
    }
}

private val commonsImageStopWords = setOf(
    "recipe", "recipes", "dish", "food", "image", "photo", "picture", "cuisine",
    "traditional", "algerian", "recette", "recettes", "plat", "plats",
    "the", "and", "with", "from", "for", "of", "in", "on", "to",
    "les", "des", "une", "un", "aux", "avec", "pour", "dans", "sur", "du", "de", "la", "le", "et"
)

private fun commonsRecipeImageMatchScore(
    recipe: MealRecipe,
    candidate: RecipeImageCandidate
): Double {
    val candidateTokens = commonsImageTokens(candidate.title)
    if (candidateTokens.isEmpty()) return 0.0

    val nameVariants = listOf(recipe.nativeName, recipe.nameFr, recipe.nameAr)
        .map(::commonsImageTokens)
        .filter { it.isNotEmpty() }

    return nameVariants.maxOfOrNull { tokens ->
        tokens.count { it in candidateTokens }.toDouble() / tokens.size
    } ?: 0.0
}

private fun commonsImageTokens(value: String): Set<String> {
    val normalized = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
    return normalized
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 2 && it !in commonsImageStopWords }
        .toSet()
}

private fun isReusableCommonsImageLicense(name: String, url: String): Boolean {
    val normalized = name.trim().lowercase()
    val excludesCommercialOrDerivatives = Regex("\\b(?:nc|nd)\\b").containsMatchIn(normalized)
    if (excludesCommercialOrDerivatives) return false
    val permittedName = normalized.startsWith("cc by") ||
        normalized.startsWith("cc0") ||
        normalized.contains("public domain")
    if (!permittedName) return false
    return normalized.contains("public domain") ||
        url.startsWith("https://creativecommons.org/licenses/") ||
        url.startsWith("https://creativecommons.org/publicdomain/")
}

private fun String.stripCommonsMarkup(): String =
    replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&#160;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()

data class GenerationResult(
    val success: Boolean,
    val message: String,
    val purchaseOutlayDzd: Int,
    val budgetDzd: Int
)


private fun FoodCategoryEntity.toModel() = FoodCategory(
    id = id,
    nameAr = nameAr,
    nameEn = nameEn,
    nameFr = nameFr,
    sortOrder = sortOrder
)

private fun FoodSubcategoryEntity.toModel() = FoodSubcategory(
    id = id,
    categoryId = categoryId,
    nameAr = nameAr,
    nameEn = nameEn,
    nameFr = nameFr,
    sortOrder = sortOrder
)

private fun FoodItemEntity.toIngredientMaster(): IngredientMaster {
    val accepted = acceptedUnitsCsv.split("|").filter { it.isNotBlank() }
    val aliases = buildList {
        add(nameEn)
        addAll(aliasesArCsv.split("|"))
        addAll(aliasesEnCsv.split("|"))
        addAll(aliasesFrCsv.split("|"))
    }.filter { it.isNotBlank() }.distinct()
    return IngredientMaster(
        id = id,
        nameAr = nameAr.ifBlank { nameEn.ifBlank { nameFr } },
        nameFr = nameFr.ifBlank { nameEn },
        baseUnit = accepted.firstOrNull() ?: "piece",
        allergens = emptySet(),
        aliases = aliases,
        nameEn = nameEn,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        acceptedUnits = accepted
    )
}

private fun IngredientEntity.toModel() = IngredientMaster(
    id = id,
    nameAr = nameAr,
    nameFr = nameFr,
    baseUnit = baseUnit,
    allergens = allergensCsv.split(",").filter { it.isNotBlank() }.toSet(),
    aliases = aliasesCsv.split("|").filter { it.isNotBlank() },
    categoryId = categoryId,
    subcategoryId = subcategoryId
)

private fun PlanMealEntity.toModel() = PlannedMeal(
    id = id,
    weekStartIso = weekStartIso,
    dayIndex = dayIndex,
    mealSlot = mealSlot,
    recipeId = recipeId,
    servings = servings,
    locked = locked,
    isCooked = isCooked,
    isUserEdited = isUserEdited
)

private fun PlannedMeal.toEntity() = PlanMealEntity(
    id = id,
    weekStartIso = weekStartIso,
    dayIndex = dayIndex,
    mealSlot = mealSlot,
    recipeId = recipeId,
    servings = servings,
    locked = locked,
    isCooked = isCooked,
    isUserEdited = isUserEdited
)

private fun ShoppingItemEntity.toModel(ingredient: IngredientMaster?) = ShoppingListItem(
    id = id,
    ingredientId = ingredientId,
    name = customName ?: ingredient?.nameAr ?: ingredientId ?: "عنصر مخصص",
    requiredQuantity = requiredQuantity,
    pantryQuantity = pantryQuantity,
    purchaseQuantity = purchaseQuantity,
    unit = unit,
    consumedValueDzd = consumedValueDzd,
    cashOutlayDzd = cashOutlayDzd,
    priceLabel = priceLabel,
    sourceLabel = sourceLabel,
    isApproximatePantry = isApproximatePantry,
    checked = checked,
    userAdded = userAdded,
    categoryId = ingredient?.categoryId ?: FoodTaxonomyIds.OTHER_CATEGORY,
    subcategoryId = ingredient?.subcategoryId ?: FoodTaxonomyIds.OTHER_SUBCATEGORY,
    storeNote = storeNote,
    isUnavailable = isUnavailable,
    actualSpentDzd = actualSpentDzd
)

private fun PriceObservationEntity.toPlanSnapshot(weekStartIso: String) = PlanPriceSnapshotEntity(
    weekStartIso = weekStartIso,
    observationId = id,
    ingredientId = ingredientId,
    packQuantityBase = packQuantityBase,
    unit = unit,
    packPriceDzd = packPriceDzd,
    sourceLabel = sourceLabel,
    locationLabel = locationLabel,
    observedAtEpochMillis = observedAtEpochMillis,
    priceKind = priceKind,
    confidenceLabel = confidenceLabel
)

private fun PlanPriceSnapshotEntity.toPriceObservation() = PriceObservationEntity(
    id = observationId,
    ingredientId = ingredientId,
    packQuantityBase = packQuantityBase,
    unit = unit,
    packPriceDzd = packPriceDzd,
    sourceLabel = sourceLabel,
    locationLabel = locationLabel,
    observedAtEpochMillis = observedAtEpochMillis,
    priceKind = priceKind,
    confidenceLabel = confidenceLabel
)

private fun PlanRunEntity.toModel() = PlanRunSummary(
    engineVersion = engineVersion,
    generatedAtEpochMillis = generatedAtEpochMillis,
    priceSnapshotAtEpochMillis = priceSnapshotAtEpochMillis,
    budgetDzd = budgetDzd,
    wilaya = wilaya,
    mealSlots = mealSlotsCsv.split(",").filter { it.isNotBlank() }.toSet()
)

fun currentWeekStartIso(date: LocalDate = LocalDate.now()): String =
    date.minusDays((date.dayOfWeek.value - 1).toLong()).toString()
