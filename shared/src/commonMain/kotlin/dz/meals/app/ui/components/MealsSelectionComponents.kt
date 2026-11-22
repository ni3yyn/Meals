package dz.meals.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.ForestDark
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen

/**
 * Compact, reusable selection chips for filters and short option lists.
 * The selected state is visibly filled; unselected chips remain quiet and
 * readable without relying on Material's default chip treatment.
 */
@Composable
fun MealsFilterChips(
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) SoftGreen.copy(alpha = 0.96f) else Paper.copy(alpha = 0.68f),
                animationSpec = tween(160),
                label = "mealsFilterChipColor"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) ForestDark else MutedInk,
                animationSpec = tween(160),
                label = "mealsFilterChipContent"
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(containerColor)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Forest.copy(alpha = 0.38f) else Border.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(15.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton
                    ) { onSelect(item) }
                    .padding(horizontal = 15.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * A lightweight reusable subtab row with a shared animated underline.
 * Equal-width targets keep the indicator stable when labels change.
 */
@Composable
fun MealsSubtabsIndicator(
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val textColor by animateColorAsState(
                targetValue = if (isSelected) ForestDark else MutedInk,
                animationSpec = tween(170),
                label = "mealsSubtabText"
            )
            val indicatorColor by animateColorAsState(
                targetValue = if (isSelected) Forest else Color.Transparent,
                animationSpec = tween(190),
                label = "mealsSubtabIndicator"
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab
                    ) { onSelect(item) }
                    .padding(top = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = item,
                    color = textColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(indicatorColor, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}
