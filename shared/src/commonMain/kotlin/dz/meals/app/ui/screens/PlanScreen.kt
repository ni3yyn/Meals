package dz.meals.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.meals.app.ui.components.MealsCard
import dz.meals.app.ui.components.MealsGlassSurface
import dz.meals.app.ui.components.MealsIllustrationBadge
import dz.meals.app.ui.components.MealsCheckRow
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
import dz.meals.app.shared.format.epochMillisToLocalDate
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
    onNextWeek: () -> Unit
) {
    val weekStart = LocalDate.parse(weekStartIso)
    var showBudgetInfo by rememberSaveable { mutableStateOf(false) }
    var mealAwaitingCookConfirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var fillEmptyOnly by rememberSaveable { mutableStateOf(false) }
    val recipesById = snapshot.recipes.associateBy { it.id }
    val visibleMealSlots = snapshot.planRun?.mealSlots ?: preferences.mealSlots

    val unsafeLockedMeal = snapshot.plannedMeals.firstOrNull { meal ->
        meal.locked && meal.recipeId?.let { recipeId ->
            recipesById[recipeId]?.allergens?.intersect(preferences.allergyIds)?.isNotEmpty()
        } == true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 122.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("خطة الأسبوع", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        formatArabicWeekRange(weekStart, weekStart.plus(DatePeriod(days = 6))),
                        color = MutedInk
                    )
                }
                IconButton(onClick = onPreviousWeek) {
                    Icon(Icons.Outlined.ArrowForward, contentDescription = "الأسبوع السابق", tint = Forest)
                }
                IconButton(onClick = onNextWeek) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "الأسبوع التالي", tint = Forest)
                }
            }
        }
        item {
            MealsGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                tintColor = androidx.compose.ui.graphics.Color(0xFFFFF6EC),
                contentPadding = 18.dp,
                shape = RoundedCornerShape(29.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "ميزانية الأسبوع",
                            color = dz.meals.app.ui.theme.ForestDark,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            preferences.weeklyBudgetDzd.formatDzd(),
                            color = dz.meals.app.ui.theme.Ink,
                            style = MaterialTheme.typography.displaySmall
                        )
                    }
                    MealsIllustrationBadge(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        background = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.84f),
                        iconTint = Forest,
                        size = 62.dp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "قيمة المكونات المستهلكة",
                            color = MutedInk,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        Text(
                            snapshot.basket.consumedValueDzd.toInt().formatDzd(),
                            color = dz.meals.app.ui.theme.Ink,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "مبلغ الشراء التقديري",
                            color = MutedInk,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        Text(
                            snapshot.basket.purchaseOutlayDzd.toInt().formatDzd(),
                            color = dz.meals.app.ui.theme.Ink,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                if (snapshot.basket.unknownPriceCount > 0) {
                    Text(
                        snapshot.basket.unknownPriceCount.toString() + " عناصر دون سعر؛ المجموع غير مكتمل.",
                        color = dz.meals.app.ui.theme.ForestDark,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "الإنفاق الفعلي المُدخل: " + snapshot.basket.actualSpendDzd.toInt().formatDzd(),
                    color = dz.meals.app.ui.theme.Ink,
                    fontWeight = FontWeight.SemiBold
                )
                snapshot.planRun?.let { run ->
                    Text(
                        "آخر إنشاء: " + epochMillisToLocalDate(run.generatedAtEpochMillis).toString() +
                            " · نسخة المحرك " + run.engineVersion,
                        color = MutedInk,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (snapshot.basket.hasDemoPrices) {
                    Text(
                        "الأسعار الحالية تجريبية فقط وليست أسعار سوق محلية أو مباشرة.",
                        color = dz.meals.app.ui.theme.ForestDark,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { showBudgetInfo = true }) {
                    Text("ما الفرق بين القيمتين؟", color = Forest)
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
            MealsCheckRow(
                title = "املأ الوجبات الفارغة فقط",
                supportingText = "اترك الوجبات التي اخترتها كما هي",
                checked = fillEmptyOnly,
                onCheckedChange = { fillEmptyOnly = it }
            )
            MealsPrimaryButton(
                label = if (isGenerating) "جارٍ إنشاء الخطة..." else "أنشئ خطة 7 أيام",
                onClick = { onGenerate(fillEmptyOnly) },
                enabled = !isGenerating && snapshot.recipes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Outlined.Refresh
            )
            Text(
                "نحافظ على الوجبات المقفلة. الحساسية والنمط الغذائي قيود إلزامية، أما وقت الطبخ وتجنب مكونات معينة فيؤثران في ترتيب الاقتراحات.",
                color = MutedInk,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        items((0 until 7).toList(), key = { it }) { day ->
            val date = weekStart.plus(DatePeriod(days = day))
            val dateLabel = formatArabicDate(date)
            val breakfast = snapshot.plannedMeals.firstOrNull { it.dayIndex == day && it.mealSlot == "breakfast" }
            val lunch = snapshot.plannedMeals.firstOrNull { it.dayIndex == day && it.mealSlot == "lunch" }
            val dinner = snapshot.plannedMeals.firstOrNull { it.dayIndex == day && it.mealSlot == "dinner" }

            MealsCard(containerColor = Paper, borderColor = Border, contentPadding = 16.dp) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text(dateLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

    mealAwaitingCookConfirmation?.let { mealId ->
        AlertDialog(
            onDismissRequest = { mealAwaitingCookConfirmation = null },
            title = { Text("تأكيد تسجيل الطهي") },
            text = {
                Text("سيُخصم من المخزون فقط ما تملكه بكمية دقيقة. الكميات التقريبية لن تُخصم. يمكنك التراجع من الوجبة نفسها لاستعادة ما خُصم.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onToggleCooked(mealId)
                    mealAwaitingCookConfirmation = null
                }) { Text("تأكيد وخصم المخزون الدقيق") }
            },
            dismissButton = {
                TextButton(onClick = { mealAwaitingCookConfirmation = null }) { Text("إلغاء") }
            }
        )
    }

    if (showBudgetInfo) {
        AlertDialog(
            onDismissRequest = { showBudgetInfo = false },
            title = { Text("قيمة الاستهلاك ومبلغ الشراء") },
            text = {
                Text(
                    "قيمة الاستهلاك تحسب الجزء الذي استعملته الوصفات من كل عبوة. مبلغ الشراء يحسب العبوات الكاملة التي قد تحتاج إلى دفع ثمنها بعد خصم المخزون الدقيق. قد يبقى جزء من العبوة لاستخدامه لاحقاً. الأسعار المعروضة حالياً تجريبية، لذلك لا تعتمد عليها كعرض سعر حقيقي."
                )
            },
            confirmButton = { TextButton(onClick = { showBudgetInfo = false }) { Text("حسناً") } }
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                color = Forest,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = recipe?.nameAr ?: "لم يتم اختيار وجبة بعد",
                color = if (recipe == null) MutedInk else dz.meals.app.ui.theme.Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (meal != null && recipe != null) {
                Text(
                    recipe.durationMinutes.toString() + " دقيقة",
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (meal?.isCooked == true) {
                Text(
                    "تم الطهي وتحديث المخزون",
                    color = Forest,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (meal != null && recipe != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onAdjustServings(-1) },
                        enabled = meal.servings > 1 && !meal.isCooked,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("−", color = Forest, style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        meal.servings.toString() + " حصص",
                        color = dz.meals.app.ui.theme.Ink,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    IconButton(
                        onClick = { onAdjustServings(1) },
                        enabled = meal.servings < 12 && !meal.isCooked,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("+", color = Forest, style = MaterialTheme.typography.titleLarge)
                    }
                }
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        if (meal.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        contentDescription = if (meal.locked) "إلغاء قفل الوجبة" else "قفل الوجبة",
                        tint = if (meal.locked) Forest else MutedInk
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MealsPrimaryButton(
                    label = if (meal.isCooked) "تراجع" else "تم الطهي",
                    onClick = onToggleCooked,
                    modifier = Modifier.weight(1f),
                    showArrow = false
                )
                MealsSecondaryButton(
                    label = "تغيير",
                    onClick = { chooseRecipe = true },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Refresh
                )
            }
        } else {
            MealsSecondaryButton(
                label = "إضافة وصفة",
                onClick = { chooseRecipe = true },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Outlined.Add
            )
        }
    }

    if (chooseRecipe) {
        AlertDialog(
            onDismissRequest = { chooseRecipe = false },
            title = { Text("اختر وصفة لـ " + title) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(recipes, key = { it.id }) { item ->
                        TextButton(
                            onClick = {
                                onSelect(item.id)
                                chooseRecipe = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(item.nameAr, fontWeight = FontWeight.SemiBold)
                                Text(
                                    item.durationMinutes.toString() + " دقيقة · " + item.baseServings + " حصص",
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSelect(null); chooseRecipe = false }) {
                    Text("إفراغ الوجبة")
                }
            },
            dismissButton = {
                TextButton(onClick = { chooseRecipe = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
