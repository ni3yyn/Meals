package dz.meals.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Cream
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.ForestDark
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen
import dz.meals.app.ui.theme.SoftPink

private val LargeTileShape = RoundedCornerShape(24.dp)
private val MealsCardShape = RoundedCornerShape(26.dp)
private val ButtonShape = RoundedCornerShape(22.dp)

/**
 * The shared elevated surface used for budgets, summaries and grouped content.
 *
 * It deliberately avoids a flat Material card fill: the warm translucent
 * highlight, fine double-tone edge and soft elevation reproduce the layered
 * porcelain/glass surfaces from the Mida visual system without per-card blur.
 */
@Composable
fun MealsCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Paper,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: Dp = 18.dp,
    borderColor: Color? = Border.copy(alpha = 0.72f),
    shape: Shape = MealsCardShape,
    shadowElevation: Dp = 5.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val faceHighlight = remember(containerColor) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.08f),
                Color(0xFFE8C9BA).copy(alpha = 0.08f)
            )
        )
    }
    val edgeHighlight = remember(borderColor) {
        borderColor?.let { edge ->
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.98f),
                    edge.copy(alpha = 0.72f),
                    edge.copy(alpha = 0.48f)
                )
            )
        }
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = edgeHighlight?.let { BorderStroke(1.dp, it) },
        shadowElevation = shadowElevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(faceHighlight, shape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
fun MealsPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    showArrow: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "mealsPrimaryButtonScale"
    )
    val buttonShape = remember { RoundedCornerShape(26.dp) }
    val buttonBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF55A978),
                Forest,
                ForestDark
            )
        )
    }
    val buttonEdge = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.42f),
                Color.White.copy(alpha = 0.10f),
                ForestDark.copy(alpha = 0.16f)
            )
        )
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .defaultMinSize(minHeight = 58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = buttonShape,
        color = Color.Transparent,
        contentColor = Color.White,
        border = BorderStroke(1.dp, buttonEdge),
        shadowElevation = if (enabled) 7.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = if (enabled) buttonBrush else Brush.linearGradient(
                        listOf(Color(0xFF91B7A0), Color(0xFF769B83))
                    ),
                    shape = buttonShape
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (enabled) 0.12f else 0.05f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (pressed) 0.035f else 0f)
                        )
                    ),
                    shape = buttonShape
                )
                .padding(horizontal = 22.dp, vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.size(9.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (showArrow) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (enabled) 0.96f else 0.55f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(23.dp)
                )
            }
        }
    }
}

@Composable
fun MealsSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.988f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "mealsSecondaryButtonScale"
    )
    val buttonShape = remember { ButtonShape }
    val surfaceBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFFEFC),
                Color(0xFFFFF8F1),
                Color(0xFFF9EEE7)
            )
        )
    }
    val edgeBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.98f),
                Color(0xFFD9E8DA).copy(alpha = 0.92f),
                Color(0xFFE7D8CD).copy(alpha = 0.88f)
            )
        )
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .defaultMinSize(minHeight = 54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = buttonShape,
        color = Color.Transparent,
        contentColor = if (enabled) ForestDark else MutedInk,
        border = BorderStroke(1.dp, edgeBrush),
        shadowElevation = if (enabled) 3.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = if (enabled) surfaceBrush else Brush.linearGradient(
                        listOf(Color(0xFFF3EEE9), Color(0xFFEFE8E2))
                    ),
                    shape = buttonShape
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (enabled) 0.52f else 0.18f),
                            Color.Transparent,
                            Color(0xFFEAD9CD).copy(alpha = if (pressed) 0.16f else 0.06f)
                        )
                    ),
                    shape = buttonShape
                )
                .padding(horizontal = 20.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    text = label,
                    color = if (enabled) ForestDark else MutedInk,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun MealsCheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange
        ),
        shape = RoundedCornerShape(18.dp),
        color = if (checked) SoftGreen.copy(alpha = 0.78f) else Paper,
        border = BorderStroke(1.dp, if (checked) Forest.copy(alpha = 0.28f) else Border.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                supportingText?.let {
                    Text(it, color = MutedInk, style = MaterialTheme.typography.bodySmall)
                }
            }
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = Forest,
                    uncheckedColor = MutedInk.copy(alpha = 0.55f),
                    checkmarkColor = Color.White
                )
            )
        }
    }
}

@Composable
fun MealsChoiceTile(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = LargeTileShape,
        color = if (selected) SoftGreen else Paper,
        border = BorderStroke(1.dp, if (selected) Forest.copy(alpha = 0.42f) else Border.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(15.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.78f) else SoftPink),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Forest, modifier = Modifier.size(23.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                description?.let { Text(it, color = MutedInk, style = MaterialTheme.typography.bodySmall) }
            }
            if (selected) {
                Box(
                    modifier = Modifier.size(27.dp).clip(CircleShape).background(Forest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = "محدد", tint = Color.White, modifier = Modifier.size(17.dp))
                }
            } else {
                Box(
                    modifier = Modifier.size(25.dp).clip(CircleShape)
                        .border(1.dp, Border.copy(alpha = 0.85f), CircleShape)
                )
            }
        }
    }
}

@Composable
fun MealsProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (label != null) {
            Text(label, color = ForestDark, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
            color = Forest,
            trackColor = SoftGreen
        )
    }
}

@Composable
fun MealsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = MutedInk, style = MaterialTheme.typography.bodySmall) }
        }
        trailing?.invoke()
    }
}

@Composable
fun MealsIllustrationBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    background: Color = SoftGreen,
    iconTint: Color = Forest,
    size: Dp = 54.dp
) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(size / 2.8f)).background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun MealsDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    onDismissAction: (() -> Unit)? = null,
    showCloseButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier.fillMaxWidth().widthIn(max = 540.dp).padding(horizontal = 18.dp),
            shape = RoundedCornerShape(30.dp),
            color = Paper,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
                        subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MutedInk)
                        }
                    }
                    if (showCloseButton) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "إغلاق", tint = MutedInk)
                        }
                    }
                }
                content()
                if (confirmLabel != null || dismissLabel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissLabel != null) {
                            TextButton(
                                onClick = onDismissAction ?: onDismiss,
                                modifier = Modifier.weight(1f)
                            ) { Text(dismissLabel, color = MutedInk) }
                        }
                        if (confirmLabel != null && onConfirm != null) {
                            MealsPrimaryButton(
                                label = confirmLabel,
                                onClick = onConfirm,
                                modifier = Modifier.weight(1f),
                                enabled = confirmEnabled
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MealsNavigationDrawer(
    drawerState: DrawerState,
    drawerContent: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Paper,
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = drawerContent
                )
            }
        },
        scrimColor = Ink.copy(alpha = 0.28f),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = Paper,
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 42.dp, height = 5.dp)
                    .clip(CircleShape).background(Border)
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
fun MealsBackdrop(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Cream)) {
        Box(
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 76.dp, y = (-74).dp)
                .size(250.dp).blur(62.dp).background(SoftPink.copy(alpha = 0.92f), CircleShape)
        )
        Box(
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-78).dp, y = 95.dp)
                .size(230.dp).blur(60.dp).background(SoftGreen.copy(alpha = 0.92f), CircleShape)
        )
        content()
    }
}
