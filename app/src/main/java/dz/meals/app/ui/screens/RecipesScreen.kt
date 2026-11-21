package dz.meals.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.meals.app.data.IngredientMaster
import dz.meals.app.data.MealRecipe
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen

private val recipeCategories = listOf("الكل", "المفضلة", "اقتصادي", "سريع", "تقليدي", "عائلي")

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
    var selectedRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var cookingRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var cookingStepIndex by rememberSaveable { mutableStateOf(0) }
    var cookingServings by rememberSaveable { mutableStateOf(2) }
    val ingredientNames = ingredients.associateBy { it.id }

    val filtered = recipes.filter { recipe ->
        val aliases = recipe.ingredients.flatMap { ingredientNames[it.ingredientId]?.aliases.orEmpty() }
        recipe.allergens.intersect(allergyIds).isEmpty() &&
            (category == "الكل" ||
                (category == "المفضلة" && recipe.id in favoriteRecipeIds) ||
                (category != "المفضلة" && recipe.category == category)) &&
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
            Row(
                modifier = Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = MutedInk)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("ابحث عن وصفة أو مكوّن...", color = MutedInk)
                        inner()
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recipeCategories.forEach { item ->
                    FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.id }) { recipe ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Paper),
                    border = BorderStroke(1.dp, Border),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.size(48.dp).background(SoftGreen, RoundedCornerShape(13.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = Forest, modifier = Modifier.size(24.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(recipe.nameAr, style = MaterialTheme.typography.titleLarge)
                                Text(recipe.descriptionAr, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
                                Text(recipe.nameFr, color = MutedInk, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onToggleFavorite(recipe.id) }) {
                                Icon(
                                    if (recipe.id in favoriteRecipeIds) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (recipe.id in favoriteRecipeIds) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                                    tint = Forest
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = MutedInk, modifier = Modifier.size(16.dp))
                            Text(recipe.durationMinutes.toString() + " د", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                            Text(recipe.baseServings.toString() + " حصص", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            Text(recipe.category, color = Forest, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "المكونات: " + recipe.ingredients.take(5).joinToString("، ") {
                                ingredientNames[it.ingredientId]?.nameAr ?: it.ingredientId
                            } + if (recipe.ingredients.size > 5) "..." else "",
                            color = MutedInk,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onAddRecipe(recipe.id) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Forest),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Text("أضف إلى الأسبوع")
                            }
                            TextButton(onClick = { selectedRecipeId = recipe.id }) {
                                Icon(Icons.Outlined.Info, contentDescription = null)
                                Text("التفاصيل")
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (category == "المفضلة") "لم تحفظ وصفات في المفضلة بعد."
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
        AlertDialog(
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onAddRecipe(detail.id); selectedRecipeId = null }) {
                        Text("أضف إلى الأسبوع")
                    }
                    TextButton(
                        onClick = {
                            cookingRecipeId = detail.id
                            cookingStepIndex = 0
                            cookingServings = detail.baseServings
                            selectedRecipeId = null
                        }
                    ) {
                        Text("ابدأ الطبخ")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedRecipeId = null }) { Text("إغلاق") }
            }
        )
    }

    val cookingRecipe = cookingRecipeId?.let { id -> recipes.firstOrNull { it.id == id } }
    if (cookingRecipe != null) {
        val stepCount = cookingRecipe.stepsAr.size
        val safeStepIndex = cookingStepIndex.coerceIn(0, (stepCount - 1).coerceAtLeast(0))
        AlertDialog(
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
                            TextButton(
                                onClick = { cookingServings = (cookingServings - 1).coerceAtLeast(1) },
                                enabled = cookingServings > 1
                            ) { Text("−") }
                            Text(cookingServings.toString(), fontWeight = FontWeight.Bold)
                            TextButton(
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
                TextButton(
                    onClick = {
                        if (safeStepIndex < stepCount - 1) {
                            cookingStepIndex = safeStepIndex + 1
                        } else {
                            cookingRecipeId = null
                        }
                    }
                ) {
                    Text(if (safeStepIndex < stepCount - 1) "الخطوة التالية" else "إنهاء الطبخ")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { cookingStepIndex = (safeStepIndex - 1).coerceAtLeast(0) },
                        enabled = safeStepIndex > 0
                    ) { Text("السابق") }
                    TextButton(onClick = { cookingRecipeId = null }) { Text("إغلاق") }
                }
            }
        )
    }
}

private fun Double.prettyQuantity(): String =
    if (this % 1.0 == 0.0) toLong().toString()
    else String.format(java.util.Locale.ROOT, "%.1f", this)
}
