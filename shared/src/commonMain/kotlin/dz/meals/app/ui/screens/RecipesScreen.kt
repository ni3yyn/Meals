package dz.meals.app.ui.screens

import dz.meals.app.ui.components.MealsIconAction
import dz.meals.app.ui.components.MealsSecondaryButton
import dz.meals.app.ui.components.MealsTextButton

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import dz.meals.app.ui.components.MealsBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.ui.components.MealsPrimaryButton
import dz.meals.app.data.IngredientMaster
import dz.meals.app.data.MealRecipe
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen
import kotlin.math.roundToLong
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import dz.meals.app.ui.components.MealsSearchBar
import dz.meals.app.ui.components.MealsFilterChips
import dz.meals.app.ui.components.MealsSubtabsIndicator

private val recipeCategories = listOf("الكل", "اقتصادي", "سريع", "تقليدي", "عائلي")

@Composable
fun RecipesScreen(
    recipes: List<MealRecipe>,
    ingredients: List<IngredientMaster>,
    allergyIds: Set<String>,
    favoriteRecipeIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onAddRecipe: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("الكل") }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var selectedRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var cookingRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var cookingStepIndex by rememberSaveable { mutableStateOf(0) }
    var cookingServings by rememberSaveable { mutableStateOf(2) }
    val ingredientNames = ingredients.associateBy { it.id }

    val filtered = recipes.filter { recipe ->
        val aliases = recipe.ingredients.flatMap { ingredientNames[it.ingredientId]?.aliases.orEmpty() }
        recipe.allergens.intersect(allergyIds).isEmpty() &&
            (!showFavorites || recipe.id in favoriteRecipeIds) &&
            (category == "الكل" || recipe.category == category) &&
            (query.isBlank() || recipe.nameAr.contains(query.trim(), true) ||
                recipe.nameFr.contains(query.trim(), true) ||
                recipe.descriptionAr.contains(query.trim(), true) ||
                recipe.descriptionFr.contains(query.trim(), true) ||
                aliases.any { it.contains(query.trim(), true) })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("وصفات من مطبخنا", style = MaterialTheme.typography.headlineMedium)
            Text("ابحث بالعربية أو الفرنسية أو بأسماء المكونات المحلية.", color = MutedInk, style = MaterialTheme.typography.bodyMedium)
            MealsSearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = "ابحث عن وصفة أو مكوّن...",
                modifier = Modifier.fillMaxWidth()
            )
            MealsSubtabsIndicator(
                items = listOf("كل الوصفات", "المفضلة"),
                selected = if (showFavorites) "المفضلة" else "كل الوصفات",
                onSelect = { showFavorites = it == "المفضلة" }
            )
            MealsFilterChips(
                items = recipeCategories,
                selected = category,
                onSelect = { category = it },
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 122.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.id }) { recipe ->
                MealsCard(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { selectedRecipeId = recipe.id }
                    ),
                    containerColor = Paper,
                    borderColor = Border,
                    contentPadding = 14.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(SoftGreen, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                MealsIcon(
                                    MealsIconName.Meal,
                                    contentDescription = null,
                                    modifier = Modifier.size(33.dp)
                                )
                            }
                            Text(
                                text = recipe.nameAr,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink
                            )
                            MealsIconAction(onClick = { onToggleFavorite(recipe.id) }) {
                                MealsIcon(
                                    name = if (recipe.id in favoriteRecipeIds) MealsIconName.Star else MealsIconName.StarOutline,
                                    contentDescription = if (recipe.id in favoriteRecipeIds) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MealsIcon(
                                MealsIconName.Clock,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                recipe.durationMinutes.toString() + " د",
                                color = MutedInk,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                recipe.baseServings.toString() + " حصص",
                                color = MutedInk,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                recipe.category,
                                color = Forest,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MealsIconAction(onClick = { selectedRecipeId = recipe.id }) {
                                MealsIcon(
                                    MealsIconName.Info,
                                    contentDescription = "تفاصيل الوصفة",
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            MealsIconAction(onClick = { onAddRecipe(recipe.id) }) {
                                MealsIcon(
                                    MealsIconName.Add,
                                    contentDescription = "أضف إلى الأسبوع",
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (showFavorites) "لم تحفظ وصفات في المفضلة بعد."
                        else if (recipes.any { it.allergens.intersect(allergyIds).isNotEmpty() }) "لا توجد وصفات مطابقة. قد تكون قيود الحساسية تستبعد بعض النتائج."
                        else "لا توجد وصفات مطابقة لبحثك.",
                        color = MutedInk,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }

    val detail = selectedRecipeId?.let { id -> recipes.firstOrNull { it.id == id } }
    if (detail != null) {
        MealsBottomSheet(
            onDismissRequest = { selectedRecipeId = null },
            title = { Text(detail.nameAr) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text(detail.descriptionAr, color = MutedInk) }
                    item { Text("المكونات", fontWeight = FontWeight.Bold) }
                    items(detail.ingredients) { ingredient ->
                        val master = ingredientNames[ingredient.ingredientId]
                        Text(
                            "• " + (master?.nameAr ?: ingredient.ingredientId) +
                                " — " + ingredient.quantityBase.toString() + " " + ingredient.unit
                        )
                    }
                    item { Text("طريقة التحضير", fontWeight = FontWeight.Bold) }
                    items(detail.stepsAr) { step ->
                        Text((detail.stepsAr.indexOf(step) + 1).toString() + ". " + step)
                    }
                    item { Text("الحساسية: " + if (detail.allergens.isEmpty()) "لا توجد مسببات مسجلة" else detail.allergens.joinToString("، ")) }
                }
            },
            confirmButton = {
                MealsPrimaryButton(
                    label = "أضف للخطة",
                    onClick = { onAddRecipe(detail.id); selectedRecipeId = null },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                MealsSecondaryButton(
                    label = "ابدأ الطبخ",
                    onClick = {
                        cookingRecipeId = detail.id
                        cookingStepIndex = 0
                        cookingServings = detail.baseServings
                        selectedRecipeId = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    val cookingRecipe = cookingRecipeId?.let { id -> recipes.firstOrNull { it.id == id } }
    if (cookingRecipe != null) {
        val stepCount = cookingRecipe.stepsAr.size
        val safeStepIndex = cookingStepIndex.coerceIn(0, (stepCount - 1).coerceAtLeast(0))
        MealsBottomSheet(
            onDismissRequest = { cookingRecipeId = null },
            title = { Text(cookingRecipe.nameAr) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(
                            if (stepCount == 0) "لا توجد خطوات مسجلة بعد"
                            else "الخطوة " + (safeStepIndex + 1) + " من " + stepCount,
                            color = Forest,
                            fontWeight = FontWeight.Bold
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = {
                                if (stepCount == 0) 0f else (safeStepIndex + 1).toFloat() / stepCount.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                    item {
                        Text(
                            cookingRecipe.stepsAr.getOrNull(safeStepIndex) ?: "يمكنك مراجعة المكونات أو إنهاء وضع الطبخ.",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    item {
                        Text("عدد الحصص", fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MealsTextButton(
                                onClick = { cookingServings = (cookingServings - 1).coerceAtLeast(1) },
                                enabled = cookingServings > 1
                            ) { Text("−") }
                            Text(cookingServings.toString(), fontWeight = FontWeight.Bold)
                            MealsTextButton(
                                onClick = { cookingServings = (cookingServings + 1).coerceAtMost(12) },
                                enabled = cookingServings < 12
                            ) { Text("+") }
                        }
                    }
                    item { Text("الكميات لهذه الحصص", fontWeight = FontWeight.SemiBold) }
                    items(cookingRecipe.ingredients) { ingredient ->
                        val master = ingredientNames[ingredient.ingredientId]
                        val amount = ingredient.quantityBase * cookingServings.toDouble() /
                            cookingRecipe.baseServings.coerceAtLeast(1).toDouble()
                        val unitLabel = when (ingredient.unit) {
                            "g" -> "غ"
                            "ml" -> "مل"
                            "piece" -> "حبة"
                            else -> ingredient.unit
                        }
                        Text(
                            "• " + (master?.nameAr ?: ingredient.ingredientId) +
                                " — " + amount.prettyQuantity() + " " + unitLabel
                        )
                    }
                    if (cookingRecipe.stepsFr.getOrNull(safeStepIndex).orEmpty().isNotBlank()) {
                        item {
                            Text(
                                cookingRecipe.stepsFr[safeStepIndex],
                                color = MutedInk,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                MealsPrimaryButton(
                    label = if (safeStepIndex < stepCount - 1) "التالي" else "إنهاء",
                    onClick = {
                        if (safeStepIndex < stepCount - 1) {
                            cookingStepIndex = safeStepIndex + 1
                        } else {
                            cookingRecipeId = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                MealsSecondaryButton(
                    label = "السابق",
                    onClick = { cookingStepIndex = (safeStepIndex - 1).coerceAtLeast(0) },
                    enabled = safeStepIndex > 0,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

private fun Double.prettyQuantity(): String {
    if (this % 1.0 == 0.0) return toLong().toString()
    val roundedTenths = (this * 10.0).roundToLong()
    val whole = roundedTenths / 10
    val fraction = kotlin.math.abs(roundedTenths % 10)
    return "$whole.$fraction"
}
