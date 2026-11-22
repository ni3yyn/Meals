package dz.meals.app.ui.screens

import androidx.compose.ui.text.style.TextAlign

import dz.meals.app.ui.components.MealsIconAction
import dz.meals.app.ui.components.MealsIconButton
import dz.meals.app.ui.components.MealsTextButton
import dz.meals.app.ui.theme.ForestDark

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.meals.app.ui.components.MealsCard
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
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.shared.format.formatArabicDate
import dz.meals.app.shared.format.formatArabicWeekRange
import dz.meals.app.shared.format.formatDzd
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.coroutines.delay

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
    var mealAwaitingCookConfirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var fillEmptyOnly by rememberSaveable { mutableStateOf(false) }
    var expandedDay by rememberSaveable { mutableStateOf(0) }
    val recipesById = snapshot.recipes.associateBy { it.id }
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 28.dp)
                            .clip(CircleShape)
                            .background(Forest)
                    )
                    Text(
                        "خطة الأسبوع",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = dz.meals.app.ui.theme.Ink
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MealsIconAction(onClick = onPreviousWeek, modifier = Modifier.size(48.dp)) {
                        MealsIcon(
                            MealsIconName.ArrowForward,
                            contentDescription = "الأسبوع السابق",
                            modifier = Modifier.size(28.dp),
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
                    MealsIconAction(onClick = onNextWeek, modifier = Modifier.size(44.dp)) {
                        MealsIcon(
                            MealsIconName.ArrowBack,
                            contentDescription = "الأسبوع التالي",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
        item {
            MealsGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                tintColor = androidx.compose.ui.graphics.Color(0xFFFFF6EC),
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
                            .background(dz.meals.app.ui.theme.SoftGreen.copy(alpha = 0.68f))
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
                Row(
                    modifier = Modifier.weight(0.92f).clip(RoundedCornerShape(17.dp))
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.28f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { fillEmptyOnly = !fillEmptyOnly }
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
                            .background(Forest)
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

        items((0 until 7).toList(), key = { it }) { day ->
            val date = weekStart.plus(DatePeriod(days = day))
            val dateLabel = formatArabicDate(date)
            val breakfast = snapshot.plannedMeals.firstOrNull { it.dayIndex == day && it.mealSlot == "breakfast" }
            val lunch = snapshot.plannedMeals.firstOrNull { it.dayIndex == day && it.mealSlot == "lunch" }
            val dinner = snapshot.plannedMeals.firstOrNull { it.dayIndex == day && it.mealSlot == "dinner" }
            val dayMeals = listOf(breakfast, lunch, dinner)
            val plannedCount = dayMeals.count { it?.recipeId != null }
            val cookedCount = dayMeals.count { it?.isCooked == true }
            val isExpanded = expandedDay == day
            val bringIntoViewRequester = remember { BringIntoViewRequester() }

            LaunchedEffect(isExpanded) {
                if (isExpanded) {
                    // Reveal the expanded card after its height transition has settled.
                    delay(230)
                    bringIntoViewRequester.bringIntoView()
                }
            }

            MealsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester),
                containerColor = Paper,
                borderColor = if (isExpanded) Forest else Border,
                contentPadding = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { expandedDay = if (isExpanded) -1 else day }
                        .padding(horizontal = 17.dp, vertical = 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MealsIllustrationBadge(
                        icon = MealsIconName.Meal,
                        background = dz.meals.app.ui.theme.SoftGreen.copy(alpha = 0.82f),
                        size = 42.dp
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            dateLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = dz.meals.app.ui.theme.Ink
                        )
                        Text(
                            if (cookedCount > 0) {
                                "$cookedCount مكتملة · $plannedCount من ${visibleMealSlots.size} وجبات"
                            } else {
                                "$plannedCount من ${visibleMealSlots.size} وجبات محددة"
                            },
                            color = MutedInk,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(dz.meals.app.ui.theme.SoftGreen.copy(alpha = 0.76f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MealsIcon(
                            name = MealsIconName.ChevronRight,
                            contentDescription = if (isExpanded) "طي اليوم" else "عرض وجبات اليوم",
                            modifier = Modifier.size(25.dp).graphicsLayer {
                                rotationZ = if (isExpanded) 90f else 0f
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(animationSpec = tween(durationMillis = 180)),
                    exit = shrinkVertically(animationSpec = tween(durationMillis = 180))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Spacer(
                            Modifier.fillMaxWidth().height(1.dp)
                                .background(Border.copy(alpha = 0.72f))
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, bottom = 15.dp),
                            verticalArrangement = Arrangement.spacedBy(15.dp)
                        ) {
                            if ("breakfast" in visibleMealSlots) SlotRow(
                                title = "الفطور",
                                meal = breakfast,
                                recipe = breakfast?.recipeId?.let(recipesById::get),
                                recipes = snapshot.recipes.filter { it.allergens.intersect(preferences.allergyIds).isEmpty() },
                                onSelect = { recipeId -> onSetMeal(day, "breakfast", recipeId) },
                                onToggleLock = { breakfast?.let { onToggleLock(it.id) } },
                                onToggleCooked = {
                                    breakfast?.let { meal ->
                                        if (meal.isCooked) onToggleCooked(meal.id)
                                        else mealAwaitingCookConfirmation = meal.id
                                    }
                                },
                                onAdjustServings = { delta -> breakfast?.let { onAdjustServings(it.id, delta) } }
                            )
                            if ("lunch" in visibleMealSlots) SlotRow(
                                title = "الغداء",
                                meal = lunch,
                                recipe = lunch?.recipeId?.let(recipesById::get),
                                recipes = snapshot.recipes.filter { it.allergens.intersect(preferences.allergyIds).isEmpty() },
                                onSelect = { recipeId -> onSetMeal(day, "lunch", recipeId) },
                                onToggleLock = { lunch?.let { onToggleLock(it.id) } },
                                onToggleCooked = {
                                    lunch?.let { meal ->
                                        if (meal.isCooked) onToggleCooked(meal.id)
                                        else mealAwaitingCookConfirmation = meal.id
                                    }
                                },
                                onAdjustServings = { delta -> lunch?.let { onAdjustServings(it.id, delta) } }
                            )
                            if ("dinner" in visibleMealSlots) SlotRow(
                                title = "العشاء",
                                meal = dinner,
                                recipe = dinner?.recipeId?.let(recipesById::get),
                                recipes = snapshot.recipes.filter { it.allergens.intersect(preferences.allergyIds).isEmpty() },
                                onSelect = { recipeId -> onSetMeal(day, "dinner", recipeId) },
                                onToggleLock = { dinner?.let { onToggleLock(it.id) } },
                                onToggleCooked = {
                                    dinner?.let { meal ->
                                        if (meal.isCooked) onToggleCooked(meal.id)
                                        else mealAwaitingCookConfirmation = meal.id
                                    }
                                },
                                onAdjustServings = { delta -> dinner?.let { onAdjustServings(it.id, delta) } }
                            )
                        }
                    }
                }
            }
        }
    }

    mealAwaitingCookConfirmation?.let { mealId ->
        MealsBottomSheet(
            onDismissRequest = { mealAwaitingCookConfirmation = null },
            title = { Text("تأكيد تسجيل الطهي") },
            text = {
                Text("سيُخصم من المخزون فقط ما تملكه بكمية دقيقة. الكميات التقريبية لن تُخصم. يمكنك التراجع من الوجبة نفسها لاستعادة ما خُصم.")
            },
            confirmButton = {
                MealsPrimaryButton(
                    label = "تأكيد الطهي",
                    onClick = {
                        onToggleCooked(mealId)
                        mealAwaitingCookConfirmation = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                MealsSecondaryButton(
                    label = "إلغاء",
                    onClick = { mealAwaitingCookConfirmation = null },
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
    recipes: List<MealRecipe>,
    onSelect: (String?) -> Unit,
    onToggleLock: () -> Unit,
    onToggleCooked: () -> Unit,
    onAdjustServings: (Int) -> Unit
) {
    var chooseRecipe by rememberSaveable { mutableStateOf(false) }

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
                    color = Forest,
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
                        color = Forest,
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
                    Text("−", color = Forest, style = MaterialTheme.typography.headlineSmall)
                }
                MealsIconButton(
                    onClick = { onAdjustServings(1) },
                    enabled = meal.servings < 12 && !meal.isCooked,
                    modifier = Modifier.size(44.dp),
                    size = 44.dp
                ) {
                    Text("+", color = Forest, style = MaterialTheme.typography.headlineSmall)
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
                    onClick = { chooseRecipe = true },
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
                    onClick = { chooseRecipe = true },
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

    if (chooseRecipe) {
        MealsBottomSheet(
            onDismissRequest = { chooseRecipe = false },
            title = { Text("اختر وصفة لـ " + title) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(recipes, key = { it.id }) { item ->
                        MealsTextButton(
                            onClick = {
                                onSelect(item.id)
                                chooseRecipe = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    item.nameAr,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    item.durationMinutes.toString() + " دقيقة · " + item.baseServings + " حصص",
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                MealsSecondaryButton(
                    label = "إفراغ الوجبة",
                    onClick = { onSelect(null); chooseRecipe = false },
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = androidx.compose.ui.graphics.Color(0xFFB65E53)
                )
            },
            dismissButton = {
                MealsSecondaryButton(
                    label = "إلغاء",
                    onClick = { chooseRecipe = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}
