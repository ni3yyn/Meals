package dz.meals.app.ui.screens

import dz.meals.app.ui.components.MealsText as Text
import dz.meals.app.ui.theme.LocalMealsLocaleTag
import dz.meals.app.ui.theme.bidiIsolate
import dz.meals.app.ui.theme.mealsText
import dz.meals.app.ui.theme.logicalStartRevealSign

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import dz.meals.app.ui.components.MealsGlassDropdown
import androidx.compose.material3.MaterialTheme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dz.meals.app.ui.components.MealsBottomSheet
import dz.meals.app.ui.components.mealsSheetDismissAction
import dz.meals.app.ui.components.MealsCard
import dz.meals.app.ui.components.MealsCheckbox
import dz.meals.app.ui.components.MealsGlassSurface
import dz.meals.app.ui.components.MealsIconButton
import dz.meals.app.ui.components.MealsIllustrationBadge
import dz.meals.app.ui.components.MealsLiquidGlassTextField
import dz.meals.app.ui.components.MealsPrimaryButton
import dz.meals.app.ui.components.MealsPressable
import dz.meals.app.ui.components.MealsProgressBar
import dz.meals.app.ui.components.MealsSecondaryButton
import dz.meals.app.ui.components.MealsSubtabsIndicator
import dz.meals.app.ui.components.MealsTextButton
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.data.IngredientMaster
import dz.meals.app.data.MealsSnapshot
import dz.meals.app.data.PriceObservationSummary
import dz.meals.app.data.ShoppingListItem
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.BodyInk
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.WarmSurface
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
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
    onShareList: (String) -> Unit
) {
    val appLocaleTag = LocalMealsLocaleTag.current
    val layoutDirection = LocalLayoutDirection.current
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var cataloguePicker by rememberSaveable { mutableStateOf(false) }
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
    val expandedCategories = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(addedFeedback) {
        if (addedFeedback != null) {
            delay(1500)
            addedFeedback = null
        }
    }

    val rows = snapshot.shoppingItems
    val checkedCount = rows.count { it.checked }
    val remainingCount = rows.size - checkedCount
    val visibleRows = if (showPurchased) rows else rows.filterNot { it.checked }
    val categoriesById = snapshot.foodCategories.associateBy { it.id }
    val subcategoriesById = snapshot.foodSubcategories.associateBy { it.id }
    val categoryIds = visibleRows.map { it.categoryId }.distinct().sortedWith(
        compareBy<String> { categoriesById[it]?.sortOrder ?: Int.MAX_VALUE }.thenBy { it }
    )

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
        item(key = "shopping-header") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MealsPrimaryButton(
                        label = "إضافة",
                        onClick = { cataloguePicker = true },
                        modifier = Modifier.weight(1f),
                        icon = MealsIconName.Add
                    )
                    MealsSecondaryButton(
                        label = "مشاركة",
                        onClick = { onShareList(buildShoppingShareText(rows, snapshot, appLocaleTag)) },
                        modifier = Modifier.weight(1f),
                        enabled = rows.isNotEmpty()
                    )
                }
            }
        }

        item(key = "shopping-summary") {
            MealsGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                tintColor = WarmSurface,
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
                            snapshot.basket.purchaseOutlayDzd.toInt().formatDzd(),
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
                    MealsProgressBar(
                        progress = checkedCount.toFloat() / rows.size.toFloat(),
                        modifier = Modifier.padding(top = 2.dp)
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
            }
        }

        item(key = "shopping-price-actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MealsTextButton(
                    onClick = { priceDialog = true },
                    modifier = Modifier.weight(1f),
                    compact = true
                ) {
                    MealsIcon(MealsIconName.Wallet, contentDescription = null, modifier = Modifier.size(24.dp))
                    Text("تسجيل سعر")
                }
                if (snapshot.priceObservations.isNotEmpty()) {
                    MealsTextButton(
                        onClick = { showPriceHistory = !showPriceHistory },
                        modifier = Modifier.weight(1f),
                        compact = true
                    ) {
                        Text(if (showPriceHistory) "إخفاء السجل" else "سجل الأسعار")
                    }
                }
            }
        }

        if (showPriceHistory && snapshot.priceObservations.isNotEmpty()) {
            item(key = "shopping-price-history") {
                MealsCard(
                    containerColor = Paper,
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
                    containerColor = WarmSurface.copy(alpha = 0.90f),
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

        when {
            rows.isEmpty() -> {
                item(key = "shopping-empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        MealsIllustrationBadge(icon = MealsIconName.Basket, size = 68.dp)
                        Text(
                            "قائمتك فارغة",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "أضيفي وصفات إلى خطة الأسبوع أو أضيفي عنصراً يدوياً.",
                            color = MutedInk,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        MealsPrimaryButton(
                            label = "إضافة",
                            onClick = { cataloguePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            icon = MealsIconName.Add
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
                    val categoryRows = visibleRows.filter { it.categoryId == categoryId }
                    val categoryLabel = categoriesById[categoryId]?.label(appLocaleTag) ?: "مواد غذائية أخرى"
                    item(key = "category-$categoryId") {
                        MealsCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Paper,
                            borderColor = Border,
                            contentPadding = 0.dp,
                            shape = RoundedCornerShape(22.dp),
                            shadowElevation = 1.dp
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                MealsPressable(
                                    onClick = {
                                        expandedCategories[categoryId] = !(expandedCategories[categoryId] ?: true)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                    Text(
                                        categoryLabel,
                                        color = dz.meals.app.ui.theme.TextAccent,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        categoryRows.size.toString(),
                                        color = MutedInk,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    MealsIcon(
                                        MealsIconName.ChevronRight,
                                        contentDescription = if (expandedCategories[categoryId] == false) "توسيع القسم" else "طي القسم",
                                        modifier = Modifier
                                            .padding(start = 7.dp)
                                            .size(21.dp)
                                            .graphicsLayer {
                                                rotationZ = if (expandedCategories[categoryId] == false) 0f else {
                                                    if (layoutDirection == LayoutDirection.Rtl) 270f else 90f
                                                }
                                            }
                                    )
                                    }
                                }
                                AnimatedVisibility(
                                    visible = expandedCategories[categoryId] != false,
                                    enter = expandVertically(animationSpec = androidx.compose.animation.core.tween(230)) +
                                        fadeIn(animationSpec = androidx.compose.animation.core.tween(160)),
                                    exit = shrinkVertically(animationSpec = androidx.compose.animation.core.tween(210)) +
                                        fadeOut(animationSpec = androidx.compose.animation.core.tween(120))
                                ) {
                                    Column(Modifier.animateContentSize(animationSpec = androidx.compose.animation.core.tween(220))) {
                                        val subcategoryIds = categoryRows.map { it.subcategoryId }.distinct().sortedWith(
                                            compareBy<String> { subcategoriesById[it]?.sortOrder ?: Int.MAX_VALUE }.thenBy { it }
                                        )
                                        subcategoryIds.forEach { subcategoryId ->
                                            val subcategoryRows = categoryRows.filter { it.subcategoryId == subcategoryId }
                                            Text(
                                                subcategoriesById[subcategoryId]?.label(appLocaleTag) ?: "أخرى",
                                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                                                color = MutedInk,
                                                style = MaterialTheme.typography.labelLarge,
                                                translate = false
                                            )
                                            subcategoryRows.forEachIndexed { index, item ->
                                                if (index > 0) {
                                                    Box(
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 14.dp)
                                                            .height(1.dp)
                                                            .background(Border.copy(alpha = 0.78f))
                                                    )
                                                }
                                                ShoppingItemRow(
                                                    item = item,
                                                    onToggle = { onToggleItem(item.id, !item.checked) },
                                                    onEdit = {
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
                                                    onDelete = { onDeleteCustomItem(item.id) }
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
    }

    if (cataloguePicker) {
        ShoppingCataloguePicker(
            ingredients = ingredients,
            foodCategories = snapshot.foodCategories,
            foodSubcategories = snapshot.foodSubcategories,
            existingIngredientIds = rows.mapNotNull { it.ingredientId }.toSet(),
            onAddIngredient = { id ->
                addedFeedback = ingredients.firstOrNull { it.id == id }?.nameAr ?: id
                onAddCatalogueItem(id)
            },
            onAddCustom = {
                cataloguePicker = false
                addDialog = true
            },
            onDismiss = { cataloguePicker = false }
        )
    }

    if (addDialog) {
        MealsBottomSheet(
            onDismissRequest = { addDialog = false },
            title = { Text("إضافة عنصر يدوي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MealsLiquidGlassTextField(
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
                    modifier = Modifier.fillMaxWidth(),
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
    onDelete: () -> Unit
) {
    val actionWidth = 56.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val layoutDirection = LocalLayoutDirection.current
    // Reveal the action at logical Start. The foreground moves right in LTR and left in RTL.
    val revealSign = logicalStartRevealSign(layoutDirection)
    val minDragOffset = minOf(0f, revealSign * actionWidthPx)
    val maxDragOffset = maxOf(0f, revealSign * actionWidthPx)
    var dragOffset by remember(item.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(item.id) { mutableStateOf(false) }
    var isActionRevealed by remember(item.id) { mutableStateOf(false) }
    val settledOffset by animateFloatAsState(
        targetValue = if (isActionRevealed) revealSign * actionWidthPx else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f),
        label = "shoppingSwipeSettle"
    )
    val dragState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceIn(minDragOffset, maxDragOffset)
    }
    val foregroundOffset = if (isDragging) dragOffset else settledOffset

    val supportingDetails = buildList {
        if (item.isUnavailable) add("غير متوفر")
        item.actualSpentDzd?.let { add("المدفوع " + it.toInt().formatDzd()) }
    }.joinToString(" · ")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(vertical = 7.dp)
                .size(width = actionWidth, height = 54.dp)
                .background(WarmSurface, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            MealsIconButton(
                onClick = {
                    if (item.userAdded) onDelete() else onEdit()
                    isActionRevealed = false
                },
                size = 44.dp
            ) {
                MealsIcon(
                    if (item.userAdded) MealsIconName.Delete else MealsIconName.Edit,
                    contentDescription = if (item.userAdded) "حذف العنصر" else "تعديل العنصر",
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(foregroundOffset.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        dragOffset = settledOffset
                        isDragging = true
                    },
                    onDragStopped = {
                        isDragging = false
                        isActionRevealed = dragOffset * revealSign > actionWidthPx * 0.30f
                        dragOffset = 0f
                    }
                )
                .heightIn(min = 80.dp)
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MealsCheckbox(
                checked = item.checked,
                onCheckedChange = { onToggle() }
            )
            MealsPressable(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                }
            }
            MealsPressable(
                onClick = onEdit,
                modifier = Modifier.width(92.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                    item.cashOutlayDzd?.toInt()?.formatDzd() ?: "السعر غير متوفر",
                    color = MutedInk,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                }
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
    appendLine(mealsText("التكلفة التقديرية: " + snapshot.basket.purchaseOutlayDzd.toInt().formatDzd(), localeTag))
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
                        val itemCost: Double? = item.cashOutlayDzd
                        if (itemCost != null) append(" · " + mealsText(itemCost.toInt().formatDzd(), localeTag))
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
