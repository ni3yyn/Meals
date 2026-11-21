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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.meals.app.data.ShoppingListItem
import dz.meals.app.data.formatQuantity
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen

@Composable
fun ShoppingScreen(
    items: List<ShoppingListItem>,
    checkedIds: Set<String>,
    onToggleItem: (String) -> Unit,
    onClearPlan: () -> Unit
) {
    val checkedCount = items.count { it.ingredientId in checkedIds }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("قائمة المشتريات", style = MaterialTheme.typography.headlineMedium)
            Text("تُبنى تلقائياً من مكوّنات الوجبات التي اخترتها.", color = MutedInk, style = MaterialTheme.typography.bodyMedium)
            Card(colors = CardDefaults.cardColors(containerColor = SoftGreen), shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(checkedCount.toString() + " من " + items.size + " عناصر", fontWeight = FontWeight.Bold, color = Forest)
                        Text("علّم ما وضعته في سلة التسوق", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        (if (items.isEmpty()) 0 else checkedCount * 100 / items.size).toString() + "%",
                        color = Forest,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (items.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClearPlan) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("مسح الخطة")
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.ShoppingBasket, contentDescription = null, tint = Forest, modifier = Modifier.size(48.dp))
                Text("قائمتك فارغة", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "أضف وصفات إلى خطة الأسبوع وستظهر مشترياتها هنا.",
                    color = MutedInk,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.ingredientId }) { item ->
                    val checked = item.ingredientId in checkedIds
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Paper),
                        border = BorderStroke(1.dp, Border),
                        shape = RoundedCornerShape(11.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { onToggleItem(item.ingredientId) })
                            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                Text(
                                    item.name,
                                    color = if (checked) MutedInk else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    formatQuantity(item.quantity) + " " + item.unit,
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (checked) Text("تم", color = Forest, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
