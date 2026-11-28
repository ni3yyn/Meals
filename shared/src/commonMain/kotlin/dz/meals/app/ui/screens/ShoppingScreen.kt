package dz.meals.app.ui.screens

import dz.meals.app.ui.components.MealsText as Text
import dz.meals.app.ui.theme.LocalMealsLocaleTag
import dz.meals.app.ui.theme.bidiIsolate
import dz.meals.app.ui.theme.mealsText
import dz.meals.app.ui.theme.logicalAnimationDirectionSign

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import dz.meals.app.ui.components.MealsGlassDropdown
import androidx.compose.material3.MaterialTheme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dz.meals.app.ui.components.MealsBottomSheet
import dz.meals.app.ui.components.mealsSheetDismissAction
import dz.meals.app.ui.components.MealsChoiceTile
import dz.meals.app.ui.components.MealsFilterChips
import dz.meals.app.ui.components.MealsCard
import dz.meals.app.ui.components.MealsCheckbox
import dz.meals.app.ui.components.MealsGlassSurface
import dz.meals.app.ui.components.MealsIconButton
import dz.meals.app.ui.components.MealsIllustrationBadge
import dz.meals.app.ui.components.MealsLiquidGlassTextField
import dz.meals.app.ui.components.MealsPrimaryButton
import dz.meals.app.ui.components.MealsPressable
import dz.meals.app.ui.components.MealsLiquidGlassProgressBar
import dz.meals.app.ui.components.MealsSearchBar
import dz.meals.app.ui.components.MealsSecondaryButton
import dz.meals.app.ui.components.MealsSubtabsIndicator
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.data.IngredientMaster
import dz.meals.app.data.FoodCatalogueSearchIndex
import dz.meals.app.data.FoodCatalogueSearchResult
import dz.meals.app.data.getFoodCatalogueSearchIndex
import dz.meals.app.data.searchFoodCatalogue
import dz.meals.app.data.MealsSnapshot
import dz.meals.app.data.PriceObservationSummary
import dz.meals.app.data.ShoppingListItem
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.BodyInk
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.WarmSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import dz.meals.app.shared.format.epochMillisToLocalDate
import dz.meals.app.shared.format.formatDateDmy
import dz.meals.app.shared.format.formatDzd
import dz.meals.app.shared.format.formatHouseholdQuantity
import dz.meals.app.shared.format.formatHouseholdUnit
import dz.meals.app.shared.format.formatPriceInputUnit
import dz.meals.app.shared.format.prettyQuantity

@Composable
fun ShoppingScreen(
    snapshot: MealsSnapshot,
    ingredients: List<IngredientMaster>,
    wilaya: String,
    commune: String,
    shoppingChannel: String,
    preferredShop: String,
    onToggleItem: (String, Boolean) -> Unit,
    onAddCustomItem: (String, Double, String, Double?) -> Unit,
    onAddCatalogueItem: (String) -> Unit,
    onEditCustomItem: (String, String, Double, String) -> Unit,
    onUpdateItemDetails: (String, Double?, String, Boolean, Double?) -> Unit,
    onDeleteCustomItem: (String) -> Unit,
    onRecordPrice: (String, Double, Double, String) -> Unit,
    onShareList: (String) -> Unit,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit
) {
    val appLocaleTag = LocalMealsLocaleTag.current
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var browsePage by rememberSaveable { mutableStateOf(0) }
    var browseDirection by remember { mutableStateOf(1) }
    var selectedBrowseCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSubcategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var locallyAddedIngredientIds by remember { mutableStateOf(emptySet<String>()) }
    var locallyRemovedIngredientIds by remember { mutableStateOf(emptySet<String>()) }
    var showPurchased by rememberSaveable { mutableStateOf(false) }
    var showPriceHistory by rememberSaveable { mutableStateOf(false) }
    var editItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var priceDialog by rememberSaveable { mutableStateOf(false) }
    var itemName by rememberSaveable { mutableStateOf("") }
    var itemEstimatedPrice by rememberSaveable { mutableStateOf("") }
    var editName by rememberSaveable { mutableStateOf("") }
    var editQuantity by rememberSaveable { mutableStateOf("1") }
    var editUnit by rememberSaveable { mutableStateOf("حبة") }
    var editPurchaseQuantity by rememberSaveable { mutableStateOf("") }
    var editStoreNote by rememberSaveable { mutableStateOf("") }
    var editActualSpent by rememberSaveable { mutableStateOf("") }
    var editUnavailable by rememberSaveable { mutableStateOf(false) }
    var editUserAdded by rememberSaveable { mutableStateOf(false) }
    var addedFeedback by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(addedFeedback) {
        if (addedFeedback != null) {
            delay(1500)
            addedFeedback = null
        }
    }

    val rows = snapshot.shoppingItems
    val checkedCount = rows.count { it.checked }
    val remainingCount = rows.size - checkedCount
    val visibleRows = remember(rows, showPurchased) {
        if (showPurchased) rows else rows.filterNot { it.checked }
    }
    val categoriesById = remember(snapshot.foodCategories) { snapshot.foodCategories.associateBy { it.id } }
    val subcategoriesById = remember(snapshot.foodSubcategories) { snapshot.foodSubcategories.associateBy { it.id } }
    // Group once per shopping-list revision. The main list and category sheets
    // both read the same groups, avoiding repeated full-list filters on open.
    val visibleRowsByCategory = remember(visibleRows) {
        visibleRows.groupBy { it.categoryId }
    }
    val shoppingRowsByIngredientId = remember(rows) {
        rows.mapNotNull { item ->
            item.ingredientId?.let { ingredientId -> ingredientId to item }
        }.toMap()
    }
    val alreadyAddedIngredientIds = shoppingRowsByIngredientId.keys
    var catalogueSearchIndex by remember { mutableStateOf<FoodCatalogueSearchIndex?>(null) }
    var searchResults by remember { mutableStateOf<FoodCatalogueSearchResult?>(null) }
    val visibleSearchResults = searchResults

    // Reconcile optimistic catalogue taps once the repository snapshot catches up.
    LaunchedEffect(rows) {
        locallyAddedIngredientIds = locallyAddedIngredientIds - alreadyAddedIngredientIds
        locallyRemovedIngredientIds = locallyRemovedIngredientIds.intersect(alreadyAddedIngredientIds)
    }

    fun isCatalogueIngredientAdded(ingredientId: String): Boolean =
        ingredientId in locallyAddedIngredientIds ||
            (ingredientId in alreadyAddedIngredientIds && ingredientId !in locallyRemovedIngredientIds)

    fun toggleCatalogueIngredient(ingredientId: String, displayName: String) {
        val existingRow = shoppingRowsByIngredientId[ingredientId]
        if (isCatalogueIngredientAdded(ingredientId)) {
            // Catalogue rows added by the user are removable; generated plan rows remain managed by the plan.
            if (existingRow?.userAdded != true) return
            locallyRemovedIngredientIds = locallyRemovedIngredientIds + ingredientId
            locallyAddedIngredientIds = locallyAddedIngredientIds - ingredientId
            onDeleteCustomItem(existingRow.id)
        } else {
            locallyRemovedIngredientIds = locallyRemovedIngredientIds - ingredientId
            locallyAddedIngredientIds = locallyAddedIngredientIds + ingredientId
            addedFeedback = displayName
            onAddCatalogueItem(ingredientId)
        }
    }

    fun navigateBackInBrowse() {
        when (browsePage) {
            2 -> {
                browseDirection = -1
                browsePage = 1
            }
            1 -> {
                browseDirection = -1
                browsePage = 0
                selectedBrowseCategoryId = null
                selectedSubcategoryId = null
            }
        }
    }

    backHandler(
        browsePage > 0 && !addDialog && selectedCategoryId == null &&
            editItemId == null && !priceDialog,
        ::navigateBackInBrowse
    )

    // Build the KMP inverted index once when the catalogue itself changes.
    // Search data is locale-neutral; changing the UI language must not rebuild it.
    LaunchedEffect(ingredients, snapshot.foodCategories, snapshot.foodSubcategories) {
        catalogueSearchIndex = null
        searchResults = null
        val newIndex = withContext(Dispatchers.Default) {
            getFoodCatalogueSearchIndex(
                ingredients = ingredients,
                categories = snapshot.foodCategories,
                subcategories = snapshot.foodSubcategories
            )
        }
        catalogueSearchIndex = newIndex
    }

    // Debounce typing, then search the immutable normalized index in the background.
    // LaunchedEffect cancellation prevents an older query from overwriting newer results.
    LaunchedEffect(searchQuery, catalogueSearchIndex) {
        val query = searchQuery
        val index = catalogueSearchIndex
        if (query.isBlank() || query.trim().length < 2 || index == null) {
            searchResults = null
            return@LaunchedEffect
        }

        delay(70)
        val results = withContext(Dispatchers.Default) {
            searchFoodCatalogue(index, query, resultLimit = 40)
        }
        searchResults = results
    }
    val categoryIds = remember(visibleRowsByCategory, categoriesById) {
        visibleRowsByCategory.keys.sortedWith(
            compareBy<String> { categoriesById[it]?.sortOrder ?: Int.MAX_VALUE }.thenBy { it }
        )
    }

    val layoutDirection = LocalLayoutDirection.current
    val orderedFoodCategories = remember(snapshot.foodCategories) {
        snapshot.foodCategories.sortedBy { it.sortOrder }
    }
    val selectedBrowseCategory = selectedBrowseCategoryId?.let(categoriesById::get)
    val browseSubcategories = remember(snapshot.foodSubcategories, selectedBrowseCategoryId) {
        snapshot.foodSubcategories
            .filter { it.categoryId == selectedBrowseCategoryId }
            .sortedBy { it.sortOrder }
    }
    val allSubcategoriesLabel = when {
        appLocaleTag.startsWith("en") -> "All"
        appLocaleTag.startsWith("fr") -> "Tout"
        else -> "الكل"
    }
    val selectedSubcategoryLabel = selectedSubcategoryId
        ?.let(subcategoriesById::get)
        ?.label(appLocaleTag)
        ?: allSubcategoriesLabel
    // Category lists come from the pre-grouped index. Locale sorting only touches
    // the selected category (not the full catalogue), and subcategory filtering
    // touches that category's already-small list.
    val browseFoods = remember(catalogueSearchIndex, selectedBrowseCategoryId, selectedSubcategoryId, appLocaleTag) {
        val categoryFoods = catalogueSearchIndex
            ?.ingredientsByCategory
            ?.get(selectedBrowseCategoryId)
            .orEmpty()
        val filtered = if (selectedSubcategoryId == null) {
            categoryFoods
        } else {
            categoryFoods.filter { it.subcategoryId == selectedSubcategoryId }
        }
        filtered.sortedBy { food ->
            when {
                appLocaleTag.startsWith("en") -> food.nameEn.ifBlank { food.nameAr }.lowercase()
                appLocaleTag.startsWith("fr") -> food.nameFr.ifBlank { food.nameAr }.lowercase()
                else -> food.nameAr.lowercase()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MealsSearchBar(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    searchResults = null
                    if (browsePage != 0) {
                        browseDirection = -1
                        browsePage = 0
                    }
                },
                placeholder = when {
                    appLocaleTag.startsWith("en") -> "Search ingredients..."
                    appLocaleTag.startsWith("fr") -> "Rechercher un ingrédient..."
                    else -> "ابحث عن مكوّن أو منتج..."
                },
                modifier = Modifier.weight(1f),
                textFieldModifier = Modifier.testTag("shopping.search")
            )
            MealsSecondaryButton(
                label = if (browsePage == 0) {
                    when {
                        appLocaleTag.startsWith("en") -> "Categories"
                        appLocaleTag.startsWith("fr") -> "Catégories"
                        else -> "التصنيفات"
                    }
                } else {
                    when {
                        appLocaleTag.startsWith("en") -> "Shopping"
                        appLocaleTag.startsWith("fr") -> "Achats"
                        else -> "المشتريات"
                    }
                },
                onClick = {
                    searchQuery = ""
                    searchResults = null
                    if (browsePage == 0) {
                        browseDirection = 1
                        browsePage = 1
                    } else {
                        browseDirection = -1
                        browsePage = 0
                    }
                },
                modifier = Modifier.width(108.dp).height(50.dp).testTag("shopping.categories")
            )
        }

        AnimatedContent(
            targetState = browsePage,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val movingForward = targetState > initialState
                val directionSign = logicalAnimationDirectionSign(layoutDirection)
                (slideInHorizontally(
                    animationSpec = spring<IntOffset>(dampingRatio = 0.82f, stiffness = 390f),
                    initialOffsetX = { width ->
                        (if (movingForward) width / 5 else -width / 5) * directionSign
                    }
                ) + fadeIn(animationSpec = tween(155, delayMillis = 18))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = spring(dampingRatio = 0.94f, stiffness = 560f),
                        targetOffsetX = { width ->
                            (if (movingForward) -width / 12 else width / 12) * directionSign
                        }
                    ) + fadeOut(animationSpec = tween(125)))
            },
            label = "shoppingBrowsePages"
        ) { activePage ->
            when (activePage) {
                0 -> {
        LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 14.dp,
            bottom = 124.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "shopping-summary") {
            MealsGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 19.dp,
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "المبلغ المتوقع",
                            color = dz.meals.app.ui.theme.TextAccent,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            snapshot.basket.purchaseOutlayMinDzd?.let { minimum ->
                                val maximum = snapshot.basket.purchaseOutlayMaxDzd
                                if (maximum != null && minimum != maximum) {
                                    minimum.toInt().formatDzd() + " – " + maximum.toInt().formatDzd()
                                } else snapshot.basket.purchaseOutlayDzd.toInt().formatDzd()
                            } ?: snapshot.basket.purchaseOutlayDzd.toInt().formatDzd(),
                            color = dz.meals.app.ui.theme.Ink,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 44.dp)
                            .background(Border.copy(alpha = 0.9f))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "تم جمعه",
                            color = dz.meals.app.ui.theme.TextAccent,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            "$checkedCount / ${rows.size}",
                            color = dz.meals.app.ui.theme.Ink,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }

                if (rows.isNotEmpty()) {
                    MealsLiquidGlassProgressBar(
                        progress = checkedCount.toFloat() / rows.size.toFloat(),
                        modifier = Modifier.padding(top = 2.dp),
                        height = 14.dp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (remainingCount == 0) "اكتملت القائمة" else "$remainingCount عناصر متبقية",
                            color = dz.meals.app.ui.theme.BodyInk,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${((checkedCount.toFloat() / rows.size.toFloat()) * 100).toInt()}%",
                            color = MutedInk,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    Text(
                        "ستظهر هنا تكلفة مشتريات خطتك عند إضافة الوصفات.",
                        color = MutedInk,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (snapshot.basket.actualSpendDzd > 0.0) {
                    Text(
                        "المدفوع فعلياً: ${snapshot.basket.actualSpendDzd.toInt().formatDzd()}",
                        color = dz.meals.app.ui.theme.BodyInk,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (snapshot.basket.hasDemoPrices || snapshot.basket.unknownPriceCount > 0) {
                    Text(
                        text = when {
                            snapshot.basket.hasDemoPrices && snapshot.basket.unknownPriceCount > 0 ->
                                "الأسعار تقديرية · ${snapshot.basket.unknownPriceCount} دون سعر"
                            snapshot.basket.hasDemoPrices ->
                                "الأسعار تقديرية وليست مباشرة"
                            else ->
                                "${snapshot.basket.unknownPriceCount} عناصر دون سعر"
                        },
                        color = dz.meals.app.ui.theme.BodyInk,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Keep the utility actions visually anchored inside the summary surface.
                // In Arabic RTL, Arrangement.End places this compact action rail at bottom-left.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MealsIconButton(
                        onClick = { onShareList(buildShoppingShareText(rows, snapshot, appLocaleTag)) },
                        size = 42.dp
                    ) {
                        MealsIcon(
                            MealsIconName.Share,
                            contentDescription = when {
                                appLocaleTag.startsWith("en") -> "Share shopping list"
                                appLocaleTag.startsWith("fr") -> "Partager la liste"
                                else -> "مشاركة قائمة المشتريات"
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    MealsIconButton(
                        onClick = { priceDialog = true },
                        size = 42.dp
                    ) {
                        MealsIcon(
                            MealsIconName.Wallet,
                            contentDescription = when {
                                appLocaleTag.startsWith("en") -> "Record a price"
                                appLocaleTag.startsWith("fr") -> "Enregistrer un prix"
                                else -> "تسجيل سعر"
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (snapshot.priceObservations.isNotEmpty()) {
                        MealsIconButton(
                            onClick = { showPriceHistory = !showPriceHistory },
                            size = 42.dp
                        ) {
                            MealsIcon(
                                MealsIconName.Clock,
                                contentDescription = when {
                                    appLocaleTag.startsWith("en") -> if (showPriceHistory) "Hide price history" else "Show price history"
                                    appLocaleTag.startsWith("fr") -> if (showPriceHistory) "Masquer l’historique des prix" else "Afficher l’historique des prix"
                                    else -> if (showPriceHistory) "إخفاء سجل الأسعار" else "عرض سجل الأسعار"
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showPriceHistory && snapshot.priceObservations.isNotEmpty()) {
            item(key = "shopping-price-history") {
                MealsCard(
                    borderColor = Border,
                    contentPadding = 14.dp,
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 1.dp
                ) {
                    Text(
                        "سجل الأسعار",
                        color = dz.meals.app.ui.theme.Ink,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    snapshot.priceObservations.take(3).forEachIndexed { index, price ->
                        if (index > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .height(1.dp)
                                    .background(Border.copy(alpha = 0.8f))
                            )
                        }
                        PriceObservationRow(price)
                    }
                    Text(
                        "ملاحظات الأسعار محفوظة على هذا الجهاز وليست أسعاراً رسمية.",
                        color = MutedInk,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (rows.isNotEmpty()) {
            item(key = "shopping-filter") {
                MealsSubtabsIndicator(
                    items = listOf("المتبقي · $remainingCount", "الكل · ${rows.size}"),
                    selected = if (showPurchased) "الكل · ${rows.size}" else "المتبقي · $remainingCount",
                    onSelect = { showPurchased = it.startsWith("الكل") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item(key = "shopping-added-feedback") {
            AnimatedVisibility(
                visible = addedFeedback != null,
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) +
                    expandVertically(animationSpec = androidx.compose.animation.core.tween(190)),
                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(120)) +
                    shrinkVertically(animationSpec = androidx.compose.animation.core.tween(170))
            ) {
                MealsCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = null,
                    contentPadding = 12.dp,
                    shadowElevation = 1.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        MealsIcon(MealsIconName.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text("تمت الإضافة إلى القائمة", fontWeight = FontWeight.Bold)
                            Text(addedFeedback.orEmpty(), color = MutedInk, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (searchQuery.isNotBlank()) {
            item(key = "shopping-catalogue-search-heading") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            appLocaleTag.startsWith("en") -> "Matching ingredients"
                            appLocaleTag.startsWith("fr") -> "Ingrédients correspondants"
                            else -> "المكوّنات المطابقة"
                        },
                        color = Ink,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        visibleSearchResults?.let {
                            it.totalCount.toString() + if (it.hasMore) "+" else ""
                        } ?: "…",
                        color = MutedInk,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (searchQuery.trim().length < 2) {
                item(key = "shopping-catalogue-search-hint") {
                    Text(
                        when {
                            appLocaleTag.startsWith("en") -> "Type at least 2 characters to search"
                            appLocaleTag.startsWith("fr") -> "Saisissez au moins 2 caractères"
                            else -> "اكتب حرفين على الأقل للبحث"
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        color = MutedInk,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else if (visibleSearchResults == null) {
                item(key = "shopping-catalogue-search-loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when {
                                catalogueSearchIndex == null && appLocaleTag.startsWith("en") -> "Preparing ingredient search…"
                                catalogueSearchIndex == null && appLocaleTag.startsWith("fr") -> "Préparation de la recherche…"
                                catalogueSearchIndex == null -> "جارٍ تجهيز البحث…"
                                appLocaleTag.startsWith("en") -> "Searching…"
                                appLocaleTag.startsWith("fr") -> "Recherche…"
                                else -> "جارٍ البحث…"
                            },
                            color = MutedInk,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (visibleSearchResults.items.isEmpty()) {
                item(key = "shopping-catalogue-search-empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MealsIllustrationBadge(icon = MealsIconName.Search, size = 62.dp)
                        Text(
                            when {
                                appLocaleTag.startsWith("en") -> "No matching ingredients"
                                appLocaleTag.startsWith("fr") -> "Aucun ingrédient correspondant"
                                else -> "لا توجد مكوّنات مطابقة"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        MealsSecondaryButton(
                            label = when {
                                appLocaleTag.startsWith("en") -> "Add a custom item"
                                appLocaleTag.startsWith("fr") -> "Ajouter un article personnalisé"
                                else -> "إضافة عنصر يدوي"
                            },
                            onClick = {
                                itemName = searchQuery.trim()
                                itemEstimatedPrice = ""
                                addDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().testTag("shopping.addCustomItem")
                        )
                    }
                }
            } else {
                item(key = "shopping-catalogue-search-results-card") {
                    MealsCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = null,
                        contentPadding = 0.dp,
                        shape = RoundedCornerShape(22.dp),
                        shadowElevation = 0.dp
                    ) {
                        visibleSearchResults.items.forEachIndexed { index, food ->
                            val foodName = when {
                                appLocaleTag.startsWith("en") -> food.nameEn.ifBlank { food.nameAr }
                                appLocaleTag.startsWith("fr") -> food.nameFr.ifBlank { food.nameAr }
                                else -> food.nameAr
                            }
                            val alreadyAdded = isCatalogueIngredientAdded(food.id)
                            val supportingText = listOfNotNull(
                                categoriesById[food.categoryId]?.label(appLocaleTag),
                                subcategoriesById[food.subcategoryId]?.label(appLocaleTag),
                                food.baseUnit.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")

                            ShoppingCatalogueIngredientRow(
                                foodName = foodName,
                                supportingText = supportingText,
                                alreadyAdded = alreadyAdded,
                                localeTag = appLocaleTag,
                                showDivider = index < visibleSearchResults.items.lastIndex,
                                onClick = { toggleCatalogueIngredient(food.id, foodName) }
                            )
                        }
                    }
                }
                if (
                    visibleSearchResults.totalCount > visibleSearchResults.items.size ||
                    visibleSearchResults.hasMore
                ) {
                    item(key = "shopping-catalogue-search-more") {
                        Text(
                            when {
                                appLocaleTag.startsWith("en") -> "Showing first ${visibleSearchResults.items.size} results${if (visibleSearchResults.hasMore) " (more matches available)" else ""}"
                                appLocaleTag.startsWith("fr") -> "Affichage des ${visibleSearchResults.items.size} premiers résultats${if (visibleSearchResults.hasMore) " (autres résultats disponibles)" else ""}"
                                else -> "يظهر أول ${visibleSearchResults.items.size} نتيجة${if (visibleSearchResults.hasMore) " (توجد نتائج أخرى)" else ""}"
                            },
                            modifier = Modifier.padding(top = 8.dp),
                            color = MutedInk,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        } else {
            when {
                rows.isEmpty() -> {
                    item(key = "shopping-empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MealsIllustrationBadge(icon = MealsIconName.Basket, size = 68.dp)
                            Text(
                                "قائمتك فارغة",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "ابحثي في الأعلى أو افتحي التصنيفات لإضافة المكوّنات.",
                                color = MutedInk,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                visibleRows.isEmpty() -> {
                    item(key = "shopping-all-collected") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            MealsIllustrationBadge(icon = MealsIconName.Check, size = 64.dp)
                            Text(
                                "اكتملت المشتريات",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text("جمعتِ كل العناصر المطلوبة.", color = MutedInk)
                            MealsSecondaryButton(
                                label = "عرض الكل",
                                onClick = { showPurchased = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                else -> {
                    categoryIds.forEach { categoryId ->
                        val categoryRows = visibleRowsByCategory[categoryId].orEmpty()
                        val categoryLabel = categoriesById[categoryId]?.label(appLocaleTag) ?: "مواد غذائية أخرى"
                        item(key = "category-$categoryId") {
                            MealsPressable(
                                onClick = { selectedCategoryId = categoryId },
                                modifier = Modifier.fillMaxWidth().testTag("shopping.category.row"),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                MealsCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    borderColor = Border,
                                    contentPadding = 0.dp,
                                    shape = RoundedCornerShape(22.dp),
                                    shadowElevation = 1.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp, vertical = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                categoryLabel,
                                                color = dz.meals.app.ui.theme.TextAccent,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                if (appLocaleTag.startsWith("en")) "${categoryRows.size} items"
                                                else if (appLocaleTag.startsWith("fr")) "${categoryRows.size} articles"
                                                else "${categoryRows.size} عناصر",
                                                color = MutedInk,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        MealsIcon(
                                            MealsIconName.ChevronRight,
                                            contentDescription = if (appLocaleTag.startsWith("en")) "Open category" else "فتح التصنيف",
                                            modifier = Modifier.size(23.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 14.dp,
                            bottom = 124.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "ingredient-categories-heading") {
                            Text(
                                when {
                                    appLocaleTag.startsWith("en") -> "Choose a category"
                                    appLocaleTag.startsWith("fr") -> "Choisissez une catégorie"
                                    else -> "اختاري تصنيفاً"
                                },
                                modifier = Modifier.padding(bottom = 2.dp),
                                color = Ink,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        orderedFoodCategories.chunked(2).forEachIndexed { rowIndex, categoryPair ->
                            item(key = "ingredient-category-row-$rowIndex") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    categoryPair.forEach { category ->
                                        val count = catalogueSearchIndex?.categoryCounts?.get(category.id) ?: 0
                                        MealsChoiceTile(
                                            title = category.label(appLocaleTag),
                                            description = when {
                                                appLocaleTag.startsWith("en") -> "$count ingredients"
                                                appLocaleTag.startsWith("fr") -> "$count ingrédients"
                                                else -> "$count مكوّن"
                                            },
                                            selected = false,
                                            onClick = {
                                                selectedBrowseCategoryId = category.id
                                                selectedSubcategoryId = null
                                                browseDirection = 1
                                                browsePage = 2
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (categoryPair.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val category = selectedBrowseCategory
                    if (category == null) {
                        LaunchedEffect(Unit) {
                            browseDirection = -1
                            browsePage = 1
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                MealsIconButton(
                                    onClick = ::navigateBackInBrowse,
                                    size = 42.dp
                                ) {
                                    MealsIcon(
                                        MealsIconName.ArrowBack,
                                        contentDescription = when {
                                            appLocaleTag.startsWith("en") -> "Back to categories"
                                            appLocaleTag.startsWith("fr") -> "Retour aux catégories"
                                            else -> "العودة إلى التصنيفات"
                                        },
                                        modifier = Modifier.size(23.dp)
                                    )
                                }
                                MealsFilterChips(
                                    items = listOf(allSubcategoriesLabel) + browseSubcategories.map { it.label(appLocaleTag) },
                                    selected = selectedSubcategoryLabel,
                                    onSelect = { selected ->
                                        selectedSubcategoryId = browseSubcategories
                                            .firstOrNull { it.label(appLocaleTag) == selected }
                                            ?.id
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            MealsCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, bottom = 124.dp),
                                borderColor = null,
                                contentPadding = 0.dp,
                                shape = RoundedCornerShape(22.dp),
                                shadowElevation = 0.dp
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        top = 4.dp,
                                        bottom = 12.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                if (browseFoods.isEmpty()) {
                                    item(key = "category-ingredients-empty") {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            MealsIllustrationBadge(icon = MealsIconName.Basket, size = 60.dp)
                                            Text(
                                                when {
                                                    appLocaleTag.startsWith("en") -> "No ingredients in this category yet"
                                                    appLocaleTag.startsWith("fr") -> "Aucun ingrédient dans cette catégorie"
                                                    else -> "لا توجد مكوّنات في هذا التصنيف حالياً"
                                                },
                                                color = MutedInk,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                                browseFoods.forEachIndexed { index, food ->
                                    item(key = "browse-food-${food.id}") {
                                        val foodName = when {
                                            appLocaleTag.startsWith("en") -> food.nameEn.ifBlank { food.nameAr }
                                            appLocaleTag.startsWith("fr") -> food.nameFr.ifBlank { food.nameAr }
                                            else -> food.nameAr
                                        }
                                        val alreadyAdded = isCatalogueIngredientAdded(food.id)
                                        val supportingText = listOfNotNull(
                                            subcategoriesById[food.subcategoryId]?.label(appLocaleTag),
                                            food.baseUnit.takeIf { it.isNotBlank() }
                                        ).joinToString(" · ")

                                        ShoppingCatalogueIngredientRow(
                                            foodName = foodName,
                                            supportingText = supportingText,
                                            alreadyAdded = alreadyAdded,
                                            localeTag = appLocaleTag,
                                            showDivider = index < browseFoods.lastIndex,
                                            onClick = { toggleCatalogueIngredient(food.id, foodName) }
                                        )
                                    }
                                }
                            
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selectedCategoryId?.let { categoryId ->
        // The rows are already grouped by category above. Only build the much
        // smaller subcategory sections once for this selected sheet.
        val categoryRows = visibleRowsByCategory[categoryId].orEmpty()
        val groupedRows = remember(categoryId, categoryRows, subcategoriesById) {
            categoryRows.groupBy { it.subcategoryId }.entries.sortedWith(
                compareBy<Map.Entry<String, List<ShoppingListItem>>> {
                    subcategoriesById[it.key]?.sortOrder ?: Int.MAX_VALUE
                }.thenBy { it.key }
            )
        }

        MealsBottomSheet(
            onDismissRequest = { selectedCategoryId = null },
            titleText = categoriesById[categoryId]?.label(appLocaleTag) ?: "مواد غذائية أخرى",
            // Use the standard Meals Liquid Glass modal treatment here:
            // cross-window Haze blur plus Android blur-behind, with its shared
            // warm tint, white optical rim and modal depth. Performance comes
            // from lazy rows and cached grouping, not removing the material.
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 540.dp)
                        .testTag("shopping.category.sheet"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 4.dp,
                        vertical = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    groupedRows.forEachIndexed { groupIndex, group ->
                        item(
                            key = "subcategory-header-$categoryId-${group.key}",
                            contentType = "subcategory-header"
                        ) {
                            Text(
                                subcategoriesById[group.key]?.label(appLocaleTag) ?: "أخرى",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                                color = MutedInk,
                                style = MaterialTheme.typography.labelLarge,
                                translate = false
                            )
                        }

                        itemsIndexed(
                            items = group.value,
                            key = { _, item -> "shopping-item-${item.id}" },
                            contentType = { _, _ -> "shopping-item" }
                        ) { itemIndex, item ->
                            ShoppingItemRow(
                                item = item,
                                onToggle = { onToggleItem(item.id, !item.checked) },
                                onEdit = {
                                    selectedCategoryId = null
                                    editItemId = item.id
                                    editName = item.name
                                    editQuantity = item.purchaseQuantity.prettyQuantity()
                                    editUnit = item.unit
                                    editPurchaseQuantity = item.purchaseQuantity.prettyQuantity()
                                    editStoreNote = item.storeNote
                                    editActualSpent = item.actualSpentDzd?.toString().orEmpty()
                                    editUnavailable = item.isUnavailable
                                    editUserAdded = item.userAdded && item.ingredientId == null
                                },
                                onDelete = { onDeleteCustomItem(item.id) },
                                showDivider = itemIndex < group.value.lastIndex || groupIndex < groupedRows.lastIndex
                            )
                        }
                    }
                }
            }
        )
    }

    if (addDialog) {
        MealsBottomSheet(
            onDismissRequest = { addDialog = false },
            title = { Text("إضافة عنصر يدوي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MealsLiquidGlassTextField(
                        modifier = Modifier.testTag("shopping.customItem.name"),
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("اسم العنصر") },
                        singleLine = true
                    )
                    MealsLiquidGlassTextField(
                        value = itemEstimatedPrice,
                        onValueChange = { itemEstimatedPrice = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("السعر الإجمالي التقديري (اختياري)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        suffix = { Text("دج") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                val dismissSheet = mealsSheetDismissAction()
                MealsPrimaryButton(
                    label = "إضافة",
                    onClick = {
                        val estimatedPrice = itemEstimatedPrice.takeIf { it.isNotBlank() }
                            ?.replace(',', '.')?.toDoubleOrNull()
                        if (itemName.isNotBlank() &&
                            (itemEstimatedPrice.isBlank() || (estimatedPrice != null && estimatedPrice >= 0.0))
                        ) {
                            dismissSheet {
                                addedFeedback = itemName.trim()
                                onAddCustomItem(itemName, 1.0, "حبة", estimatedPrice)
                                itemName = ""
                                itemEstimatedPrice = ""
                                addDialog = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("shopping.customItem.confirm"),
                    enabled = itemName.isNotBlank() &&
                        (itemEstimatedPrice.isBlank() || (itemEstimatedPrice.replace(',', '.').toDoubleOrNull() ?: -1.0) >= 0.0)
                )
            },
            dismissButton = {
                val dismissSheet = mealsSheetDismissAction()
                MealsSecondaryButton(
                    label = "إلغاء",
                    onClick = { dismissSheet { addDialog = false } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    if (editItemId != null) {
        MealsBottomSheet(
            onDismissRequest = { editItemId = null },
            title = { Text("تعديل العنصر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editUserAdded) {
                        MealsLiquidGlassTextField(value = editName, onValueChange = { editName = it }, label = { Text("اسم العنصر") }, singleLine = true)
                        MealsLiquidGlassTextField(
                            value = editQuantity,
                            onValueChange = { editQuantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            label = { Text("الكمية المطلوبة") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        MealsLiquidGlassTextField(value = editUnit, onValueChange = { editUnit = it.take(12) }, label = { Text("الوحدة") }, singleLine = true)
                    } else {
                        MealsLiquidGlassTextField(
                            value = editPurchaseQuantity,
                            onValueChange = { editPurchaseQuantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            label = { Text("كمية الشراء المعدلة (اتركها فارغة لإعادة التلقائي)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MealsCheckbox(checked = editUnavailable, onCheckedChange = { editUnavailable = it })
                        Text("غير متوفر حالياً")
                    }
                    MealsLiquidGlassTextField(
                        value = editActualSpent,
                        onValueChange = { editActualSpent = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("ما دفعته فعلياً (دج، اختياري)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                val dismissSheet = mealsSheetDismissAction()
                MealsPrimaryButton(
                    label = "حفظ التعديل",
                    onClick = {
                        val id = editItemId
                        val quantity = editQuantity.replace(',', '.').toDoubleOrNull()
                        val purchaseQuantity = editPurchaseQuantity.takeIf { it.isNotBlank() }
                            ?.replace(',', '.')?.toDoubleOrNull()
                        val actualSpent = editActualSpent.takeIf { it.isNotBlank() }
                            ?.replace(',', '.')?.toDoubleOrNull()
                        val valid = if (editUserAdded) {
                            quantity != null && quantity > 0.0 && editName.isNotBlank()
                        } else {
                            editPurchaseQuantity.isBlank() || (purchaseQuantity != null && purchaseQuantity > 0.0)
                        }
                        if (id != null && valid && (actualSpent == null || actualSpent >= 0.0)) {
                            dismissSheet {
                                if (editUserAdded && quantity != null) onEditCustomItem(id, editName, quantity, editUnit)
                                onUpdateItemDetails(id, purchaseQuantity, editStoreNote, editUnavailable, actualSpent)
                                editItemId = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = (!editUserAdded || (editName.isNotBlank() && (editQuantity.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0)) &&
                        (editPurchaseQuantity.isBlank() || (editPurchaseQuantity.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0) &&
                        (editActualSpent.isBlank() || (editActualSpent.replace(',', '.').toDoubleOrNull() ?: -1.0) >= 0.0)
                )
            },
            dismissButton = {
                val dismissSheet = mealsSheetDismissAction()
                MealsSecondaryButton(
                    label = "إلغاء",
                    onClick = { dismissSheet { editItemId = null } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    if (priceDialog) {
        PriceReportDialog(
            ingredients = ingredients,
            wilaya = wilaya,
            defaultShopContext = listOf(
                channelName(shoppingChannel),
                preferredShop
            ).filter { it.isNotBlank() }.joinToString(" · "),
            onDismiss = { priceDialog = false },
            onSubmit = { ingredientId, packQuantity, amount, context ->
                onRecordPrice(ingredientId, packQuantity, amount, context)
                priceDialog = false
            }
        )
    }
}

@Composable
private fun PriceReportDialog(
    ingredients: List<IngredientMaster>,
    wilaya: String,
    defaultShopContext: String,
    onDismiss: () -> Unit,
    onSubmit: (String, Double, Double, String) -> Unit
) {
    val appLocaleTag = LocalMealsLocaleTag.current
    var selectedId by rememberSaveable { mutableStateOf(ingredients.firstOrNull()?.id.orEmpty()) }
    var packQuantityText by rememberSaveable { mutableStateOf("1") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var shopContext by rememberSaveable(wilaya, defaultShopContext) {
        mutableStateOf(defaultShopContext.ifBlank { "سوق / بقالة" })
    }
    val selectedIngredient = ingredients.firstOrNull { it.id == selectedId }

    MealsBottomSheet(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل ملاحظة سعر") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MealsGlassDropdown(
                        selectedOption = selectedIngredient,
                        options = ingredients,
                        optionLabel = {
                            if (appLocaleTag.startsWith("en")) it.nameEn.ifBlank { it.nameAr } else it.nameAr
                        },
                        optionSupportingText = {
                            if (appLocaleTag.startsWith("en")) it.nameFr else it.nameEn
                        },
                        placeholder = "اختيار المكوّن",
                        modifier = Modifier.fillMaxWidth(),
                        maxMenuHeight = 300.dp,
                        onOptionSelected = { ingredient ->
                            selectedId = ingredient.id
                            packQuantityText = "1"
                        }
                    )
                }
                Text(
                    mealsText(
                        "الوحدة الأساسية: " +
                            (selectedIngredient?.baseUnit?.let { formatHouseholdUnit(it) } ?: "—"),
                        appLocaleTag
                    ) + " · " + mealsText("الولاية", appLocaleTag) + " " + bidiIsolate(wilaya),
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall,
                    translate = false
                )
                MealsLiquidGlassTextField(
                    value = packQuantityText,
                    onValueChange = { packQuantityText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("حجم العبوة أو الكمية المباعة (" + (selectedIngredient?.baseUnit?.let { formatPriceInputUnit(it) } ?: "وحدة") + ")") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                MealsLiquidGlassTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("السعر بالدينار الجزائري") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text("دج") },
                    singleLine = true
                )
                MealsLiquidGlassTextField(
                    value = shopContext,
                    onValueChange = { shopContext = it.take(60) },
                    label = { Text("نوع المحل أو اسمه") },
                    singleLine = true
                )
                Text(
                    "اكتبي الوزن بالكيلوغرام أو الحجم باللتر. سيُحوّل التطبيق الكمية للحساب ويحفظ البلاغ محلياً كملاحظة غير موثّقة.",
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            val dismissSheet = mealsSheetDismissAction()
            val packQuantity = packQuantityText.replace(',', '.').toDoubleOrNull()
            val amount = priceText.replace(',', '.').toDoubleOrNull()
            MealsPrimaryButton(
                label = "حفظ البلاغ",
                onClick = {
                    if (selectedIngredient != null && packQuantity != null && amount != null) {
                        val baseUnitMultiplier = when (selectedIngredient.baseUnit) {
                            "g", "ml" -> 1000.0
                            else -> 1.0
                        }
                        dismissSheet {
                            onSubmit(selectedIngredient.id, packQuantity * baseUnitMultiplier, amount, shopContext)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedIngredient != null && (packQuantity ?: 0.0) > 0.0 && (amount ?: 0.0) > 0.0
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

@Composable
private fun PriceObservationRow(price: PriceObservationSummary) {
    val date = price.observedAtEpochMillis?.let {
        formatDateDmy(epochMillisToLocalDate(it))
    } ?: "بدون تاريخ"
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(price.ingredientNameAr, fontWeight = FontWeight.Medium)
            Text(price.packPriceDzd.toInt().formatDzd(), fontWeight = FontWeight.Bold, color = Ink)
        }
        Text(
            mealsText(price.packQuantityBase.formatHouseholdQuantity(price.unit)) + " · " +
                bidiIsolate(price.locationLabel) + " · " + date,
            color = MutedInk,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            mealsText(price.sourceLabel) + " · " +
                mealsText(if (price.priceKind == "demo") "قيمة تجريبية" else "ملاحظة مستخدم غير موثّقة"),
            color = MutedInk,
            style = MaterialTheme.typography.labelSmall
        )
        if (price.isStale) {
            Text(
                "سعر قديم أو بتاريخ غير صالح — لا يدخل في تقدير المشتريات الحالي.",
                color = MutedInk,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingListItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showDivider: Boolean
) {
    val supportingDetails = remember(
        item.isUnavailable,
        item.actualSpentDzd,
        item.marketPriceWilaya,
        item.marketPriceStoreNames,
        item.marketPriceMinPackageCount,
        item.marketPriceMaxPackageCount,
        item.marketPriceIncludesLimitedAvailability
    ) {
        buildList {
            if (item.isUnavailable) add("غير متوفر")
            item.marketPriceWilaya?.let { wilaya ->
                val stores = item.marketPriceStoreNames.take(2).joinToString("، ")
                val minPacks = item.marketPriceMinPackageCount
                val maxPacks = item.marketPriceMaxPackageCount
                val packageText = when {
                    minPacks == null || maxPacks == null -> ""
                    minPacks == maxPacks -> " · $minPacks عبوة"
                    else -> " · $minPacks–$maxPacks عبوات"
                }
                add(
                    "سعر استرشادي في $wilaya" +
                        if (stores.isNotBlank()) " · $stores" else "" +
                        packageText
                )
            }
            if (item.marketPriceIncludesLimitedAvailability) add("التوفر محدود")
            item.actualSpentDzd?.let { add("المدفوع " + it.toInt().formatDzd()) }
        }.joinToString(" · ")
    }
    val actionIsDelete = item.userAdded

    // One stable horizontal layout: selection, flexible item details, compact
    // quantity/price, then one dedicated action slot at logical End. No swipe
    // reveal layer, overlapping controls, or per-row drag animation is needed.
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 4.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MealsCheckbox(
                checked = item.checked,
                onCheckedChange = { onToggle() }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onEdit)
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    item.name,
                    translate = item.ingredientId != null,
                    color = if (item.checked) MutedInk else Ink,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    overflow = TextOverflow.Ellipsis
                )
                if (supportingDetails.isNotBlank()) {
                    Text(
                        supportingDetails,
                        translate = false,
                        color = MutedInk,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier
                    .width(88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onEdit)
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    item.purchaseQuantity.formatHouseholdQuantity(item.unit),
                    color = if (item.checked) MutedInk else dz.meals.app.ui.theme.TextAccent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (item.marketCashOutlayMinDzd != null && item.marketCashOutlayMaxDzd != null) {
                        item.marketCashOutlayMinDzd.toInt().formatDzd() + " – " +
                            item.marketCashOutlayMaxDzd.toInt().formatDzd()
                    } else {
                        item.cashOutlayDzd?.toInt()?.formatDzd() ?: "السعر غير متوفر"
                    },
                    color = MutedInk,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            MealsPressable(
                onClick = if (actionIsDelete) onDelete else onEdit,
                modifier = Modifier
                    .size(38.dp)
                    .background(WarmSurface.copy(alpha = 0.78f), RoundedCornerShape(11.dp)),
                shape = RoundedCornerShape(11.dp)
            ) {
                MealsIcon(
                    if (actionIsDelete) MealsIconName.Delete else MealsIconName.Edit,
                    contentDescription = if (actionIsDelete) "حذف العنصر" else "تعديل العنصر",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(1.dp)
                    .background(Border.copy(alpha = 0.72f))
            )
        }
    }
}

@Composable
private fun ShoppingCatalogueIngredientRow(
    foodName: String,
    supportingText: String,
    alreadyAdded: Boolean,
    localeTag: String,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    foodName,
                    color = Ink,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    translate = false
                )
                if (supportingText.isNotBlank()) {
                    Text(
                        supportingText,
                        color = MutedInk,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        translate = false
                    )
                }
            }
            if (alreadyAdded) {
                Text(
                    when {
                        localeTag.startsWith("en") -> "In list"
                        localeTag.startsWith("fr") -> "Dans la liste"
                        else -> "في القائمة"
                    },
                    color = MutedInk,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            MealsIcon(
                if (alreadyAdded) MealsIconName.Check else MealsIconName.Add,
                contentDescription = null,
                modifier = Modifier.size(23.dp)
            )
        }

        if (showDivider) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.70f)
                        .height(1.dp)
                        .background(Border.copy(alpha = 0.52f))
                )
            }
        }
    }
}

private fun buildShoppingShareText(
    rows: List<ShoppingListItem>,
    snapshot: MealsSnapshot,
    localeTag: String
): String = buildString {
    appendLine(mealsText("قائمة مشتريات Midati", localeTag))
    val basketRangeText = snapshot.basket.purchaseOutlayMinDzd?.let { minimum ->
        val maximum = snapshot.basket.purchaseOutlayMaxDzd
        if (maximum != null && minimum != maximum) {
            minimum.toInt().formatDzd() + " – " + maximum.toInt().formatDzd()
        } else snapshot.basket.purchaseOutlayDzd.toInt().formatDzd()
    } ?: snapshot.basket.purchaseOutlayDzd.toInt().formatDzd()
    appendLine(mealsText("التكلفة التقديرية: " + basketRangeText, localeTag))
    if (snapshot.basket.actualSpendDzd > 0.0) {
        appendLine(mealsText("الإنفاق الفعلي المُدخل: " + snapshot.basket.actualSpendDzd.toInt().formatDzd(), localeTag))
    }
    appendLine()

    val categoriesById = snapshot.foodCategories.associateBy { it.id }
    val subcategoriesById = snapshot.foodSubcategories.associateBy { it.id }
    val groupedRows: Map<String, List<ShoppingListItem>> =
        rows.groupBy { row: ShoppingListItem -> row.categoryId }

    groupedRows.keys
        .sortedWith(compareBy<String> { categoriesById[it]?.sortOrder ?: Int.MAX_VALUE }.thenBy { it })
        .forEach { categoryId ->
            val categoryLabel = categoriesById[categoryId]?.label(localeTag) ?: "مواد غذائية أخرى"
            appendLine(mealsText(categoryLabel, localeTag))
            val categoryRows = groupedRows.getValue(categoryId)
            categoryRows.groupBy { it.subcategoryId }.keys
                .sortedWith(compareBy<String> { subcategoriesById[it]?.sortOrder ?: Int.MAX_VALUE }.thenBy { it })
                .forEach { subcategoryId ->
                    val subcategoryLabel = subcategoriesById[subcategoryId]?.label(localeTag) ?: "أخرى"
                    appendLine("  " + mealsText(subcategoryLabel, localeTag))
                    categoryRows.filter { it.subcategoryId == subcategoryId }.forEach { item ->
                        append(if (item.checked) "[x] " else "[ ] ")
                        append(bidiIsolate(if (item.ingredientId == null) item.name else mealsText(item.name, localeTag)))
                        append(" — ")
                        append(mealsText(item.purchaseQuantity.formatHouseholdQuantity(item.unit), localeTag))
                        val itemCostLabel = if (
                            item.marketCashOutlayMinDzd != null && item.marketCashOutlayMaxDzd != null
                        ) {
                            item.marketCashOutlayMinDzd.toInt().formatDzd() + " – " +
                                item.marketCashOutlayMaxDzd.toInt().formatDzd()
                        } else {
                            item.cashOutlayDzd?.toInt()?.formatDzd()
                        }
                        if (itemCostLabel != null) append(" · " + mealsText(itemCostLabel, localeTag))
                        if (item.isUnavailable) append(" · " + mealsText("غير متوفر", localeTag))
                        if (item.storeNote.isNotBlank()) append(" · " + bidiIsolate(item.storeNote))
                        appendLine()
                    }
                }
            appendLine()
        }
    if (snapshot.basket.unknownPriceCount > 0) {
        appendLine(mealsText("تنبيه: يوجد " + snapshot.basket.unknownPriceCount + " عنصر دون سعر مؤكد.", localeTag))
    }
    append(mealsText("الأسعار تقديرية وليست ضماناً لسعر المحل أو التوفر.", localeTag))
}


private fun channelName(value: String): String = when (value) {
    "market" -> "سوق"
    "grocery" -> "بقالة"
    "supermarket" -> "متجر كبير"
    "butcher" -> "ملحمة"
    "poultry" -> "محل دواجن"
    "fishmonger" -> "بائع سمك"
    "bakery" -> "مخبزة"
    else -> "مكان تسوق آخر"
}
