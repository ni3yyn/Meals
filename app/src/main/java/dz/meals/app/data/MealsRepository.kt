package dz.meals.app.data

import android.content.Context
import androidx.room.withTransaction
import dz.meals.app.data.local.FavoriteRecipeEntity
import dz.meals.app.data.local.IngredientEntity
import dz.meals.app.data.local.MealsDatabase
import dz.meals.app.data.local.MealsDao
import dz.meals.app.data.local.PantryItemEntity
import dz.meals.app.data.local.PlanMealEntity
import dz.meals.app.data.local.PriceObservationEntity
import dz.meals.app.data.local.RecipeEntity
import dz.meals.app.data.local.RecipeIngredientEntity
import dz.meals.app.data.local.RecipeStepEntity
import dz.meals.app.data.local.ShoppingItemEntity
import dz.meals.app.domain.calculateBasket
import java.time.LocalDate
import java.util.UUID

class MealsRepository(context: Context) {
    private val database = MealsDatabase.getInstance(context)
    private val dao: MealsDao = database.mealsDao()

    suspend fun ensureInitialized() {
        SeedRecipes.validateCatalogue()
        if (dao.recipeCount() > 0) return
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

    suspend fun snapshot(weekStartIso: String, selectedWilaya: String? = null): MealsSnapshot {
        ensureInitialized()
        val ingredients = dao.getIngredients().map { it.toModel() }
        val recipes = loadRecipes()
        val planned = dao.getPlanMeals(weekStartIso).map { it.toModel() }
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
        val prices = dao.getPriceObservations()
        val existingShopping = dao.getShoppingItems()
        val favoriteRecipeIds = dao.getFavoriteRecipeIds().toSet()
        val basket = calculateBasket(
            planned,
            recipes,
            pantryEntities,
            prices,
            existingShopping.filter { it.checked }.map { it.id }.toSet(),
            selectedWilaya
        )
        val names = ingredients.associateBy { it.id }
        val autoItems = basket.items.map { line ->
            val old = existingShopping.firstOrNull { it.id == line.id }
            ShoppingItemEntity(
                id = line.id,
                ingredientId = line.ingredientId,
                customName = names[line.ingredientId]?.nameAr ?: line.name,
                requiredQuantity = line.requiredQuantity,
                pantryQuantity = line.pantryQuantity,
                purchaseQuantity = line.purchaseQuantity,
                unit = line.unit,
                consumedValueDzd = line.consumedValueDzd,
                cashOutlayDzd = line.cashOutlayDzd,
                priceLabel = line.priceLabel,
                sourceLabel = line.sourceLabel,
                isApproximatePantry = line.isApproximatePantry,
                checked = old?.checked ?: false,
                userAdded = false,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
        database.withTransaction {
            dao.deleteGeneratedShoppingItems()
            if (autoItems.isNotEmpty()) dao.upsertShoppingItems(autoItems)
        }
        val shoppingItems = dao.getShoppingItems().map { it.toModel(names[it.ingredientId]) }
        return MealsSnapshot(
            recipes = recipes,
            ingredients = ingredients,
            plannedMeals = planned,
            pantry = pantry,
            shoppingItems = shoppingItems,
            priceObservations = prices.mapNotNull { observation ->
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
                            System.currentTimeMillis() - observation.observedAtEpochMillis > 30L * 24L * 60L * 60L * 1000L)
                )
            }.sortedByDescending { it.observedAtEpochMillis ?: 0L },
            favoriteRecipeIds = favoriteRecipeIds,
            basket = basket.copy(
                items = shoppingItems,
                purchaseOutlayDzd = autoItems.sumOf { it.cashOutlayDzd ?: 0.0 },
                unknownPriceCount = autoItems.count { it.cashOutlayDzd == null && it.purchaseQuantity > 0.0 }
            )
        )
    }

    suspend fun generateWeek(
        weekStartIso: String,
        weeklyBudgetDzd: Int,
        householdServings: Int,
        allergyIds: Set<String>,
        selectedWilaya: String? = null
    ): GenerationResult {
        ensureInitialized()
        val recipes = loadRecipes()
        val locked = dao.getPlanMeals(weekStartIso).filter { it.locked }.map { it.toModel() }
        val lockedConflict = findLockedAllergyConflict(locked, recipes, allergyIds)
        if (lockedConflict != null) {
            return GenerationResult(
                success = false,
                message = "توجد وجبة مقفلة لا توافق قيود الحساسية الحالية. افتح قفلها أو استبدلها قبل إنشاء الخطة. لم نغيّر أي وجبة.",
                purchaseOutlayDzd = 0,
                budgetDzd = weeklyBudgetDzd
            )
        }
        val allowed = recipes.filter { it.allergens.intersect(allergyIds).isEmpty() }
        val pantry = dao.getPantryItems()
        val prices = dao.getPriceObservations()
        if (allowed.isEmpty()) {
            return GenerationResult(false, "لا توجد وصفات متاحة بعد تطبيق قيود الحساسية. لم نغيّر خطتك.", 0, weeklyBudgetDzd)
        }

        val slots = (0 until 7).flatMap { day ->
            listOf("lunch", "dinner").map { slot -> "$weekStartIso-$day-$slot" to (day to slot) }
        }
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
                val overBudgetPenalty = (basket.purchaseOutlayDzd - weeklyBudgetDzd).coerceAtLeast(0.0) * 3.0
                basket.purchaseOutlayDzd + repeatPenalty + overBudgetPenalty
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

        dao.deleteWeek(weekStartIso)
        dao.upsertPlanMeals(chosen.values.sortedWith(compareBy<PlannedMeal> { it.dayIndex }.thenBy { it.mealSlot }).map { it.toEntity() })
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
        dao.upsertPlanMeal(
            PlanMealEntity(
                id = id,
                weekStartIso = weekStartIso,
                dayIndex = dayIndex,
                mealSlot = mealSlot,
                recipeId = recipeId,
                servings = servings.coerceIn(1, 12),
                locked = old?.locked ?: false
            )
        )
    }

    suspend fun toggleMealLock(id: String, weekStartIso: String) {
        val meal = dao.getPlanMeals(weekStartIso).firstOrNull { it.id == id } ?: return
        dao.upsertPlanMeal(meal.copy(locked = !meal.locked))
    }

    suspend fun adjustMealServings(id: String, weekStartIso: String, delta: Int) {
        val meal = dao.getPlanMeals(weekStartIso).firstOrNull { it.id == id } ?: return
        dao.upsertPlanMeal(meal.copy(servings = (meal.servings + delta).coerceIn(1, 12)))
    }

    suspend fun addRecipeToNextSlot(weekStartIso: String, recipeId: String, servings: Int) {
        val current = dao.getPlanMeals(weekStartIso)
        val empty = (0 until 7).asSequence()
            .flatMap { day -> sequenceOf(day to "lunch", day to "dinner") }
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

    suspend fun addCustomShoppingItem(name: String, quantity: Double, unit: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || quantity <= 0.0) return
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
                    cashOutlayDzd = null,
                    priceLabel = null,
                    sourceLabel = null,
                    isApproximatePantry = false,
                    checked = false,
                    userAdded = true,
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

    private suspend fun loadRecipes(): List<MealRecipe> {
        val ingredients = dao.getRecipeIngredients().groupBy { it.recipeId }
        val steps = dao.getRecipeSteps().groupBy { it.recipeId }
        return dao.getRecipes().map { recipe ->
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
                stepsFr = recipeSteps.map { it.instructionFr }
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
    locked = locked
)

private fun PlannedMeal.toEntity() = PlanMealEntity(
    id = id,
    weekStartIso = weekStartIso,
    dayIndex = dayIndex,
    mealSlot = mealSlot,
    recipeId = recipeId,
    servings = servings,
    locked = locked
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
    category = ingredient?.category ?: "أخرى"
)

fun currentWeekStartIso(date: LocalDate = LocalDate.now()): String =
    date.minusDays((date.dayOfWeek.value - 1).toLong()).toString()
