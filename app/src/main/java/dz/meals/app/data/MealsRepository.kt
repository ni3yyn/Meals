package dz.meals.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import dz.meals.app.data.local.CookedMealDeductionEntity
import dz.meals.app.data.local.FavoriteRecipeEntity
import dz.meals.app.data.local.FoodCategoryEntity
import dz.meals.app.data.local.FoodSubcategoryEntity
import dz.meals.app.data.local.FoodItemEntity
import dz.meals.app.data.local.IngredientEntity
import dz.meals.app.data.local.MarketProductEntity
import dz.meals.app.data.local.MarketProductSourceEntity
import dz.meals.app.data.local.MarketPriceObservationEntity
import dz.meals.app.data.local.VerifiedMarketProductPriceRow
import dz.meals.app.data.local.MealsDatabase
import dz.meals.app.data.local.MealsDao
import dz.meals.app.data.local.PantryItemEntity
import dz.meals.app.data.local.PlanMealEntity
import dz.meals.app.data.local.PlanPriceSnapshotEntity
import dz.meals.app.data.local.PlanRunEntity
import dz.meals.app.data.local.PlanningPeriodEntity
import dz.meals.app.data.local.PlanIngredientAdjustmentEntity
import dz.meals.app.data.local.PlanMarketPriceSnapshotEntity
import dz.meals.app.data.local.PlanningPeriodMealEntity
import dz.meals.app.data.local.PriceObservationEntity
import dz.meals.app.data.local.RecipeEntity
import dz.meals.app.data.local.RecipeIngredientEntity
import dz.meals.app.data.local.RecipeStepEntity
import dz.meals.app.data.local.RecipeImageOverrideEntity
import dz.meals.app.data.local.ShoppingItemEntity
import dz.meals.app.BuildConfig
import dz.meals.app.domain.MarketProductPriceEvidence
import dz.meals.app.domain.ProductPriceEstimator
import dz.meals.app.domain.ProductPriceLocality
import dz.meals.app.domain.PlanIngredientAdjustment
import dz.meals.app.domain.PlanningPeriodType
import dz.meals.app.domain.PlanningPeriodId
import dz.meals.app.domain.PlanningDateRange
import dz.meals.app.domain.PeriodMeatAllowance
import dz.meals.app.domain.calculatePeriodMeatAllowances
import dz.meals.app.domain.periodSearchAttempts
import dz.meals.app.domain.scorePeriodIngredientReuse
import dz.meals.app.domain.calculatePurchaseNeed
import dz.meals.app.domain.resolvePlanningDateRange
import dz.meals.app.domain.buildPlanningPeriodSlots
import dz.meals.app.domain.calculateBasket
import dz.meals.app.domain.estimateMarketProductPrice
import dz.meals.app.domain.exactPantryDeduction
import dz.meals.app.domain.findLockedAllergyConflict
import dz.meals.app.domain.findPinnedDietConflict
import dz.meals.app.domain.isRecipeAllowed
import dz.meals.app.domain.isRecipeAllowedByMeatPreference
import dz.meals.app.domain.recipeMeatQuantityGrams
import dz.meals.app.domain.recipeOilQuantityPerServing
import dz.meals.app.domain.pricesForSavedPlan
import dz.meals.app.domain.softPreferencePenalty
import dz.meals.app.domain.shouldPreserveMeal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.text.Normalizer
import java.time.LocalDate
import java.util.UUID
import java.time.YearMonth
import kotlin.random.Random
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
    private val recipeImageDiscoveryPreferences =
        appContext.getSharedPreferences("recipe_image_discovery", Context.MODE_PRIVATE)
    private val recipeImageDiscoveryMutex = Mutex()
    private val commonsSearchMutex = Mutex()
    private val commonsClient = OkHttpClient.Builder()
        .callTimeout(24, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(16, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val initializationMutex = Mutex()
    private val catalogueCacheMutex = Mutex()
    private var cachedCatalogueRecipes: List<MealRecipe>? = null
    private var cachedCatalogueIngredients: List<IngredientMaster>? = null

    suspend fun ensureInitialized() = initializationMutex.withLock {
        SeedRecipes.validateCatalogue()
        FoodCatalogueInstaller(appContext, databaseOverride = database).installIfNeeded()
        MarketProductCatalogueInstaller(appContext, databaseOverride = database).installIfNeeded()
        dao.assignUnscopedShoppingItemsToWeek(currentWeekStartIso())
        dao.scopeLegacyGeneratedShoppingItemIds()

        if (dao.recipeCount() == 0) {
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

        // Mapping validation intentionally targets the canonical recipe
        // ingredients table. Install only after a fresh database has its seeds.
        MarketProductIngredientMatchInstaller(appContext, databaseOverride = database).installIfNeeded()
    }

    /**
     * Searches the bundled retail catalogue. Branded products stay separate
     * from generic recipe ingredients and user-reported ingredient prices.
     */
    suspend fun searchMarketProducts(query: String = "", limit: Int = 60): List<MarketProductEntity> {
        ensureInitialized()
        val safeLimit = limit.coerceIn(1, 200)
        val term = query.trim().take(100)
        return if (term.isBlank()) {
            dao.getMarketProducts(safeLimit)
        } else {
            dao.searchMarketProducts(term, safeLimit)
        }
    }

    suspend fun getMarketProductSources(productId: String): List<MarketProductSourceEntity> {
        ensureInitialized()
        return dao.getMarketProductSources(productId)
    }

    suspend fun getMarketPriceObservations(productId: String): List<MarketPriceObservationEntity> {
        ensureInitialized()
        return dao.getMarketPriceObservations(productId)
    }

    suspend fun invalidateCatalogueCache() {
        catalogueCacheMutex.withLock {
            cachedCatalogueRecipes = null
            cachedCatalogueIngredients = null
        }
    }

    /**
     * Compatibility wrapper for existing weekly call sites. Week keys remain
     * Monday-starting ISO dates and retain their existing storage scope.
     */
    suspend fun snapshot(
        weekStartIso: String,
        selectedWilaya: String? = null,
        selectedMealSlots: Set<String> = MealSlots.ids.toSet()
    ): MealsSnapshot {
        val range = resolvePlanningDateRange(PlanningPeriodType.WEEK, weekStartIso)
        return snapshot(PlanningPeriodId(range.periodId), selectedWilaya, selectedMealSlots)
    }

    /**
     * Canonical period snapshot. WEEK uses legacy plan_runs/plan_meals when
     * present; MONTH uses date-keyed period meals. Both share period-scoped
     * adjustments, shopping state, immutable price evidence and basket rebuild.
     */
    suspend fun snapshot(
        periodId: PlanningPeriodId,
        selectedWilaya: String? = null,
        selectedMealSlots: Set<String> = MealSlots.ids.toSet()
    ): MealsSnapshot = withContext(Dispatchers.Default) {
        ensureInitialized()

        val separator = periodId.value.indexOf(':')
        require(separator > 0) { "Invalid planning period ID: ${periodId.value}" }
        val periodType = PlanningPeriodType.fromStorage(periodId.value.substring(0, separator))
            ?: throw IllegalArgumentException("Unknown planning period type: ${periodId.value}")
        val range = resolvePlanningDateRange(periodType, periodId.value.substring(separator + 1))
        require(range.periodId == periodId.value) {
            "Planning period ID is not canonical: ${periodId.value}"
        }

        // Existing week-scoped tables use the Monday ISO date. Period-native
        // tables use the canonical ID; this keeps legacy callers byte-compatible.
        val scopeKey = if (periodType == PlanningPeriodType.WEEK) {
            range.startDate.toString()
        } else {
            periodId.value
        }
        val savedRun = if (periodType == PlanningPeriodType.WEEK) dao.getPlanRun(scopeKey) else null
        val period = dao.getPlanningPeriod(periodId.value) ?: PlanningPeriodEntity(
            id = periodId.value,
            periodType = periodType.storageValue,
            startDateIso = range.startDate.toString(),
            endDateIso = range.endDate.toString(),
            budgetDzd = savedRun?.budgetDzd ?: 0,
            wilaya = savedRun?.wilaya.orEmpty(),
            mealSlotsCsv = savedRun?.mealSlotsCsv ?: selectedMealSlots.sorted().joinToString(","),
            engineVersion = savedRun?.engineVersion ?: "snapshot-adapter",
            createdAtEpochMillis = savedRun?.generatedAtEpochMillis ?: 0L,
            priceSnapshotAtEpochMillis = savedRun?.priceSnapshotAtEpochMillis ?: 0L,
            householdServings = 1,
            updatedAtEpochMillis = savedRun?.generatedAtEpochMillis ?: 0L
        ).also { dao.upsertPlanningPeriod(it) }

        val persistedSlots = period.mealSlotsCsv.split(",").filter { it.isNotBlank() }.toSet()
        val runSlots = savedRun?.mealSlotsCsv?.split(",")?.filter { it.isNotBlank() }?.toSet().orEmpty()
        val visibleSlots = persistedSlots.ifEmpty { runSlots.ifEmpty { selectedMealSlots } }

        val ingredients = loadIngredients()
        val taxonomy = ingredients.associate { it.id to FoodTaxonomyRef(it.categoryId, it.subcategoryId) }
        val foodCategories = dao.getFoodCategories().map { it.toModel() }
        val foodSubcategories = dao.getFoodSubcategories().map { it.toModel() }
        val recipes = loadRecipes()
        val adjustments = dao.getPlanIngredientAdjustments(periodId.value)
            .mapNotNull { it.toDomainAdjustment() }
            .toMap()

        val plannedMeals = when (periodType) {
            PlanningPeriodType.WEEK -> {
                val legacyRows = dao.getPlanMeals(scopeKey).filter { it.mealSlot in visibleSlots }
                if (legacyRows.isNotEmpty()) legacyRows.map { it.toModel() }
                else dao.getPlanningPeriodMeals(periodId.value)
                    .filter { it.mealSlot in visibleSlots }
                    .map { it.toPlannedMeal(range.startDate) }
            }
            PlanningPeriodType.MONTH -> dao.getPlanningPeriodMeals(periodId.value)
                .filter { it.mealSlot in visibleSlots }
                .map { it.toPlannedMeal(range.startDate) }
        }

        // Inventory is not assumed; deductions come only from this period's explicit adjustments.
        val pantryEntities = emptyList<PantryItemEntity>()
        val pantry = emptyList<PantryItem>()
        val currentPrices = dao.getPriceObservations()
        val priceTime = period.priceSnapshotAtEpochMillis
            .takeIf { it > 0L }
            ?: savedRun?.priceSnapshotAtEpochMillis?.takeIf { it > 0L }
        val savedPriceRows = if (priceTime != null) {
            dao.getPlanPriceSnapshots(scopeKey).map { it.toPriceObservation() }
        } else emptyList()
        val prices = if (priceTime != null || savedRun != null) {
            pricesForSavedPlan(savedPriceRows, currentPrices, priceTime ?: 0L)
        } else currentPrices

        val planWilaya = period.wilaya.takeIf { it.isNotBlank() } ?: selectedWilaya
        val marketPriceSnapshotAt = priceTime ?: System.currentTimeMillis()
        val storedMarketRows = if (period.marketPriceSnapshotCaptured) {
            dao.getPlanMarketPriceSnapshots(periodId.value)
        } else emptyList()
        val marketEvidence = if (period.marketPriceSnapshotCaptured) {
            storedMarketRows.map { it.toPriceEvidence() }
        } else {
            dao.getVerifiedMarketProductPriceRows()
                .asSequence()
                .filter { (it.observedAtEpochMillis ?: Long.MAX_VALUE) <= marketPriceSnapshotAt }
                .map { it.toPriceEvidence() }
                .toList()
        }

        val existingShopping = dao.getShoppingItems(scopeKey)
        // A canonical demand may use g/ml even when an older saved shopping
        // row used kg/l. Keep rows grouped by ingredient and match only units
        // that have a defined conversion into the current demand unit.
        val existingByIngredient = existingShopping
            .filter { it.ingredientId != null }
            .groupBy { requireNotNull(it.ingredientId) }
        val favorites = dao.getFavoriteRecipeIds().toSet()
        val checkedIds = existingShopping
            .filter { it.checked && it.ingredientId != null }
            .mapNotNull { item ->
                item.ingredientId?.let { "$it::${item.unit}" }
                    ?: item.id.substringAfter("::").takeIf { it.isNotBlank() }
            }.toSet()
        val basket = calculateBasket(
            plannedMeals,
            recipes,
            pantryEntities,
            prices,
            checkedIds,
            planWilaya,
            allowStaleSnapshotPrices = savedPriceRows.isNotEmpty(),
            foodTaxonomyByIngredientId = taxonomy,
            marketProductPriceEvidence = marketEvidence,
            planIngredientAdjustments = adjustments,
            nowEpochMillis = marketPriceSnapshotAt
        )

        // Shadow-test the newer confidence-aware estimator during development and
        // the explicitly enabled internal beta. It never feeds the displayed
        // basket yet; current plan totals remain on the established estimator
        // until the parallel results have been validated.
        if (BuildConfig.DEBUG || BuildConfig.MONTHLY_PLANNING_ENABLED) {
            logProductPriceShadowEstimate(
                periodId = periodId,
                demands = basket.ingredientDemands,
                wilaya = planWilaya,
                nowEpochMillis = marketPriceSnapshotAt,
                establishedTypicalDzd = basket.purchaseOutlayDzd
            )
        }

        val names = ingredients.associateBy { it.id }
        val generatedShoppingRows = basket.items.map { line ->
            val itemId = "$scopeKey::${line.id}"
            val oldRows = line.ingredientId
                ?.let(existingByIngredient::get)
                .orEmpty()
                .filter { oldRow ->
                    convertIngredientQuantity(1.0, oldRow.unit, line.unit) != null
                }
            val old = oldRows.maxByOrNull { it.updatedAtEpochMillis }
            val purchaseOverride = oldRows.mapNotNull { oldRow ->
                oldRow.purchaseQuantityOverride?.let { quantity ->
                    convertIngredientQuantity(quantity, oldRow.unit, line.unit)
                }
            }.takeIf { it.isNotEmpty() }?.sum()
            val storeNotes = oldRows.map { it.storeNote.trim() }
                .filter { it.isNotEmpty() }.distinct().joinToString(" · ")
            val actualSpend = oldRows.mapNotNull { it.actualSpentDzd }.takeIf { it.isNotEmpty() }?.sum()
            ShoppingItemEntity(
                id = itemId,
                ingredientId = line.ingredientId,
                customName = names[line.ingredientId]?.nameAr ?: line.name,
                requiredQuantity = line.requiredQuantity,
                pantryQuantity = line.pantryQuantity,
                purchaseQuantity = purchaseOverride ?: line.purchaseQuantity,
                unit = line.unit,
                consumedValueDzd = line.consumedValueDzd.takeUnless { line.marketCashOutlayMinDzd != null },
                cashOutlayDzd = if (line.marketCashOutlayMinDzd != null) null
                    else if (purchaseOverride != null && line.purchaseQuantity > 0.0) {
                        line.cashOutlayDzd?.times(purchaseOverride / line.purchaseQuantity)
                    } else line.cashOutlayDzd,
                priceLabel = if (line.marketCashOutlayMinDzd != null) null
                    else if (purchaseOverride != null) "تقدير معدل يدوياً" else line.priceLabel,
                sourceLabel = line.sourceLabel.takeUnless { line.marketCashOutlayMinDzd != null },
                isApproximatePantry = line.isApproximatePantry,
                checked = oldRows.any { it.checked },
                userAdded = false,
                updatedAtEpochMillis = old?.updatedAtEpochMillis ?: System.currentTimeMillis(),
                weekStartIso = scopeKey,
                purchaseQuantityOverride = purchaseOverride,
                storeNote = storeNotes,
                isUnavailable = oldRows.any { it.isUnavailable },
                actualSpentDzd = actualSpend
            )
        }
        database.withTransaction {
            dao.deleteGeneratedShoppingItems(scopeKey)
            if (generatedShoppingRows.isNotEmpty()) dao.upsertShoppingItems(generatedShoppingRows)
        }

        val shoppingItems = dao.getShoppingItems(scopeKey)
            .map { it.toModel(names[it.ingredientId]) }
            .map { it.withPlanIngredientAdjustment(adjustments) }
            .map { item ->
                item.withMarketProductPrice(
                    evidence = marketEvidence,
                    wilaya = planWilaya,
                    snapshotAtEpochMillis = marketPriceSnapshotAt
                )
            }
        val hasRanges = shoppingItems.any {
            it.marketCashOutlayMinDzd != null && it.marketCashOutlayMaxDzd != null
        }
        val basketMin = shoppingItems.sumOf { it.marketCashOutlayMinDzd ?: it.cashOutlayDzd ?: 0.0 }
        val basketMax = shoppingItems.sumOf { it.marketCashOutlayMaxDzd ?: it.cashOutlayDzd ?: 0.0 }
        val allPricesKnown = shoppingItems.all {
            it.purchaseQuantity <= 0.0 ||
                it.cashOutlayDzd != null ||
                (it.marketCashOutlayMinDzd != null && it.marketCashOutlayMaxDzd != null)
        }

        val priceSummaries = currentPrices.mapNotNull { observation ->
            val ingredient = names[observation.ingredientId] ?: return@mapNotNull null
            PriceObservationSummary(
                id = observation.id,
                ingredientId = observation.ingredientId,
                ingredientNameAr = ingredient.nameAr,
                packQuantityBase = observation.packQuantityBase,
                unit = observation.unit,
                packPriceDzd = observation.packPriceDzd,
                sourceLabel = observation.sourceLabel,
                locationLabel = observation.locationLabel,
                observedAtEpochMillis = observation.observedAtEpochMillis,
                priceKind = observation.priceKind,
                confidenceLabel = observation.confidenceLabel,
                categoryId = ingredient.categoryId,
                subcategoryId = ingredient.subcategoryId,
                isStale = observation.priceKind != "demo" &&
                    (observation.observedAtEpochMillis == null ||
                        System.currentTimeMillis() < observation.observedAtEpochMillis ||
                        System.currentTimeMillis() - observation.observedAtEpochMillis >
                            30L * 24L * 60L * 60L * 1000L)
            )
        }.sortedByDescending { it.observedAtEpochMillis ?: 0L }

        val runSummary = savedRun?.toModel()
            ?: if (periodType == PlanningPeriodType.MONTH && period.updatedAtEpochMillis > 0L) {
                period.toPlanRunSummary()
            } else null

        MealsSnapshot(
            recipes = recipes,
            ingredients = ingredients,
            plannedMeals = plannedMeals,
            pantry = pantry,
            shoppingItems = shoppingItems,
            priceObservations = priceSummaries,
            favoriteRecipeIds = favorites,
            planRun = runSummary,
            basket = basket.copy(
                items = shoppingItems,
                consumedValueDzd = shoppingItems.sumOf { it.consumedValueDzd ?: 0.0 },
                // Market-backed items carry a range instead of cashOutlayDzd.
                // Use the high bound for the conservative scalar; keep the
                // explicit low/high fields below for the UI.
                purchaseOutlayDzd = shoppingItems.sumOf {
                    it.marketCashOutlayMaxDzd ?: it.cashOutlayDzd ?: 0.0
                },
                unknownPriceCount = shoppingItems.count {
                    it.purchaseQuantity > 0.0 &&
                        it.cashOutlayDzd == null &&
                        (it.marketCashOutlayMinDzd == null || it.marketCashOutlayMaxDzd == null)
                },
                actualSpendDzd = shoppingItems.sumOf { it.actualSpentDzd ?: 0.0 },
                purchaseOutlayMinDzd = basketMin.takeIf { hasRanges && allPricesKnown },
                purchaseOutlayMaxDzd = basketMax.takeIf { hasRanges && allPricesKnown }
            ),
            foodCategories = foodCategories,
            foodSubcategories = foodSubcategories,
            planningPeriod = period.toPlanningPeriodSummary(),
            marketPriceEvidence = marketEvidence.map { it.toSummary() }
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
        meatPreference: String = "regular",
        monthlyMeatGrams: Int = 3_000,
        oilPreference: String = "regular",
        fillEmptyOnly: Boolean = false,
        weeklyMeatGrams: Int? = null
    ): GenerationResult {
        ensureInitialized()
        val canonicalDislikedIngredientIds = dislikedIngredientIds
            .mapTo(linkedSetOf(), foodIdentityResolver::canonicalId)
        val recipes = loadRecipes()
        // Temporary default: only the separately curated Algerian collection can
        // enter generated plans. Settings will later supply the selected cuisines
        // through this centralized policy.
        // Automatic generation accepts only explicitly classified Algerian main dishes.
        val generatorRecipes = recipes.filter(PlanCuisinePolicy::allowsForGeneration)
        val validSlots = selectedMealSlots.intersect(MealSlots.ids.toSet())
        if (validSlots.isEmpty()) {
            return GenerationResult(false, "اختر وجبة واحدة على الأقل لتخطيط الأسبوع.", 0, weeklyBudgetDzd)
        }
        val existingMeals = dao.getPlanMeals(weekStartIso)
            .filter { it.mealSlot in validSlots }
            .map { it.toModel() }
        val weeklyRange = resolvePlanningDateRange(PlanningPeriodType.WEEK, weekStartIso)
        val periodId = weeklyRange.periodId
        val savedRun = dao.getPlanRun(weekStartIso)
        val existingPeriod = dao.getPlanningPeriod(periodId)
        if (existingPeriod == null) {
            dao.upsertPlanningPeriod(
                PlanningPeriodEntity(
                    id = periodId,
                    periodType = PlanningPeriodType.WEEK.storageValue,
                    startDateIso = weeklyRange.startDate.toString(),
                    endDateIso = weeklyRange.endDate.toString(),
                    budgetDzd = savedRun?.budgetDzd ?: weeklyBudgetDzd,
                    wilaya = savedRun?.wilaya ?: selectedWilaya.orEmpty(),
                    mealSlotsCsv = savedRun?.mealSlotsCsv ?: validSlots.sorted().joinToString(","),
                    engineVersion = savedRun?.engineVersion ?: "legacy-adapter",
                    createdAtEpochMillis = savedRun?.generatedAtEpochMillis ?: 0L,
                    priceSnapshotAtEpochMillis = savedRun?.priceSnapshotAtEpochMillis ?: 0L,
                    householdServings = householdServings.coerceIn(1, 12),
                    updatedAtEpochMillis = savedRun?.generatedAtEpochMillis ?: 0L
                )
            )
        }
        val recipes = loadRecipes()
        val persistedAdjustmentRows = dao.getPlanIngredientAdjustments(periodId)
        val previousIngredientDemands = IngredientDemandCalculator.calculate(existingMeals, recipes)
        // Only adjustments active in the current saved recipe set influence
        // candidate scoring. Inactive records remain persisted but cannot
        // silently deduct stock if a later regeneration introduces that food.
        val planAdjustmentsByIngredientUnit = activePlanIngredientAdjustments(
            persistedAdjustmentRows,
            previousIngredientDemands
        )
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
        val normalizedMeatPreference = meatPreference.takeIf {
            it in setOf("regular", "none", "chicken_only", "monthly_limit")
        } ?: "regular"
        val normalizedOilPreference = oilPreference.takeIf { it in setOf("regular", "less") } ?: "regular"
        val meatAllowanceBreakdown = if (normalizedMeatPreference == "monthly_limit") {
            calculatePeriodMeatAllowances(weeklyRange, monthlyMeatGrams, weeklyMeatGrams)
        } else {
            emptyList()
        }
        val meatAllowanceGramsByMonth = meatAllowanceBreakdown.associate { it.month to it.allowedGrams }
        val pinnedMeatConflict = locked.firstNotNullOfOrNull { meal ->
            val recipe = meal.recipeId?.let { id -> recipes.firstOrNull { it.id == id } }
                ?: return@firstNotNullOfOrNull null
            recipe.id.takeIf {
                !isRecipeAllowedByMeatPreference(recipe, ingredientTaxonomyById, normalizedMeatPreference, ingredientsById)
            }
        }
        if (pinnedMeatConflict != null) {
            return GenerationResult(
                success = false,
                message = "توجد وجبة مطبوخة أو مثبتة لا توافق تفضيل استهلاك اللحوم الحالي. غيّر الوجبة أو عدّل التفضيل قبل إعادة التخطيط. لم نغيّر أي وجبة.",
                purchaseOutlayDzd = 0,
                budgetDzd = weeklyBudgetDzd
            )
        }
        val allowed = generatorRecipes.filter { recipe ->
            isRecipeAllowed(recipe, ingredientTaxonomyById, allergyIds, dietPattern) &&
                isRecipeAllowedByMeatPreference(recipe, ingredientTaxonomyById, normalizedMeatPreference, ingredientsById) &&
                (normalizedMeatPreference != "monthly_limit" ||
                    recipeMeatQuantityGrams(recipe, householdServings, ingredientTaxonomyById) != null)
        }
        val pantry = dao.getPantryItems()
        val prices = dao.getPriceObservations()
        val marketPriceSnapshotAt = System.currentTimeMillis()
        val storedMarketPriceSnapshots = if (
            savedRun != null && existingPeriod?.marketPriceSnapshotCaptured == true
        ) {
            dao.getPlanMarketPriceSnapshots(periodId)
        } else {
            emptyList()
        }
        val marketPriceEvidence = if (
            savedRun != null && existingPeriod?.marketPriceSnapshotCaptured == true
        ) {
            storedMarketPriceSnapshots.map { it.toPriceEvidence() }
        } else {
            dao.getVerifiedMarketProductPriceRows()
                .asSequence()
                .filter { (it.observedAtEpochMillis ?: Long.MAX_VALUE) <= marketPriceSnapshotAt }
                .map { it.toPriceEvidence() }
                .toList()
        }
        if (allowed.isEmpty()) {
            return GenerationResult(false, "لا توجد أطباق رئيسية جزائرية متاحة بعد تطبيق قيود الحساسية وتفضيلات الطعام الحالية. لم نغيّر خطتك.", 0, weeklyBudgetDzd)
        }

        val planningWeekStart = weeklyRange.startDate

        // Search is bounded and local: every pass evaluates the entire basket,
        // so shared ingredients and rounded purchase packs are considered together.
        // Existing generated meals are rotation history, not locks. User-edited,
        // cooked and locked meals remain preserved by the existing policy.
        val chosen = generatePeriodCandidates(
            periodType = PlanningPeriodType.WEEK,
            startDate = weeklyRange.startDate,
            endDate = weeklyRange.endDate,
            periodBudgetDzd = weeklyBudgetDzd,
            householdServings = householdServings,
            mealSlots = validSlots,
            fillEmptyOnly = fillEmptyOnly,
            existingMeals = existingMeals,
            locked = locked,
            recipes = recipes,
            allowed = allowed,
            canonicalDislikedIngredientIds = canonicalDislikedIngredientIds,
            ingredientTaxonomyById = ingredientTaxonomyById,
            pantry = pantry,
            prices = prices,
            selectedWilaya = selectedWilaya,
            marketPriceEvidence = marketPriceEvidence,
            planAdjustmentsByIngredientUnit = planAdjustmentsByIngredientUnit,
            marketPriceSnapshotAt = marketPriceSnapshotAt,
            budgetBufferPercent = budgetBufferPercent,
            previousGeneratedAtEpochMillis = savedRun?.generatedAtEpochMillis,
            favoriteRecipeIds = dao.getFavoriteRecipeIds().toSet(),
            meatAllowanceGramsByMonth = meatAllowanceGramsByMonth,
            normalizedMeatPreference = normalizedMeatPreference,
            normalizedOilPreference = normalizedOilPreference,
            maxCookingMinutes = maxCookingMinutes
        )

        // Carry only adjustments that were active before regeneration and
        // remain compatible with demand in the newly selected recipes.
        val adjustmentRebase = rebasePlanIngredientAdjustments(
            periodId = periodId,
            previousPlannedMeals = existingMeals,
            plannedMeals = chosen.values.toList(),
            recipes = recipes
        )
        val rebasedAdjustmentRows = adjustmentRebase.storedRows
        val rebasedAdjustments = adjustmentRebase.activeAdjustments

        val generatedAt = System.currentTimeMillis()
        val priceSnapshotAt = generatedAt
        database.withTransaction {
            dao.deletePlanPriceSnapshots(weekStartIso)
            if (prices.isNotEmpty()) {
                dao.insertPlanPriceSnapshots(prices.map { it.toPlanSnapshot(weekStartIso) })
            }
            dao.deletePlanSlots(weekStartIso, validSlots.toList())
            dao.upsertPlanMeals(chosen.values.sortedWith(compareBy<PlannedMeal> { it.dayIndex }.thenBy { it.mealSlot }).map { it.toEntity() })
            dao.deletePlanIngredientAdjustments(periodId)
            for (rebasedAdjustment in rebasedAdjustmentRows) {
                dao.upsertPlanIngredientAdjustment(rebasedAdjustment)
            }
            dao.upsertPlanRun(
                PlanRunEntity(
                    weekStartIso = weekStartIso,
                    engineVersion = "0.5.0",
                    generatedAtEpochMillis = generatedAt,
                    priceSnapshotAtEpochMillis = priceSnapshotAt,
                    budgetDzd = weeklyBudgetDzd,
                    wilaya = selectedWilaya.orEmpty(),
                    mealSlotsCsv = validSlots.sorted().joinToString(",")
                )
            )
            val weeklyRange = resolvePlanningDateRange(PlanningPeriodType.WEEK, weekStartIso)
            val periodId = weeklyRange.periodId
            val previousPeriod = dao.getPlanningPeriod(periodId)
            dao.upsertPlanningPeriod(
                PlanningPeriodEntity(
                    id = periodId,
                    periodType = PlanningPeriodType.WEEK.storageValue,
                    startDateIso = weeklyRange.startDate.toString(),
                    endDateIso = weeklyRange.endDate.toString(),
                    budgetDzd = weeklyBudgetDzd,
                    wilaya = selectedWilaya.orEmpty(),
                    mealSlotsCsv = validSlots.sorted().joinToString(","),
                    engineVersion = "0.5.0",
                    createdAtEpochMillis = previousPeriod?.createdAtEpochMillis?.takeIf { it > 0L } ?: generatedAt,
                    priceSnapshotAtEpochMillis = priceSnapshotAt,
                    marketPriceSnapshotCaptured = true,
                    householdServings = householdServings.coerceIn(1, 12),
                    updatedAtEpochMillis = generatedAt
                )
            )
            // Freeze the exact verified product-offer evidence available to this plan.
            dao.deletePlanMarketPriceSnapshots(periodId)
            if (marketPriceEvidence.isNotEmpty()) {
                dao.upsertPlanMarketPriceSnapshots(marketPriceEvidence.map { it.toPlanMarketSnapshot(periodId) })
            }
        }
        val result = calculateBasket(
            chosen.values.toList(),
            recipes,
            pantry,
            prices,
            selectedWilaya = selectedWilaya,
            foodTaxonomyByIngredientId = ingredientTaxonomyById,
            marketProductPriceEvidence = marketPriceEvidence,
            planIngredientAdjustments = rebasedAdjustments,
            nowEpochMillis = marketPriceSnapshotAt
        )
        val within = result.unknownPriceCount == 0 && result.purchaseOutlayDzd <= weeklyBudgetDzd
        val finalRecipesById = recipes.associateBy { it.id }
        val meatUsageByMonth = meatAllowanceBreakdown.associate { allowance ->
            allowance.month to chosen.values.sumOf { meal ->
                val mealDate = planningWeekStart.plusDays(meal.dayIndex.toLong())
                if (YearMonth.from(mealDate) != allowance.month) return@sumOf 0.0
                val recipe = meal.recipeId?.let(finalRecipesById::get)
                    ?: return@sumOf 0.0
                recipeMeatQuantityGrams(recipe, meal.servings, ingredientTaxonomyById)
                    ?: 1_000_000_000.0
            }
        }
        val monthlyLimitExceeded = normalizedMeatPreference == "monthly_limit" &&
            meatAllowanceBreakdown.any { allowance ->
                (meatUsageByMonth[allowance.month] ?: 0.0) > allowance.allowedGrams + 0.001
            }
        val message = when {
            monthlyLimitExceeded ->
                "أنشأنا الخطة، لكن الوصفات المتاحة أو الوجبات المحفوظة تجاوزت السقف النسبي المحسوب للحوم في أيام هذا الأسبوع. خفّضنا الكمية قدر الإمكان."
            !within ->
                "أنشأنا خطة أولية، لكنها تتجاوز الميزانية أو تحتوي على أسعار غير متوفرة. راجع الوجبات قبل التسوق."
            normalizedMeatPreference == "monthly_limit" ->
                "أنشأنا الخطة ضمن ميزانيتك، مع احترام سقف استهلاك اللحوم الشهري."
            normalizedMeatPreference == "none" || normalizedMeatPreference == "chicken_only" ->
                "أنشأنا الخطة وفق تفضيل استهلاك اللحوم الذي اخترته."
            normalizedOilPreference == "less" ->
                "أنشأنا الخطة مع تفضيل الوصفات الأقل استعمالاً للزيت."
            else -> "أنشأنا خطة أولية ضمن ميزانيتك التقديرية."
        }
        return GenerationResult(
            success = true,
            message = message,
            purchaseOutlayDzd = result.purchaseOutlayDzd.toInt(),
            budgetDzd = weeklyBudgetDzd,
            meatAllowanceBreakdown = meatAllowanceBreakdown
        )
    }


    /**
     * Shared bounded planner engine. It works on calendar-relative day indexes
     * for any canonical WEEK or MONTH range; storage and legacy UI models stay in
     * the caller's compatibility adapter. No Room reads or writes occur here.
     */

    /**
     * Generates one complete calendar month through the shared period engine.
     *
     * Monthly meals are persisted only in planning_period_meals. Legacy
     * plan_meals/plan_runs and week-scoped shopping rows remain untouched.
     */
    suspend fun generateMonthlyPlan(
        month: YearMonth,
        monthlyBudgetDzd: Int,
        householdServings: Int,
        allergyIds: Set<String>,
        selectedWilaya: String? = null,
        selectedMealSlots: Set<String> = MealSlots.ids.toSet(),
        dislikedIngredientIds: Set<String> = emptySet(),
        dietPattern: String = "none",
        maxCookingMinutes: Int = 60,
        budgetBufferPercent: Int = 5,
        meatPreference: String = "regular",
        monthlyMeatGrams: Int = 3_000,
        oilPreference: String = "regular",
        fillEmptyOnly: Boolean = false
    ): GenerationResult {
        ensureInitialized()

        val monthlyRange = resolvePlanningDateRange(
            PlanningPeriodType.MONTH,
            month.atDay(1).toString()
        )
        val periodId = monthlyRange.periodId
        val validSlots = selectedMealSlots.intersect(MealSlots.ids.toSet())
        val budget = monthlyBudgetDzd.coerceAtLeast(0)
        if (validSlots.isEmpty()) {
            return GenerationResult(
                success = false,
                message = "اختر وجبة واحدة على الأقل لتخطيط الشهر.",
                purchaseOutlayDzd = 0,
                budgetDzd = budget
            )
        }

        val existingPeriod = dao.getPlanningPeriod(periodId)
        if (existingPeriod == null) {
            val now = System.currentTimeMillis()
            dao.upsertPlanningPeriod(
                PlanningPeriodEntity(
                    id = periodId,
                    periodType = PlanningPeriodType.MONTH.storageValue,
                    startDateIso = monthlyRange.startDate.toString(),
                    endDateIso = monthlyRange.endDate.toString(),
                    budgetDzd = budget,
                    wilaya = selectedWilaya.orEmpty(),
                    mealSlotsCsv = validSlots.sorted().joinToString(","),
                    engineVersion = "period-adapter",
                    createdAtEpochMillis = now,
                    priceSnapshotAtEpochMillis = 0L,
                    marketPriceSnapshotCaptured = false,
                    householdServings = householdServings.coerceIn(1, 12),
                    updatedAtEpochMillis = now
                )
            )
        }

        val existingMeals = dao.getPlanningPeriodMeals(periodId)
            .filter { it.mealSlot in validSlots }
            .map { it.toPlannedMeal(monthlyRange.startDate) }

        val persistedAdjustmentRows = dao.getPlanIngredientAdjustments(periodId)
        val previousIngredientDemands = IngredientDemandCalculator.calculate(existingMeals, recipes)
        // Only adjustments active in the current saved recipe set influence
        // candidate scoring. Inactive records remain persisted but cannot
        // silently deduct stock if a later regeneration introduces that food.
        val planAdjustmentsByIngredientUnit = activePlanIngredientAdjustments(
            persistedAdjustmentRows,
            previousIngredientDemands
        )

        val locked = existingMeals.filter { shouldPreserveMeal(it, fillEmptyOnly) }
        val lockedConflict = findLockedAllergyConflict(locked, recipes, allergyIds)
        if (lockedConflict != null) {
            return GenerationResult(
                success = false,
                message = "توجد وجبة مثبتة لا توافق قيود الحساسية الحالية. استبدلها أو غيّر قيود الطعام قبل إنشاء خطة الشهر. لم نغيّر أي وجبة.",
                purchaseOutlayDzd = 0,
                budgetDzd = budget
            )
        }

        val ingredientsById = loadIngredients().associateBy { it.id }
        val ingredientTaxonomyById = ingredientsById.mapValues { (_, ingredient) ->
            FoodTaxonomyRef(ingredient.categoryId, ingredient.subcategoryId)
        }
        val pinnedDietConflict = findPinnedDietConflict(
            locked,
            recipes,
            ingredientTaxonomyById,
            dietPattern
        )
        if (pinnedDietConflict != null) {
            return GenerationResult(
                success = false,
                message = "توجد وجبة مثبتة لا توافق النمط الغذائي الحالي. استبدلها أو غيّر النمط قبل إنشاء خطة الشهر. لم نغيّر أي وجبة.",
                purchaseOutlayDzd = 0,
                budgetDzd = budget
            )
        }

        val normalizedMeatPreference = meatPreference.takeIf {
            it in setOf("regular", "none", "chicken_only", "monthly_limit")
        } ?: "regular"
        val normalizedOilPreference = oilPreference.takeIf {
            it in setOf("regular", "less")
        } ?: "regular"
        val meatAllowanceBreakdown = if (normalizedMeatPreference == "monthly_limit") {
            calculatePeriodMeatAllowances(monthlyRange, monthlyMeatGrams)
        } else {
            emptyList()
        }
        val meatAllowanceGramsByMonth = meatAllowanceBreakdown.associate { it.month to it.allowedGrams }

        val pinnedMeatConflict = locked.firstNotNullOfOrNull { meal ->
            val recipe = meal.recipeId?.let { id -> recipes.firstOrNull { it.id == id } }
                ?: return@firstNotNullOfOrNull null
            recipe.id.takeIf {
                !isRecipeAllowedByMeatPreference(
                    recipe,
                    ingredientTaxonomyById,
                    normalizedMeatPreference,
                    ingredientsById
                )
            }
        }
        if (pinnedMeatConflict != null) {
            return GenerationResult(
                success = false,
                message = "توجد وجبة مطبوخة أو مثبتة لا توافق تفضيل استهلاك اللحوم الحالي. غيّر الوجبة أو عدّل التفضيل قبل إعادة تخطيط الشهر. لم نغيّر أي وجبة.",
                purchaseOutlayDzd = 0,
                budgetDzd = budget
            )
        }

        val generatorRecipes = recipes.filter(PlanCuisinePolicy::allowsForGeneration)
        val allowed = generatorRecipes.filter { recipe ->
            isRecipeAllowed(recipe, ingredientTaxonomyById, allergyIds, dietPattern) &&
                isRecipeAllowedByMeatPreference(
                    recipe,
                    ingredientTaxonomyById,
                    normalizedMeatPreference,
                    ingredientsById
                ) &&
                (normalizedMeatPreference != "monthly_limit" ||
                    recipeMeatQuantityGrams(
                        recipe,
                        householdServings,
                        ingredientTaxonomyById
                    ) != null)
        }
        if (allowed.isEmpty()) {
            return GenerationResult(
                success = false,
                message = "لا توجد أطباق رئيسية جزائرية متاحة بعد تطبيق قيود الحساسية وتفضيلات الطعام الحالية. لم نغيّر خطة الشهر.",
                purchaseOutlayDzd = 0,
                budgetDzd = budget
            )
        }

        val pantry = dao.getPantryItems()
        val prices = dao.getPriceObservations()
        val marketPriceSnapshotAt = System.currentTimeMillis()
        val marketPriceEvidence = if (existingPeriod?.marketPriceSnapshotCaptured == true) {
            dao.getPlanMarketPriceSnapshots(periodId).map { it.toPriceEvidence() }
        } else {
            dao.getVerifiedMarketProductPriceRows()
                .asSequence()
                .filter { (it.observedAtEpochMillis ?: Long.MAX_VALUE) <= marketPriceSnapshotAt }
                .map { it.toPriceEvidence() }
                .toList()
        }

        // This period already spans the entire calendar month. Its allowance
        // therefore remains the full monthly cap, regardless of weekly plans.
        val chosen = generatePeriodCandidates(
            periodType = PlanningPeriodType.MONTH,
            startDate = monthlyRange.startDate,
            endDate = monthlyRange.endDate,
            periodBudgetDzd = budget,
            householdServings = householdServings,
            mealSlots = validSlots,
            fillEmptyOnly = fillEmptyOnly,
            existingMeals = existingMeals,
            locked = locked,
            recipes = recipes,
            allowed = allowed,
            canonicalDislikedIngredientIds = dislikedIngredientIds
                .mapTo(linkedSetOf(), foodIdentityResolver::canonicalId),
            ingredientTaxonomyById = ingredientTaxonomyById,
            pantry = pantry,
            prices = prices,
            selectedWilaya = selectedWilaya,
            marketPriceEvidence = marketPriceEvidence,
            planAdjustmentsByIngredientUnit = planAdjustmentsByIngredientUnit,
            marketPriceSnapshotAt = marketPriceSnapshotAt,
            budgetBufferPercent = budgetBufferPercent,
            previousGeneratedAtEpochMillis = existingPeriod?.updatedAtEpochMillis,
            favoriteRecipeIds = dao.getFavoriteRecipeIds().toSet(),
            meatAllowanceGramsByMonth = meatAllowanceGramsByMonth,
            normalizedMeatPreference = normalizedMeatPreference,
            normalizedOilPreference = normalizedOilPreference,
            maxCookingMinutes = maxCookingMinutes
        )

        // Removed ingredients stay stored but inactive; new ingredients receive
        // no assumed at-home amount even when an older inactive row exists.
        val adjustmentRebase = rebasePlanIngredientAdjustments(
            periodId = periodId,
            previousPlannedMeals = existingMeals,
            plannedMeals = chosen.values.toList(),
            recipes = recipes
        )
        val rebasedAdjustmentRows = adjustmentRebase.storedRows
        val rebasedAdjustments = adjustmentRebase.activeAdjustments

        val generatedAt = System.currentTimeMillis()
        database.withTransaction {
            dao.deletePlanningPeriodMealSlots(periodId, validSlots.toList())
            dao.upsertPlanningPeriodMeals(
                chosen.values
                    .sortedWith(compareBy<PlannedMeal> { it.dayIndex }.thenBy { it.mealSlot })
                    .map { it.toPlanningPeriodEntity(periodId, monthlyRange.startDate) }
            )
            dao.deletePlanIngredientAdjustments(periodId)
            for (rebasedAdjustment in rebasedAdjustmentRows) {
                dao.upsertPlanIngredientAdjustment(rebasedAdjustment)
            }

            val previousPeriod = dao.getPlanningPeriod(periodId)
            dao.upsertPlanningPeriod(
                PlanningPeriodEntity(
                    id = periodId,
                    periodType = PlanningPeriodType.MONTH.storageValue,
                    startDateIso = monthlyRange.startDate.toString(),
                    endDateIso = monthlyRange.endDate.toString(),
                    budgetDzd = budget,
                    wilaya = selectedWilaya.orEmpty(),
                    mealSlotsCsv = validSlots.sorted().joinToString(","),
                    engineVersion = "0.6.0",
                    createdAtEpochMillis = previousPeriod?.createdAtEpochMillis?.takeIf { it > 0L }
                        ?: generatedAt,
                    priceSnapshotAtEpochMillis = generatedAt,
                    marketPriceSnapshotCaptured = true,
                    householdServings = householdServings.coerceIn(1, 12),
                    updatedAtEpochMillis = generatedAt
                )
            )

            // Freeze ingredient-price observations under the generic period key.
            dao.deletePlanPriceSnapshots(periodId)
            if (prices.isNotEmpty()) {
                dao.insertPlanPriceSnapshots(prices.map { it.toPlanSnapshot(periodId) })
            }
            // Freeze the precise verified product-offer rows used by this month.
            dao.deletePlanMarketPriceSnapshots(periodId)
            if (marketPriceEvidence.isNotEmpty()) {
                dao.upsertPlanMarketPriceSnapshots(
                    marketPriceEvidence.map { it.toPlanMarketSnapshot(periodId) }
                )
            }
        }

        val basket = calculateBasket(
            chosen.values.toList(),
            recipes,
            pantry,
            prices,
            selectedWilaya = selectedWilaya,
            foodTaxonomyByIngredientId = ingredientTaxonomyById,
            marketProductPriceEvidence = marketPriceEvidence,
            planIngredientAdjustments = rebasedAdjustments,
            nowEpochMillis = marketPriceSnapshotAt
        )
        val monthlyMeatUsedGrams = if (normalizedMeatPreference == "monthly_limit") {
            chosen.values.sumOf { meal ->
                val recipe = meal.recipeId?.let { id -> recipes.firstOrNull { it.id == id } }
                    ?: return@sumOf 0.0
                recipeMeatQuantityGrams(recipe, meal.servings, ingredientTaxonomyById)
                    ?: 1_000_000_000.0
            }
        } else {
            0.0
        }
        val monthlyLimitExceeded = normalizedMeatPreference == "monthly_limit" &&
            monthlyMeatUsedGrams > (meatAllowanceBreakdown.firstOrNull()?.allowedGrams ?: 0.0) + 0.001
        val withinBudget = basket.unknownPriceCount == 0 && basket.purchaseOutlayDzd <= budget
        val message = when {
            monthlyLimitExceeded ->
                "أنشأنا الخطة الشهرية، لكن الوصفات المتاحة أو الوجبات المحفوظة لا تسمح بالالتزام الكامل بسقف اللحوم الشهري. خفّضنا الكمية قدر الإمكان."
            !withinBudget ->
                "أنشأنا خطة الشهر، لكنها تتجاوز الميزانية أو تحتوي على أسعار غير متوفرة. راجع الوجبات قبل التسوق."
            normalizedMeatPreference == "monthly_limit" ->
                "أنشأنا خطة الشهر ضمن ميزانيتك، مع احترام سقف استهلاك اللحوم الشهري."
            normalizedMeatPreference == "none" || normalizedMeatPreference == "chicken_only" ->
                "أنشأنا خطة الشهر وفق تفضيل استهلاك اللحوم الذي اخترته."
            normalizedOilPreference == "less" ->
                "أنشأنا خطة الشهر مع تفضيل الوصفات الأقل استعمالاً للزيت."
            else -> "أنشأنا خطة شهرية أولية ضمن ميزانيتك التقديرية."
        }
        return GenerationResult(
            success = true,
            message = message,
            purchaseOutlayDzd = basket.purchaseOutlayDzd.toInt(),
            budgetDzd = budget,
            meatAllowanceBreakdown = meatAllowanceBreakdown
        )
    }

    private suspend fun generatePeriodCandidates(
        periodType: PlanningPeriodType,
        startDate: LocalDate,
        endDate: LocalDate,
        periodBudgetDzd: Int,
        householdServings: Int,
        mealSlots: Set<String>,
        fillEmptyOnly: Boolean,
        existingMeals: List<PlannedMeal>,
        locked: List<PlannedMeal>,
        recipes: List<MealRecipe>,
        allowed: List<MealRecipe>,
        canonicalDislikedIngredientIds: Set<String>,
        ingredientTaxonomyById: Map<String, FoodTaxonomyRef>,
        pantry: List<PantryItemEntity>,
        prices: List<PriceObservationEntity>,
        selectedWilaya: String?,
        marketPriceEvidence: List<MarketProductPriceEvidence>,
        planAdjustmentsByIngredientUnit: Map<Pair<String, String>, PlanIngredientAdjustment>,
        marketPriceSnapshotAt: Long,
        budgetBufferPercent: Int,
        previousGeneratedAtEpochMillis: Long?,
        favoriteRecipeIds: Set<String>,
        meatAllowanceGramsByMonth: Map<YearMonth, Double>,
        normalizedMeatPreference: String,
        normalizedOilPreference: String,
        maxCookingMinutes: Int
    ): Map<String, PlannedMeal> {
        val canonicalRange = resolvePlanningDateRange(periodType, startDate.toString())
        require(canonicalRange.startDate == startDate && canonicalRange.endDate == endDate) {
            "Generator dates must match a complete canonical planning period."
        }
        val periodStartDate = canonicalRange.startDate
        val periodStartIso = periodStartDate.toString()
        val periodDayCount = canonicalRange.dayCount
        val validSlots = mealSlots.intersect(MealSlots.ids.toSet())
        require(validSlots.isNotEmpty()) { "At least one meal slot is required." }
        val slots = buildPlanningPeriodSlots(
            canonicalRange,
            MealSlots.ids.filter { it in validSlots }
        )
        val planningMonths = (0 until periodDayCount)
            .map { offset -> YearMonth.from(periodStartDate.plusDays(offset.toLong())) }
            .toSet()
        val targetBudget = periodBudgetDzd *
            (100 - budgetBufferPercent.coerceIn(0, 30)) / 100.0
        val previousGeneratedRecipes = existingMeals
            .filterNot { shouldPreserveMeal(it, fillEmptyOnly) }
            .mapNotNull { meal -> meal.recipeId?.let { meal.id to it } }
            .toMap()

        return withContext(Dispatchers.Default) {
            data class RecipeSignature(
                val proteinGroups: Set<String>,
                val proteinIngredientIds: Set<String>,
                val reusableIngredientIds: Set<String>
            )

            val proteinCategoryIds = setOf(
                "foodcat-meat",
                "foodcat-poultry",
                "foodcat-seafood",
                "foodcat-eggs",
                "foodcat-legumes"
            )
            val signatures = allowed.associate { recipe ->
                val proteinIngredients = recipe.ingredients
                    .asSequence()
                    .filter { !it.optional && it.quantityBase > 0.0 }
                    .mapNotNull { ingredient ->
                        val categoryId = ingredientTaxonomyById[ingredient.ingredientId]?.categoryId
                            ?: return@mapNotNull null
                        if (categoryId in proteinCategoryIds) ingredient to categoryId else null
                    }
                    .sortedByDescending { it.first.quantityBase }
                    .take(2)
                    .toList()
                val reusableIngredientIds = recipe.ingredients
                    .asSequence()
                    .filter { !it.optional && it.quantityBase > 0.0 }
                    .mapNotNull { ingredient ->
                        val categoryId = ingredientTaxonomyById[ingredient.ingredientId]?.categoryId
                            ?: return@mapNotNull null
                        ingredient.ingredientId.takeIf {
                            categoryId !in proteinCategoryIds && categoryId != "foodcat-oils"
                        }
                    }
                    .toSet()
                recipe.id to RecipeSignature(
                    proteinGroups = proteinIngredients.mapTo(linkedSetOf()) { it.second },
                    proteinIngredientIds = proteinIngredients.mapTo(linkedSetOf()) { it.first.ingredientId },
                    reusableIngredientIds = reusableIngredientIds
                )
            }
            val recipesById = recipes.associateBy { it.id }
            val lockedIds = locked.mapTo(hashSetOf()) { it.id }
            val rotationPenalty = (periodBudgetDzd * 0.24).coerceIn(1_800.0, 5_200.0)

            fun monthlyMeatUsageGrams(candidateMeals: List<PlannedMeal>, month: YearMonth): Double {
                if (normalizedMeatPreference != "monthly_limit") return 0.0
                val currentPeriodUsage = candidateMeals.sumOf { meal ->
                    val mealDate = periodStartDate.plusDays(meal.dayIndex.toLong())
                    if (YearMonth.from(mealDate) != month) return@sumOf 0.0
                    val recipe = meal.recipeId?.let(recipesById::get) ?: return@sumOf 0.0
                    recipeMeatQuantityGrams(recipe, meal.servings, ingredientTaxonomyById)
                        ?: 1_000_000_000.0
                }
                return currentPeriodUsage
            }

            fun scorePlan(candidateMeals: List<PlannedMeal>, tieBreak: Double = 0.0): Double {
                // Allergy, diet and meat constraints are already enforced before a
                // recipe can enter this search. Keep weighted cost/quality scoring
                // strictly downstream so a cheap but forbidden recipe never wins.
                val basket = calculateBasket(
                    candidateMeals,
                    recipes,
                    pantry,
                    prices,
                    selectedWilaya = selectedWilaya,
                    foodTaxonomyByIngredientId = ingredientTaxonomyById,
                    marketProductPriceEvidence = marketPriceEvidence,
                    planIngredientAdjustments = planAdjustmentsByIngredientUnit,
                    nowEpochMillis = marketPriceSnapshotAt
                )
                val mealsWithRecipes = candidateMeals.mapNotNull { meal ->
                    meal.recipeId?.let { recipeId ->
                        recipesById[recipeId]?.let { meal to it }
                    }
                }
                // Variety and soft preferences are scored across every populated slot
                // in this canonical period, never as disconnected weekly fragments.
                val repeatedRecipeCount = mealsWithRecipes
                    .groupingBy { it.second.id }
                    .eachCount()
                    .values
                    .sumOf { (it - 1).coerceAtLeast(0) }
                var adjacentSimilarityPenalty = 0.0
                for (firstIndex in mealsWithRecipes.indices) {
                    for (secondIndex in firstIndex + 1 until mealsWithRecipes.size) {
                        val (firstMeal, firstRecipe) = mealsWithRecipes[firstIndex]
                        val (secondMeal, secondRecipe) = mealsWithRecipes[secondIndex]
                        if (kotlin.math.abs(firstMeal.dayIndex - secondMeal.dayIndex) > 1) continue
                        val firstSignature = signatures[firstRecipe.id] ?: continue
                        val secondSignature = signatures[secondRecipe.id] ?: continue
                        val sharedProteinGroups =
                            firstSignature.proteinGroups.intersect(secondSignature.proteinGroups).size
                        val sharedProteinIngredients =
                            firstSignature.proteinIngredientIds.intersect(secondSignature.proteinIngredientIds).size
                        adjacentSimilarityPenalty += sharedProteinGroups * 560.0
                        adjacentSimilarityPenalty += sharedProteinIngredients * 320.0
                    }
                }

                // Reward a compact shared-staple basket across different recipes.
                // Protein and oil are excluded from this signal; meal variety has its
                // own penalty and should not be sacrificed merely to reuse ingredients.
                val ingredientReuseAdjustment = scorePeriodIngredientReuse(
                    mealsWithRecipes.map { (_, recipe) ->
                        signatures[recipe.id]?.reusableIngredientIds.orEmpty()
                    }
                )
                val overBuffer = (basket.purchaseOutlayDzd - targetBudget).coerceAtLeast(0.0)
                val overBudget = (basket.purchaseOutlayDzd - periodBudgetDzd).coerceAtLeast(0.0)
                val previousMatches = candidateMeals.count { meal ->
                    meal.recipeId != null && previousGeneratedRecipes[meal.id] == meal.recipeId
                }
                val preferencePenalty = mealsWithRecipes.sumOf { (_, recipe) ->
                    softPreferencePenalty(recipe, canonicalDislikedIngredientIds, maxCookingMinutes)
                }
                val favoriteReward = mealsWithRecipes.count { (_, recipe) ->
                    recipe.id in favoriteRecipeIds
                } * 240.0
                val oilUsagePenalty = if (normalizedOilPreference == "less") {
                    mealsWithRecipes.sumOf { (meal, recipe) ->
                        val measuredPerServing = recipeOilQuantityPerServing(
                            recipe,
                            meal.servings,
                            ingredientTaxonomyById
                        )
                        if (measuredPerServing == null) 650.0
                        else measuredPerServing * meal.servings * 42.0
                    }
                } else {
                    0.0
                }
                val monthlyMeatPenalty = if (normalizedMeatPreference == "monthly_limit") {
                    planningMonths.sumOf { month ->
                        (monthlyMeatUsageGrams(candidateMeals, month) - (meatAllowanceGramsByMonth[month] ?: 0.0))
                            .coerceAtLeast(0.0) * 250.0
                    }
                } else {
                    0.0
                }

                return basket.purchaseOutlayDzd +
                    overBuffer * 2.25 +
                    overBudget * 4.0 +
                    basket.unknownPriceCount * 350.0 +
                    repeatedRecipeCount * 1_250.0 +
                    adjacentSimilarityPenalty +
                    ingredientReuseAdjustment +
                    previousMatches * rotationPenalty +
                    preferencePenalty +
                    oilUsagePenalty +
                    monthlyMeatPenalty -
                    favoriteReward +
                    tieBreak
            }

            // Keep search bounded for long plans, and make every regeneration
            // distinct even when taps arrive within the same millisecond.
            val searchAttempts = periodSearchAttempts(slots.size)
            val seed = System.nanoTime() xor
                (previousGeneratedAtEpochMillis ?: 0L) xor
                periodStartIso.hashCode().toLong() xor
                UUID.randomUUID().mostSignificantBits
            var bestPlan: List<PlannedMeal>? = null
            var bestScore = Double.POSITIVE_INFINITY

            repeat(searchAttempts) { attempt ->
                val random = Random(seed + attempt.toLong() * 104_729L)
                val dayOffset = random.nextInt(periodDayCount)
                val dayOrder = (0 until periodDayCount).drop(dayOffset) + (0 until periodDayCount).take(dayOffset)
                val attemptSlots = dayOrder.flatMap { day ->
                    slots.filter { it.dayIndex == day }
                }
                val attemptMeals = locked.associateBy { it.id }.toMutableMap()
                val repeatCounts = locked.mapNotNull { it.recipeId }
                    .groupingBy { it }.eachCount().toMutableMap()
                val preferRotation = attempt < searchAttempts - 2

                attemptSlots.forEach { periodSlot ->
                    val id = periodSlot.id
                    if (id in attemptMeals) return@forEach
                    val day = periodSlot.dayIndex
                    val slot = periodSlot.mealSlot
                    val withoutSameDayDuplicate = allowed
                        .filter { (repeatCounts[it.id] ?: 0) < 4 }
                        .filter { recipe ->
                            attemptMeals.values.none { it.dayIndex == day && it.recipeId == recipe.id }
                        }
                    val candidates = withoutSameDayDuplicate.ifEmpty {
                        allowed.filter { recipe ->
                            attemptMeals.values.none { it.dayIndex == day && it.recipeId == recipe.id }
                        }.ifEmpty { allowed }
                    }
                    val rotationAlternatives = candidates.filter {
                        it.id != previousGeneratedRecipes[id]
                    }
                    val candidatePool = if (
                        preferRotation && rotationAlternatives.isNotEmpty()
                    ) rotationAlternatives else candidates
                    val quotaAwarePool = if (normalizedMeatPreference == "monthly_limit") {
                        val currentMeals = attemptMeals.values.toList()
                        val currentUsageByMonth = planningMonths.associateWith { month ->
                            monthlyMeatUsageGrams(currentMeals, month)
                        }
                        candidatePool.filter { recipe ->
                            val proposed = PlannedMeal(
                                id = id,
                                weekStartIso = periodStartIso,
                                dayIndex = day,
                                mealSlot = slot,
                                recipeId = recipe.id,
                                servings = householdServings.coerceIn(1, 12),
                                locked = false
                            )
                            val trialMeals = currentMeals + proposed
                            planningMonths.all { month ->
                                val allowedCeiling = maxOf(
                                    meatAllowanceGramsByMonth[month] ?: 0.0,
                                    currentUsageByMonth[month] ?: 0.0
                                )
                                monthlyMeatUsageGrams(trialMeals, month) <= allowedCeiling + 0.001
                            }
                        }
                    } else {
                        emptyList()
                    }
                    val constrainedPool = quotaAwarePool.ifEmpty { candidatePool }

                    val selectedRecipe = constrainedPool.minByOrNull { recipe ->
                        val proposedMeal = PlannedMeal(
                            id = id,
                            weekStartIso = periodStartIso,
                            dayIndex = day,
                            mealSlot = slot,
                            recipeId = recipe.id,
                            servings = householdServings.coerceIn(1, 12),
                            locked = false
                        )
                        scorePlan(
                            attemptMeals.values.toList() + proposedMeal,
                            tieBreak = random.nextDouble(0.0, 110.0)
                        )
                    } ?: allowed.minBy { it.id }

                    attemptMeals[id] = PlannedMeal(
                        id = id,
                        weekStartIso = periodStartIso,
                        dayIndex = day,
                        mealSlot = slot,
                        recipeId = selectedRecipe.id,
                        servings = householdServings.coerceIn(1, 12),
                        locked = false
                    )
                    repeatCounts[selectedRecipe.id] = (repeatCounts[selectedRecipe.id] ?: 0) + 1
                }

                val completedPlan = attemptMeals.values.toList()
                val completedScore = scorePlan(
                    completedPlan,
                    tieBreak = random.nextDouble(0.0, 35.0)
                )
                if (completedScore < bestScore) {
                    bestScore = completedScore
                    bestPlan = completedPlan
                }
            }

            // A final whole-period single-meal improvement pass catches swaps that
            // become attractive only after all other days have been selected.
            var improvedPlan = bestPlan ?: locked
            var improvedScore = scorePlan(improvedPlan)
            slots.forEach { periodSlot ->
                val id = periodSlot.id
                if (id in lockedIds) return@forEach
                val day = periodSlot.dayIndex
                val slot = periodSlot.mealSlot
                val currentRecipeId = improvedPlan.firstOrNull { it.id == id }?.recipeId
                val sameDayRecipeIds = improvedPlan
                    .filter { it.id != id && it.dayIndex == day }
                    .mapNotNull { it.recipeId }
                    .toSet()
                val alternatives = allowed
                    .filter { it.id != currentRecipeId && it.id !in sameDayRecipeIds }
                    .sortedBy {
                        softPreferencePenalty(it, canonicalDislikedIngredientIds, maxCookingMinutes)
                    }
                    .take(18)
                val monthlyUsageCeilings = planningMonths.associateWith { month ->
                    maxOf(meatAllowanceGramsByMonth[month] ?: 0.0, monthlyMeatUsageGrams(improvedPlan, month))
                }
                alternatives.forEach { recipe ->
                    val replacement = PlannedMeal(
                        id = id,
                        weekStartIso = periodStartIso,
                        dayIndex = day,
                        mealSlot = slot,
                        recipeId = recipe.id,
                        servings = householdServings.coerceIn(1, 12),
                        locked = false
                    )
                    val trial = improvedPlan.filterNot { it.id == id } + replacement
                    val withinMonthlyLimit = normalizedMeatPreference != "monthly_limit" ||
                        planningMonths.all { month ->
                            monthlyMeatUsageGrams(trial, month) <=
                                (monthlyUsageCeilings[month] ?: (meatAllowanceGramsByMonth[month] ?: 0.0)) + 0.001
                        }
                    val score = if (withinMonthlyLimit) scorePlan(trial) else Double.POSITIVE_INFINITY
                    if (score < improvedScore) {
                        improvedPlan = trial
                        improvedScore = score
                    }
                }
            }

            improvedPlan.associateBy { it.id }
        }


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

    /** Date-keyed meal editing shared by monthly plans and weekly adapters. */
    suspend fun setMeal(
        periodId: PlanningPeriodId,
        dayIndex: Int,
        mealSlot: String,
        recipeId: String?,
        servings: Int
    ) {
        val (type, range) = resolvePeriod(periodId) ?: return
        if (type == PlanningPeriodType.WEEK) {
            setMeal(range.startDate.toString(), dayIndex, mealSlot, recipeId, servings)
            return
        }
        if (dayIndex !in 0..30 || mealSlot !in MealSlots.ids) return
        val date = range.startDate.plusDays(dayIndex.toLong())
        if (date.isAfter(range.endDate)) return
        val existing = dao.getPlanningPeriodMeals(periodId.value)
            .firstOrNull { it.dateIso == date.toString() && it.mealSlot == mealSlot }
        if (existing?.isCooked == true) return
        dao.upsertPlanningPeriodMeal(
            (existing ?: PlanningPeriodMealEntity(
                id = "${periodId.value}-${date}-${mealSlot}",
                periodId = periodId.value,
                dateIso = date.toString(),
                mealSlot = mealSlot,
                recipeId = null,
                servings = servings.coerceIn(1, 12),
                locked = false,
                isCooked = false,
                isUserEdited = true
            )).copy(
                recipeId = recipeId,
                servings = servings.coerceIn(1, 12),
                isUserEdited = true
            )
        )
    }

    suspend fun toggleMealLock(id: String, periodId: PlanningPeriodId) {
        val (type, range) = resolvePeriod(periodId) ?: return
        if (type == PlanningPeriodType.WEEK) {
            toggleMealLock(id, range.startDate.toString())
            return
        }
        val meal = dao.getPlanningPeriodMealById(id)?.takeIf { it.periodId == periodId.value } ?: return
        dao.upsertPlanningPeriodMeal(meal.copy(locked = !meal.locked))
    }

    suspend fun toggleMealCooked(id: String, periodId: PlanningPeriodId) {
        val (type, range) = resolvePeriod(periodId) ?: return
        if (type == PlanningPeriodType.WEEK) {
            toggleMealCooked(id, range.startDate.toString())
            return
        }
        val meal = dao.getPlanningPeriodMealById(id)?.takeIf { it.periodId == periodId.value } ?: return
        dao.upsertPlanningPeriodMeal(meal.copy(isCooked = !meal.isCooked))
    }

    suspend fun addRecipeToNextSlot(
        periodId: PlanningPeriodId,
        recipeId: String,
        servings: Int
    ) {
        val (type, range) = resolvePeriod(periodId) ?: return
        if (type == PlanningPeriodType.WEEK) {
            addRecipeToNextSlot(range.startDate.toString(), recipeId, servings)
            return
        }
        val period = dao.getPlanningPeriod(periodId.value) ?: return
        val enabledSlots = period.mealSlotsCsv.split(",").filter { it in MealSlots.ids }
        if (enabledSlots.isEmpty()) return
        val existing = dao.getPlanningPeriodMeals(periodId.value)
        val nextSlot = (0 until range.dayCount).asSequence()
            .flatMap { day -> enabledSlots.asSequence().map { day to it } }
            .firstOrNull { (day, slot) ->
                existing.none { it.dateIso == range.startDate.plusDays(day.toLong()).toString() &&
                    it.mealSlot == slot && it.recipeId != null }
            } ?: return
        setMeal(periodId, nextSlot.first, nextSlot.second, recipeId, servings)
    }

    suspend fun adjustMealServings(id: String, periodId: PlanningPeriodId, delta: Int) {
        val (type, range) = resolvePeriod(periodId) ?: return
        if (type == PlanningPeriodType.WEEK) {
            adjustMealServings(id, range.startDate.toString(), delta)
            return
        }
        val meal = dao.getPlanningPeriodMealById(id)?.takeIf { it.periodId == periodId.value } ?: return
        if (meal.isCooked) return
        dao.upsertPlanningPeriodMeal(
            meal.copy(
                servings = (meal.servings + delta).coerceIn(1, 12),
                isUserEdited = true
            )
        )
    }

    private fun resolvePeriod(periodId: PlanningPeriodId): Pair<PlanningPeriodType, PlanningDateRange>? {
        val separator = periodId.value.indexOf(':')
        if (separator <= 0) return null
        val type = PlanningPeriodType.fromStorage(periodId.value.substring(0, separator)) ?: return null
        val range = runCatching {
            resolvePlanningDateRange(type, periodId.value.substring(separator + 1))
        }.getOrNull() ?: return null
        return (type to range).takeIf { range.periodId == periodId.value }
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
        commonsSearchMutex.withLock {
            val typedTerm = query.trim().take(100)
            require(typedTerm.isNotBlank()) { "أدخلي اسم الطبق للبحث عن صورة." }

            // Search the supplied term plus one independent recipe-name variant.
            // Combining languages into one long query was reducing Commons recall.
            val searchTerms = buildList {
                add(typedTerm)
                listOf(recipe.nativeName, recipe.nameFr, recipe.nameAr).forEach { variant ->
                    val normalized = variant.trim().take(100)
                    if (normalized.isNotBlank() && none { it.equals(normalized, ignoreCase = true) }) {
                        add(normalized)
                    }
                }
            }.take(2)

            val accepted = linkedMapOf<String, RecipeImageCandidate>()
            var successfulRequests = 0
            var failedRequests = 0
            var scannedPages = 0
            var rejectedByLicence = 0
            var rejectedByAttribution = 0
            var rejectedByFormatOrSize = 0
            var lastFailure: IOException? = null

            for (term in searchTerms) {
                try {
                    val requestUrl = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
                        .addQueryParameter("action", "query")
                        .addQueryParameter("format", "json")
                        .addQueryParameter("formatversion", "2")
                        .addQueryParameter("generator", "search")
                        .addQueryParameter("gsrsearch", "$term filetype:bitmap")
                        .addQueryParameter("gsrnamespace", "6")
                        // extmetadata is expensive; keep the per-query batch bounded.
                        .addQueryParameter("gsrlimit", "20")
                        .addQueryParameter("prop", "imageinfo")
                        .addQueryParameter("iiprop", "url|extmetadata|size|mime")
                        .addQueryParameter("iiurlwidth", "720")
                        .addQueryParameter(
                            "iiextmetadatafilter",
                            "Artist|Credit|LicenseShortName|LicenseUrl|Copyrighted"
                        )
                        .build()
                    val request = Request.Builder()
                        .url(requestUrl)
                        .header("Accept", "application/json")
                        .header("Accept-Language", "en,fr;q=0.9,ar;q=0.8")
                        .header("User-Agent", "Midati-Android/1.0 (https://github.com/ni3yyn/Meals)")
                        .build()

                    val body = commonsClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("Wikimedia Commons returned HTTP ${response.code}.")
                        }
                        response.body?.string()
                            ?: throw IOException("Wikimedia Commons returned an empty response.")
                    }
                    successfulRequests++
                    val pages = JSONObject(body).optJSONObject("query")?.optJSONArray("pages")
                        ?: continue
                    scannedPages += pages.length()

                    for (index in 0 until pages.length()) {
                        val page = pages.optJSONObject(index) ?: continue
                        val title = page.optString("title").trim()
                        if (!title.startsWith("File:", ignoreCase = true)) continue

                        val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                        val mime = info.optString("mime").trim().lowercase()
                        val supportedRaster = mime == "image/jpeg" ||
                            mime == "image/png" || mime == "image/webp" || mime == "image/gif"
                        val width = info.optInt("width")
                        val height = info.optInt("height")
                        if (!supportedRaster || width < 200 || height < 140) {
                            rejectedByFormatOrSize++
                            continue
                        }

                        val metadata = info.optJSONObject("extmetadata") ?: continue
                        fun metadataRawValue(key: String): String =
                            metadata.optJSONObject(key)?.optString("value").orEmpty()
                        fun metadataValue(key: String): String =
                            metadataRawValue(key).stripCommonsMarkup()

                        val license = metadataValue("LicenseShortName")
                        // Commons extmetadata is HTML-formatted. Preserve the LicenseUrl
                        // anchor href before stripping tags, otherwise only its link label
                        // survives and the exact licence comparison rejects every result.
                        val licenseUrl = extractCommonsLicenseUrl(metadataRawValue("LicenseUrl")).ifBlank {
                            canonicalRecipeImageLicenseUrl(license).orEmpty()
                        }
                        if (!isReusableRecipeImageLicense(license, licenseUrl)) {
                            rejectedByLicence++
                            continue
                        }

                        val artist = metadataValue("Artist")
                        val credit = metadataValue("Credit")
                        val author = artist.ifBlank { credit }
                        val creditLine = credit.ifBlank { artist }
                        if (recipeImageLicenseRequiresAttribution(license) &&
                            (author.isBlank() || creditLine.isBlank())
                        ) {
                            rejectedByAttribution++
                            continue
                        }

                        // Only use Wikimedia's generated thumbnail, never its original
                        // full-resolution file URL.
                        val imageUrl = info.optString("thumburl").trim()
                        val sourcePageUrl = info.optString("descriptionurl").trim()
                        if (!imageUrl.startsWith("https://upload.wikimedia.org/", ignoreCase = true)) {
                            rejectedByFormatOrSize++
                            continue
                        }
                        if (!sourcePageUrl.startsWith(
                                "https://commons.wikimedia.org/wiki/File:",
                                ignoreCase = true
                            )
                        ) continue

                        accepted.putIfAbsent(
                            imageUrl,
                            RecipeImageCandidate(
                                title = title.substringAfter(':', title),
                                imageUrl = imageUrl,
                                sourcePageUrl = sourcePageUrl,
                                author = author,
                                credit = creditLine,
                                license = license,
                                licenseUrl = licenseUrl,
                                width = width,
                                height = height
                            )
                        )
                    }
                    if (accepted.size >= 30) break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failedRequests++
                    lastFailure = if (error is IOException) error else IOException(
                        "Wikimedia Commons response could not be processed.",
                        error
                    )
                    Log.w(
                        RECIPE_IMAGE_LOG_TAG,
                        "commons_search_failed variant=${searchTerms.indexOf(term) + 1} reason=${error.message?.take(120)}"
                    )
                }
            }

            Log.d(
                RECIPE_IMAGE_LOG_TAG,
                "commons_search requests=${successfulRequests + failedRequests} scanned=$scannedPages accepted=${accepted.size} rejected_license=$rejectedByLicence rejected_attribution=$rejectedByAttribution rejected_format=$rejectedByFormatOrSize"
            )
            val commonsResults = accepted.values.take(30)
            if (commonsResults.size >= 8) return@withLock commonsResults

            // Commons has incomplete metadata for some otherwise useful files.
            // Openverse provides a second image index with structured attribution.
            var openverseFailure: Exception? = null
            val openverseResults = try {
                searchOpenverseRecipeImages(typedTerm)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                openverseFailure = error
                Log.w(RECIPE_IMAGE_LOG_TAG, "openverse_search_failed reason=${error.message?.take(120)}")
                emptyList()
            }

            val combined = (commonsResults + openverseResults)
                .distinctBy { it.imageUrl }
                .take(30)
            Log.d(
                RECIPE_IMAGE_LOG_TAG,
                "image_search commons=${commonsResults.size} openverse=${openverseResults.size} total=${combined.size}"
            )
            if (combined.isEmpty()) {
                if (successfulRequests == 0 && lastFailure != null) throw lastFailure
                if (openverseFailure != null) {
                    throw IOException("تعذر الاتصال بمصادر الصور حالياً.", openverseFailure)
                }
            }
            combined
        }
    }

    private fun searchOpenverseRecipeImages(query: String): List<RecipeImageCandidate> {
        val requestUrl = "https://api.openverse.org/v1/images/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.take(180))
            .addQueryParameter("page_size", "20")
            .addQueryParameter("license", "cc0,by,by-sa,pdm")
            .addQueryParameter("category", "photograph")
            .addQueryParameter("mature", "false")
            .addQueryParameter("filter_dead", "true")
            .build()
        val request = Request.Builder()
            .url(requestUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "Midati-Android/1.0 (https://github.com/ni3yyn/Meals)")
            .build()
        val body = commonsClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Openverse returned HTTP ${response.code}.")
            }
            response.body?.string() ?: throw IOException("Openverse returned an empty response.")
        }
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val code = item.optString("license").trim().lowercase()
                val version = item.optString("license_version").trim()
                val license = when (code) {
                    "cc0" -> "CC0 1.0"
                    "by" -> "CC BY ${version.ifBlank { "4.0" }}"
                    "by-sa" -> "CC BY-SA ${version.ifBlank { "4.0" }}"
                    "pdm" -> "Public domain"
                    else -> continue
                }
                val licenseUrl = item.optString("license_url").trim().ifBlank {
                    canonicalRecipeImageLicenseUrl(license).orEmpty()
                }
                if (!isReusableRecipeImageLicense(license, licenseUrl)) continue

                // Prefer Openverse's own resized thumbnail URL rather than the
                // provider's original image. It is stable and sized for app cards.
                val imageUrl = item.optString("thumbnail").trim()
                val sourcePageUrl = item.optString("foreign_landing_url").trim()
                if (!imageUrl.startsWith("https://api.openverse.org/v1/images/")) continue
                if (!sourcePageUrl.startsWith("https://", ignoreCase = true)) continue

                val author = item.optString("creator").trim()
                val attribution = item.optString("attribution").trim()
                val credit = attribution.ifBlank { author }
                if (recipeImageLicenseRequiresAttribution(license) &&
                    (author.isBlank() || credit.isBlank())
                ) continue

                add(
                    RecipeImageCandidate(
                        title = item.optString("title").trim().ifBlank { query },
                        imageUrl = imageUrl,
                        sourcePageUrl = sourcePageUrl,
                        author = author.ifBlank { credit },
                        credit = credit,
                        license = license,
                        licenseUrl = licenseUrl,
                        width = item.optInt("width"),
                        height = item.optInt("height")
                    )
                )
            }
        }.take(20)
    }

    /**
     * Searches multiple recipe-name variants and persists only a meaningful match.
     * The score threshold allows Commons file names to include descriptive words,
     * while still requiring overlap with a recognised recipe name.
     */
    suspend fun automaticallyFindAndSaveRecipeImage(recipe: MealRecipe): RecipeImageCandidate {
        val query = sequenceOf(recipe.nativeName, recipe.nameFr, recipe.nameAr)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: throw IOException("لا يتوفر اسم مناسب للبحث عن صورة هذه الوصفة.")

        val candidates = searchCommonsRecipeImages(recipe, query)
        val bestMatch = candidates
            .map { candidate -> candidate to commonsRecipeImageMatchScore(recipe, candidate) }
            .filter { (_, score) -> score >= 0.38 }
            .maxByOrNull { (_, score) -> score }
            ?.first
            ?: throw NoMatchingRecipeImageException()

        saveRecipeImage(recipe.id, bestMatch)
        return bestMatch
    }

    /**
     * Performs a small, serial background pass when the recipe library is opened.
     * Successful photos are persisted in Room; failed/no-match attempts are cooled
     * down so app navigation cannot repeatedly hammer Wikimedia.
     */
    suspend fun discoverMissingRecipeImages(
        recipes: List<MealRecipe>,
        maxAttempts: Int = 3
    ): Int = withContext(Dispatchers.IO) {
        recipeImageDiscoveryMutex.withLock {
            val now = System.currentTimeMillis()
            val eligible = recipes
                .asSequence()
                .distinctBy { it.id }
                .filterNot { it.hasVerifiedReusableImage() }
                .filter { it.id.isNotBlank() }
                .filter { recipe ->
                    recipeImageDiscoveryPreferences.getLong(
                        recipeImageDiscoveryKey(recipe.id),
                        0L
                    ) <= now
                }
                .take(maxAttempts.coerceIn(1, 4))
                .toList()

            var savedCount = 0
            for (recipe in eligible) {
                val key = recipeImageDiscoveryKey(recipe.id)
                recipeImageDiscoveryPreferences.edit()
                    .putLong(key, System.currentTimeMillis() + IMAGE_DISCOVERY_IN_PROGRESS_COOLDOWN_MS)
                    .apply()
                try {
                    automaticallyFindAndSaveRecipeImage(recipe)
                    recipeImageDiscoveryPreferences.edit().remove(key).apply()
                    savedCount++
                    Log.i(RECIPE_IMAGE_LOG_TAG, "automatic_image_saved recipe_id=${recipe.id}")
                } catch (cancelled: CancellationException) {
                    recipeImageDiscoveryPreferences.edit().remove(key).apply()
                    throw cancelled
                } catch (error: Exception) {
                    val cooldown = if (error is NoMatchingRecipeImageException) {
                        IMAGE_DISCOVERY_NO_MATCH_COOLDOWN_MS
                    } else {
                        IMAGE_DISCOVERY_NETWORK_ERROR_COOLDOWN_MS
                    }
                    recipeImageDiscoveryPreferences.edit()
                        .putLong(key, System.currentTimeMillis() + cooldown)
                        .apply()
                    Log.w(
                        RECIPE_IMAGE_LOG_TAG,
                        "automatic_image_failed recipe_id=${recipe.id} retry_after_ms=$cooldown reason=${error.message?.take(160)}"
                    )
                }
                delay(250)
            }
            savedCount
        }
    }

    suspend fun saveRecipeImage(recipeId: String, image: RecipeImageCandidate) {
        val isCommonsImage = image.imageUrl.startsWith(
            "https://upload.wikimedia.org/",
            ignoreCase = true
        ) && image.sourcePageUrl.startsWith(
            "https://commons.wikimedia.org/wiki/File:",
            ignoreCase = true
        )
        val isOpenverseImage = image.imageUrl.startsWith(
            "https://api.openverse.org/v1/images/",
            ignoreCase = true
        ) && image.sourcePageUrl.startsWith("https://", ignoreCase = true) &&
            !image.sourcePageUrl.startsWith("https://api.openverse.org/", ignoreCase = true)
        require(isCommonsImage || isOpenverseImage) {
            "رابط الصورة أو صفحة مصدرها غير موثوق."
        }
        require(isReusableRecipeImageLicense(image.license, image.licenseUrl)) {
            "ترخيص هذه الصورة لا يسمح بإعادة استخدامها داخل التطبيق."
        }
        require(
            !recipeImageLicenseRequiresAttribution(image.license) ||
                (image.author.isNotBlank() && image.credit.isNotBlank())
        ) {
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

    /** Refresh price evidence only; saved recipes and plan-local edits are immutable. */
    suspend fun refreshPlanPrices(periodId: PlanningPeriodId): MealsSnapshot? {
        ensureInitialized()
        val separator = periodId.value.indexOf(':')
        if (separator <= 0) return null
        val type = PlanningPeriodType.fromStorage(periodId.value.substring(0, separator)) ?: return null
        val range = runCatching { resolvePlanningDateRange(type, periodId.value.substring(separator + 1)) }.getOrNull() ?: return null
        if (range.periodId != periodId.value) return null
        val before = snapshot(periodId)
        val now = System.currentTimeMillis()
        val priceRows = dao.getPriceObservations()
        val marketEvidence = dao.getVerifiedMarketProductPriceRows()
            .asSequence().filter { (it.observedAtEpochMillis ?: Long.MAX_VALUE) <= now }
            .map { it.toPriceEvidence() }.toList()
        val scopeKey = if (type == PlanningPeriodType.WEEK) range.startDate.toString() else periodId.value

        database.withTransaction {
            val current = dao.getPlanningPeriod(periodId.value) ?: return@withTransaction
            dao.deletePlanPriceSnapshots(scopeKey)
            if (priceRows.isNotEmpty()) dao.insertPlanPriceSnapshots(priceRows.map { it.toPlanSnapshot(scopeKey) })
            dao.deletePlanMarketPriceSnapshots(periodId.value)
            if (marketEvidence.isNotEmpty()) {
                dao.upsertPlanMarketPriceSnapshots(marketEvidence.map { it.toPlanMarketSnapshot(periodId.value) })
            }
            // Keep updatedAtEpochMillis as the recipe-generation seed/version;
            // a price-only refresh must not influence the next regeneration.
            dao.upsertPlanningPeriod(current.copy(
                priceSnapshotAtEpochMillis = now,
                marketPriceSnapshotCaptured = true
            ))
            if (type == PlanningPeriodType.WEEK) {
                dao.getPlanRun(scopeKey)?.let { dao.upsertPlanRun(it.copy(priceSnapshotAtEpochMillis = now)) }
            }
        }
        return snapshot(periodId, selectedWilaya = before.planningPeriod?.wilaya?.takeIf { it.isNotBlank() })
    }

    suspend fun updatePlanIngredientAdjustment(
        weekStartIso: String,
        ingredientId: String,
        unit: String,
        adjustmentType: String?,
        availableQuantity: Double = 0.0
    ) {
        ensureInitialized()
        val canonicalId = foodIdentityResolver.canonicalId(ingredientId)
        val normalizedUnit = unit.trim()
        if (normalizedUnit.isBlank()) return
        val ingredientExists = dao.getIngredients().any { it.id == canonicalId }
        if (!ingredientExists) return
        val weeklyRange = resolvePlanningDateRange(PlanningPeriodType.WEEK, weekStartIso)
        val periodId = weeklyRange.periodId
        val type = when (adjustmentType?.trim()?.uppercase()) {
            null, "", "NONE" -> null
            "SOME" -> "SOME"
            "ENOUGH" -> "ENOUGH"
            else -> return
        }

        // Validate before mutating either persisted row. A rejected quantity must
        // never erase a previous valid adjustment or a manual shopping override.
        if (type == "SOME" && (!availableQuantity.isFinite() || availableQuantity <= 0.0)) {
            return
        }

        database.withTransaction {
            persistPlanIngredientAdjustment(
                weekStartIso = weekStartIso,
                periodId = periodId,
                ingredientId = canonicalId,
                unit = normalizedUnit,
                type = type,
                availableQuantity = availableQuantity,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
    }

    /**
     * Updates the at-home adjustment for one canonical ingredient in one saved
     * planning period, then returns a rebuilt snapshot with purchase needs and
     * estimates recalculated from that period's existing recipes and saved price
     * evidence. This never calls either recipe generator.
     *
     * alreadyHaveAll takes precedence over the entered quantity. When false,
     * zero clears the adjustment; a positive amount is converted into the demand
     * unit and stored as a plan-local partial amount.
     */
    suspend fun updatePlanIngredientAdjustment(
        periodId: PlanningPeriodId,
        ingredientId: String,
        unit: String,
        alreadyHaveAll: Boolean,
        alreadyHaveQuantity: Double
    ): MealsSnapshot? {
        ensureInitialized()

        val normalizedUnit = unit.trim()
        if (normalizedUnit.isBlank() ||
            !alreadyHaveQuantity.isFinite() ||
            alreadyHaveQuantity < 0.0
        ) return null

        val canonicalId = foodIdentityResolver.canonicalId(ingredientId)
        if (canonicalId.isBlank() || dao.getIngredients().none { it.id == canonicalId }) {
            return null
        }

        val separator = periodId.value.indexOf(':')
        if (separator <= 0) return null
        val periodType = PlanningPeriodType.fromStorage(periodId.value.substring(0, separator))
            ?: return null
        val range = runCatching {
            resolvePlanningDateRange(periodType, periodId.value.substring(separator + 1))
        }.getOrNull() ?: return null
        if (range.periodId != periodId.value) return null

        // This read also creates the lightweight period adapter for old weekly
        // plans before the period/ingredient adjustment foreign key is written.
        val currentSnapshot = snapshot(periodId)
        val matchingDemand = currentSnapshot.basket.ingredientDemands.singleOrNull { demand ->
            demand.ingredientId == canonicalId &&
                convertIngredientQuantity(1.0, normalizedUnit, demand.unit) != null
        } ?: return null
        val demandUnit = matchingDemand.unit
        val availableInDemandUnit = convertIngredientQuantity(
            alreadyHaveQuantity,
            normalizedUnit,
            demandUnit
        ) ?: return null

        val adjustmentType = when {
            alreadyHaveAll -> PlanIngredientAdjustment.Type.ENOUGH
            availableInDemandUnit > 0.0 -> PlanIngredientAdjustment.Type.SOME
            else -> null
        }
        val shoppingScopeKey = if (periodType == PlanningPeriodType.WEEK) {
            range.startDate.toString()
        } else {
            periodId.value
        }
        val updatedAt = System.currentTimeMillis()

        val persisted = database.withTransaction {
            if (dao.getPlanningPeriod(periodId.value) == null) {
                return@withTransaction false
            }

            // Remove older compatible-unit rows first. New adjustments are
            // always stored against the calculator's canonical demand unit.
            dao.getPlanIngredientAdjustments(periodId.value)
                .filter { existing ->
                    existing.ingredientId == canonicalId &&
                        convertIngredientQuantity(1.0, existing.unit, demandUnit) != null
                }
                .forEach { existing ->
                    dao.deletePlanIngredientAdjustment(
                        periodId.value,
                        canonicalId,
                        existing.unit
                    )
                }

            // An at-home change invalidates an old hand-entered purchase amount
            // for this ingredient, but keeps checked state, notes and actual spend.
            dao.getShoppingItems(shoppingScopeKey)
                .filter { existing ->
                    existing.ingredientId == canonicalId &&
                        convertIngredientQuantity(1.0, existing.unit, demandUnit) != null
                }
                .forEach { existing ->
                    dao.clearGeneratedPurchaseQuantityOverride(
                        shoppingScopeKey,
                        canonicalId,
                        existing.unit
                    )
                }

            when (adjustmentType) {
                PlanIngredientAdjustment.Type.SOME -> dao.upsertPlanIngredientAdjustment(
                    PlanIngredientAdjustmentEntity(
                        periodId = periodId.value,
                        ingredientId = canonicalId,
                        unit = demandUnit,
                        adjustmentType = "SOME",
                        availableQuantity = availableInDemandUnit,
                        updatedAtEpochMillis = updatedAt
                    )
                )
                PlanIngredientAdjustment.Type.ENOUGH -> dao.upsertPlanIngredientAdjustment(
                    PlanIngredientAdjustmentEntity(
                        periodId = periodId.value,
                        ingredientId = canonicalId,
                        unit = demandUnit,
                        adjustmentType = "ENOUGH",
                        availableQuantity = 0.0,
                        updatedAtEpochMillis = updatedAt
                    )
                )
                null -> Unit
            }
            true
        }
        if (!persisted) return null

        return snapshot(
            periodId = periodId,
            selectedWilaya = currentSnapshot.planningPeriod?.wilaya
                ?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Saves the plan-only availability choice and the corresponding shopping
     * row edit as one transaction. The UI must use this for ingredient rows so
     * clearing an adjustment cannot race a separate purchase-quantity update.
     */
    suspend fun updatePlanShoppingItemDetails(
        weekStartIso: String,
        shoppingItemId: String,
        ingredientId: String,
        unit: String,
        adjustmentType: String?,
        availableQuantity: Double,
        purchaseQuantity: Double?,
        storeNote: String,
        unavailable: Boolean,
        actualSpentDzd: Double?
    ) {
        ensureInitialized()
        if (purchaseQuantity != null && (!purchaseQuantity.isFinite() || purchaseQuantity <= 0.0)) return
        if (actualSpentDzd != null && (!actualSpentDzd.isFinite() || actualSpentDzd < 0.0)) return

        val canonicalId = foodIdentityResolver.canonicalId(ingredientId)
        val normalizedUnit = unit.trim()
        if (normalizedUnit.isBlank()) return
        if (dao.getIngredients().none { it.id == canonicalId }) return

        val periodId = resolvePlanningDateRange(
            PlanningPeriodType.WEEK,
            weekStartIso
        ).periodId
        val type = when (adjustmentType?.trim()?.uppercase()) {
            null, "", "NONE" -> null
            "SOME" -> "SOME"
            "ENOUGH" -> "ENOUGH"
            else -> return
        }
        if (type == "SOME" && (!availableQuantity.isFinite() || availableQuantity <= 0.0)) return

        database.withTransaction {
            val current = dao.getShoppingItem(shoppingItemId) ?: return@withTransaction
            if (current.ingredientId != canonicalId || current.unit != normalizedUnit) {
                return@withTransaction
            }

            persistPlanIngredientAdjustment(
                weekStartIso = weekStartIso,
                periodId = periodId,
                ingredientId = canonicalId,
                unit = normalizedUnit,
                type = type,
                availableQuantity = availableQuantity,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
            dao.upsertShoppingItems(
                listOf(
                    current.copy(
                        purchaseQuantityOverride = purchaseQuantity,
                        purchaseQuantity = purchaseQuantity ?: current.purchaseQuantity,
                        cashOutlayDzd = if (purchaseQuantity != null && current.purchaseQuantity > 0.0) {
                            current.cashOutlayDzd?.times(purchaseQuantity / current.purchaseQuantity)
                        } else current.cashOutlayDzd,
                        priceLabel = if (purchaseQuantity != null) {
                            "تقدير معدل يدوياً"
                        } else {
                            current.priceLabel
                        },
                        storeNote = storeNote.trim().take(100),
                        isUnavailable = unavailable,
                        actualSpentDzd = actualSpentDzd,
                        updatedAtEpochMillis = System.currentTimeMillis()
                    )
                )
            )
        }
    }

    /**
     * Runs the next-generation product estimator over one batched, demand-scoped
     * catalogue read. The result is diagnostic only during shadow rollout.
     * Product IDs are queried once in bounded chunks rather than scanning the
     * entire retailer catalogue separately for each ingredient.
     */
    private suspend fun logProductPriceShadowEstimate(
        periodId: PlanningPeriodId,
        demands: List<dz.meals.app.data.IngredientDemand>,
        wilaya: String?,
        nowEpochMillis: Long,
        establishedTypicalDzd: Double
    ) {
        if (demands.isEmpty()) return
        try {
            val demandedIngredientIds = demands.mapTo(hashSetOf()) { it.ingredientId }
            val approvedMatches = dao.getVerifiedMarketProductIngredientMatches()
                .filter { it.ingredientId in demandedIngredientIds }
            val productIds = approvedMatches.map { it.productId }.distinct().sorted()
            val products = productIds.chunked(800).flatMap { dao.getMarketProductsByIds(it) }
            val listings = productIds.chunked(800).flatMap { dao.getMarketProductSourcesForProducts(it) }
            val observations = productIds.chunked(800)
                .flatMap { dao.getMarketPriceObservationsForProducts(it) }

            val result = ProductPriceEstimator.estimate(
                demands = demands,
                approvedMatches = approvedMatches,
                products = products,
                sourceListings = listings,
                observations = observations,
                locality = ProductPriceLocality(wilaya = wilaya),
                nowEpochMillis = nowEpochMillis
            )
            val pricedCount = result.ingredientEstimates.count { it.typicalDzd != null }
            val averageConfidence = result.ingredientEstimates
                .map { it.confidence.score }
                .takeIf { it.isNotEmpty() }
                ?.average()
            val missingReasons = result.ingredientEstimates
                .filter { it.typicalDzd == null }
                .flatMap { estimate -> estimate.missingPriceReasons.map { it.name } }
                .distinct()
                .sorted()
                .joinToString(",")
            Log.d(
                "MidatiPriceShadow",
                "period=${periodId.value} ingredients=${demands.size} priced=$pricedCount " +
                    "missing=${result.missingIngredientCount} establishedDzd=$establishedTypicalDzd " +
                    "shadowLowDzd=${result.lowDzd} shadowTypicalDzd=${result.typicalDzd} " +
                    "shadowHighDzd=${result.highDzd} avgConfidence=$averageConfidence " +
                    "missingReasons=$missingReasons"
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // A diagnostic calculation must never prevent opening or editing a plan.
            Log.w("MidatiPriceShadow", "Shadow estimate unavailable for ${periodId.value}", error)
        }
    }

    private data class PlanIngredientAdjustmentRebase(
        val storedRows: List<PlanIngredientAdjustmentEntity>,
        val activeAdjustments: Map<Pair<String, String>, PlanIngredientAdjustment>
    )

    /**
     * Candidate scoring may use only adjustments active in the previously
     * saved recipe set. Rebase preserves those when the same canonical
     * ingredient/form remains demanded; removed rows stay stored but inactive,
     * and a later newly introduced ingredient does not inherit an old amount.
     */
    private fun activePlanIngredientAdjustments(
        rows: List<PlanIngredientAdjustmentEntity>,
        demands: List<IngredientDemand>
    ): Map<Pair<String, String>, PlanIngredientAdjustment> =
        rows.mapNotNull { row ->
            val matchingDemand = demands.singleOrNull { demand ->
                demand.ingredientId == row.ingredientId &&
                    convertIngredientQuantity(1.0, row.unit, demand.unit) != null
            } ?: return@mapNotNull null
            row.toDomainAdjustment()?.let { (key, adjustment) ->
                (row.ingredientId to row.unit) to adjustment
            }
        }.toMap()

    private suspend fun rebasePlanIngredientAdjustments(
        periodId: String,
        previousPlannedMeals: List<PlannedMeal>,
        plannedMeals: List<PlannedMeal>,
        recipes: List<MealRecipe>
    ): PlanIngredientAdjustmentRebase {
        val previousDemands = IngredientDemandCalculator.calculate(previousPlannedMeals, recipes)
        val nextDemands = IngredientDemandCalculator.calculate(plannedMeals, recipes)
        val existingRows = dao.getPlanIngredientAdjustments(periodId)
        val activeCandidates = existingRows.mapNotNull { row ->
            val validAdjustment = row.toDomainAdjustment() ?: return@mapNotNull null
            // If it was not part of the old plan, it is an inactive historical
            // row, even when the newly generated recipes happen to use it.
            val oldDemand = previousDemands.singleOrNull { demand ->
                demand.ingredientId == row.ingredientId &&
                    convertIngredientQuantity(1.0, row.unit, demand.unit) != null
            } ?: return@mapNotNull null
            val nextDemand = nextDemands.singleOrNull { demand ->
                demand.ingredientId == row.ingredientId &&
                    convertIngredientQuantity(1.0, row.unit, demand.unit) != null &&
                    convertIngredientQuantity(1.0, oldDemand.unit, demand.unit) != null
            } ?: return@mapNotNull null
            val quantity = if (validAdjustment.second.type == PlanIngredientAdjustment.Type.SOME) {
                convertIngredientQuantity(
                    validAdjustment.second.availableQuantity,
                    row.unit,
                    nextDemand.unit
                ) ?: return@mapNotNull null
            } else 0.0
            row to row.copy(
                unit = nextDemand.unit,
                availableQuantity = quantity,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }

        val activeOriginalRows = activeCandidates.mapTo(hashSetOf()) { it.first }
        val inactiveRows = existingRows.filterNot { it in activeOriginalRows }
        val activeRows = activeCandidates.groupBy { (_, rebased) ->
            rebased.ingredientId to rebased.unit
        }.map { (key, candidates) ->
            val rebasedRows = candidates.map { it.second }
            val isEnough = rebasedRows.any {
                it.adjustmentType.equals("ENOUGH", ignoreCase = true)
            }
            PlanIngredientAdjustmentEntity(
                periodId = periodId,
                ingredientId = key.first,
                unit = key.second,
                adjustmentType = if (isEnough) "ENOUGH" else "SOME",
                availableQuantity = if (isEnough) 0.0 else rebasedRows.sumOf { it.availableQuantity },
                updatedAtEpochMillis = rebasedRows.maxOf { it.updatedAtEpochMillis }
            )
        }
        val activeAdjustments = activeRows.mapNotNull { row ->
            row.toDomainAdjustment()?.let { it.first to it.second }
        }.toMap()
        val storedRows = (inactiveRows + activeRows)
            .distinctBy { Triple(it.periodId, it.ingredientId, it.unit) }
        return PlanIngredientAdjustmentRebase(
            storedRows = storedRows,
            activeAdjustments = activeAdjustments
        )
    }

    private suspend fun persistPlanIngredientAdjustment(
        weekStartIso: String,
        periodId: String,
        ingredientId: String,
        unit: String,
        type: String?,
        availableQuantity: Double,
        updatedAtEpochMillis: Long
    ) {
        // Any change to the available amount (including clearing it) invalidates
        // the previous manual quantity before the caller applies a new override.
        dao.clearGeneratedPurchaseQuantityOverride(weekStartIso, ingredientId, unit)
        when (type) {
            "SOME" -> dao.upsertPlanIngredientAdjustment(
                PlanIngredientAdjustmentEntity(
                    periodId = periodId,
                    ingredientId = ingredientId,
                    unit = unit,
                    adjustmentType = "SOME",
                    availableQuantity = availableQuantity,
                    updatedAtEpochMillis = updatedAtEpochMillis
                )
            )
            "ENOUGH" -> dao.upsertPlanIngredientAdjustment(
                PlanIngredientAdjustmentEntity(
                    periodId = periodId,
                    ingredientId = ingredientId,
                    unit = unit,
                    adjustmentType = "ENOUGH",
                    availableQuantity = 0.0,
                    updatedAtEpochMillis = updatedAtEpochMillis
                )
            )
            null -> dao.deletePlanIngredientAdjustment(periodId, ingredientId, unit)
        }
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

private const val RECIPE_IMAGE_LOG_TAG = "MidatiRecipeImages"
private const val IMAGE_DISCOVERY_IN_PROGRESS_COOLDOWN_MS = 30L * 60L * 1000L
private const val IMAGE_DISCOVERY_NETWORK_ERROR_COOLDOWN_MS = 30L * 60L * 1000L
private const val IMAGE_DISCOVERY_NO_MATCH_COOLDOWN_MS = 24L * 60L * 60L * 1000L

private fun recipeImageDiscoveryKey(recipeId: String): String =
    "retry_after:" + recipeId.hashCode().toUInt().toString(16)

private class NoMatchingRecipeImageException :
    IOException("لم نعثر على صورة مرخّصة يمكن مطابقتها بثقة.")

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

/**
 * LicenseUrl values from CommonsMetadata may be HTML anchors. Return the actual
 * href (or a plain URL), not the visible link text.
 */
internal fun extractCommonsLicenseUrl(htmlValue: String): String {
    val href = Regex("""(?i)href\s*=\s*["']([^"']+)["']""")
        .find(htmlValue)?.groupValues?.get(1)
    val plainUrl = Regex("""https://creativecommons\.org/[^\s"'<>]+""")
        .find(htmlValue)?.value
    return (href ?: plainUrl)
        ?.replace("&amp;", "&", ignoreCase = true)
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?.takeIf { it.startsWith("https://creativecommons.org/", ignoreCase = true) }
        .orEmpty()
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
    val budgetDzd: Int,
    val meatAllowanceBreakdown: List<PeriodMeatAllowance> = emptyList()
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

private fun PlanningPeriodMealEntity.toPlannedMeal(periodStartDate: LocalDate): PlannedMeal {
    val calendarDate = LocalDate.parse(dateIso)
    val dayIndex = java.time.temporal.ChronoUnit.DAYS
        .between(periodStartDate, calendarDate)
        .toInt()
    require(dayIndex in 0..30) {
        "Planning-period meal date must belong to its selected calendar month."
    }
    return PlannedMeal(
        id = id,
        weekStartIso = periodStartDate.toString(),
        dayIndex = dayIndex,
        mealSlot = mealSlot,
        recipeId = recipeId,
        servings = servings,
        locked = locked,
        isCooked = isCooked,
        isUserEdited = isUserEdited
    )
}


private fun PlanningPeriodEntity.toPlanRunSummary() = PlanRunSummary(
    engineVersion = engineVersion,
    generatedAtEpochMillis = updatedAtEpochMillis,
    priceSnapshotAtEpochMillis = priceSnapshotAtEpochMillis,
    budgetDzd = budgetDzd,
    wilaya = wilaya,
    mealSlots = mealSlotsCsv.split(",").filter { it.isNotBlank() }.toSet()
)

private fun PlanningPeriodEntity.toPlanningPeriodSummary() = PlanningPeriodSummary(
    id = id,
    periodType = periodType,
    startDateIso = startDateIso,
    endDateIso = endDateIso,
    budgetDzd = budgetDzd,
    wilaya = wilaya,
    mealSlots = mealSlotsCsv.split(",").filter { it.isNotBlank() }.toSet(),
    engineVersion = engineVersion,
    createdAtEpochMillis = createdAtEpochMillis,
    priceSnapshotAtEpochMillis = priceSnapshotAtEpochMillis,
    marketPriceSnapshotCaptured = marketPriceSnapshotCaptured,
    householdServings = householdServings,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun MarketProductPriceEvidence.toSummary() = MarketPriceEvidenceSummary(
    productId = productId,
    sourceListingId = sourceListingId,
    ingredientId = ingredientId,
    productName = productName,
    packageQuantity = packageQuantity,
    packageUnit = packageUnit,
    mappingStatus = mappingStatus,
    mappingReviewedAtEpochMillis = mappingReviewedAtEpochMillis,
    foodScopeReviewNeeded = foodScopeReviewNeeded,
    priceDzd = priceDzd,
    priceMinDzd = priceMinDzd,
    priceMaxDzd = priceMaxDzd,
    currencyCode = currencyCode,
    observedAtEpochMillis = observedAtEpochMillis,
    priceStatus = priceStatus,
    availabilityRaw = availabilityRaw,
    storeName = storeName,
    source = source,
    wilaya = wilaya,
    sourceNotice = sourceNotice
)

private fun PlannedMeal.toPlanningPeriodEntity(
    periodId: String,
    periodStartDate: LocalDate
) = PlanningPeriodMealEntity(
    id = id,
    periodId = periodId,
    dateIso = periodStartDate.plusDays(dayIndex.toLong()).toString(),
    mealSlot = mealSlot,
    recipeId = recipeId,
    servings = servings.coerceIn(1, 12),
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

private fun PlanIngredientAdjustmentEntity.toDomainAdjustment():
    Pair<Pair<String, String>, PlanIngredientAdjustment>? {
    val adjustment = when (adjustmentType.uppercase()) {
        "SOME" -> PlanIngredientAdjustment(
            type = PlanIngredientAdjustment.Type.SOME,
            availableQuantity = availableQuantity
        )
        "ENOUGH" -> PlanIngredientAdjustment(
            type = PlanIngredientAdjustment.Type.ENOUGH
        )
        else -> return null
    }
    if (!dz.meals.app.domain.validatePlanIngredientAdjustment(adjustment)) return null
    return (ingredientId to unit) to adjustment
}

private fun ShoppingListItem.withPlanIngredientAdjustment(
    adjustments: Map<Pair<String, String>, PlanIngredientAdjustment>
): ShoppingListItem {
    val ingredient = ingredientId ?: return this
    val adjustment = adjustments[ingredient to unit] ?: return this
    return copy(
        alreadyHaveQuantity = adjustment.availableQuantity.takeIf {
            adjustment.type == PlanIngredientAdjustment.Type.SOME
        } ?: 0.0,
        alreadyHaveEnough = adjustment.type == PlanIngredientAdjustment.Type.ENOUGH
    )
}

private fun MarketProductPriceEvidence.toPlanMarketSnapshot(periodId: String) =
    PlanMarketPriceSnapshotEntity(
        periodId = periodId,
        sourceListingId = sourceListingId,
        productId = productId,
        ingredientId = ingredientId,
        productName = productName,
        packageQuantity = packageQuantity,
        packageUnit = packageUnit,
        mappingStatus = mappingStatus,
        mappingReviewedAtEpochMillis = mappingReviewedAtEpochMillis,
        priceDzd = priceDzd,
        priceMinDzd = priceMinDzd,
        priceMaxDzd = priceMaxDzd,
        currencyCode = currencyCode,
        observedAtEpochMillis = observedAtEpochMillis,
        priceStatus = priceStatus,
        availabilityRaw = availabilityRaw,
        storeName = storeName,
        source = source,
        wilaya = wilaya,
        sourceNotice = sourceNotice
    )

private fun PlanMarketPriceSnapshotEntity.toPriceEvidence() = MarketProductPriceEvidence(
    productId = productId,
    sourceListingId = sourceListingId,
    ingredientId = ingredientId,
    productName = productName,
    packageQuantity = packageQuantity,
    packageUnit = packageUnit,
    mappingStatus = mappingStatus,
    mappingReviewedAtEpochMillis = mappingReviewedAtEpochMillis,
    foodScopeReviewNeeded = false,
    priceDzd = priceDzd,
    priceMinDzd = priceMinDzd,
    priceMaxDzd = priceMaxDzd,
    currencyCode = currencyCode,
    observedAtEpochMillis = observedAtEpochMillis,
    priceStatus = priceStatus,
    availabilityRaw = availabilityRaw,
    storeName = storeName,
    source = source,
    wilaya = wilaya,
    sourceNotice = sourceNotice
)

private fun VerifiedMarketProductPriceRow.toPriceEvidence() = MarketProductPriceEvidence(
    productId = productId,
    sourceListingId = sourceListingId,
    ingredientId = ingredientId,
    productName = productName,
    packageQuantity = packageQuantity,
    packageUnit = packageUnit,
    mappingStatus = mappingStatus,
    mappingReviewedAtEpochMillis = mappingReviewedAtEpochMillis,
    foodScopeReviewNeeded = foodScopeReviewNeeded,
    priceDzd = priceDzd,
    priceMinDzd = priceMinDzd,
    priceMaxDzd = priceMaxDzd,
    currencyCode = currencyCode,
    observedAtEpochMillis = observedAtEpochMillis,
    priceStatus = priceStatus,
    availabilityRaw = availabilityRaw,
    storeName = storeName,
    source = source,
    wilaya = wilaya,
    sourceNotice = sourceNotice
)

private fun ShoppingListItem.withMarketProductPrice(
    evidence: List<MarketProductPriceEvidence>,
    wilaya: String?,
    snapshotAtEpochMillis: Long
): ShoppingListItem {
    val canonicalIngredientId = ingredientId ?: return this
    val purchaseEstimate = estimateMarketProductPrice(
        ingredientId = canonicalIngredientId,
        requiredQuantity = purchaseQuantity,
        requiredUnit = unit,
        selectedWilaya = wilaya,
        evidence = evidence,
        nowEpochMillis = snapshotAtEpochMillis
    ) ?: return this
    // A manual purchase-quantity adjustment changes the cash outlay, not how
    // much the recipes consume. Keep those two quantities distinct.
    val consumptionEstimate = estimateMarketProductPrice(
        ingredientId = canonicalIngredientId,
        requiredQuantity = requiredQuantity,
        requiredUnit = unit,
        selectedWilaya = wilaya,
        evidence = evidence,
        nowEpochMillis = snapshotAtEpochMillis
    )

    val source = listOf(
        purchaseEstimate.wilaya,
        purchaseEstimate.storeNames.joinToString(" · ")
    ).filter { it.isNotBlank() }.joinToString(" · ")
    return copy(
        consumedValueDzd = consumptionEstimate?.maxConsumedValueDzd,
        cashOutlayDzd = purchaseEstimate.maxCashOutlayDzd,
        priceLabel = "سعر استرشادي غير مؤكد",
        sourceLabel = source,
        marketCashOutlayMinDzd = purchaseEstimate.minCashOutlayDzd,
        marketCashOutlayMaxDzd = purchaseEstimate.maxCashOutlayDzd,
        marketConsumedValueMinDzd = consumptionEstimate?.minConsumedValueDzd,
        marketConsumedValueMaxDzd = consumptionEstimate?.maxConsumedValueDzd,
        marketPriceOfferCount = purchaseEstimate.offerCount,
        marketPriceMinPackageCount = purchaseEstimate.minPackageCount,
        marketPriceMaxPackageCount = purchaseEstimate.maxPackageCount,
        marketPriceWilaya = purchaseEstimate.wilaya,
        marketPriceStoreNames = purchaseEstimate.storeNames,
        marketPriceSourceNotices = purchaseEstimate.sourceNotices,
        marketPriceObservedAtEpochMillis = purchaseEstimate.latestObservedAtEpochMillis,
        marketPriceIncludesLimitedAvailability = purchaseEstimate.includesLimitedAvailability
    )
}

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
