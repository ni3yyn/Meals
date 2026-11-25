package dz.meals.app.ui.screens

import dz.meals.app.ui.components.MealsText as Text

import androidx.compose.ui.text.style.TextAlign

import dz.meals.app.ui.components.MealsIconAction
import dz.meals.app.ui.components.MealsIconButton
import dz.meals.app.ui.components.MealsTextButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import dz.meals.app.ui.components.MealsBottomSheet
import dz.meals.app.ui.components.mealsSheetDismissAction
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.meals.app.ui.components.MealsCard
import dz.meals.app.ui.components.MealsPressable
import dz.meals.app.ui.components.MealsGlassSurface
import dz.meals.app.ui.components.MealsIllustrationBadge
import dz.meals.app.ui.components.MealsCheckbox
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.ui.components.MealsPrimaryButton
import dz.meals.app.ui.components.MealsSecondaryButton
import dz.meals.app.data.AppPreferences
import dz.meals.app.data.MealRecipe
import dz.meals.app.data.MealsSnapshot
import dz.meals.app.data.PlannedMeal
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.TextAccent
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.shared.format.formatArabicDate
import dz.meals.app.shared.format.formatArabicWeekRange
import dz.meals.app.shared.format.formatDzd
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
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
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val weekStart = LocalDate.parse(weekStartIso)
    val weekDays = remember { (0 until 7).toList() }
    var fillEmptyOnly by rememberSaveable { mutableStateOf(false) }
    var selectedDayIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingMealSlot by rememberSaveable { mutableStateOf<String?>(null) }
    val recipesById = remember(snapshot.recipes) { snapshot.recipes.associateBy { it.id } }
    val eligibleRecipes = remember(snapshot.recipes, preferences.allergyIds) {
        snapshot.recipes.filter { it.allergens.intersect(preferences.allergyIds).isEmpty() }
    }
    val mealsByDayAndSlot = remember(snapshot.plannedMeals) {
        snapshot.plannedMeals.associateBy { it.dayIndex to it.mealSlot }
    }
    val visibleMealSlots = snapshot.planRun?.mealSlots ?: preferences.mealSlots

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
                tintColor = androidx.compose.ui.graphics.Color.Transparent,
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
                            snapshot.basket.purchaseOutlayDzd.toInt().formatDzd(),
                            color = dz.meals.app.ui.theme.Ink,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                if (snapshot.basket.unknownPriceCount > 0 || snapshot.basket.hasDemoPrices) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MealsPressable(
                    onClick = { fillEmptyOnly = !fillEmptyOnly },
                    modifier = Modifier.weight(0.92f),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(17.dp))
                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.28f))
                            .padding(start = 5.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
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
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 25.dp)
                            .clip(CircleShape)
                            .background(TextAccent)
                    )
                    Text(
                        "الوجبات اليومية",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("7 أيام", color = MutedInk, style = MaterialTheme.typography.labelMedium)
            }
        }

        items(
            weekDays,
            key = { day -> day },
            contentType = { "plan-day" }
        ) { day ->
            val date = weekStart.plus(DatePeriod(days = day))
            val dateLabel = formatArabicDate(date)
            val breakfast = mealsByDayAndSlot[day to "breakfast"]
            val lunch = mealsByDayAndSlot[day to "lunch"]
            val dinner = mealsByDayAndSlot[day to "dinner"]
            val dayMeals = listOf(breakfast, lunch, dinner)
            val plannedCount = dayMeals.count { it?.recipeId != null }
            val cookedCount = dayMeals.count { it?.isCooked == true }

            MealsPressable(
                onClick = {
                    selectedDayIndex = day
                    editingMealSlot = null
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                MealsCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    borderColor = Border,
                    contentPadding = 15.dp
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    MealsIllustrationBadge(
                        icon = MealsIconName.Meal,
                        background = dz.meals.app.ui.theme.WarmSurface.copy(alpha = 0.82f),
                        size = 46.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            dateLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = dz.meals.app.ui.theme.Ink
                        )
                        Text(
                            when {
                                plannedCount == 0 -> "لا توجد وجبات محددة بعد"
                                cookedCount == plannedCount -> "$cookedCount وجبات مكتملة"
                                cookedCount > 0 -> "$plannedCount وجبات · $cookedCount مكتملة"
                                else -> "$plannedCount من ${visibleMealSlots.size} وجبات محددة"
                            },
                            color = MutedInk,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            "$plannedCount/${visibleMealSlots.size}",
                            color = TextAccent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        MealsIcon(
                            name = MealsIconName.ChevronRight,
                            contentDescription = "عرض تفاصيل اليوم",
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
                }
            }
        }
    }

    selectedDayIndex?.let { day ->
        val date = weekStart.plus(DatePeriod(days = day))
        val dateLabel = formatArabicDate(date)
        val breakfast = mealsByDayAndSlot[day to "breakfast"]
        val lunch = mealsByDayAndSlot[day to "lunch"]
        val dinner = mealsByDayAndSlot[day to "dinner"]
        val dayMeals = listOf(breakfast, lunch, dinner)
        val plannedCount = dayMeals.count { it?.recipeId != null }
        val cookedCount = dayMeals.count { it?.isCooked == true }
        val editingSlot = editingMealSlot
        val slotTitle = when (editingSlot) {
            "breakfast" -> "الفطور"
            "lunch" -> "الغداء"
            "dinner" -> "العشاء"
            else -> null
        }
        val existingMeal = when (editingSlot) {
            "breakfast" -> breakfast
            "lunch" -> lunch
            "dinner" -> dinner
            else -> null
        }
        val currentRecipe = existingMeal?.recipeId?.let(recipesById::get)

        MealsBottomSheet(
            onDismissRequest = {
                selectedDayIndex = null
                editingMealSlot = null
            },
            title = {
                Text(
                    when {
                        slotTitle != null -> "اختر وصفة · $slotTitle"
                        else -> dateLabel
                    }
                )
            },
            subtitle = when {
                slotTitle != null -> "وصفات مناسبة لتفضيلات الأسرة"
                else -> "$plannedCount من ${visibleMealSlots.size} وجبات محددة · $cookedCount مكتملة"
            },
            fixedHeightFraction = 0.88f,
            fillBody = true,
            text = {
                when {
                    slotTitle != null -> {
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
                                        onSetMeal(day, editingSlot!!, recipe.id)
                                        editingMealSlot = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
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
                                                    recipe.baseServings + " حصص",
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
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(15.dp)
                        ) {
                            item {
                                MealsCard(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    borderColor = null,
                                    contentPadding = 14.dp
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                "تقدم اليوم",
                                                color = TextAccent,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                if (plannedCount == 0) "لنخطط وجبات هذا اليوم"
                                                else "$plannedCount وجبات محددة · $cookedCount مكتملة",
                                                color = dz.meals.app.ui.theme.Ink,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            "$cookedCount / $plannedCount",
                                            color = TextAccent,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            if ("breakfast" in visibleMealSlots) {
                                item {
                                    SlotRow(
                                        title = "الفطور",
                                        meal = breakfast,
                                        recipe = breakfast?.recipeId?.let(recipesById::get),
                                        onChooseRecipe = { editingMealSlot = "breakfast" },
                                        onToggleLock = { breakfast?.let { onToggleLock(it.id) } },
                                        onToggleCooked = {
                                            breakfast?.let { meal ->
                                                onToggleCooked(meal.id)
                                            }
                                        },
                                        onAdjustServings = { delta -> breakfast?.let { onAdjustServings(it.id, delta) } }
                                    )
                                }
                            }
                            if ("lunch" in visibleMealSlots) {
                                item {
                                    SlotRow(
                                        title = "الغداء",
                                        meal = lunch,
                                        recipe = lunch?.recipeId?.let(recipesById::get),
                                        onChooseRecipe = { editingMealSlot = "lunch" },
                                        onToggleLock = { lunch?.let { onToggleLock(it.id) } },
                                        onToggleCooked = {
                                            lunch?.let { meal ->
                                                onToggleCooked(meal.id)
                                            }
                                        },
                                        onAdjustServings = { delta -> lunch?.let { onAdjustServings(lunch.id, delta) } }
                                    )
                                }
                            }
                            if ("dinner" in visibleMealSlots) {
                                item {
                                    SlotRow(
                                        title = "العشاء",
                                        meal = dinner,
                                        recipe = dinner?.recipeId?.let(recipesById::get),
                                        onChooseRecipe = { editingMealSlot = "dinner" },
                                        onToggleLock = { dinner?.let { onToggleLock(it.id) } },
                                        onToggleCooked = {
                                            dinner?.let { meal ->
                                                onToggleCooked(meal.id)
                                            }
                                        },
                                        onAdjustServings = { delta -> dinner?.let { onAdjustServings(it.id, delta) } }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    slotTitle != null -> {
                        MealsSecondaryButton(
                            label = "إفراغ الوجبة",
                            onClick = {
                                onSetMeal(day, editingSlot!!, null)
                                editingMealSlot = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {
                        val dismissSheet = mealsSheetDismissAction()
                        MealsPrimaryButton(
                            label = "إغلاق اليوم",
                            onClick = { dismissSheet { selectedDayIndex = null } },
                            modifier = Modifier.fillMaxWidth(),
                            showArrow = false
                        )
                    }
                }
            },
            dismissButton = when {
                slotTitle != null -> ({
                    MealsSecondaryButton(
                        label = "العودة إلى اليوم",
                        onClick = { editingMealSlot = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                })
                else -> null
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
    onToggleLock: () -> Unit,
    onToggleCooked: () -> Unit,
    onAdjustServings: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MealsIcon(
                name = MealsIconName.Meal,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    title,
                    color = TextAccent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    recipe?.nameAr ?: "لم تُحدّد بعد",
                    color = if (recipe == null) MutedInk else dz.meals.app.ui.theme.Ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                        modifier = Modifier.size(31.dp)
                    )
                }
            }
        }

        if (meal != null && recipe != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (meal.isCooked) {
                    MealsIcon(
                        name = MealsIconName.Check,
                        contentDescription = null,
                        modifier = Modifier.size(27.dp)
                    )
                    Text(
                        "تم الطهي",
                        color = TextAccent,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    MealsIcon(
                        name = MealsIconName.Clock,
                        contentDescription = null,
                        modifier = Modifier.size(27.dp)
                    )
                    Text(
                        recipe.durationMinutes.toString() + " دقيقة",
                        color = MutedInk,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = 1.dp, height = 18.dp)
                        .background(Border.copy(alpha = 0.8f))
                )
                MealsIcon(
                    name = MealsIconName.Meal,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                dz.meals.app.ui.components.MealsAnimatedCount(
                    value = meal.servings.toString() + " حصص",
                    color = dz.meals.app.ui.theme.Ink,
                    style = MaterialTheme.typography.bodySmall
                )
                MealsIconButton(
                    onClick = { onAdjustServings(-1) },
                    enabled = meal.servings > 1 && !meal.isCooked,
                    modifier = Modifier.size(44.dp),
                    size = 44.dp
                ) {
                    Text("−", color = TextAccent, style = MaterialTheme.typography.headlineSmall)
                }
                MealsIconButton(
                    onClick = { onAdjustServings(1) },
                    enabled = meal.servings < 12 && !meal.isCooked,
                    modifier = Modifier.size(44.dp),
                    size = 44.dp
                ) {
                    Text("+", color = TextAccent, style = MaterialTheme.typography.headlineSmall)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (meal.isCooked) {
                    MealsSecondaryButton(
                        label = "تراجع",
                        onClick = onToggleCooked,
                        modifier = Modifier.weight(1f),
                        icon = MealsIconName.Check
                    )
                } else {
                    MealsPrimaryButton(
                        label = "تم الطهي",
                        onClick = onToggleCooked,
                        modifier = Modifier.weight(1f),
                        icon = MealsIconName.Check
                    )
                }
                MealsSecondaryButton(
                    label = "تغيير",
                    onClick = onChooseRecipe,
                    modifier = Modifier.weight(1f),
                    icon = MealsIconName.Refresh
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                MealsTextButton(
                    onClick = onChooseRecipe,
                    compact = true
                ) {
                    MealsIcon(
                        name = MealsIconName.Add,
                        contentDescription = null,
                        modifier = Modifier.size(25.dp)
                    )
                    Text(
                        "إضافة وصفة",
                        color = dz.meals.app.ui.theme.TextAccent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

}
