package dz.meals.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.glass.hazeGlass
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

/**
 * Shared refraction-based glass surface for high-emphasis content.
 *
 * Haze supplies actual backdrop sampling, depth blur, optical refraction,
 * ambient edge lift and specular rim lighting. The Compose overlays only tune
 * the warm porcelain tint and fine double edge; they are not a fake blur.
 *
 * Use for hero summaries, floating controls and grouped surfaces. Dense,
 * repeating rows should continue using MealsCard to protect LazyColumn scroll
 * performance and avoid paying for glass rendering on every item.
 */
val LocalMealsHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * A single, shape-consistent glass material. Haze owns the material fill,
 * refraction, rim and edge; the child content is not covered by a second
 * background rectangle. Keep this for hero surfaces, not every LazyColumn row.
 */
@Composable
fun MealsGlassSurface(
    modifier: Modifier = Modifier,
    tintColor: Color = Color(0xFFFFFCF7),
    tintAlpha: Float = 0.28f,
    contentPadding: Dp = 18.dp,
    shape: RoundedCornerShape = RoundedCornerShape(29.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val hazeState = LocalMealsHazeState.current
    val glassStyle = remember(shape, tintColor, tintAlpha) {
        GlassStyle.regular.then {
            tint(tintColor.copy(alpha = tintAlpha.coerceIn(0.12f, 0.42f)))
            optics(
                GlassOptics.regular.copy(
                    refractionStrength = 0.46f,
                    refractionHeightFraction = 0.30f,
                    refractionFoldStrength = 0.08f
                )
            )
            specularIntensity(0.72f)
            ambientResponse(0.66f)
            edgeSoftness(5.dp)
            // Haze 2.1.0 exposes edgeShadow but not the newer edge-outline
            // or rim-highlight controls. Keep the edge response subtle here;
            // do not depend on APIs introduced after the pinned stable version.
            edgeShadow(Color(0x14745D4B))
            shape(shape)
            surfaceProfile(SurfaceProfile.Squircle)
            lightPosition(Alignment.TopStart)
            chromaticAberrationStrength(0.025f)
        }
    }

    Box(
        modifier = modifier
            // Do not put a RenderNode elevation shadow on a translucent glass
            // layer. Haze draws the optical edge and rim as part of the same
            // material, avoiding a platform shadow bleeding through the tint.
            .hazeGlass(
                input = HazeInput.Backdrop(hazeState),
                style = glassStyle
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
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
        modifier = modifier
            .shadow(7.dp, CircleShape, clip = false)
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.96f)), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.46f))
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

/**
 * A navigation destination used by the shared frosted bottom bar.
 * Kept platform-neutral so the same navigation surface can be used by Android
 * and iOS hosts while each host keeps ownership of its actual navigation state.
 */
data class MealsNavigationItem(
    val label: String,
    val icon: ImageVector
)

/**
 * Floating frosted navigation capsule based on Haze's source-backed Blur API.
 *
 * One Haze node is used for the entire bar, not one per destination. The
 * scrolling screen is captured as a sibling Haze source, so its cards move
 * naturally beneath the capsule. A translucent tint preserves label contrast;
 * a fine white rim supplies the light-catching glass edge.
 */
@Composable
fun MealsFrostedBottomBar(
    items: List<MealsNavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val capsuleShape = RoundedCornerShape(32.dp)
    val blurStyle = remember {
        HazeBlurStyle {
            blurRadius(25.dp)
            noiseFactor(0.045f)
            colorEffects(
                listOf(
                    HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = 0.46f))
                )
            )
            fallbackColorEffect(
                HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = 0.92f))
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(capsuleShape)
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = blurStyle,
                    performanceMode = HazePerformanceMode.Default,
                    expandLayerBounds = false
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color(0xFFFFFCF7).copy(alpha = 0.22f),
                            Color(0xFFF4ECE5).copy(alpha = 0.31f)
                        )
                    ),
                    capsuleShape
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.98f),
                                Color.White.copy(alpha = 0.64f),
                                Color(0xFFE5D9CF).copy(alpha = 0.78f)
                            )
                        )
                    ),
                    capsuleShape
                )
                .padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val foreground by animateColorAsState(
                    targetValue = if (selected) ForestDark else MutedInk,
                    label = "mealsNavForeground$index"
                )
                val iconSize by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.93f,
                    animationSpec = tween(durationMillis = 180),
                    label = "mealsNavIconScale$index"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(25.dp))
                        .clickable(
                            onClick = { onItemSelected(index) },
                            role = Role.Tab
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = foreground,
                        modifier = Modifier.size(23.dp * iconSize)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        color = foreground,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        // The hairline is drawn inside the already-clipped capsule, so it
        // cannot create a second rectangular surface or escape the rounded rim.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 58.dp, vertical = 1.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.98f),
                            Color.White.copy(alpha = 0.82f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
fun MealsBackdrop(content: @Composable () -> Unit) {
    val hazeState = rememberHazeState()

    CompositionLocalProvider(LocalMealsHazeState provides hazeState) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Capture only the atmospheric background. Capturing this parent
            // would also capture its foreground glass cards, feeding their
            // translucent output back into later Haze surfaces on fallback
            // renderers (Android < 17 and non-native paths).
            Canvas(
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
            ) {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFF4ED),
                            Cream,
                            Color(0xFFF5F1DE),
                            Color(0xFFFFF0EA)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )

                val radius = maxOf(size.width, size.height) * 0.60f
                drawCloud(
                    center = Offset(size.width * 0.04f, size.height * 0.10f),
                    color = Color(0xFFF0D2C8),
                    radius = radius
                )
                drawCloud(
                    center = Offset(size.width * 0.50f, -size.height * 0.06f),
                    color = Color(0xFFDCE8CF),
                    radius = radius * 1.04f
                )
                drawCloud(
                    center = Offset(size.width * 1.02f, size.height * 0.13f),
                    color = Color(0xFFF1E4BB),
                    radius = radius * 0.88f
                )
                drawCloud(
                    center = Offset(size.width * 1.04f, size.height * 0.59f),
                    color = Color(0xFFF4DAD7),
                    radius = radius * 0.94f
                )
                drawCloud(
                    center = Offset(size.width * 0.02f, size.height * 1.02f),
                    color = Color(0xFFE0EBD8),
                    radius = radius * 0.98f
                )
                drawCloud(
                    center = Offset(size.width * 0.93f, size.height * 0.98f),
                    color = Color(0xFFF2D9CE),
                    radius = radius * 0.92f
                )
            }
            content()
        }
    }
}

private fun DrawScope.drawCloud(
    center: Offset,
    color: Color,
    radius: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.70f),
                color.copy(alpha = 0.32f),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
