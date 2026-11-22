package dz.meals.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import dz.meals.app.ui.components.MealsGlassDropdown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import dz.meals.app.ui.components.MealsBottomSheet
import dz.meals.app.ui.components.mealsSheetDismissAction
import dz.meals.app.ui.components.MealsCard
import dz.meals.app.ui.components.MealsCheckbox
import dz.meals.app.ui.components.MealsGlassSurface
import dz.meals.app.ui.components.MealsIconButton
import dz.meals.app.ui.components.MealsIllustrationBadge
import dz.meals.app.ui.components.MealsLiquidGlassTextField
import dz.meals.app.ui.components.MealsPrimaryButton
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
import dz.meals.app.ui.theme.SoftPink
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
    onEditCustomItem: (String, String, Double, String) -> Unit,
    onUpdateItemDetails: (String, Double?, String, Boolean, Double?) -> Unit,
    onDeleteCustomItem: (String) -> Unit,
    onRecordPrice: (String, Double, Double, String) -> Unit,
    onShareList: (String) -> Unit
) {
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var showPurchased by rememberSaveable { mutableStateOf(false) }
    var showPriceHistory by rememberSaveable { mutableStateOf(false) }
    var editItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var priceDialog by rememberSaveable { mutableStateOf(false) }
    var itemName by rememberSaveable { mutableStateOf("") }
    var itemQuantity by rememberSaveable { mutableStateOf("1") }
    var itemUnit by rememberSaveable { mutableStateOf("حبة") }
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
    // Remember the prior IDs so only genuinely new rows receive the arrival motion.
    val previousShoppingItemIds = remember { mutableStateOf(rows.map { it.id }.toSet()) }
    val currentShoppingItemIds = rows.map { it.id }.toSet()
    val newlyAddedItemIds = currentShoppingItemIds - previousShoppingItemIds.value
    SideEffect { previousShoppingItemIds.value = currentShoppingItemIds }
    val checkedCount = rows.count { it.checked }
    val remainingCount = rows.size - checkedCount
    val visibleRows = if (showPurchased) rows else rows.filterNot { it.checked }
    val categoryNames = visibleRows.map { it.category }.distinct().sorted()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 12.dp,
            bottom = 122.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "shopping-header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("قائمة التسوق", style = MaterialTheme.typography.headlineMedium, color = dz.meals.app.ui.theme.Ink)
                    Text(
                        "احتياجات الأسبوع في مكان واحد",
                        color = MutedInk,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MealsPrimaryButton(
                        label = "إضافة",
                        onClick = { addDialog = true },
                        modifier = Modifier.weight(1f),
                        icon = MealsIconName.Add
                    )
                    MealsSecondaryButton(
                        label = "مشاركة",
                        onClick = { onShareList(buildShoppingShareText(rows, snapshot)) },
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
                contentPadding = 16.dp,
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
                            onClick = { addDialog = true },
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
                categoryNames.forEach { category ->
                    val categoryRows = visibleRows.filter { it.category == category }
                    item(key = "category-$category") {
                        MealsCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Paper,
                            borderColor = Border,
                            contentPadding = 0.dp,
                            shape = RoundedCornerShape(22.dp),
                            shadowElevation = 1.dp
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedCategories[category] = !(expandedCategories[category] ?: true)
                                        }
                                        .padding(horizontal = 15.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        category,
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
                                        contentDescription = if (expandedCategories[category] == false) "توسيع القسم" else "طي القسم",
                                        modifier = Modifier
                                            .padding(start = 7.dp)
                                            .size(21.dp)
                                            .graphicsLayer { rotationZ = if (expandedCategories[category] == false) 0f else 90f }
                                    )
                                }
                                AnimatedVisibility(
                                    visible = expandedCategories[category] != false,
                                    enter = expandVertically(animationSpec = androidx.compose.animation.core.tween(230)) +
                                        fadeIn(animationSpec = androidx.compose.animation.core.tween(160)),
                                    exit = shrinkVertically(animationSpec = androidx.compose.animation.core.tween(210)) +
                                        fadeOut(animationSpec = androidx.compose.animation.core.tween(120))
                                ) {
                                    Column(Modifier.animateContentSize(animationSpec = androidx.compose.animation.core.tween(220))) {
                                        categoryRows.forEachIndexed { index, item ->
                                            if (index > 0) {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp)
                                                        .height(1.dp)
                                                        .background(Border.copy(alpha = 0.78f))
                                                )
                                            }
                                            val isNewlyAdded = remember(item.id) { item.id in newlyAddedItemIds }
                                            AnimatedVisibility(
                                                visible = true,
                                                enter = if (isNewlyAdded) {
                                                    slideInHorizontally(
                                                        initialOffsetX = { width -> width / 3 },
                                                        animationSpec = spring(dampingRatio = 0.74f, stiffness = 430f)
                                                    ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) +
                                                        scaleIn(
                                                            initialScale = 0.94f,
                                                            animationSpec = spring(dampingRatio = 0.76f, stiffness = 430f)
                                                        )
                                                } else EnterTransition.None,
                                                exit = ExitTransition.None
                                            ) {
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
                                                        editUserAdded = item.userAdded
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

    if (addDialog) {
        MealsBottomSheet(
            onDismissRequest = { addDialog = false },
            title = { Text("إضافة عنصر يدوي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealsLiquidGlassTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("اسم العنصر") }, singleLine = true)
                    MealsLiquidGlassTextField(
                        value = itemQuantity,
                        onValueChange = { itemQuantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("الكمية") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    MealsLiquidGlassTextField(value = itemUnit, onValueChange = { itemUnit = it.take(12) }, label = { Text("الوحدة") }, singleLine = true)
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
                        val quantity = itemQuantity.replace(',', '.').toDoubleOrNull()
                        val estimatedPrice = itemEstimatedPrice.takeIf { it.isNotBlank() }
                            ?.replace(',', '.')?.toDoubleOrNull()
                        if (quantity != null && quantity > 0 && itemName.isNotBlank() &&
                            (itemEstimatedPrice.isBlank() || (estimatedPrice != null && estimatedPrice >= 0.0))
                        ) {
                            dismissSheet {
                                addedFeedback = itemName.trim()
                                onAddCustomItem(itemName, quantity, itemUnit, estimatedPrice)
                                itemName = ""
                                itemQuantity = "1"
                                itemUnit = "حبة"
                                itemEstimatedPrice = ""
                                addDialog = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = itemName.isNotBlank() &&
                        (itemQuantity.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 &&
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
                    MealsLiquidGlassTextField(
                        value = editStoreNote,
                        onValueChange = { editStoreNote = it.take(100) },
                        label = { Text("ملاحظة للمحل") },
                        singleLine = true
                    )
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
    var selectedId by rememberSaveable { mutableStateOf(ingredients.firstOrNull()?.id.orEmpty()) }
    var ingredientMenu by rememberSaveable { mutableStateOf(false) }
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
                        optionLabel = { it.nameAr },
                        optionSupportingText = { it.nameFr },
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
                    "الوحدة الأساسية: " + (selectedIngredient?.baseUnit?.let { formatHouseholdUnit(it) } ?: "—") +
                        " · الولاية: " + wilaya,
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall
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
            price.packQuantityBase.formatHouseholdQuantity(price.unit) + " · " +
                price.locationLabel + " · " + date,
            color = MutedInk,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            price.sourceLabel + " · " +
                if (price.priceKind == "demo") "قيمة تجريبية" else "ملاحظة مستخدم غير موثّقة",
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
    var dragOffset by remember(item.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(item.id) { mutableStateOf(false) }
    var isActionRevealed by remember(item.id) { mutableStateOf(false) }
    val settledOffset by animateFloatAsState(
        targetValue = if (isActionRevealed) -actionWidthPx else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f),
        label = "shoppingSwipeSettle"
    )
    val dragState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceIn(-actionWidthPx, 0f)
    }
    val foregroundOffset = if (isDragging) dragOffset else settledOffset

    Box(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(vertical = 7.dp)
                .size(width = actionWidth, height = 52.dp)
                .background(SoftPink.copy(alpha = 0.78f), RoundedCornerShape(14.dp)),
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
                    onDragStarted = { isDragging = true },
                    onDragStopped = {
                        isDragging = false
                        isActionRevealed = dragOffset < -actionWidthPx * 0.42f
                        dragOffset = 0f
                    }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            MealsCheckbox(
                checked = item.checked,
                onCheckedChange = { onToggle() }
            )
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = onEdit),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    item.name,
                    color = if (item.checked) MutedInk else dz.meals.app.ui.theme.Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        item.purchaseQuantity.formatHouseholdQuantity(item.unit),
                        color = if (item.checked) MutedInk else BodyInk,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    Text("·", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                    Text(
                        item.cashOutlayDzd?.toInt()?.formatDzd() ?: "السعر غير متوفر",
                        color = MutedInk,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.pantryQuantity > 0.0) {
                    Text(
                        "المطلوب " + item.requiredQuantity.formatHouseholdQuantity(item.unit) +
                            " · في المخزون " + item.pantryQuantity.formatHouseholdQuantity(item.unit),
                        color = MutedInk,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.isApproximatePantry || item.isUnavailable || item.storeNote.isNotBlank()) {
                    Text(
                        listOfNotNull(
                            if (item.isApproximatePantry) "مخزون تقريبي" else null,
                            if (item.isUnavailable) "غير متوفر" else null,
                            item.storeNote.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        color = MutedInk,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item.actualSpentDzd?.let {
                    Text(
                        "المدفوع " + it.toInt().formatDzd(),
                        color = BodyInk,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}


private fun buildShoppingShareText(
    rows: List<ShoppingListItem>,
    snapshot: MealsSnapshot
): String = buildString {
    appendLine("قائمة مشتريات مائدتي")
    appendLine("التكلفة التقديرية: " + snapshot.basket.purchaseOutlayDzd.toInt().formatDzd())
    if (snapshot.basket.actualSpendDzd > 0.0) {
        appendLine("الإنفاق الفعلي المُدخل: " + snapshot.basket.actualSpendDzd.toInt().formatDzd())
    }
    appendLine()

    val groupedRows: Map<String, List<ShoppingListItem>> =
        rows.groupBy { row: ShoppingListItem -> row.category }

    groupedRows.keys.sorted().forEach { category: String ->
        appendLine(category)
        val categoryRows: List<ShoppingListItem> = groupedRows.getValue(category)
        categoryRows.forEach { item: ShoppingListItem ->
            append(if (item.checked) "[x] " else "[ ] ")
            append(item.name)
            append(" — ")
            append(item.purchaseQuantity.formatHouseholdQuantity(item.unit))
            val itemCost: Double? = item.cashOutlayDzd
            if (itemCost != null) append(" · " + itemCost.toInt().formatDzd())
            if (item.isUnavailable) append(" · غير متوفر")
            if (item.storeNote.isNotBlank()) append(" · " + item.storeNote)
            appendLine()
        }
        appendLine()
    }
    if (snapshot.basket.unknownPriceCount > 0) {
        appendLine("تنبيه: يوجد " + snapshot.basket.unknownPriceCount + " عنصر دون سعر مؤكد.")
    }
    append("الأسعار تقديرية وليست ضماناً لسعر المحل أو التوفر.")
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
