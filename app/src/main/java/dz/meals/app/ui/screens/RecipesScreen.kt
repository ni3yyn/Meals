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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import dz.meals.app.data.MealRecipe
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen
import dz.meals.app.ui.theme.Tangerine
import java.text.NumberFormat
import java.util.Locale

private val recipeCategories = listOf("الكل", "اقتصادي", "سريع", "تقليدي", "عائلي")

@Composable
fun RecipesScreen(
    recipes: List<MealRecipe>,
    plannedRecipeIds: Set<String>,
    onToggleRecipe: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("الكل") }
    val filtered = recipes.filter { recipe ->
        (category == "الكل" || recipe.category == category) &&
            (query.isBlank() || recipe.name.contains(query.trim(), ignoreCase = true) ||
                recipe.description.contains(query.trim(), ignoreCase = true))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("وصفات من مطبخنا", style = MaterialTheme.typography.headlineMedium)
            Text("أفكار بسيطة بمكوّنات مألوفة في الجزائر.", color = MutedInk, style = MaterialTheme.typography.bodyMedium)
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
                        if (query.isEmpty()) Text("ابحث عن وصفة...", color = MutedInk)
                        inner()
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recipeCategories.forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item) }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.id }) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    isPlanned = recipe.id in plannedRecipeIds,
                    onToggle = { onToggleRecipe(recipe.id) }
                )
            }
            if (filtered.isEmpty()) {
                item { Text("لا توجد وصفات مطابقة لبحثك.", color = MutedInk, modifier = Modifier.padding(16.dp)) }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun RecipeCard(recipe: MealRecipe, isPlanned: Boolean, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).background(SoftGreen, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = Forest, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(recipe.name, style = MaterialTheme.typography.titleLarge)
                    Text(recipe.description, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = MutedInk, modifier = Modifier.size(16.dp))
                    Text(recipe.durationMinutes.toString() + " د", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                }
                Text(recipe.servings.toString() + " حصص", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(recipe.estimatedCostDzd.asDzd(), color = Forest, fontWeight = FontWeight.Bold)
            }
            Text(
                "المكوّنات: " + recipe.ingredients.take(4).joinToString("، ") { it.name } +
                    if (recipe.ingredients.size > 4) "..." else "",
                color = MutedInk,
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlanned) SoftGreen else Forest,
                    contentColor = if (isPlanned) Forest else Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isPlanned) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text("أُضيفت إلى الخطة — إزالة")
                } else {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("أضف إلى خطة الأسبوع")
                }
            }
        }
    }
}

private fun Int.asDzd(): String =
    NumberFormat.getIntegerInstance(Locale("fr", "DZ")).format(this) + " دج"
