package dz.meals.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.meals.app.data.AppPreferences
import dz.meals.app.data.MealRecipe
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen
import dz.meals.app.ui.theme.SoftOrange
import dz.meals.app.ui.theme.Tangerine
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    preferences: AppPreferences,
    plannedRecipes: List<MealRecipe>,
    onOpenRecipes: () -> Unit,
    onOpenShopping: () -> Unit,
    onRemoveRecipe: (String) -> Unit
) {
    val estimatedTotal = plannedRecipes.sumOf { it.estimatedCostDzd }
    val remaining = (preferences.weeklyBudgetDzd - estimatedTotal).coerceAtLeast(0)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("أهلاً بك في مطبخك", color = MutedInk)
            Text("خطّط وجباتك بذكاء", style = MaterialTheme.typography.headlineLarge)
            Text(
                "اختر ما ستطبخه، وسنرتّب لك المشتريات والميزانية.",
                color = MutedInk,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        BudgetCard(budget = preferences.weeklyBudgetDzd, estimated = estimatedTotal, remaining = remaining)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile(
                modifier = Modifier.weight(1f),
                value = plannedRecipes.size.toString(),
                label = "وجبات في خطتك",
                icon = Icons.Outlined.Restaurant
            )
            SummaryTile(
                modifier = Modifier.weight(1f),
                value = plannedRecipes.sumOf { it.servings }.toString(),
                label = "حصص تقريبية",
                icon = Icons.Outlined.ShoppingBasket
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("خطة وجباتك", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onOpenRecipes) {
                Text("اكتشف الوصفات")
                Icon(Icons.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
            }
        }

        if (plannedRecipes.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Paper),
                border = BorderStroke(1.dp, Border),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(58.dp).background(SoftGreen, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = Forest, modifier = Modifier.size(27.dp))
                    }
                    Text("لم تضف أي وجبة بعد", fontWeight = FontWeight.SemiBold)
                    Text(
                        "ابدأ بوصفة واحدة، وستظهر مكوّناتها تلقائياً في قائمة التسوق.",
                        color = MutedInk,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onOpenRecipes, colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("أضف أول وجبة")
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                plannedRecipes.forEach { recipe ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Paper),
                        border = BorderStroke(1.dp, Border),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp).background(SoftOrange, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = Tangerine)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(recipe.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    recipe.durationMinutes.toString() + " دقيقة · " + recipe.servings + " حصص",
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(recipe.estimatedCostDzd.asDzd(), color = Forest, fontWeight = FontWeight.Bold)
                                TextButton(
                                    onClick = { onRemoveRecipe(recipe.id) },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) {
                                    Text("إزالة", color = MutedInk, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onOpenShopping,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Forest),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.ShoppingBasket, contentDescription = null)
                Text("افتح قائمة المشتريات")
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = SoftOrange), shape = RoundedCornerShape(12.dp)) {
            Text(
                "تنبيه: المبالغ المعروضة حالياً تجريبية لتجربة التطبيق، وليست أسعار سوق مباشرة.",
                modifier = Modifier.padding(14.dp),
                color = Ink,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BudgetCard(budget: Int, estimated: Int, remaining: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Forest, RoundedCornerShape(18.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ميزانيتك الأسبوعية", color = Color.White.copy(alpha = 0.82f))
            Text("DZD", color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Bold)
        }
        Text(budget.asDzd(), color = Color.White, style = MaterialTheme.typography.headlineLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("تكلفة الوجبات", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                Text(estimated.asDzd(), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("المتبقي من الميزانية", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                Text(remaining.asDzd(), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        LinearProgressIndicator(
            progress = { if (budget > 0) (estimated.toFloat() / budget).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = Tangerine,
            trackColor = Color.White.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun SummaryTile(
    modifier: Modifier,
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Forest, modifier = Modifier.size(22.dp))
            Column {
                Text(value, fontWeight = FontWeight.Bold, color = Ink)
                Text(label, color = MutedInk, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun Int.asDzd(): String =
    NumberFormat.getIntegerInstance(Locale("fr", "DZ")).format(this) + " دج"
