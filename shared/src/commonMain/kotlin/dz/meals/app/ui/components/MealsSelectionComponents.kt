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
import dz.meals.app.ui.theme.ForestDark
import dz.meals.app.ui.theme.MutedInk

private val SelectionShape = RoundedCornerShape(15.dp)

/**
 * The same source-backed liquid-glass material used by Meals controls:
 * 12dp Haze blur, translucent porcelain face, bright gradient rim,
 * inner highlight and a restrained green-tinted lift.
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
                Color.White.copy(alpha = 0.46f),
                Color(0xFFEAF4EC).copy(alpha = 0.40f),
                Color(0xFFDCEBDD).copy(alpha = 0.42f),
                Color.White.copy(alpha = 0.38f)
            )
        )
    } else {
        MealsLiquidGlass.controlFace()
    }
    val rim = if (selected) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 1f),
                Color.White.copy(alpha = 0.90f),
                Color(0xFFC9DDCE).copy(alpha = 0.78f),
                Color.White.copy(alpha = 0.94f)
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
                ambientColor = Color(0xFF78917F).copy(alpha = 0.12f),
                spotColor = Color(0xFF536D5B).copy(alpha = 0.08f)
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
            .border(BorderStroke(1.15.dp, rim), SelectionShape)
            .padding(1.dp)
            .border(BorderStroke(0.65.dp, MealsLiquidGlass.innerRim()), SelectionShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 1.dp)
                .fillMaxWidth(0.64f)
                .height(1.dp)
                .background(MealsLiquidGlass.specular(), SelectionShape)
        )
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
                targetValue = if (isSelected) ForestDark else MutedInk,
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
                    targetValue = if (isSelected) ForestDark else MutedInk,
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
