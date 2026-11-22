package dz.meals.app.ui.screens

import androidx.compose.ui.graphics.Color
import dz.meals.app.ui.components.MealsIconAction
import dz.meals.app.ui.components.MealsSecondaryButton
import dz.meals.app.ui.components.MealsTextButton
import dz.meals.app.ui.theme.ForestDark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import dz.meals.app.ui.components.MealsAlertDialog
import dz.meals.app.ui.components.MealsLiquidGlassTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dz.meals.app.ui.components.MealsCard
import dz.meals.app.ui.components.MealsCheckbox
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.ui.components.MealsProgressBar
import dz.meals.app.ui.components.MealsIllustrationBadge
import dz.meals.app.data.IngredientMaster
import dz.meals.app.data.MealsSnapshot
import dz.meals.app.data.PriceObservationSummary
import dz.meals.app.data.ShoppingListItem
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen
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

    val checkedCount = snapshot.shoppingItems.count { it.checked }
    val rows = snapshot.shoppingItems
    val remainingCount = rows.size - checkedCount
    val visibleRows = if (showPurchased) rows else rows.filterNot { it.checked }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 10.dp,
            bottom = 122.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "shopping-overview") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("قائمة المشتريات", style = MaterialTheme.typography.headlineMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MealsTextButton(
                            onClick = { onShareList(buildShoppingShareText(rows, snapshot)) },
                            enabled = rows.isNotEmpty()
                        ) {
                            Text("مشاركة")
                        }
                        MealsTextButton(onClick = { addDialog = true }) {
                            MealsIcon(MealsIconName.Add, contentDescription = null, modifier = Modifier.size(19.dp))
                            Text("إضافة")
                        }
                    }
                }

                MealsCard(
                    containerColor = SoftGreen,
                    borderColor = null,
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                if (rows.isEmpty()) "لا توجد عناصر بعد"
                                else "$remainingCount متبقٍ من ${rows.size}",
                                fontWeight = FontWeight.Bold,
                                color = Forest
                            )
                            if (rows.isNotEmpty()) {
                                Text(
                                    "$checkedCount تم جمعها",
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                MealsProgressBar(
                                    progress = checkedCount.toFloat() / rows.size.toFloat()
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "الشراء المتوقع",
                                color = MutedInk,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                snapshot.basket.purchaseOutlayDzd.toInt().formatDzd(),
                                fontWeight = FontWeight.Bold,
                                color = Forest,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (snapshot.basket.actualSpendDzd > 0.0) {
                                Text(
                                    "المدفوع ${snapshot.basket.actualSpendDzd.toInt().formatDzd()}",
                                    color = MutedInk,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    if (snapshot.basket.hasDemoPrices || snapshot.basket.unknownPriceCount > 0) {
                        Text(
                            text = when {
                                snapshot.basket.hasDemoPrices && snapshot.basket.unknownPriceCount > 0 ->
                                    "الأسعار تقديرية · ${snapshot.basket.unknownPriceCount} عناصر دون سعر"
                                snapshot.basket.hasDemoPrices ->
                                    "الأسعار تقديرية وليست أسعار سوق مباشرة"
                                else ->
                                    "${snapshot.basket.unknownPriceCount} عناصر دون سعر"
                            },
                            color = dz.meals.app.ui.theme.ForestDark,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MealsTextButton(onClick = { priceDialog = true }) {
                        Text("تسجيل سعر")
                    }
                    if (snapshot.priceObservations.isNotEmpty()) {
                        MealsTextButton(onClick = { showPriceHistory = !showPriceHistory }) {
                            Text(if (showPriceHistory) "إخفاء آخر الأسعار" else "آخر الأسعار")
                        }
                    }
                }

                if (showPriceHistory && snapshot.priceObservations.isNotEmpty()) {
                    MealsCard(
                        containerColor = Paper,
                        borderColor = Border,
                        contentPadding = 12.dp
                    ) {
                        snapshot.priceObservations.take(3).forEach { PriceObservationRow(it) }
                        Text(
                            "تُحفظ ملاحظات الأسعار على هذا الجهاز فقط، ولا تمثل أسعاراً رسمية.",
                            color = MutedInk,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        if (rows.isNotEmpty()) {
            item(key = "shopping-filter") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MealsTextButton(onClick = { showPurchased = false }) {
                        Text(
                            "المتبقي ($remainingCount)",
                            color = if (!showPurchased) Forest else MutedInk,
                            fontWeight = if (!showPurchased) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                    MealsTextButton(onClick = { showPurchased = true }) {
                        Text(
                            "كل العناصر (${rows.size})",
                            color = if (showPurchased) Forest else MutedInk,
                            fontWeight = if (showPurchased) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        when {
            rows.isEmpty() -> {
                item(key = "shopping-empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MealsIllustrationBadge(icon = MealsIconName.Basket, size = 60.dp)
                        Text("قائمتك فارغة", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "أضيفي وصفات إلى خطة الأسبوع أو أضيفي عنصراً يدوياً.",
                            color = MutedInk,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            visibleRows.isEmpty() -> {
                item(key = "shopping-all-collected") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MealsIllustrationBadge(icon = MealsIconName.Basket, size = 60.dp)
                        Text("اكتملت القائمة", style = MaterialTheme.typography.titleLarge)
                        Text("جمعتِ كل العناصر المطلوبة.", color = MutedInk)
                        MealsTextButton(onClick = { showPurchased = true }) {
                            Text("عرض العناصر التي جمعتها")
                        }
                    }
                }
            }
            else -> {
                val grouped: Map<String, List<ShoppingListItem>> =
                    visibleRows.groupBy { row: ShoppingListItem -> row.category }
                val categoryNames: List<String> = grouped.keys.sorted()
                categoryNames.forEach { category ->
                    val categoryItems = grouped.getValue(category)
                    item(key = "category-$category") {
                        Text(
                            category,
                            style = MaterialTheme.typography.titleMedium,
                            color = Forest,
                            modifier = Modifier.padding(top = 6.dp, bottom = 1.dp)
                        )
                    }
                    items(categoryItems, key = { it.id }) { item ->
                        ShoppingItemCard(
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
    if (addDialog) {
        MealsAlertDialog(
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
                MealsTextButton(
                    onClick = {
                        val quantity = itemQuantity.replace(',', '.').toDoubleOrNull()
                        val estimatedPrice = itemEstimatedPrice.takeIf { it.isNotBlank() }
                            ?.replace(',', '.')?.toDoubleOrNull()
                        if (quantity != null && quantity > 0 && itemName.isNotBlank() &&
                            (itemEstimatedPrice.isBlank() || (estimatedPrice != null && estimatedPrice >= 0.0))
                        ) {
                            onAddCustomItem(itemName, quantity, itemUnit, estimatedPrice)
                            itemName = ""
                            itemQuantity = "1"
                            itemUnit = "حبة"
                            itemEstimatedPrice = ""
                            addDialog = false
                        }
                    },
                    enabled = itemName.isNotBlank() &&
                        (itemQuantity.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 &&
                        (itemEstimatedPrice.isBlank() || (itemEstimatedPrice.replace(',', '.').toDoubleOrNull() ?: -1.0) >= 0.0)
                ) { Text("إضافة") }
            },
            dismissButton = { MealsSecondaryButton(label = "إلغاء", onClick = { addDialog = false }) }
        )
    }

    if (editItemId != null) {
        MealsAlertDialog(
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
                MealsTextButton(
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
                            if (editUserAdded && quantity != null) onEditCustomItem(id, editName, quantity, editUnit)
                            onUpdateItemDetails(id, purchaseQuantity, editStoreNote, editUnavailable, actualSpent)
                            editItemId = null
                        }
                    },
                    enabled = (!editUserAdded || (editName.isNotBlank() && (editQuantity.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0)) &&
                        (editPurchaseQuantity.isBlank() || (editPurchaseQuantity.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0) &&
                        (editActualSpent.isBlank() || (editActualSpent.replace(',', '.').toDoubleOrNull() ?: -1.0) >= 0.0)
                ) { Text("حفظ التعديل") }
            },
            dismissButton = { MealsSecondaryButton(label = "إلغاء", onClick = { editItemId = null }) }
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

    MealsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل ملاحظة سعر") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    MealsSecondaryButton(
                        label = selectedIngredient?.nameAr ?: "اختر المكوّن",
                        onClick = { ingredientMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = ingredientMenu,
                        onDismissRequest = { ingredientMenu = false },
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        ingredients.forEach { ingredient ->
                            DropdownMenuItem(
                                text = { Text(ingredient.nameAr + " · " + ingredient.nameFr) },
                                onClick = {
                                    selectedId = ingredient.id
                                    packQuantityText = "1"
                                    ingredientMenu = false
                                }
                            )
                        }
                    }
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
            val packQuantity = packQuantityText.replace(',', '.').toDoubleOrNull()
            val amount = priceText.replace(',', '.').toDoubleOrNull()
            MealsTextButton(
                onClick = {
                    if (selectedIngredient != null && packQuantity != null && amount != null) {
                        val baseUnitMultiplier = when (selectedIngredient.baseUnit) {
                            "g", "ml" -> 1000.0
                            else -> 1.0
                        }
                        onSubmit(selectedIngredient.id, packQuantity * baseUnitMultiplier, amount, shopContext)
                    }
                },
                enabled = selectedIngredient != null && (packQuantity ?: 0.0) > 0.0 && (amount ?: 0.0) > 0.0
            ) { Text("حفظ البلاغ") }
        },
        dismissButton = { MealsSecondaryButton(label = "إلغاء", onClick = onDismiss) }
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
            Text(price.packPriceDzd.toInt().formatDzd(), fontWeight = FontWeight.Bold, color = Forest)
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
private fun ShoppingItemCard(
    item: ShoppingListItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    MealsCard(
        containerColor = Paper,
        borderColor = Border,
        shape = RoundedCornerShape(20.dp),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MealsCheckbox(
                checked = item.checked,
                onCheckedChange = { onToggle() }
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 5.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    item.name,
                    color = if (item.checked) MutedInk else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "شراء " + item.purchaseQuantity.formatHouseholdQuantity(item.unit),
                        color = if (item.checked) MutedInk else Forest,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        item.cashOutlayDzd?.toInt()?.formatDzd() ?: "السعر غير متوفر",
                        color = MutedInk,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                if (item.pantryQuantity > 0.0) {
                    Text(
                        "المطلوب ${item.requiredQuantity.formatHouseholdQuantity(item.unit)} · في المخزون ${item.pantryQuantity.formatHouseholdQuantity(item.unit)}",
                        color = MutedInk,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                if (item.isApproximatePantry) {
                    Text(
                        "مخزون تقريبي لم يُخصم من الكمية",
                        color = MutedInk,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (item.isUnavailable || item.storeNote.isNotBlank()) {
                    Text(
                        listOfNotNull(
                            if (item.isUnavailable) "غير متوفر" else null,
                            item.storeNote.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        color = MutedInk,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                item.actualSpentDzd?.let {
                    Text(
                        "المدفوع ${it.toInt().formatDzd()}",
                        color = Forest,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MealsIconAction(onClick = onEdit) {
                    MealsIcon(MealsIconName.Edit, contentDescription = "تعديل", modifier = Modifier.size(20.dp), tint = ForestDark)
                }
                if (item.userAdded) {
                    MealsIconAction(onClick = onDelete) {
                        MealsIcon(MealsIconName.Delete, contentDescription = "حذف", modifier = Modifier.size(20.dp), tint = Color(0xFFB65E53))
                    }
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
