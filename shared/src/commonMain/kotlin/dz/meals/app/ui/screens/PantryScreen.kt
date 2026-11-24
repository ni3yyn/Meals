package dz.meals.app.ui.screens

import dz.meals.app.ui.components.MealsText as Text

import dz.meals.app.ui.components.MealsIconAction
import dz.meals.app.ui.theme.LocalMealsLocaleTag
import dz.meals.app.ui.theme.mealsText
import dz.meals.app.ui.theme.mealsUnitLabel
import dz.meals.app.ui.components.MealsSecondaryButton
import dz.meals.app.ui.components.MealsPrimaryButton
import dz.meals.app.ui.components.MealsTextButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import dz.meals.app.ui.components.MealsBottomSheet
import dz.meals.app.ui.components.mealsSheetDismissAction
import dz.meals.app.ui.components.MealsLiquidGlassTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dz.meals.app.ui.components.MealsCard
import dz.meals.app.ui.components.MealsCheckbox
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.ui.components.MealsIllustrationBadge
import dz.meals.app.data.FoodCategory
import dz.meals.app.data.FoodSubcategory
import dz.meals.app.data.IngredientMaster
import dz.meals.app.data.foodMatchesSearch
import dz.meals.app.data.PantryItem
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.shared.format.epochMillisToLocalDate
import dz.meals.app.shared.format.formatDateDmy
import dz.meals.app.shared.format.formatHouseholdQuantity
import dz.meals.app.shared.format.formatHouseholdUnit
import dz.meals.app.shared.format.localDateToEpochMillis
import dz.meals.app.shared.format.prettyQuantity
import kotlinx.datetime.LocalDate

@Composable
fun PantryScreen(
    ingredients: List<IngredientMaster>,
    pantry: List<PantryItem>,
    foodCategories: List<FoodCategory>,
    foodSubcategories: List<FoodSubcategory>,
    onSave: (String, Double?, Boolean, String, Long?) -> Unit,
    onRemove: (String) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSubcategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedIngredientId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingItem by rememberSaveable { mutableStateOf<PantryItem?>(null) }
    val appLocaleTag = LocalMealsLocaleTag.current
    val categoriesById = foodCategories.associateBy { it.id }
    val subcategoriesById = foodSubcategories.associateBy { it.id }
    val searchQuery = search.trim()
    val orderedCategories = foodCategories.sortedBy { it.sortOrder }
    val selectedCategory = selectedCategoryId?.let(categoriesById::get)
    val selectedSubcategories = foodSubcategories
        .filter { it.categoryId == selectedCategoryId }
        .sortedBy { it.sortOrder }
    val categoryFoods = ingredients
        .filter { it.categoryId == selectedCategoryId }
        .filter { selectedSubcategoryId == null || it.subcategoryId == selectedSubcategoryId }
        .sortedBy { pantryIngredientDisplayName(it, appLocaleTag).lowercase() }

    val selectedIngredient = selectedIngredientId?.let { id -> ingredients.firstOrNull { it.id == id } }
    val matchingIngredients = if (searchQuery.isBlank()) {
        emptyList()
    } else {
        ingredients.filter { ingredient ->
            val category = categoriesById[ingredient.categoryId]
            val subcategory = subcategoriesById[ingredient.subcategoryId]
            foodMatchesSearch(
                buildList {
                    add(ingredient.nameAr)
                    add(ingredient.nameEn)
                    add(ingredient.nameFr)
                    addAll(ingredient.aliases)
                    add(category?.nameAr.orEmpty())
                    add(category?.nameEn.orEmpty())
                    add(category?.nameFr.orEmpty())
                    add(subcategory?.nameAr.orEmpty())
                    add(subcategory?.nameEn.orEmpty())
                    add(subcategory?.nameFr.orEmpty())
                    add(mealsText(ingredient.nameAr, appLocaleTag))
                },
                searchQuery
            )
        }.sortedBy { pantryIngredientDisplayName(it, appLocaleTag).lowercase() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("المخزون المنزلي", style = MaterialTheme.typography.headlineMedium)
            Text("ابحث عن مكوّن لإضافته أو عدّل كمية موجودة في البيت.", color = MutedInk)
            MealsLiquidGlassTextField(
                value = search,
                onValueChange = { search = it.take(60) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { MealsIcon(MealsIconName.Search, contentDescription = null, modifier = Modifier.size(26.dp)) },
                label = { Text("ابحث بالعربية أو الإنجليزية أو الفرنسية أو الدارجة") },
                singleLine = true
            )
            Text(
                if (appLocaleTag.startsWith("en")) "${ingredients.size} searchable food identities in the catalogue."
                else "${ingredients.size} هوية غذائية قابلة للبحث في القاموس.",
                color = MutedInk,
                style = MaterialTheme.typography.bodySmall,
                translate = false
            )
            Text(
                "الكمية التقريبية لا تُخصم من المشتريات. تاريخ الانتهاء تذكير فقط وليس حكماً على سلامة الطعام.",
                color = MutedInk,
                style = MaterialTheme.typography.bodySmall
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 122.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (searchQuery.isNotBlank()) {
                item {
                    Text(
                        if (appLocaleTag.startsWith("en")) "Search results (${matchingIngredients.size})"
                        else "نتائج البحث (${matchingIngredients.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = dz.meals.app.ui.theme.TextAccent,
                        translate = false
                    )
                }
                items(matchingIngredients, key = { "search-" + it.id }) { ingredient ->
                    PantryIngredientRow(
                        ingredient = ingredient,
                        category = categoriesById[ingredient.categoryId],
                        subcategory = subcategoriesById[ingredient.subcategoryId],
                        localeTag = appLocaleTag,
                        onClick = {
                            editingItem = pantry.firstOrNull { it.ingredientId == ingredient.id }
                            selectedIngredientId = ingredient.id
                        }
                    )
                }
                if (matchingIngredients.isEmpty()) {
                    item {
                        Text(
                            if (appLocaleTag.startsWith("en")) "No matching foods in the catalogue."
                            else "لا توجد مكوّنات مطابقة في القاموس الحالي.",
                            color = MutedInk,
                            translate = false
                        )
                    }
                }
            } else if (selectedCategory == null) {
                item {
                    Text(
                        if (appLocaleTag.startsWith("en")) "Browse all ${foodCategories.size} food categories"
                        else "تصفّح التصنيفات الغذائية (${foodCategories.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = dz.meals.app.ui.theme.TextAccent,
                        translate = false
                    )
                }
                items(orderedCategories.chunked(2), key = { row -> "category-${row.first().id}" }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { category ->
                            val count = ingredients.count { it.categoryId == category.id }
                            MealsCard(
                                modifier = Modifier.weight(1f).clickable {
                                    selectedCategoryId = category.id
                                    selectedSubcategoryId = null
                                },
                                containerColor = Paper,
                                borderColor = Border,
                                contentPadding = 12.dp,
                                shape = RoundedCornerShape(15.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        category.label(appLocaleTag),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = dz.meals.app.ui.theme.TextAccent,
                                        translate = false
                                    )
                                    Text(
                                        if (appLocaleTag.startsWith("en")) "$count foods" else "$count مكوّن",
                                        color = MutedInk,
                                        style = MaterialTheme.typography.bodySmall,
                                        translate = false
                                    )
                                }
                            }
                        }
                        if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        MealsTextButton(
                            onClick = {
                                selectedCategoryId = null
                                selectedSubcategoryId = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MealsIcon(MealsIconName.ChevronRight, contentDescription = null, modifier = Modifier.size(19.dp))
                                Text(
                                    if (appLocaleTag.startsWith("en")) "All categories" else "كل التصنيفات",
                                    color = dz.meals.app.ui.theme.TextAccent,
                                    translate = false
                                )
                            }
                        }
                        Text(
                            selectedCategory.label(appLocaleTag),
                            style = MaterialTheme.typography.titleLarge,
                            color = dz.meals.app.ui.theme.TextAccent,
                            translate = false
                        )
                        Text(
                            if (appLocaleTag.startsWith("en")) "${categoryFoods.size} foods in this selection"
                            else "${categoryFoods.size} مكوّن في هذا الاختيار",
                            color = MutedInk,
                            style = MaterialTheme.typography.bodySmall,
                            translate = false
                        )
                    }
                }
                item {
                    Text(
                        if (appLocaleTag.startsWith("en")) "Subcategories"
                        else "التصنيفات الفرعية",
                        style = MaterialTheme.typography.titleMedium,
                        color = dz.meals.app.ui.theme.TextAccent,
                        translate = false
                    )
                }
                item {
                    MealsCard(
                        modifier = Modifier.fillMaxWidth().clickable { selectedSubcategoryId = null },
                        containerColor = Paper,
                        borderColor = if (selectedSubcategoryId == null) dz.meals.app.ui.theme.TextAccent else Border,
                        contentPadding = 11.dp,
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (appLocaleTag.startsWith("en")) "All in ${selectedCategory.label(appLocaleTag)}"
                                else "كل الأغذية ضمن ${selectedCategory.label(appLocaleTag)}",
                                color = MaterialTheme.colorScheme.onSurface,
                                translate = false
                            )
                            Text(
                                ingredients.count { it.categoryId == selectedCategory.id }.toString(),
                                color = MutedInk
                            )
                        }
                    }
                }
                items(selectedSubcategories, key = { "subcategory-${it.id}" }) { subcategory ->
                    val count = ingredients.count { it.subcategoryId == subcategory.id }
                    MealsCard(
                        modifier = Modifier.fillMaxWidth().clickable { selectedSubcategoryId = subcategory.id },
                        containerColor = Paper,
                        borderColor = if (selectedSubcategoryId == subcategory.id) dz.meals.app.ui.theme.TextAccent else Border,
                        contentPadding = 11.dp,
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(subcategory.label(appLocaleTag), color = MaterialTheme.colorScheme.onSurface, translate = false)
                            Text(count.toString(), color = MutedInk, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Text(
                        if (appLocaleTag.startsWith("en")) "Foods"
                        else "المكوّنات",
                        style = MaterialTheme.typography.titleMedium,
                        color = dz.meals.app.ui.theme.TextAccent,
                        translate = false
                    )
                }
                items(categoryFoods, key = { "browse-" + it.id }) { ingredient ->
                    PantryIngredientRow(
                        ingredient = ingredient,
                        category = categoriesById[ingredient.categoryId],
                        subcategory = subcategoriesById[ingredient.subcategoryId],
                        localeTag = appLocaleTag,
                        onClick = {
                            editingItem = pantry.firstOrNull { it.ingredientId == ingredient.id }
                            selectedIngredientId = ingredient.id
                        }
                    )
                }
                if (categoryFoods.isEmpty()) {
                    item {
                        Text(
                            if (appLocaleTag.startsWith("en")) "No foods are assigned to this selection yet."
                            else "لا توجد مكوّنات مسندة إلى هذا التصنيف حالياً.",
                            color = MutedInk,
                            translate = false
                        )
                    }
                }
            }

            item {
                Text("المخزون الحالي (" + pantry.size + ")", style = MaterialTheme.typography.titleMedium, color = dz.meals.app.ui.theme.TextAccent)
            }

            if (pantry.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MealsIllustrationBadge(icon = MealsIconName.Pantry, size = 64.dp)
                        Text("مخزونك فارغ", style = MaterialTheme.typography.titleLarge)
                        Text("اكتب اسم مكوّن في البحث ثم اختره لإضافته.", color = MutedInk)
                    }
                }
            } else {
                items(pantry, key = { it.id }) { item ->
                    val expiry = item.expiryAtEpochMillis?.let {
                        formatDateDmy(epochMillisToLocalDate(it))
                    }
                    MealsCard(containerColor = Paper, borderColor = Border, contentPadding = 13.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.nameAr, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    if (item.isApproximate) "كمية تقريبية — لم تُخصم من حساب المشتريات"
                                    else (item.quantityBase ?: 0.0).formatHouseholdQuantity(item.unit),
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                item.note.takeIf { it.isNotBlank() }?.let {
                                    Text("ملاحظة: " + it, color = MutedInk, style = MaterialTheme.typography.bodySmall)
                                }
                                expiry?.let {
                                    Text("تاريخ الانتهاء المُدخل: $it", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MealsIconAction(
                                    onClick = {
                                        editingItem = item
                                        selectedIngredientId = item.ingredientId
                                    }
                                ) {
                                    MealsIcon(MealsIconName.Edit, contentDescription = "تعديل المخزون", modifier = Modifier.size(30.dp))
                                }
                                MealsIconAction(onClick = { onRemove(item.id) }) {
                                    MealsIcon(MealsIconName.Delete, contentDescription = "حذف", modifier = Modifier.size(30.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedIngredient != null) {
        PantryAddDialog(
            ingredient = selectedIngredient,
            existing = editingItem,
            onDismiss = {
                selectedIngredientId = null
                editingItem = null
            },
            onSave = { amount, approximate, note, expiry ->
                onSave(editingItem?.id ?: selectedIngredient.id, amount, approximate, note, expiry)
                selectedIngredientId = null
                editingItem = null
                search = ""
            }
        )
    }
}

private fun pantryIngredientDisplayName(ingredient: IngredientMaster, localeTag: String): String = when {
    localeTag.startsWith("en", ignoreCase = true) -> ingredient.nameEn.ifBlank { ingredient.nameAr }
    localeTag.startsWith("fr", ignoreCase = true) -> ingredient.nameFr.ifBlank { ingredient.nameAr }
    else -> ingredient.nameAr
}

@Composable
private fun PantryIngredientRow(
    ingredient: IngredientMaster,
    category: FoodCategory?,
    subcategory: FoodSubcategory?,
    localeTag: String,
    onClick: () -> Unit
) {
    MealsTextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    pantryIngredientDisplayName(ingredient, localeTag),
                    color = MaterialTheme.colorScheme.onSurface,
                    translate = false
                )
                Text(
                    listOfNotNull(
                        category?.label(localeTag),
                        subcategory?.label(localeTag),
                        mealsUnitLabel(ingredient.baseUnit, localeTag)
                    ).joinToString(" · "),
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall,
                    translate = false
                )
            }
            MealsIcon(MealsIconName.Add, contentDescription = "إضافة أو تعديل", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun PantryAddDialog(
    ingredient: IngredientMaster,
    existing: PantryItem?,
    onDismiss: () -> Unit,
    onSave: (Double?, Boolean, String, Long?) -> Unit
) {
    var quantity by rememberSaveable(ingredient.id, existing?.id) {
        mutableStateOf(existing?.quantityBase?.toString().orEmpty())
    }
    var approximate by rememberSaveable(ingredient.id, existing?.id) {
        mutableStateOf(existing?.isApproximate ?: false)
    }
    var note by rememberSaveable(ingredient.id, existing?.id) {
        mutableStateOf(existing?.note.orEmpty())
    }
    var expiryText by rememberSaveable(ingredient.id, existing?.id) {
        mutableStateOf(
            existing?.expiryAtEpochMillis?.let {
                epochMillisToLocalDate(it).toString()
            }.orEmpty()
        )
    }

    val parsedExpiry = if (expiryText.isBlank()) {
        null
    } else {
        runCatching {
            localDateToEpochMillis(LocalDate.parse(expiryText.trim()))
        }.getOrNull()
    }
    val expiryIsValid = expiryText.isBlank() || parsedExpiry != null

    MealsBottomSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "إضافة " + ingredient.nameAr else "تعديل " + ingredient.nameAr) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("الوحدة: " + formatHouseholdUnit(ingredient.baseUnit), color = MutedInk)
                MealsLiquidGlassTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("الكمية (" + formatHouseholdUnit(ingredient.baseUnit) + ")") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !approximate,
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MealsCheckbox(checked = approximate, onCheckedChange = { approximate = it })
                    Text("لدي كمية تقريبية فقط")
                }
                MealsLiquidGlassTextField(
                    value = expiryText,
                    onValueChange = { expiryText = it.take(10) },
                    label = { Text("تاريخ الانتهاء (YYYY-MM-DD، اختياري)") },
                    singleLine = true,
                    isError = !expiryIsValid
                )
                MealsLiquidGlassTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    label = { Text("ملاحظة أو مكان التخزين (اختياري)") },
                    singleLine = true
                )
                Text(
                    "التاريخ تذكير أدخله المستخدم فقط، وليس حكماً على سلامة الطعام.",
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            val dismissSheet = mealsSheetDismissAction()
            MealsPrimaryButton(
                label = "حفظ",
                onClick = {
                    val exactQuantity = if (approximate) null else quantity.replace(',', '.').toDoubleOrNull()
                    dismissSheet { onSave(exactQuantity, approximate, note.trim(), parsedExpiry) }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = expiryIsValid && (approximate || ((quantity.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0))
            )
        },
        dismissButton = {
            val dismissSheet = mealsSheetDismissAction()
            MealsSecondaryButton(
                label = "إلغاء",
                onClick = { dismissSheet { onDismiss() } },
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

