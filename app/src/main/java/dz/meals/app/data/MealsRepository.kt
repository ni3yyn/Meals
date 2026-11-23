package dz.meals.app.data

import android.content.Context
import androidx.room.withTransaction
import dz.meals.app.data.local.CookedMealDeductionEntity
import dz.meals.app.data.local.FavoriteRecipeEntity
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
import dz.meals.app.data.local.ShoppingItemEntity
import dz.meals.app.domain.calculateBasket
import dz.meals.app.domain.exactPantryDeduction
import dz.meals.app.domain.findLockedAllergyConflict
import dz.meals.app.domain.findPinnedDietConflict
import dz.meals.app.domain.isRecipeAllowed
import dz.meals.app.domain.pricesForSavedPlan
import dz.meals.app.domain.softPreferencePenalty
import dz.meals.app.domain.shouldPreserveMeal
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

class MealsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = MealsDatabase.getInstance(appContext)
    private val dao: MealsDao = database.mealsDao()
    private val initializationMutex = Mutex()
    private val catalogueCacheMutex = Mutex()
    private var cachedCatalogueRecipes: List<MealRecipe>? = null
    private var cachedCatalogueIngredients: List<IngredientMaster>? = null

    suspend fun ensureInitialized() = initializationMutex.withLock {
        SeedRecipes.validateCatalogue()
        dao.assignUnscopedShoppingItemsToWeek(currentWeekStartIso())
        dao.scopeLegacyGeneratedShoppingItemIds()
        if (dao.recipeCount() > 0) return@withLock
        database.withTransaction {
            dao.insertIngredients(SeedRecipes.ingredients.map {
                IngredientEntity(
                    id = it.id,
                    nameAr = it.nameAr,
                    nameFr = it.nameFr,
                    category = it.category,
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
                recipe.ingredients.map { item ->
                    RecipeIngredientEntity(
                        recipeId = recipe.id,
                        ingredientId = item.ingredientId,
                        quantityBase = item.quantityBase,
                        unit = item.unit,
                        prepNote = item.prepNote,
                        optional = item.optional
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
    ): MealsSnapshot {
        ensureInitialized()
        val ingredients = loadIngredients()
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
        val pantryEntities = dao.getPantryItems()
        val pantry = pantryEntities.map { entity ->
            val master = ingredients.firstOrNull { it.id == entity.ingredientId }
            PantryItem(
                id = entity.id,
                ingredientId = entity.ingredientId,
                nameAr = master?.nameAr ?: entity.ingredientId,
                quantityBase = entity.quantityBase,
                unit = entity.unit,
                isApproximate = entity.isApproximate,
                note = entity.note,
                expiryAtEpochMillis = entity.expiryAtEpochMillis
            )
        }
        val currentPrices = dao.getPriceObservations()
        val savedPriceSnapshot = if (savedRun != null) {
            dao.getPlanPriceSnapshots(weekStartIso).map { it.toPriceObservation() }
        } else emptyList()
        val prices = if (savedRun != null) {
            pricesForSavedPlan(savedPriceSnapshot, currentPrices, savedRun.priceSnapshotAtEpochMillis)
        } else currentPrices
        val planWilaya = savedRun?.wilaya?.takeIf { it.isNotBlank() } ?: selectedWilaya
        val existingShopping = dao.getShoppingItems(weekStartIso)
        val favoriteRecipeIds = dao.getFavoriteRecipeIds().toSet()
        val basket = calculateBasket(
            planned,
            recipes,
            pantryEntities,
            prices,
            existingShopping.filter { it.checked }.map { it.id.substringAfter("::") }.toSet(),
            planWilaya,
            allowStaleSnapshotPrices = savedRun != null && savedPriceSnapshot.isNotEmpty()
        )
        val names = ingredients.associateBy { it.id }
        val autoItems = basket.items.map { line ->
            val scopedId = "$weekStartIso::${line.id}"
            val old = existingShopping.firstOrNull { it.id == scopedId }
            ShoppingItemEntity(
                id = scopedId,
                ingredientId = line.ingredientId,
                customName = names[line.ingredientId]?.nameAr ?: line.name,
                requiredQuantity = line.requiredQuantity,
                pantryQuantity = line.pantryQuantity,
                purchaseQuantity = old?.purchaseQuantityOverride ?: line.purchaseQuantity,
                unit = line.unit,
                consumedValueDzd = line.consumedValueDzd,
                cashOutlayDzd = if (old?.purchaseQuantityOverride != null && line.purchaseQuantity > 0.0) {
                    line.cashOutlayDzd?.times(old.purchaseQuantityOverride / line.purchaseQuantity)
                } else line.cashOutlayDzd,
                priceLabel = if (old?.purchaseQuantityOverride != null) "تقدير معدل يدوياً" else line.priceLabel,
                sourceLabel = line.sourceLabel,
                isApproximatePantry = line.isApproximatePantry,
                checked = old?.checked ?: false,
                userAdded = false,
                updatedAtEpochMillis = System.currentTimeMillis(),
                weekStartIso = weekStartIso,
                purchaseQuantityOverride = old?.purchaseQuantityOverride,
                storeNote = old?.storeNote.orEmpty(),
                isUnavailable = old?.isUnavailable ?: false,
                actualSpentDzd = old?.actualSpentDzd
            )
        }
        database.withTransaction {
            dao.deleteGeneratedShoppingItems(weekStartIso)
            if (autoItems.isNotEmpty()) dao.upsertShoppingItems(autoItems)
        }
        val shoppingItems = dao.getShoppingItems(weekStartIso).map { it.toModel(names[it.ingredientId]) }
        return MealsSnapshot(
            recipes = recipes,
            ingredients = ingredients,
            plannedMeals = planned,
            pantry = pantry,
            shoppingItems = shoppingItems,
            priceObservations = currentPrices.mapNotNull { observation ->
                val master = ingredients.firstOrNull { it.id == observation.ingredientId } ?: return@mapNotNull null
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
            )
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
        val recipes = loadRecipes()
        // Community recipes are browseable and manually plannable. Keep automatic
        // generation on the curated, price-aware seed catalogue for now.
        val generatorRecipes = recipes.filterNot {
            it.id.startsWith(ArabicRecipeCatalogueInstaller.RECIPE_ID_PREFIX)
        }
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
        val ingredientCategories = ingredientsById.mapValues { it.value.category }
        val pinnedDietConflict = findPinnedDietConflict(locked, recipes, ingredientCategories, dietPattern)
        if (pinnedDietConflict != null) {
            return GenerationResult(
                success = false,
                message = "توجد وجبة مثبتة لا توافق النمط الغذائي الحالي. استبدلها أو غيّر النمط قبل إنشاء الخطة. لم نغيّر أي وجبة.",
                purchaseOutlayDzd = 0,
                budgetDzd = weeklyBudgetDzd
            )
        }
        val allowed = generatorRecipes.filter { recipe ->
            isRecipeAllowed(recipe, ingredientCategories, allergyIds, dietPattern)
        }
        val pantry = dao.getPantryItems()
        val prices = dao.getPriceObservations()
        if (allowed.isEmpty()) {
            return GenerationResult(false, "لا توجد وصفات متاحة بعد تطبيق قيود الحساسية والنمط الغذائي. لم نغيّر خطتك.", 0, weeklyBudgetDzd)
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
                val basket = calculateBasket(trial, recipes, pantry, prices, selectedWilaya = selectedWilaya)
                val repeatPenalty = (repeats[recipe.id] ?: 0) * 420.0
                val overBudgetPenalty = (basket.purchaseOutlayDzd - targetBudget).coerceAtLeast(0.0) * 3.0
                val preferencePenalty = softPreferencePenalty(recipe, dislikedIngredientIds, maxCookingMinutes)
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
        val result = calculateBasket(chosen.values.toList(), recipes, pantry, prices, selectedWilaya = selectedWilaya)
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
        val recipe = current.recipeId?.let { recipeId -> loadRecipes().firstOrNull { it.id == recipeId } } ?: return
        database.withTransaction {
            if (!current.isCooked) {
                val factor = current.servings.toDouble() / recipe.baseServings.coerceAtLeast(1)
                val required = recipe.ingredients.filterNot { it.optional }
                    .groupBy { it.ingredientId to it.unit }
                required.forEach { (key, parts) ->
                    val needed = parts.sumOf { it.quantityBase * factor }
                    val stock = dao.getPantryItems().firstOrNull {
                        it.ingredientId == key.first && it.unit == key.second
                    } ?: return@forEach
                    val deducted = exactPantryDeduction(needed, stock.quantityBase, stock.isApproximate)
                    if (deducted > 0.0) {
                        dao.upsertPantryItem(stock.copy(quantityBase = (stock.quantityBase ?: 0.0) - deducted))
                        dao.upsertCookedMealDeduction(
                            CookedMealDeductionEntity(
                                mealId = current.id,
                                ingredientId = key.first,
                                unit = key.second,
                                quantityBase = deducted
                            )
                        )
                    }
                }
                dao.upsertPlanMeal(current.copy(isCooked = true))
            } else {
                dao.getCookedMealDeductions(current.id).forEach { deduction ->
                    val stock = dao.getPantryItems().firstOrNull {
                        it.ingredientId == deduction.ingredientId && it.unit == deduction.unit
                    }
                    if (stock == null) {
                        dao.upsertPantryItem(
                            PantryItemEntity(
                                id = deduction.ingredientId,
                                ingredientId = deduction.ingredientId,
                                quantityBase = deduction.quantityBase,
                                unit = deduction.unit,
                                isApproximate = false,
                                note = "استُعيد بعد التراجع عن تسجيل الطهي",
                                expiryAtEpochMillis = null
                            )
                        )
                    } else if (!stock.isApproximate && stock.quantityBase != null) {
                        dao.upsertPantryItem(stock.copy(quantityBase = stock.quantityBase + deduction.quantityBase))
                    } else {
                        dao.upsertPantryItem(
                            stock.copy(
                                quantityBase = deduction.quantityBase,
                                isApproximate = false,
                                note = (stock.note + " · استُعيد بعد التراجع عن تسجيل الطهي").trim(' ', '·')
                            )
                        )
                    }
                }
                dao.deleteCookedMealDeductions(current.id)
                dao.upsertPlanMeal(current.copy(isCooked = false))
            }
        }
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
        val ingredient = dao.getIngredients().firstOrNull { it.id == ingredientId } ?: return
        if (quantityBase == null && !isApproximate) {
            dao.deletePantryItem(ingredientId)
        } else {
            dao.upsertPantryItem(
                PantryItemEntity(
                    id = ingredientId,
                    ingredientId = ingredientId,
                    quantityBase = if (isApproximate) null else quantityBase?.coerceAtLeast(0.0),
                    unit = ingredient.baseUnit,
                    isApproximate = isApproximate,
                    note = note,
                    expiryAtEpochMillis = expiryAtEpochMillis
                )
            )
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
        val ingredient = dao.getIngredients().firstOrNull { it.id == ingredientId }
            ?: throw IllegalArgumentException("Unknown ingredient")
        val wilaya = selectedWilaya.trim()
        require(wilaya.isNotEmpty()) { "Wilaya is required" }
        val context = shopContext.trim().ifBlank { "نوع المحل غير محدد" }
        dao.upsertPriceObservation(
            PriceObservationEntity(
                id = "user-" + UUID.randomUUID().toString(),
                ingredientId = ingredientId,
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
            dao.getIngredients()
                .filter { ingredient ->
                    !ingredient.id.startsWith(ArabicRecipeCatalogueInstaller.INGREDIENT_ID_PREFIX) ||
                        catalogueInstalled
                }
                .map { it.toModel() }
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
        // Hide partially imported community rows until the entire pinned
        // catalogue revision has passed validation and has been committed.
        val catalogueInstalled = hasInstalledArabicRecipeCatalogue()
        return dao.getRecipes()
            .filter { recipe ->
                !recipe.id.startsWith(ArabicRecipeCatalogueInstaller.RECIPE_ID_PREFIX) || catalogueInstalled
            }
            .map { recipe ->
                val recipeSteps = steps[recipe.id].orEmpty().sortedBy { it.stepNumber }
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
                        RecipeIngredient(it.ingredientId, it.quantityBase, it.unit, it.prepNote, it.optional)
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
                    imageUrl = recipe.imageUrl
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
                ingredientId = id,
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

data class GenerationResult(
    val success: Boolean,
    val message: String,
    val purchaseOutlayDzd: Int,
    val budgetDzd: Int
)

private fun IngredientEntity.toModel() = IngredientMaster(
    id = id,
    nameAr = nameAr,
    nameFr = nameFr,
    category = category,
    baseUnit = baseUnit,
    allergens = allergensCsv.split(",").filter { it.isNotBlank() }.toSet(),
    aliases = aliasesCsv.split("|").filter { it.isNotBlank() }
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
    category = ingredient?.category ?: "أخرى",
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
