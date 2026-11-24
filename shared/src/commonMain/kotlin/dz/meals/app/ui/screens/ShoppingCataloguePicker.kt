package dz.meals.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dz.meals.app.data.FoodCategory
import dz.meals.app.data.FoodSubcategory
import dz.meals.app.data.IngredientMaster
import dz.meals.app.data.foodMatchesSearch
import dz.meals.app.ui.components.MealsBottomSheet
import dz.meals.app.ui.components.MealsCard
import dz.meals.app.ui.components.MealsPressable
import dz.meals.app.ui.components.MealsSubtabsIndicator
import dz.meals.app.ui.components.MealsText as Text
import dz.meals.app.ui.components.MealsTextButton
import dz.meals.app.ui.components.MealsSearchBar
import dz.meals.app.ui.components.mealsSheetDismissAction
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.LocalMealsLocaleTag
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.WarmSurface
import kotlin.math.abs

/**
 * Canonical catalogue picker used by the shopping list.
 *
 * The content is a three-step horizontal flow: search, categories, then the
 * selected category's subcategory filters and foods. Food selection adds the
 * canonical catalogue UUID directly to the current shopping list.
 */
@Composable
fun ShoppingCataloguePicker(
    ingredients: List<IngredientMaster>,
    foodCategories: List<FoodCategory>,
    foodSubcategories: List<FoodSubcategory>,
    existingIngredientIds: Set<String>,
    onAddIngredient: (String) -> Unit,
    onAddCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    val localeTag = LocalMealsLocaleTag.current
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf(0) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSubcategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var justAddedIds by remember { mutableStateOf(emptySet<String>()) }

    val categoriesById = remember(foodCategories) { foodCategories.associateBy { it.id } }
    val subcategoriesById = remember(foodSubcategories) { foodSubcategories.associateBy { it.id } }
    val orderedCategories = remember(foodCategories) { foodCategories.sortedBy { it.sortOrder } }
    val selectedCategory = selectedCategoryId?.let(categoriesById::get)
    val selectedSubcategories = remember(foodSubcategories, selectedCategoryId) {
        foodSubcategories.filter { it.categoryId == selectedCategoryId }.sortedBy { it.sortOrder }
    }

    val searchResults = remember(ingredients, foodCategories, foodSubcategories, query, localeTag) {
        val term = query.trim()
        if (term.isBlank()) {
            emptyList()
        } else {
            ingredients.filter { food ->
                val category = categoriesById[food.categoryId]
                val subcategory = subcategoriesById[food.subcategoryId]
                foodMatchesSearch(
                    buildList {
                        add(food.nameAr)
                        add(food.nameEn)
                        add(food.nameFr)
                        addAll(food.aliases)
                        add(category?.nameAr.orEmpty())
                        add(category?.nameEn.orEmpty())
                        add(category?.nameFr.orEmpty())
                        add(subcategory?.nameAr.orEmpty())
                        add(subcategory?.nameEn.orEmpty())
                        add(subcategory?.nameFr.orEmpty())
                    },
                    term
                )
            }.sortedBy { pickerFoodName(it, localeTag).lowercase() }
        }
    }

    val categoryFoods = remember(ingredients, selectedCategoryId, selectedSubcategoryId, localeTag) {
        ingredients.asSequence()
            .filter { it.categoryId == selectedCategoryId }
            .filter { selectedSubcategoryId == null || it.subcategoryId == selectedSubcategoryId }
            .sortedBy { pickerFoodName(it, localeTag).lowercase() }
            .toList()
    }

    fun addFood(food: IngredientMaster) {
        if (food.id in existingIngredientIds || food.id in justAddedIds) return
        onAddIngredient(food.id)
        justAddedIds = justAddedIds + food.id
    }

    MealsBottomSheet(
        onDismissRequest = onDismiss,
        titleText = "إضافة إلى قائمة المشتريات",
        fillBody = true,
        text = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (page == 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MealsPressable(
                            onClick = {
                                page = 1
                                selectedSubcategoryId = null
                            },
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(WarmSurface, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                MealsIcon(
                                    if (layoutDirection == LayoutDirection.Rtl) MealsIconName.ArrowBack else MealsIconName.ArrowForward,
                                    contentDescription = "الرجوع إلى التصنيفات",
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                selectedCategory?.label(localeTag).orEmpty(),
                                color = Ink,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                translate = false
                            )
                            Text(
                                "${categoryFoods.size} مكوّناً",
                                color = MutedInk,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                } else {
                    MealsSubtabsIndicator(
                        items = listOf("بحث", "التصنيفات"),
                        selected = if (page == 0) "بحث" else "التصنيفات",
                        onSelect = { page = if (it == "بحث") 0 else 1 },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                AnimatedContent(
                    targetState = page,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(page, layoutDirection) {
                            var dragDistance = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, delta ->
                                    dragDistance += delta
                                },
                                onDragEnd = {
                                    val threshold = with(density) { 54.dp.toPx() }
                                    if (abs(dragDistance) >= threshold) {
                                        val towardLogicalNext = if (layoutDirection == LayoutDirection.Rtl) {
                                            dragDistance < 0f
                                        } else {
                                            dragDistance > 0f
                                        }
                                        when {
                                            page == 0 && towardLogicalNext -> page = 1
                                            page == 1 && !towardLogicalNext -> page = 0
                                            page == 2 && !towardLogicalNext -> page = 1
                                        }
                                    }
                                    dragDistance = 0f
                                },
                                onDragCancel = { dragDistance = 0f }
                            )
                        },
                    transitionSpec = {
                        val towardNext = targetState > initialState
                        (slideInHorizontally(tween(230)) { width ->
                            if (towardNext) width / 3 else -width / 3
                        } + fadeIn(tween(170))) togetherWith
                            (slideOutHorizontally(tween(190)) { width ->
                                if (towardNext) -width / 4 else width / 4
                            } + fadeOut(tween(130)))
                    },
                    label = "shoppingCatalogueBrowse"
                ) { activePage ->
                    when (activePage) {
                        0 -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MealsSearchBar(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = "ابحثي عن مكوّن أو منتج...",
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (query.isBlank()) {
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxWidth().padding(18.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "ابحثي بالاسم العربي أو الفرنسي أو الإنجليزي، أو اسحبي لعرض التصنيفات.",
                                            color = MutedInk,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                } else if (searchResults.isEmpty()) {
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxWidth().padding(18.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("لا توجد نتائج مطابقة.", color = MutedInk)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentPadding = PaddingValues(bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        items(searchResults, key = { it.id }) { food ->
                                            ShoppingCatalogueFoodRow(
                                                food = food,
                                                category = categoriesById[food.categoryId],
                                                localeTag = localeTag,
                                                alreadyAdded = food.id in existingIngredientIds || food.id in justAddedIds,
                                                onClick = { addFood(food) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "اختاري تصنيفاً لعرض مكوّناته.",
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentPadding = PaddingValues(bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    items(orderedCategories.chunked(2), key = { row -> row.joinToString { it.id } }) { categoryRow ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                            categoryRow.forEach { category ->
                                                val count = ingredients.count { it.categoryId == category.id }
                                                MealsPressable(
                                                    onClick = {
                                                        selectedCategoryId = category.id
                                                        selectedSubcategoryId = null
                                                        page = 2
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(17.dp)
                                                ) {
                                                    MealsCard(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        containerColor = Paper,
                                                        borderColor = null,
                                                        contentPadding = 15.dp,
                                                        shape = RoundedCornerShape(17.dp),
                                                        shadowElevation = 0.dp
                                                    ) {
                                                        Text(
                                                            category.label(localeTag),
                                                            color = Ink,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            translate = false
                                                        )
                                                        Text(
                                                            count.toString(),
                                                            color = MutedInk,
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                }
                                            }
                                            if (categoryRow.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ShoppingCatalogueFilterChip(
                                        label = "الكل",
                                        selected = selectedSubcategoryId == null,
                                        onClick = { selectedSubcategoryId = null }
                                    )
                                    selectedSubcategories.forEach { subcategory ->
                                        ShoppingCatalogueFilterChip(
                                            label = subcategory.label(localeTag),
                                            selected = selectedSubcategoryId == subcategory.id,
                                            onClick = { selectedSubcategoryId = subcategory.id }
                                        )
                                    }
                                }

                                if (categoryFoods.isEmpty()) {
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxWidth().padding(18.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "لا توجد مكوّنات في هذا التصنيف الفرعي حالياً.",
                                            color = MutedInk,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentPadding = PaddingValues(bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        items(categoryFoods, key = { it.id }) { food ->
                                            ShoppingCatalogueFoodRow(
                                                food = food,
                                                category = selectedCategory,
                                                localeTag = localeTag,
                                                alreadyAdded = food.id in existingIngredientIds || food.id in justAddedIds,
                                                onClick = { addFood(food) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        content = {
            val dismissSheet = mealsSheetDismissAction()
            MealsTextButton(
                onClick = { dismissSheet { onAddCustom() } },
                modifier = Modifier.fillMaxWidth()
            ) {
                MealsIcon(MealsIconName.Add, contentDescription = null, modifier = Modifier.size(21.dp))
                Text("العنصر غير موجود؟ أضيفيه يدوياً")
            }
        }
    )
}

@Composable
private fun ShoppingCatalogueFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    MealsPressable(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) WarmSurface else Paper.copy(alpha = 0.68f))
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            Text(
                label,
                color = if (selected) Ink else MutedInk,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                translate = false
            )
        }
    }
}

@Composable
private fun ShoppingCatalogueFoodRow(
    food: IngredientMaster,
    category: FoodCategory?,
    localeTag: String,
    alreadyAdded: Boolean,
    onClick: () -> Unit
) {
    MealsPressable(
        onClick = onClick,
        enabled = !alreadyAdded,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    pickerFoodName(food, localeTag),
                    color = if (alreadyAdded) MutedInk else Ink,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    translate = false
                )
                Text(
                    listOfNotNull(category?.label(localeTag), food.baseUnit.takeIf { it.isNotBlank() }).joinToString(" · "),
                    color = MutedInk,
                    style = MaterialTheme.typography.labelSmall,
                    translate = false
                )
            }
            if (alreadyAdded) {
                Text("في القائمة", color = MutedInk, style = MaterialTheme.typography.labelMedium)
                MealsIcon(MealsIconName.Check, contentDescription = "موجود في القائمة", modifier = Modifier.size(23.dp))
            } else {
                MealsIcon(MealsIconName.Add, contentDescription = "إضافة إلى القائمة", modifier = Modifier.size(25.dp))
            }
        }
    }
}

private fun pickerFoodName(food: IngredientMaster, localeTag: String): String = when {
    localeTag.startsWith("en") -> food.nameEn.ifBlank { food.nameAr }
    localeTag.startsWith("fr") -> food.nameFr.ifBlank { food.nameAr }
    else -> food.nameAr
}
