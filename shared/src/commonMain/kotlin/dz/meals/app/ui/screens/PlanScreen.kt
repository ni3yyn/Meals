package dz.meals.app.ui.screens

import dz.meals.app.ui.components.MealsText as Text

import androidx.compose.ui.text.style.TextAlign

import dz.meals.app.ui.components.MealsIconAction
import dz.meals.app.ui.components.MealsIconButton
import dz.meals.app.ui.components.MealsTextButton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import dz.meals.app.ui.components.MealsBottomSheet
import dz.meals.app.ui.components.mealsSheetDismissAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dz.meals.app.ui.components.MealsCard
import dz.meals.app.ui.components.MealsPressable
import dz.meals.app.ui.components.MealsSelectionGlass
import dz.meals.app.ui.components.MealsRecipeImage
import dz.meals.app.ui.components.MealsGlassSurface
import dz.meals.app.ui.components.MealsIllustrationBadge
import dz.meals.app.ui.components.MealsCheckbox
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.ui.components.MealsPrimaryButton
import dz.meals.app.ui.components.MealsSecondaryButton
import dz.meals.app.data.AppPreferences
import dz.meals.app.data.MealRecipe
import dz.meals.app.data.hasDisplayableRecipeImage
import dz.meals.app.data.MealsSnapshot
import dz.meals.app.data.PlannedMeal
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.TextAccent
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.shared.format.formatArabicDate
import dz.meals.app.shared.format.formatArabicWeekRange
import dz.meals.app.shared.format.formatDzd
import dz.meals.app.shared.format.formatHouseholdQuantity
import dz.meals.app.shared.format.formatArabicMonth
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PlanScreen(
    weekStartIso: String,
    planningMode: String,
    monthlyPlanningEnabled: Boolean,
    monthStartIso: String,
    preferences: AppPreferences,
    snapshot: MealsSnapshot,
    isGenerating: Boolean,
    onGenerate: (Boolean) -> Unit,
    onPlanningModeChange: (String) -> Unit,
    onMonthChange: (Int) -> Unit,
    onRefreshPrices: () -> Unit,
    onAdjustIngredient: (String, String, Boolean, Double) -> Unit,
    onSetMeal: (Int, String, String?) -> Unit,
    onToggleLock: (String) -> Unit,
    onToggleCooked: (String) -> Unit,
    onAdjustServings: (String, Int) -> Unit,
    onOpenRecipe: (String) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val weekStart = LocalDate.parse(weekStartIso)
    val monthStart = LocalDate.parse(monthStartIso)
    val monthDayCount = (
        monthStart.plus(DatePeriod(months = 1)).toEpochDays() - monthStart.toEpochDays()
    ).toInt()
    val isMonthly = monthlyPlanningEnabled && planningMode == "MONTH"
    val weekDays = remember { (0 until 7).toList() }
    val today = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val todayIndexInWeek = today.toEpochDays() - weekStart.toEpochDays()
    val todayIndexInMonth = today.toEpochDays() - monthStart.toEpochDays()
    val initialDayIndex = if (isMonthly) {
        todayIndexInMonth.takeIf { it in 0L until monthDayCount.toLong() }?.toInt() ?: 0
    } else {
        todayIndexInWeek.takeIf { it in 0L..6L }?.toInt() ?: 0
    }
    var fillEmptyOnly by rememberSaveable { mutableStateOf(false) }
    var selectedDayIndex by rememberSaveable(planningMode, weekStartIso, monthStartIso) {
        mutableStateOf(initialDayIndex)
    }
    var editingMealSlot by rememberSaveable { mutableStateOf<String?>(null) }
    var editingIngredientRowId by rememberSaveable { mutableStateOf<String?>(null) }
    var availableAmountText by rememberSaveable { mutableStateOf("") }
    val dayChipListState = rememberLazyListState()
    LaunchedEffect(weekStartIso, initialDayIndex, isMonthly) {
        if (!isMonthly) dayChipListState.animateScrollToItem(initialDayIndex)
    }
    val recipesById = remember(snapshot.recipes) { snapshot.recipes.associateBy { it.id } }
    val eligibleRecipes = remember(snapshot.recipes, preferences.allergyIds) {
        snapshot.recipes.filter {
            it.allergens.intersect(preferences.allergyIds).isEmpty()
        }
    }
    val mealsByDayAndSlot = remember(snapshot.plannedMeals) {
        snapshot.plannedMeals.associateBy { it.dayIndex to it.mealSlot }
    }
    val visibleMealSlots = snapshot.planRun?.mealSlots ?: preferences.mealSlots
    val orderedMealSlots = listOf("breakfast", "lunch", "dinner").filter { it in visibleMealSlots }
    val hasMarketPriceEstimates = snapshot.shoppingItems.any {
        it.marketCashOutlayMinDzd != null
    }

    val unsafeLockedMeal = snapshot.plannedMeals.firstOrNull { meal ->
        meal.locked && meal.recipeId?.let { recipeId ->
            recipesById[recipeId]?.allergens?.intersect(preferences.allergyIds)?.isNotEmpty()
        } == true
    }

    if (isMonthly) {
        MonthlyPlanContent(
            monthStart = monthStart,
            monthDayCount = monthDayCount,
            preferences = preferences,
            snapshot = snapshot,
            isGenerating = isGenerating,
            fillEmptyOnly = fillEmptyOnly,
            onFillEmptyOnlyChange = { fillEmptyOnly = it },
            onGenerate = { onGenerate(fillEmptyOnly) },
            onPreviousMonth = { onMonthChange(-1) },
            onNextMonth = { onMonthChange(1) },
            onChangeMode = onPlanningModeChange,
            onRefreshPrices = onRefreshPrices,
            onSetMeal = onSetMeal,
            onToggleLock = onToggleLock,
            onToggleCooked = onToggleCooked,
            onAdjustServings = onAdjustServings,
            onOpenRecipe = onOpenRecipe,
            onChooseMeal = { day, slot ->
                selectedDayIndex = day
                editingMealSlot = slot
            },
            onAdjustIngredient = onAdjustIngredient,
            onEditIngredient = { item ->
                editingIngredientRowId = item.id
                availableAmountText = item.alreadyHaveQuantity
                    .takeIf { it > 0.0 }?.let { it.toString() } ?: ""
            }
        )
    } else {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 122.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MealsGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 15.dp,
                shape = RoundedCornerShape(24.dp)
            ) {
                if (monthlyPlanningEnabled) {
                    PeriodModeSelector(
                        selectedMode = planningMode,
                        onModeChange = onPlanningModeChange
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MealsIllustrationBadge(
                        icon = MealsIconName.Wallet,
                        background = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.84f),
                        size = 42.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("ميزانية الأسبوع", color = dz.meals.app.ui.theme.TextAccent, style = MaterialTheme.typography.labelMedium)
                        Text(
                            (
                                snapshot.planRun?.budgetDzd
                                    ?: snapshot.planningPeriod?.budgetDzd?.takeIf { it > 0 }
                                    ?: preferences.weeklyBudgetDzd
                            ).formatDzd(),
                            color = dz.meals.app.ui.theme.Ink,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Box(
                        modifier = Modifier.size(width = 1.dp, height = 38.dp).background(Border.copy(alpha = 0.75f))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("الشراء المتوقع", color = dz.meals.app.ui.theme.TextAccent, style = MaterialTheme.typography.labelMedium)
                        Text(
                            snapshot.purchaseEstimateLabel(),
                            color = dz.meals.app.ui.theme.Ink,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    MealsIconAction(onClick = onRefreshPrices, modifier = Modifier.size(38.dp)) {
                        MealsIcon(
                            MealsIconName.Refresh,
                            contentDescription = "تحديث الأسعار",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (snapshot.basket.unknownPriceCount > 0 || snapshot.basket.hasDemoPrices || hasMarketPriceEstimates) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(dz.meals.app.ui.theme.WarmSurface.copy(alpha = 0.68f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MealsIcon(
                            name = MealsIconName.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = when {
                                hasMarketPriceEstimates && snapshot.basket.unknownPriceCount > 0 ->
                                    "بعض الأسعار استرشادية وغير مؤكدة، وهناك " + snapshot.basket.unknownPriceCount + " عناصر دون سعر."
                                hasMarketPriceEstimates ->
                                    "الأسعار استرشادية من عروض محلية وقد تختلف حسب المتجر والتوفر."
                                snapshot.basket.hasDemoPrices && snapshot.basket.unknownPriceCount > 0 ->
                                    "الأسعار تقديرية، وهناك " + snapshot.basket.unknownPriceCount + " عناصر دون سعر."
                                snapshot.basket.hasDemoPrices ->
                                    "الأسعار تقديرية وليست أسعار سوق مباشرة."
                                else ->
                                    snapshot.basket.unknownPriceCount.toString() + " عناصر دون سعر."
                            },
                            color = dz.meals.app.ui.theme.BodyInk,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Border.copy(alpha = 0.48f))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MealsIconAction(onClick = onPreviousWeek, modifier = Modifier.size(40.dp)) {
                            MealsIcon(
                                MealsIconName.ArrowBack,
                                contentDescription = "الأسبوع السابق",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Text(
                            text = formatArabicWeekRange(weekStart, weekStart.plus(DatePeriod(days = 6))),
                            modifier = Modifier.weight(1f),
                            color = MutedInk,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        MealsIconAction(onClick = onNextWeek, modifier = Modifier.size(40.dp)) {
                            MealsIcon(
                                MealsIconName.ArrowForward,
                                contentDescription = "الأسبوع التالي",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
        item {
            LazyRow(
                    state = dayChipListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(
                        weekDays,
                        key = { day -> "week-day-$day" },
                        contentType = { "week-day-chip" }
                    ) { day ->
                        val date = weekStart.plus(DatePeriod(days = day))
                        val weekday = formatArabicDate(date).substringBefore(' ')
                        val selected = selectedDayIndex == day
                        val chipScale = androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (selected) 1.035f else 1f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.78f,
                                stiffness = 560f
                            ),
                            label = "planDayChipScale"
                        )
                        MealsSelectionGlass(
                            modifier = Modifier
                                .width(72.dp)
                                .graphicsLayer {
                                    scaleX = chipScale.value
                                    scaleY = chipScale.value
                                },
                            selected = selected,
                            emphasizeSelectedFace = true
                        ) {
                            MealsPressable(
                                onClick = {
                                    selectedDayIndex = day
                                    editingMealSlot = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                role = androidx.compose.ui.semantics.Role.RadioButton
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            weekday,
                                            color = androidx.compose.ui.graphics.Color(0xFF171717),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            date.day.toString(),
                                            color = androidx.compose.ui.graphics.Color(0xFF171717),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        if (orderedMealSlots.isNotEmpty()) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                orderedMealSlots.forEach { slot ->
                                                    val hasRecipe =
                                                        mealsByDayAndSlot[day to slot]?.recipeId != null
                                                    Box(
                                                        Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (hasRecipe) {
                                                                    androidx.compose.ui.graphics.Color(0xFF171717)
                                                                } else {
                                                                    Border.copy(alpha = 0.58f)
                                                                }
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MealsPressable(
                    onClick = { fillEmptyOnly = !fillEmptyOnly },
                    modifier = Modifier.weight(0.92f).height(48.dp),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(17.dp))
                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.28f))
                            .padding(start = 5.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        MealsCheckbox(
                            checked = fillEmptyOnly,
                            onCheckedChange = { fillEmptyOnly = it }
                        )
                        Text(
                            "املأ الفارغة فقط",
                            modifier = Modifier.weight(1f),
                            color = dz.meals.app.ui.theme.Ink,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                    }
                }
                MealsPrimaryButton(
                    label = if (isGenerating) "جارٍ الإنشاء" else "أنشئ الخطة",
                    onClick = { onGenerate(fillEmptyOnly) },
                    enabled = !isGenerating && snapshot.recipes.isNotEmpty(),
                    modifier = Modifier.weight(1.08f),
                    icon = MealsIconName.Refresh,
                    showArrow = false
                )
            }
        }
        if (unsafeLockedMeal != null) {
            item {
                MealsCard(
                    containerColor = dz.meals.app.ui.theme.SoftOrange,
                    borderColor = null,
                    contentPadding = 14.dp
                ) {
                    Text(
                        "تنبيه مهم: توجد وجبة مقفلة لا توافق قيود الحساسية الحالية. لن تُعاد كتابة الخطة حتى تفتح قفل هذه الوجبة أو تستبدلها.",
                        modifier = Modifier.padding(14.dp),
                        color = androidx.compose.ui.graphics.Color(0xFF6A3D12),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            val selectedDate = weekStart.plus(DatePeriod(days = selectedDayIndex))
            val plannedCount = orderedMealSlots.count { slot ->
                mealsByDayAndSlot[selectedDayIndex to slot]?.recipeId != null
            }
            val cookedCount = orderedMealSlots.count { slot ->
                mealsByDayAndSlot[selectedDayIndex to slot]?.isCooked == true
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        formatArabicDate(selectedDate),
                        color = dz.meals.app.ui.theme.Ink,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            plannedCount == 0 -> "لم تُخطط وجبات هذا اليوم بعد"
                            cookedCount == plannedCount -> "$cookedCount وجبات مكتملة"
                            cookedCount > 0 -> "$plannedCount وجبات محددة · $cookedCount مكتملة"
                            else -> "$plannedCount من ${orderedMealSlots.size} وجبات محددة"
                        },
                        color = MutedInk,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "$plannedCount / ${orderedMealSlots.size}",
                    color = TextAccent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (orderedMealSlots.isEmpty()) {
            item {
                Text(
                    "لا توجد وجبات مفعّلة لهذا الأسبوع. يمكنك إضافة الفطور من إعدادات الأسرة.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    color = MutedInk,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(
                orderedMealSlots,
                key = { slot -> "selected-day-meal-$slot" },
                contentType = { "selected-day-meal" }
            ) { slot ->
                val meal = mealsByDayAndSlot[selectedDayIndex to slot]
                val recipe = meal?.recipeId?.let(recipesById::get)
                val title = when (slot) {
                    "breakfast" -> "الفطور"
                    "lunch" -> "الغداء"
                    else -> "العشاء"
                }
                SlotRow(
                    title = title,
                    meal = meal,
                    recipe = recipe,
                    onChooseRecipe = { editingMealSlot = slot },
                    onOpenRecipe = { recipe?.id?.let(onOpenRecipe) },
                    onToggleLock = { meal?.let { onToggleLock(it.id) } },
                    onToggleCooked = { meal?.let { onToggleCooked(it.id) } },
                    onAdjustServings = { delta -> meal?.let { onAdjustServings(it.id, delta) } }
                )
            }
        }
        item {
            IngredientNeedsSection(
                items = snapshot.shoppingItems,
                onSetEnough = { item, enough ->
                    item.ingredientId?.let { onAdjustIngredient(it, item.unit, enough, 0.0) }
                },
                onEditAmount = { item ->
                    editingIngredientRowId = item.id
                    availableAmountText = item.alreadyHaveQuantity
                        .takeIf { it > 0.0 }?.let { it.toString() } ?: ""
                }
            )
        }
    }
    }

    editingMealSlot?.let { editingSlot ->
        val day = selectedDayIndex
        val slotTitle = when (editingSlot) {
            "breakfast" -> "الفطور"
            "lunch" -> "الغداء"
            "dinner" -> "العشاء"
            else -> "الوجبة"
        }
        val existingMeal = mealsByDayAndSlot[day to editingSlot]
        val currentRecipe = existingMeal?.recipeId?.let(recipesById::get)

        MealsBottomSheet(
            onDismissRequest = { editingMealSlot = null },
            title = { Text("اختر وصفة · $slotTitle") },
            subtitle = "وصفات مناسبة لتفضيلات الأسرة",
            fixedHeightFraction = 0.88f,
            fillBody = true,
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    item {
                        Text(
                            "اختاري الوصفة التي تناسب هذه الوجبة",
                            color = MutedInk,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 7.dp)
                        )
                    }
                    if (eligibleRecipes.isEmpty()) {
                        item {
                            Text(
                                "لا توجد وصفات تطابق قيود الحساسية الحالية.",
                                color = MutedInk,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                    items(
                        eligibleRecipes,
                        key = { it.id },
                        contentType = { "recipe-choice" }
                    ) { recipe ->
                        MealsTextButton(
                            onClick = {
                                onSetMeal(day, editingSlot, recipe.id)
                                editingMealSlot = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MealsRecipeImage(
                                    imageUrl = recipe.takeIf { it.hasDisplayableRecipeImage() }?.imageUrl.orEmpty(),
                                    contentDescription = recipe.nameAr,
                                    modifier = Modifier.size(58.dp),
                                    imageWidth = 280,
                                    cornerRadius = 12.dp,
                                    placeholderIconSize = 25.dp
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        recipe.nameAr,
                                        color = dz.meals.app.ui.theme.Ink,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        recipe.durationMinutes.toString() + " دقيقة · " +
                                            preferences.householdServings.toString() + " حصص",
                                        color = MutedInk,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (recipe.id == currentRecipe?.id) {
                                    MealsIcon(
                                        name = MealsIconName.Check,
                                        contentDescription = "الوصفة الحالية",
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    MealsIcon(
                                        name = MealsIconName.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = if (existingMeal?.recipeId != null) {
                {
                    MealsSecondaryButton(
                        label = "إفراغ الوجبة",
                        onClick = {
                            onSetMeal(day, editingSlot, null)
                            editingMealSlot = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                null
            },
            dismissButton = {
                val dismissSheet = mealsSheetDismissAction()
                MealsSecondaryButton(
                    label = "العودة إلى الوجبات",
                    onClick = { dismissSheet { editingMealSlot = null } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    val editingIngredient = snapshot.shoppingItems.firstOrNull {
        it.id == editingIngredientRowId && !it.userAdded
    }
    if (editingIngredient != null) {
        MealsBottomSheet(
            onDismissRequest = { editingIngredientRowId = null },
            title = { Text("كم لديك من ${editingIngredient.name}؟") },
            subtitle = "تُطبّق هذه الكمية على هذه الخطة فقط",
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "الكمية المطلوبة: ${editingIngredient.requiredQuantity.formatHouseholdQuantity(editingIngredient.unit)}",
                        color = MutedInk,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = availableAmountText,
                        onValueChange = { value ->
                            availableAmountText = value.filter { it.isDigit() || it == '.' || it == ',' }.take(12)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("الكمية المتوفرة") },
                        suffix = { Text(dz.meals.app.shared.format.formatHouseholdUnit(editingIngredient.unit)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                MealsPrimaryButton(
                    label = "حفظ الكمية",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val amount = availableAmountText.replace(',', '.').toDoubleOrNull()
                        if (amount != null && amount.isFinite() && amount >= 0.0) {
                            editingIngredient.ingredientId?.let {
                                onAdjustIngredient(it, editingIngredient.unit, false, amount)
                            }
                            editingIngredientRowId = null
                        }
                    }
                )
            },
            dismissButton = {
                MealsSecondaryButton(
                    label = "إلغاء",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { editingIngredientRowId = null }
                )
            }
        )
    }
}

@Composable
private fun PeriodModeSelector(
    selectedMode: String,
    onModeChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("WEEK" to "أسبوعي", "MONTH" to "شهري").forEach { (mode, label) ->
            MealsSelectionGlass(
                modifier = Modifier.weight(1f),
                selected = selectedMode == mode,
                emphasizeSelectedFace = true
            ) {
                MealsPressable(
                    onClick = { if (selectedMode != mode) onModeChange(mode) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = dz.meals.app.ui.theme.Ink,
                            fontWeight = if (selectedMode == mode) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyPlanContent(
    monthStart: LocalDate,
    monthDayCount: Int,
    preferences: AppPreferences,
    snapshot: MealsSnapshot,
    isGenerating: Boolean,
    fillEmptyOnly: Boolean,
    onFillEmptyOnlyChange: (Boolean) -> Unit,
    onGenerate: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onChangeMode: (String) -> Unit,
    onRefreshPrices: () -> Unit,
    onSetMeal: (Int, String, String?) -> Unit,
    onToggleLock: (String) -> Unit,
    onToggleCooked: (String) -> Unit,
    onAdjustServings: (String, Int) -> Unit,
    onOpenRecipe: (String) -> Unit,
    onChooseMeal: (Int, String) -> Unit,
    onAdjustIngredient: (String, String, Boolean, Double) -> Unit,
    onEditIngredient: (dz.meals.app.data.ShoppingListItem) -> Unit
) {
    val recipesById = remember(snapshot.recipes) { snapshot.recipes.associateBy { it.id } }
    val mealsByDayAndSlot = remember(snapshot.plannedMeals) {
        snapshot.plannedMeals.associateBy { it.dayIndex to it.mealSlot }
    }
    val visibleMealSlots = snapshot.planningPeriod?.mealSlots
        ?: snapshot.planRun?.mealSlots
        ?: preferences.mealSlots
    val orderedMealSlots = listOf("breakfast", "lunch", "dinner").filter { it in visibleMealSlots }
    val hasMarketPriceEstimates = snapshot.shoppingItems.any { it.marketCashOutlayMinDzd != null }
    val hasPriceContext = snapshot.basket.unknownPriceCount > 0 ||
        snapshot.basket.hasDemoPrices || hasMarketPriceEstimates
    val purchaseRange = snapshot.purchaseEstimateLabel()
    val weekOffset = monthStart.dayOfWeek.ordinal
    val weekGroups = (0..((weekOffset + monthDayCount - 1) / 7)).map { weekIndex ->
        val firstDay = (weekIndex * 7 - weekOffset).coerceAtLeast(0)
        val lastDay = ((weekIndex + 1) * 7 - weekOffset - 1).coerceAtMost(monthDayCount - 1)
        weekIndex to (firstDay..lastDay).toList()
    }
    val monthBudget = snapshot.planningPeriod?.takeIf { it.updatedAtEpochMillis > 0L }?.budgetDzd
        ?: (preferences.weeklyBudgetDzd * monthDayCount / 7.0).roundToInt()
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val todayIndex = (today.toEpochDays() - monthStart.toEpochDays()).toInt()
    val todayWeekIndex = ((weekOffset + todayIndex) / 7).coerceIn(0, weekGroups.lastIndex)
    val monthListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val unsafeLockedMeal = snapshot.plannedMeals.firstOrNull { meal ->
        meal.locked && meal.recipeId?.let { recipeId ->
            recipesById[recipeId]?.allergens?.intersect(preferences.allergyIds)?.isNotEmpty()
        } == true
    }

    LazyColumn(
        state = monthListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 122.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MealsGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 15.dp,
                shape = RoundedCornerShape(24.dp)
            ) {
                PeriodModeSelector(selectedMode = "MONTH", onModeChange = onChangeMode)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MealsIllustrationBadge(
                        icon = MealsIconName.Wallet,
                        background = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.84f),
                        size = 42.dp
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("ميزانية الشهر", color = TextAccent, style = MaterialTheme.typography.labelMedium)
                        Text(
                            monthBudget.formatDzd(),
                            color = dz.meals.app.ui.theme.Ink,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Box(Modifier.size(width = 1.dp, height = 38.dp).background(Border.copy(alpha = 0.75f)))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("الشراء المتوقع", color = TextAccent, style = MaterialTheme.typography.labelMedium)
                        Text(
                            purchaseRange,
                            color = dz.meals.app.ui.theme.Ink,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    MealsIconAction(onClick = onRefreshPrices, modifier = Modifier.size(38.dp)) {
                        MealsIcon(MealsIconName.Refresh, contentDescription = "تحديث الأسعار", modifier = Modifier.size(22.dp))
                    }
                }
                if (hasPriceContext) {
                    Text(
                        when {
                            hasMarketPriceEstimates && snapshot.basket.unknownPriceCount > 0 ->
                                "بعض الأسعار استرشادية، وهناك ${snapshot.basket.unknownPriceCount} عناصر دون سعر."
                            hasMarketPriceEstimates -> "الأسعار استرشادية وقد تختلف حسب المتجر والتوفر."
                            snapshot.basket.hasDemoPrices -> "الأسعار تقديرية وليست أسعار سوق مباشرة."
                            else -> "${snapshot.basket.unknownPriceCount} عناصر دون سعر."
                        },
                        color = dz.meals.app.ui.theme.BodyInk,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(1.dp).background(Border.copy(alpha = 0.48f))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MealsIconAction(onClick = onPreviousMonth, modifier = Modifier.size(40.dp)) {
                        MealsIcon(MealsIconName.ArrowBack, contentDescription = "الشهر السابق", modifier = Modifier.size(24.dp))
                    }
                    Text(
                        formatArabicMonth(monthStart),
                        modifier = Modifier.weight(1f),
                        color = MutedInk,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    if (todayIndex in 0 until monthDayCount) {
                        MealsTextButton(
                            onClick = {
                                coroutineScope.launch {
                                    monthListState.animateScrollToItem(todayWeekIndex + 2)
                                }
                            }
                        ) {
                            Text("اليوم", color = TextAccent, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    MealsIconAction(onClick = onNextMonth, modifier = Modifier.size(40.dp)) {
                        MealsIcon(MealsIconName.ArrowForward, contentDescription = "الشهر التالي", modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        if (unsafeLockedMeal != null) {
            item {
                MealsCard(
                    containerColor = dz.meals.app.ui.theme.SoftOrange,
                    borderColor = null,
                    contentPadding = 14.dp
                ) {
                    Text(
                        "تنبيه مهم: توجد وجبة مقفلة لا توافق قيود الحساسية الحالية. افتحي قفلها أو استبدليها قبل تعديل القيود. لم تتغير الوجبات.",
                        modifier = Modifier.padding(14.dp),
                        color = androidx.compose.ui.graphics.Color(0xFF6A3D12),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MealsPressable(
                    onClick = { onFillEmptyOnlyChange(!fillEmptyOnly) },
                    modifier = Modifier.weight(0.92f).height(48.dp),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(17.dp))
                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.28f))
                            .padding(start = 5.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        MealsCheckbox(checked = fillEmptyOnly, onCheckedChange = onFillEmptyOnlyChange)
                        Text("املأ الفارغة فقط", modifier = Modifier.weight(1f), color = dz.meals.app.ui.theme.Ink,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    }
                }
                MealsPrimaryButton(
                    label = if (isGenerating) "جارٍ الإنشاء" else "أنشئ الخطة",
                    onClick = onGenerate,
                    enabled = !isGenerating && snapshot.recipes.isNotEmpty(),
                    modifier = Modifier.weight(1.08f),
                    icon = MealsIconName.Refresh,
                    showArrow = false
                )
            }
        }
        items(
            weekGroups,
            key = { it.first },
            contentType = { "month-week-section" }
        ) { (weekIndex, days) ->
            val firstDate = monthStart.plus(DatePeriod(days = days.first()))
            val lastDate = monthStart.plus(DatePeriod(days = days.last()))
            MealsGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 13.dp,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("الأسبوع ${weekIndex + 1}", color = TextAccent,
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(formatArabicWeekRange(firstDate, lastDate), color = MutedInk,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        val plannedInWeek = snapshot.plannedMeals.count { it.dayIndex in days && it.recipeId != null }
                        Text("$plannedInWeek / ${days.size * orderedMealSlots.size}", color = TextAccent,
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    days.forEach { day ->
                        val date = monthStart.plus(DatePeriod(days = day))
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    formatArabicDate(date),
                                    modifier = Modifier.weight(1f),
                                    color = dz.meals.app.ui.theme.Ink,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${orderedMealSlots.count { mealsByDayAndSlot[day to it]?.recipeId != null }} / ${orderedMealSlots.size}",
                                    color = MutedInk,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            if (orderedMealSlots.isEmpty()) {
                                Text("لا توجد وجبات مفعّلة لهذا الشهر.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                            } else {
                                orderedMealSlots.forEach { slot ->
                                    val meal = mealsByDayAndSlot[day to slot]
                                    val recipe = meal?.recipeId?.let(recipesById::get)
                                    val title = when (slot) {
                                        "breakfast" -> "الفطور"
                                        "lunch" -> "الغداء"
                                        else -> "العشاء"
                                    }
                                    SlotRow(
                                        title = title,
                                        meal = meal,
                                        recipe = recipe,
                                        onChooseRecipe = { onChooseMeal(day, slot) },
                                        onOpenRecipe = { recipe?.id?.let(onOpenRecipe) },
                                        onToggleLock = { meal?.let { onToggleLock(it.id) } },
                                        onToggleCooked = { meal?.let { onToggleCooked(it.id) } },
                                        onAdjustServings = { delta -> meal?.let { onAdjustServings(it.id, delta) } }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            IngredientNeedsSection(
                items = snapshot.shoppingItems,
                onSetEnough = { item, enough ->
                    item.ingredientId?.let { onAdjustIngredient(it, item.unit, enough, 0.0) }
                },
                onEditAmount = onEditIngredient
            )
        }
    }
}

@Composable
private fun IngredientNeedsSection(
    items: List<dz.meals.app.data.ShoppingListItem>,
    onSetEnough: (dz.meals.app.data.ShoppingListItem, Boolean) -> Unit,
    onEditAmount: (dz.meals.app.data.ShoppingListItem) -> Unit
) {
    val needs = remember(items) {
        items.filter { !it.userAdded && it.ingredientId != null && it.requiredQuantity > 0.0 }
    }
    MealsGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 14.dp,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("احتياجات المكونات", color = dz.meals.app.ui.theme.Ink,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("الكميات تخص هذه الخطة فقط، ولا تُحفظ كمخزون دائم.", color = MutedInk,
                style = MaterialTheme.typography.bodySmall)
            if (needs.isEmpty()) {
                Text("ستظهر احتياجات المكونات بعد إنشاء الخطة.", color = MutedInk,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 7.dp))
            }
            needs.forEachIndexed { index, item ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Border.copy(alpha = 0.42f)))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.name, color = dz.meals.app.ui.theme.Ink, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall)
                    Text("المطلوب: ${item.requiredQuantity.formatHouseholdQuantity(item.unit)}",
                        color = dz.meals.app.ui.theme.BodyInk, style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (item.alreadyHaveEnough) "المتوفر: الكمية كاملة"
                        else if (item.alreadyHaveQuantity > 0.0) {
                            "المتوفر: ${item.alreadyHaveQuantity.formatHouseholdQuantity(item.unit)}"
                        } else "المتوفر: لم تُسجّل كمية",
                        color = MutedInk, style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "المتبقي للشراء: ${item.purchaseQuantity.formatHouseholdQuantity(item.unit)}",
                        color = TextAccent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        item.cashOutlayDzd?.let { "تكلفة الشراء: ${it.toInt().formatDzd()}" } ?: "السعر غير متوفر",
                        color = MutedInk, style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MealsSecondaryButton(
                            label = if (item.alreadyHaveEnough) "إلغاء توفر الكمية" else "لدي الكمية",
                            modifier = Modifier.weight(1f),
                            onClick = { onSetEnough(item, !item.alreadyHaveEnough) }
                        )
                        MealsTextButton(
                            modifier = Modifier.weight(1f),
                            onClick = { onEditAmount(item) }
                        ) {
                            Text(
                                if (item.alreadyHaveQuantity > 0.0) "تعديل الكمية" else "لدي بعض منها",
                                color = TextAccent, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * Do not display a zero estimate when the plan contains purchases with no price.
 * A partial total is labelled as partial; an empty, not-yet-generated plan stays
 * neutral instead of implying a zero-cost basket.
 */
private fun MealsSnapshot.purchaseEstimateLabel(): String {
    val plannedPurchases = shoppingItems.filter { !it.userAdded && it.purchaseQuantity > 0.0 }
    if (plannedPurchases.isEmpty()) {
        return if (plannedMeals.isEmpty()) "—" else 0.formatDzd()
    }

    fun hasPrice(item: dz.meals.app.data.ShoppingListItem): Boolean =
        item.cashOutlayDzd != null ||
            (item.marketCashOutlayMinDzd != null && item.marketCashOutlayMaxDzd != null)

    val pricedItems = plannedPurchases.filter(::hasPrice)
    val missingCount = plannedPurchases.size - pricedItems.size
    if (pricedItems.isEmpty()) return "غير متوفر"

    val knownLow = pricedItems.sumOf {
        it.marketCashOutlayMinDzd ?: it.cashOutlayDzd ?: 0.0
    }
    val knownHigh = pricedItems.sumOf {
        it.marketCashOutlayMaxDzd ?: it.cashOutlayDzd ?: 0.0
    }
    if (missingCount > 0) {
        val partial = if (knownLow != knownHigh) {
            "${knownLow.toInt().formatDzd()} – ${knownHigh.toInt().formatDzd()}"
        } else knownHigh.toInt().formatDzd()
        return "تقدير جزئي · $partial"
    }

    val minimum = basket.purchaseOutlayMinDzd ?: knownLow
    val maximum = basket.purchaseOutlayMaxDzd ?: knownHigh
    return if (minimum != maximum) {
        "${minimum.toInt().formatDzd()} – ${maximum.toInt().formatDzd()}"
    } else maximum.toInt().formatDzd()
}

@Composable
private fun SlotRow(
    title: String,
    meal: PlannedMeal?,
    recipe: MealRecipe?,
    onChooseRecipe: () -> Unit,
    onOpenRecipe: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleCooked: () -> Unit,
    onAdjustServings: (Int) -> Unit
) {
    val cardLayoutDirection = LocalLayoutDirection.current

    // Keep the action rail on the physical left edge, including in Arabic RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MealsIconButton(
                    onClick = onChooseRecipe,
                    modifier = Modifier.size(40.dp),
                    size = 40.dp
                ) {
                    MealsIcon(
                        name = if (recipe == null) MealsIconName.Add else MealsIconName.Refresh,
                        contentDescription = if (recipe == null) "إضافة وصفة لـ $title" else "تغيير وصفة $title",
                        modifier = Modifier.size(26.dp)
                    )
                }
                if (meal != null && recipe != null) {
                    MealsIconButton(
                        onClick = onToggleLock,
                        modifier = Modifier.size(40.dp),
                        size = 40.dp
                    ) {
                        MealsIcon(
                            name = if (meal.locked) MealsIconName.Lock else MealsIconName.LockOpen,
                            contentDescription = if (meal.locked) "فتح الوجبة" else "قفل الوجبة",
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    MealsIconButton(
                        onClick = onToggleCooked,
                        modifier = Modifier.size(40.dp),
                        size = 40.dp
                    ) {
                        MealsIcon(
                            name = MealsIconName.Check,
                            contentDescription = if (meal.isCooked) "إلغاء علامة تم الطهي" else "تحديد كوجبة مطهية",
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides cardLayoutDirection) {
                MealsPressable(
                    onClick = {
                        if (recipe != null) onOpenRecipe() else onChooseRecipe()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    MealsCard(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp),
                        containerColor = if (meal?.isCooked == true) {
                            dz.meals.app.ui.theme.SoftGreen.copy(alpha = 0.32f)
                        } else {
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.34f)
                        },
                        borderColor = Border.copy(alpha = 0.72f),
                        contentPadding = 13.dp,
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                MealsRecipeImage(
                                    imageUrl = recipe?.takeIf { it.hasDisplayableRecipeImage() }?.imageUrl.orEmpty(),
                                    contentDescription = recipe?.nameAr ?: title,
                                    modifier = Modifier.size(82.dp),
                                    imageWidth = 360,
                                    cornerRadius = 15.dp,
                                    placeholderIconSize = 32.dp
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        title,
                                        color = TextAccent,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        recipe?.nameAr ?: "لم تُضف وصفة بعد",
                                        color = if (recipe == null) MutedInk else dz.meals.app.ui.theme.Ink,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (meal != null && recipe != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        if (meal.isCooked) "تم الطهي" else "${recipe.durationMinutes} دقيقة",
                                        color = if (meal.isCooked) TextAccent else MutedInk,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.weight(1f))
                                    dz.meals.app.ui.components.MealsAnimatedCount(
                                        value = meal.servings.toString() + " حصص",
                                        color = dz.meals.app.ui.theme.Ink,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    MealsIconButton(
                                        onClick = { onAdjustServings(-1) },
                                        enabled = meal.servings > 1 && !meal.isCooked,
                                        modifier = Modifier.size(36.dp),
                                        size = 36.dp
                                    ) {
                                        Text("−", color = TextAccent, style = MaterialTheme.typography.titleMedium)
                                    }
                                    MealsIconButton(
                                        onClick = { onAdjustServings(1) },
                                        enabled = meal.servings < 12 && !meal.isCooked,
                                        modifier = Modifier.size(36.dp),
                                        size = 36.dp
                                    ) {
                                        Text("+", color = TextAccent, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            } else {
                                Text(
                                    "اضغط لإضافة وصفة لهذه الوجبة",
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
