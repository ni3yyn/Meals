package dz.meals.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import dz.meals.app.data.MealsSnapshot
import dz.meals.app.data.ShoppingListItem
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ShoppingScreen(
    snapshot: MealsSnapshot,
    onToggleItem: (String, Boolean) -> Unit,
    onAddCustomItem: (String, Double, String) -> Unit,
    onDeleteCustomItem: (String) -> Unit
) {
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var itemName by rememberSaveable { mutableStateOf("") }
    var itemQuantity by rememberSaveable { mutableStateOf("1") }
    var itemUnit by rememberSaveable { mutableStateOf("حبة") }

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
                    "مبلغ شراء توضيحي: " + snapshot.basket.purchaseOutlayDzd.toInt().asDzd(),
                    fontWeight = FontWeight.Bold,
                    color = Forest
                )
                TextButton(onClick = { addDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("عنصر يدوي")
                }
            }
            if (snapshot.basket.hasDemoPrices) {
                Text("الأسعار تجريبية وغير موثّقة. لا تمثل سعراً مباشراً في ولايتك.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows, key = { it.id }) { item ->
                    ShoppingItemCard(
                        item = item,
                        onToggle = { onToggleItem(item.id, !item.checked) },
                        onDelete = { onDeleteCustomItem(item.id) }
                    )
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
}

@Composable
private fun ShoppingItemCard(
    item: ShoppingListItem,
    onToggle: () -> Unit,
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
            }
            if (item.userAdded) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف العنصر")
                }
            }
        }
    }
}

private fun Double.pretty(): String =
    if (this % 1.0 == 0.0) toLong().toString() else String.format(Locale.ROOT, "%.1f", this)

private fun Int.asDzd(): String =
    NumberFormat.getIntegerInstance(Locale("fr", "DZ")).format(this) + " دج"
