package dz.meals.app.ui.components

import dz.meals.app.ui.components.MealsText as Text

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
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
fun MealsSelectionGlass(
    modifier: Modifier = Modifier,
    selected: Boolean,
    prominent: Boolean = false,
    emphasizeSelectedFace: Boolean = false,
    content: @Composable () -> Unit
) {
    val hazeState = LocalMealsCardHazeState.current ?: LocalMealsHazeState.current
    val glassBackdrop = LocalMealsCardGlassBackdrop.current ?: LocalMealsScreenGlassBackdrop.current
    val face = when {
        prominent -> Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.86f),
                0.18f to Color(0xFFFFFEFC).copy(alpha = 0.78f),
                0.62f to Color(0xFFFFFCF7).copy(alpha = 0.68f),
                1f to Color(0xFFF1EBE3).copy(alpha = 0.62f)
            )
        )
        selected && emphasizeSelectedFace -> Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.86f),
                0.18f to Color(0xFFFFFEFC).copy(alpha = 0.82f),
                0.62f to Color(0xFFFFFCF7).copy(alpha = 0.76f),
                1f to Color(0xFFF1EBE3).copy(alpha = 0.70f)
            )
        )
        selected -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.48f),
                Color(0xFFFFFEFC).copy(alpha = 0.40f),
                Color(0xFFF3EFE9).copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.40f)
            )
        )
        else -> MealsLiquidGlass.controlFace()
    }
    val rim = when {
        prominent -> Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 1f),
                0.20f to Color.White.copy(alpha = 0.94f),
                0.56f to Color.White.copy(alpha = 0.66f),
                1f to Color.White.copy(alpha = 0.98f)
            )
        )
        selected -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.98f),
                Color.White.copy(alpha = 0.76f),
                Color.White.copy(alpha = 0.38f),
                Color.White.copy(alpha = 0.88f)
            )
        )
        else -> MealsLiquidGlass.controlRim()
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = when {
                    prominent -> 5.dp
                    selected -> 3.dp
                    else -> 2.dp
                },
                shape = SelectionShape,
                clip = false,
                ambientColor = Color(0xFFB7AA9F).copy(alpha = if (prominent) 0.20f else 0.13f),
                spotColor = Color(0xFF8F8278).copy(alpha = if (prominent) 0.12f else 0.075f)
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
            val chipScale = androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isSelected) 1.035f else 1f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 560f),
                label = "mealsFilterChipScale"
            )
            MealsSelectionGlass(
                modifier = Modifier.graphicsLayer {
                    // Read animated scale in the graphics phase, not composition.
                    scaleX = chipScale.value
                    scaleY = chipScale.value
                },
                selected = isSelected
            ) {
                MealsPressable(onClick = { onSelect(item) }, role = Role.RadioButton) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(item, color = if (isSelected) Ink else MutedInk,
                            style = MaterialTheme.typography.labelLarge,
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
    if (items.isEmpty()) return

    // Keep the selected value in the logical item order. The offset modifier
    // below mirrors its direction automatically for RTL layouts.
    val selectedIndex = items.indexOf(selected).takeIf { it >= 0 } ?: 0
    val gap = 5.dp

    MealsSelectionGlass(
        modifier = modifier.fillMaxWidth(),
        selected = false
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp)
                .height(48.dp)
        ) {
            val itemWidth = ((maxWidth - gap * (items.size - 1)) / items.size).coerceAtLeast(0.dp)
            // Row places index 0 at logical start in both directions. A
            // relative offset therefore tracks the same slot without a second
            // manual RTL inversion.
            val targetOffset = (itemWidth + gap) * selectedIndex
            val indicatorOffset = animateDpAsState(
                targetValue = targetOffset,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 520f
                ),
                label = "mealsSubtabIndicatorOffset"
            )

            // One persistent glass lens travels between tabs. It is not recreated
            // at each destination, so its position and optical surface animate together.
            MealsSelectionGlass(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(indicatorOffset.value.roundToPx(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight(),
                selected = true,
                prominent = true
            ) {}

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = item == selected
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        MealsPressable(
                            onClick = { onSelect(item) },
                            modifier = Modifier.fillMaxSize(),
                            role = Role.Tab,
                            shape = SelectionShape,
                            checked = isSelected
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    item,
                                    color = if (isSelected) Ink else MutedInk,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
