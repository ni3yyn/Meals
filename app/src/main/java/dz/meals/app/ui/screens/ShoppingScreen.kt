package dz.meals.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import dz.meals.app.data.IngredientMaster
import dz.meals.app.data.MealsSnapshot
import dz.meals.app.data.PriceObservationSummary
import dz.meals.app.data.ShoppingListItem
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ShoppingScreen(
    snapshot: MealsSnapshot,
    ingredients: List<IngredientMaster>,
    wilaya: String,
    onToggleItem: (String, Boolean) -> Unit,
    onAddCustomItem: (String, Double, String) -> Unit,
    onEditCustomItem: (String, String, Double, String) -> Unit,
    onUpdateItemDetails: (String, Double?, String, Boolean, Double?) -> Unit,
    onDeleteCustomItem: (String) -> Unit,
    onRecordPrice: (String, Double, Double, String) -> Unit
) {
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var editItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var priceDialog by rememberSaveable { mutableStateOf(false) }
    var itemName by rememberSaveable { mutableStateOf("") }
    var itemQuantity by rememberSaveable { mutableStateOf("1") }
    var itemUnit by rememberSaveable { mutableStateOf("حبة") }
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

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("قائمة المشتريات", style = MaterialTheme.typography.headlineMedium)
            Text("المطلوب بعد خصم المخزون الدقيق، مع تقريب العبوات الكاملة عند توفر حجمها.", color = MutedInk, style = MaterialTheme.typography.bodyMedium)
            Card(colors = CardDefaults.cardColors(containerColor = SoftGreen), shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(checkedCount.toString() + " من " + rows.size + " عناصر", fontWeight = FontWeight.Bold, color = Forest)
                        Text("العناصر التي وضعتها في السلة", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        (if (rows.isEmpty()) 0 else checkedCount * 100 / rows.size).toString() + "%",
                        color = Forest,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "مبلغ شراء تقديري: " + snapshot.basket.purchaseOutlayDzd.toInt().asDzd(),
                    fontWeight = FontWeight.Bold,
                    color = Forest
                )
                TextButton(onClick = { addDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("عنصر يدوي")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("حسّن تقديرات منطقتك", fontWeight = FontWeight.SemiBold)
                    Text("أضف سعراً شاهدته في محل أو سوق في $wilaya.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { priceDialog = true }) { Text("إبلاغ عن سعر") }
            }
            if (snapshot.basket.hasDemoPrices) {
                Text("بعض القيم تجريبية وغير موثّقة. لا تمثل سعراً مباشراً في ولايتك.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
            }
            if (snapshot.priceObservations.isNotEmpty()) {
                Text("آخر الأسعار المسجلة على جهازك", fontWeight = FontWeight.SemiBold)
                snapshot.priceObservations.take(3).forEach { PriceObservationRow(it) }
                Text(
                    "البلاغات تحفظ محلياً في هذا الإصدار ولا تُرسل إلى مستخدمين آخرين أو تُعرض كسعر رسمي.",
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.ShoppingBasket, contentDescription = null, tint = Forest, modifier = Modifier.size(48.dp))
                Text("قائمتك فارغة", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Text("أضف وصفات إلى خطة الأسبوع أو أضف عنصراً يدوياً.", color = MutedInk, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            val grouped = rows.groupBy { it.category }.toSortedMap()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (category, categoryItems) ->
                    item(key = "category-$category") {
                        Text(
                            category,
                            style = MaterialTheme.typography.titleLarge,
                            color = Forest,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(categoryItems, key = { it.id }) { item ->
                        ShoppingItemCard(
                            item = item,
                            onToggle = { onToggleItem(item.id, !item.checked) },
                            onEdit = {
                                editItemId = item.id
                                editName = item.name
                                editQuantity = item.purchaseQuantity.pretty()
                                editUnit = item.unit
                                editPurchaseQuantity = item.purchaseQuantity.pretty()
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
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("إضافة عنصر يدوي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("اسم العنصر") }, singleLine = true)
                    OutlinedTextField(
                        value = itemQuantity,
                        onValueChange = { itemQuantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("الكمية") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(value = itemUnit, onValueChange = { itemUnit = it.take(12) }, label = { Text("الوحدة") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val quantity = itemQuantity.replace(',', '.').toDoubleOrNull()
                        if (quantity != null && quantity > 0 && itemName.isNotBlank()) {
                            onAddCustomItem(itemName, quantity, itemUnit)
                            itemName = ""
                            itemQuantity = "1"
                            itemUnit = "حبة"
                            addDialog = false
                        }
                    },
                    enabled = itemName.isNotBlank() && (itemQuantity.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0
                ) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("إلغاء") } }
        )
    }

    if (editItemId != null) {
        AlertDialog(
            onDismissRequest = { editItemId = null },
            title = { Text("تعديل العنصر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editUserAdded) {
                        OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("اسم العنصر") }, singleLine = true)
                        OutlinedTextField(
                            value = editQuantity,
                            onValueChange = { editQuantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            label = { Text("الكمية المطلوبة") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(value = editUnit, onValueChange = { editUnit = it.take(12) }, label = { Text("الوحدة") }, singleLine = true)
                    } else {
                        OutlinedTextField(
                            value = editPurchaseQuantity,
                            onValueChange = { editPurchaseQuantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            label = { Text("كمية الشراء المعدلة (اتركها فارغة لإعادة التلقائي)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = editStoreNote,
                        onValueChange = { editStoreNote = it.take(100) },
                        label = { Text("ملاحظة للمحل") },
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editUnavailable, onCheckedChange = { editUnavailable = it })
                        Text("غير متوفر حالياً")
                    }
                    OutlinedTextField(
                        value = editActualSpent,
                        onValueChange = { editActualSpent = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("ما دفعته فعلياً (دج، اختياري)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
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
            dismissButton = { TextButton(onClick = { editItemId = null }) { Text("إلغاء") } }
        )
    }

    if (priceDialog) {
        PriceReportDialog(
            ingredients = ingredients,
            wilaya = wilaya,
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
    onDismiss: () -> Unit,
    onSubmit: (String, Double, Double, String) -> Unit
) {
    var selectedId by rememberSaveable { mutableStateOf(ingredients.firstOrNull()?.id.orEmpty()) }
    var ingredientMenu by rememberSaveable { mutableStateOf(false) }
    var packQuantityText by rememberSaveable { mutableStateOf("1") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var shopContext by rememberSaveable { mutableStateOf("سوق / بقالة") }
    val selectedIngredient = ingredients.firstOrNull { it.id == selectedId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل ملاحظة سعر") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { ingredientMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedIngredient?.nameAr ?: "اختر المكوّن")
                    }
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
                    "الوحدة الأساسية: " + (selectedIngredient?.baseUnit ?: "—") +
                        " · الولاية: " + wilaya,
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = packQuantityText,
                    onValueChange = { packQuantityText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("حجم العبوة أو الكمية المباعة (" + (selectedIngredient?.baseUnit ?: "وحدة") + ")") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("السعر بالدينار الجزائري") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text("دج") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = shopContext,
                    onValueChange = { shopContext = it.take(60) },
                    label = { Text("نوع المحل أو اسمه") },
                    singleLine = true
                )
                Text(
                    "سيُحفظ البلاغ محلياً بتاريخ اليوم، وسيظهر كملاحظة مستخدم غير موثّقة وليس كسعر رسمي أو مباشر.",
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            val packQuantity = packQuantityText.replace(',', '.').toDoubleOrNull()
            val amount = priceText.replace(',', '.').toDoubleOrNull()
            TextButton(
                onClick = {
                    if (selectedIngredient != null && packQuantity != null && amount != null) {
                        onSubmit(selectedIngredient.id, packQuantity, amount, shopContext)
                    }
                },
                enabled = selectedIngredient != null && (packQuantity ?: 0.0) > 0.0 && (amount ?: 0.0) > 0.0
            ) { Text("حفظ البلاغ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun PriceObservationRow(price: PriceObservationSummary) {
    val date = price.observedAtEpochMillis?.let {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
    } ?: "بدون تاريخ"
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(price.ingredientNameAr, fontWeight = FontWeight.Medium)
            Text(price.packPriceDzd.toInt().asDzd(), fontWeight = FontWeight.Bold, color = Forest)
        }
        Text(
            price.packQuantityBase.pretty() + " " + price.unit + " · " +
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
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name,
                    color = if (item.checked) MutedInk else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "المطلوب " + item.requiredQuantity.pretty() + " " + item.unit +
                        if (item.pantryQuantity > 0) " · في المخزون " + item.pantryQuantity.pretty() else "",
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall
                )
                if (item.isApproximatePantry) {
                    Text("يوجد مخزون تقريبي؛ لم يُخصم من الكمية المطلوبة.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "شراء " + item.purchaseQuantity.pretty() + " " + item.unit +
                        " · " + (item.cashOutlayDzd?.toInt()?.asDzd() ?: "السعر غير متوفر"),
                    color = Forest,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
                item.sourceLabel?.let { Text(it, color = MutedInk, style = MaterialTheme.typography.labelSmall) }
                if (item.isUnavailable) {
                    Text("غير متوفر حالياً في المحل", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                }
                if (item.storeNote.isNotBlank()) {
                    Text("ملاحظة: " + item.storeNote, color = MutedInk, style = MaterialTheme.typography.bodySmall)
                }
                item.actualSpentDzd?.let {
                    Text("المبلغ الفعلي: " + it.toInt().asDzd(), color = Forest, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column {
                TextButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "تعديل") }
                if (item.userAdded) {
                    TextButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف") }
                }
            }
        }
    }
}

private fun Double.pretty(): String =
    if (this % 1.0 == 0.0) toLong().toString() else String.format(Locale.ROOT, "%.1f", this)

private fun Int.asDzd(): String =
    NumberFormat.getIntegerInstance(Locale("fr", "DZ")).format(this) + " دج"
