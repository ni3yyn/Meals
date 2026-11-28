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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus

@Composable
fun PlanScreen(
    weekStartIso: String,
    preferences: AppPreferences,
    snapshot: MealsSnapshot,
    isGenerating: Boolean,
    onGenerate: (Boolean) -> Unit,
    onSetMeal: (Int, String, String?) -> Unit,
    onToggleLock: (String) -> Unit,
    onToggleCooked: (String) -> Unit,
    onAdjustServings: (String, Int) -> Unit,
    onOpenRecipe: (String) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val weekStart = LocalDate.parse(weekStartIso)
    val weekDays = remember { (0 until 7).toList() }
    val today = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val todayIndexInWeek = today.toEpochDays() - weekStart.toEpochDays()
    val initialDayIndex = todayIndexInWeek.takeIf { it in 0L..6L }?.toInt() ?: 0
    var fillEmptyOnly by rememberSaveable { mutableStateOf(false) }
    var selectedDayIndex by rememberSaveable(weekStartIso) { mutableStateOf(initialDayIndex) }
    var editingMealSlot by rememberSaveable { mutableStateOf<String?>(null) }
    val dayChipListState = rememberLazyListState()
    LaunchedEffect(weekStartIso, initialDayIndex) {
        dayChipListState.animateScrollToItem(initialDayIndex)
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
                            preferences.weeklyBudgetDzd.formatDzd(),
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
                            snapshot.basket.purchaseOutlayMinDzd?.let { minimum ->
                                val maximum = snapshot.basket.purchaseOutlayMaxDzd
                                if (maximum != null && minimum != maximum) {
                                    minimum.toInt().formatDzd() + " – " + maximum.toInt().formatDzd()
                                } else snapshot.basket.purchaseOutlayDzd.toInt().formatDzd()
                            } ?: snapshot.basket.purchaseOutlayDzd.toInt().formatDzd(),
                            color = dz.meals.app.ui.theme.Ink,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
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
