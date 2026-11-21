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
import dz.meals.app.data.AppPreferences
import dz.meals.app.data.MealRecipe
import dz.meals.app.data.MealsSnapshot
import dz.meals.app.data.PlannedMeal
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PlanScreen(
    weekStart: LocalDate,
    preferences: AppPreferences,
    snapshot: MealsSnapshot,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    onSetMeal: (Int, String, String?) -> Unit,
    onToggleLock: (String) -> Unit,
    onAdjustServings: (String, Int) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    var showBudgetInfo by rememberSaveable { mutableStateOf(false) }
    val recipesById = snapshot.recipes.associateBy { it.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp),
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
                        weekStart.format(DateTimeFormatter.ofPattern("d MMM", Locale("ar", "DZ"))) +
                            " - " + weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ar", "DZ"))),
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
            Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ميزانية الأسبوع", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.78f))
                    Text(
                        preferences.weeklyBudgetDzd.asDzd(),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("قيمة المكونات المستهلكة", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                            Text(snapshot.basket.consumedValueDzd.toInt().asDzd(), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("مبلغ الشراء التقديري", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                            Text(snapshot.basket.purchaseOutlayDzd.toInt().asDzd(), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (snapshot.basket.unknownPriceCount > 0) {
                        Text(
                            snapshot.basket.unknownPriceCount.toString() + " عناصر دون سعر؛ المجموع غير مكتمل.",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (snapshot.basket.hasDemoPrices) {
                        Text(
                            "الأسعار الحالية تجريبية فقط وليست أسعار سوق محلية أو مباشرة.",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { showBudgetInfo = true }) {
                        Text("ما الفرق بين القيمتين؟", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
        item {
            Button(
                onClick = onGenerate,
                enabled = !isGenerating && snapshot.recipes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Forest),
                shape = RoundedCornerShape(11.dp)
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text(if (isGenerating) "جارٍ إنشاء الخطة..." else "أنشئ خطة 7 أيام")
            }
            Text(
                "نحافظ على الوجبات المقفلة، ولا نقترح وصفات تحتوي على مسببات الحساسية المحددة.",
                color = MutedInk,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        items((0 until 7).toList(), key = { it }) { day ->
            val date = weekStart.plusDays(day.toLong())
            val dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("ar", "DZ")))
            val lunch = snapshot.plannedMeals.firstOrNull { it.dayIndex == day && it.mealSlot == "lunch" }
            val dinner = snapshot.plannedMeals.firstOrNull { it.dayIndex == day && it.mealSlot == "dinner" }

            Card(
                colors = CardDefaults.cardColors(containerColor = Paper),
                border = BorderStroke(1.dp, Border),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(dateLabel, fontWeight = FontWeight.Bold)
                    SlotRow(
                        title = "الغداء",
                        meal = lunch,
                        recipe = lunch?.recipeId?.let(recipesById::get),
                        recipes = snapshot.recipes.filter { it.allergens.intersect(preferences.allergyIds).isEmpty() },
                        onSelect = { recipeId -> onSetMeal(day, "lunch", recipeId) },
                        onToggleLock = { lunch?.let { onToggleLock(it.id) } },
                        onAdjustServings = { delta -> lunch?.let { onAdjustServings(it.id, delta) } }
                    )
                    SlotRow(
                        title = "العشاء",
                        meal = dinner,
                        recipe = dinner?.recipeId?.let(recipesById::get),
                        recipes = snapshot.recipes.filter { it.allergens.intersect(preferences.allergyIds).isEmpty() },
                        onSelect = { recipeId -> onSetMeal(day, "dinner", recipeId) },
                        onToggleLock = { dinner?.let { onToggleLock(it.id) } },
                        onAdjustServings = { delta -> dinner?.let { onAdjustServings(it.id, delta) } }
                    )
                }
            }
        }
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
    onAdjustServings: (Int) -> Unit
) {
    var chooseRecipe by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = MutedInk, style = MaterialTheme.typography.bodySmall)
            Text(recipe?.nameAr ?: "لم يتم اختيار وجبة", fontWeight = FontWeight.SemiBold)
            if (meal != null && recipe != null) {
                Text(recipe.durationMinutes.toString() + " دقيقة · " + meal.servings + " حصص", color = MutedInk, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (meal != null && recipe != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onAdjustServings(-1) }, enabled = meal.servings > 1) { Text("−") }
                Text(meal.servings.toString(), fontWeight = FontWeight.Bold)
                TextButton(onClick = { onAdjustServings(1) }, enabled = meal.servings < 12) { Text("+") }
            }
            IconButton(onClick = onToggleLock) {
                Icon(
                    if (meal.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = if (meal.locked) "إلغاء القفل" else "قفل الوجبة",
                    tint = if (meal.locked) Forest else MutedInk
                )
            }
        } else {
            Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = MutedInk)
        }
        OutlinedButton(onClick = { chooseRecipe = true }) {
            Icon(if (recipe == null) Icons.Outlined.Add else Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(if (recipe == null) "إضافة" else "تغيير")
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
                                Text(item.durationMinutes.toString() + " دقيقة · " + item.baseServings + " حصص", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onSelect(null); chooseRecipe = false }) { Text("إفراغ الوجبة") } },
            dismissButton = { TextButton(onClick = { chooseRecipe = false }) { Text("إلغاء") } }
        )
    }
}

private fun Int.asDzd(): String =
    NumberFormat.getIntegerInstance(Locale("fr", "DZ")).format(this) + " دج"
