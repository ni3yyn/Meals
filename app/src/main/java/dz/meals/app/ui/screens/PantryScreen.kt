package dz.meals.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dz.meals.app.data.IngredientMaster
import dz.meals.app.data.PantryItem
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper

@Composable
fun PantryScreen(
    ingredients: List<IngredientMaster>,
    pantry: List<PantryItem>,
    onSave: (String, Double?, Boolean) -> Unit,
    onRemove: (String) -> Unit
) {
    var selectedIngredientId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedIngredient = selectedIngredientId?.let { id -> ingredients.firstOrNull { it.id == id } }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("المخزون المنزلي", style = MaterialTheme.typography.headlineMedium)
            Text("أضف ما لديك في البيت حتى لا نطلب شراءه مرة أخرى.", color = MutedInk)
            Text("المخزون التقريبي مثل «بعض الزيت» يظهر كتذكير فقط ولا يُخصم حسابياً.", color = MutedInk, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { selectedIngredientId = ingredients.firstOrNull()?.id },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Forest)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("إضافة إلى المخزون")
            }
        }

        if (pantry.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.Kitchen, contentDescription = null, tint = Forest, modifier = Modifier.size(48.dp))
                Text("مخزونك فارغ", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
                Text("أضف المكونات المتوفرة لديك لتقليل مشتريات الأسبوع.", color = MutedInk)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pantry, key = { it.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Paper),
                        border = BorderStroke(1.dp, Border),
                        shape = RoundedCornerShape(11.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.nameAr, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    if (item.isApproximate) "كمية تقريبية — لم تخصم من حساب المشتريات"
                                    else (item.quantityBase ?: 0.0).pretty() + " " + item.unit,
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { onRemove(item.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف")
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
            onDismiss = { selectedIngredientId = null },
            onSave = { amount, approximate ->
                onSave(selectedIngredient.id, amount, approximate)
                selectedIngredientId = null
            }
        )
    }
}

@Composable
private fun PantryAddDialog(
    ingredient: IngredientMaster,
    onDismiss: () -> Unit,
    onSave: (Double?, Boolean) -> Unit
) {
    var quantity by rememberSaveable(ingredient.id) { mutableStateOf("") }
    var approximate by rememberSaveable(ingredient.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة " + ingredient.nameAr) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الوحدة الأساسية: " + ingredient.baseUnit, color = MutedInk)
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("الكمية") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !approximate,
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = approximate, onCheckedChange = { approximate = it })
                    Text("لدي كمية تقريبية فقط")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(if (approximate) null else quantity.replace(',', '.').toDoubleOrNull(), approximate) },
                enabled = approximate || ((quantity.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0)
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun Double.pretty(): String =
    if (this % 1.0 == 0.0) toLong().toString() else String.format(java.util.Locale.ROOT, "%.1f", this)
