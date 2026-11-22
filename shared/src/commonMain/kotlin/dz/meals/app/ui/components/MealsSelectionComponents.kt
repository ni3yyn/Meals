package dz.meals.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.MutedInk

private val SelectionShape = RoundedCornerShape(15.dp)

/**
 * The same source-backed glass material used by Meals controls, including
 * the non-uniform outer catchlight and inset optical bevel.
 */
@Composable
private fun MealsSelectionGlass(
    modifier: Modifier = Modifier,
    selected: Boolean,
    content: @Composable () -> Unit
) {
    val hazeState = LocalMealsCardHazeState.current ?: LocalMealsHazeState.current
    val glassBackdrop = LocalMealsCardGlassBackdrop.current ?: LocalMealsScreenGlassBackdrop.current
    val face = if (selected) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.48f),
                Color(0xFFFFFEFC).copy(alpha = 0.40f),
                Color(0xFFF3EFE9).copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.40f)
            )
        )
    } else {
        MealsLiquidGlass.controlFace()
    }
    val rim = if (selected) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.98f),
                Color.White.copy(alpha = 0.76f),
                Color.White.copy(alpha = 0.38f),
                Color.White.copy(alpha = 0.88f)
            )
        )
    } else {
        MealsLiquidGlass.controlRim()
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (selected) 3.dp else 2.dp,
                shape = SelectionShape,
                clip = false,
                ambientColor = Color(0xFFB7AA9F).copy(alpha = 0.13f),
                spotColor = Color(0xFF8F8278).copy(alpha = 0.075f)
            )
            .clip(SelectionShape)
            .mealsGlassBlur(
                backdrop = glassBackdrop,
                hazeState = hazeState,
                blurStyle = MealsLiquidGlass.ControlBlurStyle,
                shape = SelectionShape,
                blurRadius = MealsLiquidGlass.ControlBlurRadius
            )
            .background(face, SelectionShape)
            .mealsOpticalEdges(
                shape = SelectionShape,
                baseRim = rim,
                depth = 1f,
                sideGlintHeight = 17.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Horizontally scrollable reusable liquid-glass filter chips.
 * Each option shares the same blur, translucent face and optical rim
 * as Meals' buttons and icon actions.
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
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) Ink else MutedInk,
                animationSpec = tween(160),
                label = "mealsFilterChipContent"
            )
            MealsSelectionGlass(selected = isSelected) {
                MealsPressable(onClick = { onSelect(item) }, role = Role.RadioButton) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(item, color = contentColor, style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Reusable glass subtab rail. The rail is frosted glass and the active
 * destination receives its own raised glass lens instead of a flat underline.
 */
@Composable
fun MealsSubtabsIndicator(
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    MealsSelectionGlass(
        modifier = modifier.fillMaxWidth(),
        selected = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Ink else MutedInk,
                    animationSpec = tween(170),
                    label = "mealsSubtabText"
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (isSelected) {
                        MealsSelectionGlass(
                            modifier = Modifier.fillMaxWidth(),
                            selected = true
                        ) {
                            MealsPressable(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth(), role = Role.Tab) {
                                Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(item, color = textColor, style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    } else {
                        MealsPressable(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth(), role = Role.Tab, shape = SelectionShape) {
                            Box(Modifier.padding(horizontal = 10.dp, vertical = 11.dp), contentAlignment = Alignment.Center) {
                                Text(item, color = textColor, style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
