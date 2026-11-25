package dz.meals.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.meals.app.data.LocalCorpusRecipe
import dz.meals.app.data.LocalCorpusTranslation
import dz.meals.app.ui.components.MealsPrimaryButton
import dz.meals.app.ui.components.MealsSearchBar
import dz.meals.app.ui.components.MealsSecondaryButton
import dz.meals.app.ui.components.MealsText as Text
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.WarmSurface

@Composable
fun RecipeCorpusTesterScreen(
    recipes: List<LocalCorpusRecipe>,
    isLoading: Boolean,
    loadError: String?,
    translations: Map<String, LocalCorpusTranslation>,
    translatingKeys: Set<String>,
    translationErrors: Map<String, String>,
    onSelectRecipe: (LocalCorpusRecipe) -> Unit,
    onTranslate: (LocalCorpusRecipe) -> Unit,
    onClose: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var languageFilter by rememberSaveable { mutableStateOf("all") }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRecipe = recipes.firstOrNull { it.key == selectedKey }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                if (selectedKey != null) selectedKey = null else onClose()
            }) {
                Text(if (selectedKey != null) "رجوع إلى القائمة" else "إغلاق")
            }
            Text(
                "اختبار محلي · Debug",
                color = MutedInk,
                style = MaterialTheme.typography.labelMedium
            )
        }

        if (selectedRecipe != null) {
            RecipeCorpusDetail(
                recipe = selectedRecipe,
                translation = translations[selectedRecipe.key],
                isTranslating = selectedRecipe.key in translatingKeys,
                isCached = translations[selectedRecipe.key] != null,
                error = translationErrors[selectedRecipe.key],
                onTranslate = { onTranslate(selectedRecipe) },
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                "مجموعة الوصفات الأجنبية",
                color = Ink,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "تصفّح النسختين الإنجليزية والبولندية، ثم اختبر ترجمة وصفة كاملة إلى العربية.",
                color = MutedInk,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 5.dp, bottom = 14.dp)
            )

            MealsSearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = "ابحث باسم الوصفة أو المكوّن...",
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "all" to "الكل",
                    "en" to "English",
                    "pl" to "Polski"
                ).forEach { (value, label) ->
                    val selected = languageFilter == value
                    TextButton(onClick = { languageFilter = value }) {
                        Text(
                            text = label,
                            color = if (selected) Ink else MutedInk,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    recipes.size.toString() + " سجل لغوي",
                    color = MutedInk,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            HorizontalDivider(color = Border)

            val visibleRecipes = remember(recipes, query, languageFilter) {
                recipes.filter { recipe ->
                    (languageFilter == "all" || recipe.language == languageFilter) &&
                        (query.isBlank() ||
                            recipe.name.contains(query.trim(), ignoreCase = true) ||
                            recipe.summary.contains(query.trim(), ignoreCase = true) ||
                            recipe.ingredients.any { it.name.contains(query.trim(), ignoreCase = true) })
                }
            }

            when {
                isLoading -> {
                    Text(
                        "جارٍ تحميل بيانات الوصفات المحلية...",
                        color = MutedInk,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
                loadError != null -> {
                    Text(
                        loadError,
                        color = Ink,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
                visibleRecipes.isEmpty() -> {
                    Text(
                        "لا توجد نتائج مطابقة.",
                        color = MutedInk,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
                else -> {
                    Text(
                        visibleRecipes.size.toString() + " نتيجة",
                        color = MutedInk,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(visibleRecipes, key = { it.key }) { recipe ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedKey = recipe.key
                                        onSelectRecipe(recipe)
                                    }
                                    .padding(vertical = 14.dp)
                            ) {
                                Text(
                                    recipe.name.ifBlank { recipe.id },
                                    color = Ink,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    (if (recipe.language == "en") "ENGLISH" else "POLSKI") +
                                        " · " + recipe.category +
                                        if (recipe.machineTranslated) " · ترجمة آلية من البولندية" else "",
                                    color = MutedInk,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                                if (recipe.key in translations) {
                                    Text(
                                        "الترجمة العربية محفوظة على هذا الجهاز",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 5.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = Border)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeCorpusDetail(
    recipe: LocalCorpusRecipe,
    translation: LocalCorpusTranslation?,
    isTranslating: Boolean,
    isCached: Boolean,
    error: String?,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Paper)
            .verticalScroll(rememberScrollState())
    ) {
        val uriHandler = LocalUriHandler.current
        Text(
            recipe.name,
            color = Ink,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            (if (recipe.language == "en") "English" else "Polski") +
                " · " + recipe.cuisine + " · " + recipe.category,
            color = MutedInk,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 5.dp, bottom = 8.dp)
        )

        if (recipe.machineTranslated) {
            Text(
                "النص الإنجليزي في هذا المصدر مترجم آلياً من البولندية، وليس وصفة إنجليزية مستقلة.",
                color = MutedInk,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarmSurface)
                    .padding(12.dp)
            )
        }

        Text(
            "النص الأصلي",
            color = Ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp, bottom = 5.dp)
        )
        Text(recipe.summary, color = MutedInk, style = MaterialTheme.typography.bodyMedium)

        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            recipe.servings?.let { Text("$it حصص", color = MutedInk, style = MaterialTheme.typography.labelSmall) }
            recipe.prepMinutes?.let { Text("تحضير $it د", color = MutedInk, style = MaterialTheme.typography.labelSmall) }
            recipe.cookMinutes?.let { Text("طهي $it د", color = MutedInk, style = MaterialTheme.typography.labelSmall) }
        }

        if (recipe.ingredients.isNotEmpty()) {
            Text(
                "المكونات الأصلية",
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp, bottom = 5.dp)
            )
            recipe.ingredients.forEach { ingredient ->
                val amount = listOfNotNull(ingredient.quantity, ingredient.unit).joinToString(" ")
                Text(
                    if (amount.isBlank()) ingredient.name else amount + "  " + ingredient.name,
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        if (recipe.steps.isNotEmpty()) {
            Text(
                "الخطوات الأصلية",
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp, bottom = 5.dp)
            )
            recipe.steps.forEachIndexed { index, step ->
                Text(
                    (index + 1).toString() + ". " + step,
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }

        MealsPrimaryButton(
            label = when {
                isTranslating -> "جارٍ ترجمة الوصفة..."
                translation != null || isCached -> "الترجمة العربية محفوظة محلياً"
                else -> "ترجمة الوصفة إلى العربية"
            },
            onClick = onTranslate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            enabled = !isTranslating && translation == null
        )
        Text(
            "خدمة مؤقتة للاختبار فقط · بدون مفتاح API · حصة مجهولة محدودة يومياً",
            color = MutedInk,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
        )

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (translation != null) {
            HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 12.dp))
            Text(
                "الترجمة العربية",
                color = Ink,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (isCached) {
                Text(
                    "محفوظة محلياً · لن نطلب ترجمتها مجدداً عند فتحها",
                    color = MutedInk,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Text(
                translation.name,
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (translation.summary.isNotBlank()) {
                Text(
                    translation.summary,
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }

            Text(
                "المكونات",
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
            )
            recipe.ingredients.forEachIndexed { index, ingredient ->
                val translatedIngredient = translation.ingredients.getOrNull(index)
                val name = translatedIngredient?.name?.takeIf { it.isNotBlank() } ?: ingredient.name
                val amount = listOfNotNull(ingredient.quantity, ingredient.unit).joinToString(" ")
                Text(
                    if (amount.isBlank()) name else amount + "  " + name,
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
                translatedIngredient?.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(note, color = MutedInk, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(
                "طريقة التحضير",
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
            )
            translation.steps.forEachIndexed { index, step ->
                Text(
                    (index + 1).toString() + ". " + step,
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
        }

        HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 14.dp))
        Text("المصدر والترخيص", color = Ink, style = MaterialTheme.typography.titleMedium)
        Text(recipe.provenance.title, color = MutedInk, style = MaterialTheme.typography.bodySmall)
        Text(recipe.provenance.credit, color = MutedInk, style = MaterialTheme.typography.bodySmall)
        if (recipe.provenance.revisionUrl.isNotBlank()) {
            TextButton(onClick = { uriHandler.openUri(recipe.provenance.revisionUrl) }) {
                Text("فتح صفحة الوصفة الأصلية", color = Ink)
            }
        } else if (recipe.provenance.url.isNotBlank()) {
            TextButton(onClick = { uriHandler.openUri(recipe.provenance.url) }) {
                Text("فتح المصدر الأصلي", color = Ink)
            }
        }
        if (recipe.provenance.historyUrl.isNotBlank()) {
            TextButton(onClick = { uriHandler.openUri(recipe.provenance.historyUrl) }) {
                Text("سجل المساهمين", color = Ink)
            }
        }
        Text(
            "الترخيص: " + recipe.provenance.license.ifBlank { "CC BY-SA 4.0" },
            color = MutedInk,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            recipe.provenance.changes,
            color = MutedInk,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 3.dp, bottom = 22.dp)
        )
    }
}
