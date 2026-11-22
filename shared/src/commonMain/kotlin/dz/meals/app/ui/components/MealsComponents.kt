package dz.meals.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Cream
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.ForestDark
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen
import dz.meals.app.ui.theme.SoftPink
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsNavigationIcon
import dz.meals.app.ui.icons.MealsIconName
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor

private val LargeTileShape = RoundedCornerShape(24.dp)
private val MealsCardShape = MealsLiquidGlass.CardShape

/**
 * The shared elevated surface used for budgets, summaries and grouped content.
 *
 * Each card uses Haze's source-backed Blur with a shared restrained style,
 * a translucent porcelain tint, fine white rim and no platform elevation
 * shadow. The dedicated card source contains only the cloudy backdrop.
 */
@Composable
private fun MealsLiquidGlassFrame(
    modifier: Modifier = Modifier,
    shape: Shape,
    elevation: Dp,
    face: Brush,
    hazeState: HazeState? = null,
    blurStyle: HazeBlurStyle? = null,
    rim: Brush = MealsLiquidGlass.controlRim(),
    sideGlintHeight: Dp = 17.dp,
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val surfaceModifier = modifier
        .shadow(elevation = elevation, shape = shape, clip = false)
        .clip(shape)
        .then(
            if (hazeState != null && blurStyle != null) {
                Modifier.hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = blurStyle,
                    performanceMode = HazePerformanceMode.Default
                )
            } else {
                Modifier
            }
        )
        .background(face, shape)
        .border(BorderStroke(1.15.dp, rim), shape)
        .then(
            if (onClick != null && interactionSource != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Box(modifier = surfaceModifier, contentAlignment = Alignment.TopStart) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(1.dp)
                .border(BorderStroke(0.65.dp, MealsLiquidGlass.innerRim()), shape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 1.dp)
                .fillMaxWidth(0.62f)
                .height(1.dp)
                .background(MealsLiquidGlass.specular(), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 1.dp, top = 10.dp, bottom = 10.dp)
                .width(1.dp)
                .height(sideGlintHeight)
                .background(MealsLiquidGlass.sideGlint(), CircleShape)
        )
        content()
    }
}

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
    val cardHazeState = LocalMealsCardHazeState.current
    val usesLightContent = contentColor == Color.White
    val surfaceAlpha = if (usesLightContent) 0.34f else 0.27f
    val face = remember(containerColor, usesLightContent, surfaceAlpha) {
        MealsLiquidGlass.cardFace(containerColor, usesLightContent, surfaceAlpha)
    }
    val rim = remember(borderColor) {
        MealsLiquidGlass.surfaceRim(borderColor ?: Color(0xFFEADFD7))
    }

    MealsLiquidGlassFrame(
        modifier = modifier,
        shape = shape,
        elevation = shadowElevation.coerceIn(0.dp, 3.dp),
        face = face,
        hazeState = cardHazeState,
        blurStyle = MealsLiquidGlass.SurfaceBlurStyle,
        rim = rim,
        sideGlintHeight = 23.dp
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

val LocalMealsHazeState = staticCompositionLocalOf<HazeState?> { null }
val LocalMealsCardHazeState = staticCompositionLocalOf<HazeState?> { null }


/**
 * High-emphasis hero surface using the exact same backdrop blur material as
 * every MealsCard. The hero differs only in scale and content spacing; it does
 * not use the separate refraction/elevation path that can read as a dark shadow.
 */
/**
 * Large-format glass surface for hero areas, dialogs and sheets. It shares the
 * same double rim and specular treatment as the smaller Meals controls.
 */
@Composable
fun MealsGlassSurface(
    modifier: Modifier = Modifier,
    tintColor: Color = Color(0xFFFFF6EC),
    tintAlpha: Float = 0.34f,
    contentPadding: Dp = 18.dp,
    shape: RoundedCornerShape = MealsLiquidGlass.HeroShape,
    content: @Composable ColumnScope.() -> Unit
) {
    val hazeState = LocalMealsCardHazeState.current
    val surfaceAlpha = (0.24f + tintAlpha.coerceIn(0f, 0.5f) * 0.16f).coerceIn(0.24f, 0.34f)
    val face = remember(tintColor, surfaceAlpha) {
        MealsLiquidGlass.heroFace(tintColor, surfaceAlpha)
    }

    MealsLiquidGlassFrame(
        modifier = modifier,
        shape = shape,
        elevation = MealsLiquidGlass.SurfaceElevation,
        face = face,
        hazeState = hazeState,
        blurStyle = MealsLiquidGlass.SurfaceBlurStyle,
        sideGlintHeight = 27.dp
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}


@Composable
private fun MealsLiquidGlassControl(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 44.dp,
    fixedSize: Dp? = null,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 8.dp,
    contentColor: Color = ForestDark,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) MealsLiquidGlass.PressedScale else 1f,
        animationSpec = tween(durationMillis = MealsLiquidGlass.PressDurationMillis),
        label = "mealsLiquidGlassControlScale"
    )
    val contentModifier = if (fixedSize != null) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxWidth().defaultMinSize(minHeight = minHeight)
    }

    MealsLiquidGlassFrame(
        modifier = modifier
            .then(if (fixedSize != null) Modifier.size(fixedSize) else Modifier.defaultMinSize(minHeight = minHeight))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = MealsLiquidGlass.ControlShape,
        elevation = if (enabled) MealsLiquidGlass.ControlElevation else 0.dp,
        face = MealsLiquidGlass.controlFace(),
        hazeState = LocalMealsCardHazeState.current ?: LocalMealsHazeState.current,
        blurStyle = MealsLiquidGlass.ControlBlurStyle,
        rim = MealsLiquidGlass.controlRim(),
        sideGlintHeight = 17.dp,
        interactionSource = interactionSource,
        enabled = enabled,
        onClick = onClick
    ) {
        Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) contentColor else MutedInk
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
        }
    }
}


@Composable
fun MealsPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: MealsIconName? = null,
    showArrow: Boolean = false
) {
    MealsLiquidGlassControl(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = 48.dp,
        horizontalPadding = 16.dp,
        verticalPadding = 10.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                MealsIcon(
                    name = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text = label,
                color = ForestDark,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            if (showArrow) {
                Spacer(Modifier.size(8.dp))
                MealsIcon(
                    MealsIconName.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
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
    icon: MealsIconName? = null
) {
    MealsLiquidGlassControl(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = 48.dp,
        horizontalPadding = 16.dp,
        verticalPadding = 10.dp
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                MealsIcon(
                    name = icon,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.size(7.dp))
            }
            Text(
                text = label,
                color = ForestDark,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun MealsTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = ForestDark,
    compact: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    MealsLiquidGlassControl(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = if (compact) 38.dp else 44.dp,
        horizontalPadding = if (compact) 8.dp else 10.dp,
        verticalPadding = if (compact) 4.dp else 6.dp,
        contentColor = color
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun MealsLiquidGlassIconControl(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    content: @Composable () -> Unit
) {
    MealsLiquidGlassControl(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = size,
        fixedSize = size,
        horizontalPadding = 0.dp,
        verticalPadding = 0.dp,
        content = content
    )
}

/** A 44dp liquid-glass icon action with a consistent white optical rim. */


@Composable
fun MealsIconAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    MealsIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = 44.dp,
        content = content
    )
}

/** Canonical glass surface for every icon-only app action. */
@Composable
fun MealsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    content: @Composable () -> Unit
) {
    MealsLiquidGlassIconControl(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = size,
        content = content
    )
}

@Composable
fun MealsLiquidGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = true
) {
    val rim = if (isError) MealsLiquidGlass.errorRim() else MealsLiquidGlass.controlRim()
    MealsLiquidGlassFrame(
        modifier = modifier.fillMaxWidth(),
        shape = MealsLiquidGlass.ControlShape,
        elevation = if (enabled) MealsLiquidGlass.SurfaceElevation else 0.dp,
        face = MealsLiquidGlass.controlFace(),
        hazeState = LocalMealsCardHazeState.current ?: LocalMealsHazeState.current,
        blurStyle = MealsLiquidGlass.ControlBlurStyle,
        rim = rim,
        sideGlintHeight = 17.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (label != null) {
                CompositionLocalProvider(
                    LocalContentColor provides if (isError) Color(0xFFB64A45) else MutedInk,
                    LocalTextStyle provides MaterialTheme.typography.labelMedium
                ) {
                    Box { label() }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 27.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                leadingIcon?.invoke()
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    cursorBrush = SolidColor(Forest),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = if (enabled) Ink else MutedInk
                    )
                )
                suffix?.invoke()
            }
        }
    }
}

@Composable
fun MealsSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    MealsLiquidGlassFrame(
        modifier = modifier.fillMaxWidth(),
        shape = MealsLiquidGlass.ControlShape,
        elevation = MealsLiquidGlass.ControlElevation,
        face = MealsLiquidGlass.controlFace(),
        hazeState = LocalMealsCardHazeState.current ?: LocalMealsHazeState.current,
        blurStyle = MealsLiquidGlass.ControlBlurStyle,
        rim = MealsLiquidGlass.controlRim(),
        sideGlintHeight = 17.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MealsIcon(
                name = MealsIconName.Search,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                cursorBrush = SolidColor(Forest),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MutedInk,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}


@Composable
fun MealsCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val fillColor by animateColorAsState(
        targetValue = if (checked) ForestDark else Color.White.copy(alpha = 0.34f),
        animationSpec = tween(durationMillis = 180),
        label = "mealsCheckboxFill"
    )
    val edgeColor by animateColorAsState(
        targetValue = if (checked) ForestDark else Color.White.copy(alpha = 0.96f),
        animationSpec = tween(durationMillis = 180),
        label = "mealsCheckboxEdge"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.72f,
        animationSpec = tween(durationMillis = 180),
        label = "mealsCheckboxCheck"
    )

    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .toggleable(
                value = checked,
                enabled = enabled && onCheckedChange != null,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = { onCheckedChange?.invoke(it) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            fillColor.copy(alpha = if (checked) 0.94f else 0.74f),
                            fillColor.copy(alpha = if (checked) 0.78f else 0.26f)
                        )
                    ),
                    RoundedCornerShape(8.dp)
                )
                .border(
                    BorderStroke(
                        1.15.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.98f), edgeColor.copy(alpha = 0.58f))
                        )
                    ),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Canvas(
                    modifier = Modifier.size(14.dp).graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                        alpha = checkScale
                    }
                ) {
                    val mark = Path().apply {
                        moveTo(size.width * 0.16f, size.height * 0.52f)
                        lineTo(size.width * 0.40f, size.height * 0.76f)
                        lineTo(size.width * 0.86f, size.height * 0.24f)
                    }
                    drawPath(
                        path = mark,
                        color = Color.White,
                        style = Stroke(
                            width = size.minDimension * 0.15f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
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
    MealsCard(
        modifier = modifier.fillMaxWidth().toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange
        ),
        containerColor = if (checked) SoftGreen.copy(alpha = 0.78f) else Paper,
        contentPadding = 0.dp,
        borderColor = if (checked) Forest.copy(alpha = 0.28f) else Border.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 0.dp
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
            MealsCheckbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
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
    icon: MealsIconName? = null
) {
    MealsCard(
        modifier = modifier.fillMaxWidth().clickable(
            onClick = onClick,
            role = Role.Button
        ),
        containerColor = if (selected) SoftGreen else Paper,
        contentPadding = 0.dp,
        borderColor = if (selected) Forest.copy(alpha = 0.42f) else Border.copy(alpha = 0.72f),
        shape = LargeTileShape,
        shadowElevation = 0.dp
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
                    MealsIcon(name = icon, contentDescription = null, modifier = Modifier.size(34.dp))
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
                    MealsIcon(name = MealsIconName.Check, contentDescription = "محدد", modifier = Modifier.size(23.dp))
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
    icon: MealsIconName,
    modifier: Modifier = Modifier,
    background: Color = SoftGreen,
    size: Dp = 54.dp
) {
    MealsLiquidGlassFrame(
        modifier = modifier.size(size),
        shape = CircleShape,
        elevation = MealsLiquidGlass.ControlElevation,
        face = MealsLiquidGlass.illustrationFace(background),
        hazeState = LocalMealsCardHazeState.current ?: LocalMealsHazeState.current,
        blurStyle = MealsLiquidGlass.ControlBlurStyle,
        rim = MealsLiquidGlass.controlRim(),
        sideGlintHeight = size * 0.42f
    ) {
        MealsIcon(
            icon,
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).size(size * 0.78f)
        )
    }
}


@Composable
fun MealsAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            MealsGlassSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = 760.dp),
                contentPadding = 22.dp,
                shape = RoundedCornerShape(29.dp),
                tintColor = Color(0xFFFFF6EC),
                tintAlpha = 0.34f
            ) {
                title?.invoke()
                if (text != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 620.dp)
                    ) {
                        text()
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    confirmButton()
                    dismissButton?.invoke()
                }
            }
        }
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
        MealsGlassSurface(
            modifier = modifier.fillMaxWidth().widthIn(max = 540.dp).padding(horizontal = 18.dp),
            tintColor = Color(0xFFFFF6EC),
            tintAlpha = 0.38f,
            shape = RoundedCornerShape(30.dp),
            contentPadding = 22.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                        MealsIconAction(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                            MealsIcon(
                                MealsIconName.Close,
                                contentDescription = "إغلاق",
                                modifier = Modifier.size(30.dp),
                            )
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
                        if (confirmLabel != null && onConfirm != null) {
                            MealsPrimaryButton(
                                label = confirmLabel,
                                onClick = onConfirm,
                                modifier = Modifier.weight(1f),
                                enabled = confirmEnabled
                            )
                        }
                        if (dismissLabel != null) {
                            MealsSecondaryButton(
                                label = dismissLabel,
                                onClick = onDismissAction ?: onDismiss,
                                modifier = Modifier.weight(1f)
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
            val drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
            ModalDrawerSheet(
                drawerContainerColor = Color.Transparent,
                drawerShape = drawerShape
            ) {
                MealsGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = drawerShape,
                    contentPadding = 20.dp,
                    tintAlpha = 0.36f
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = drawerContent
                    )
                }
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
        containerColor = Color.Transparent,
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 42.dp, height = 5.dp)
                    .clip(CircleShape).background(Border)
            )
        }
    ) {
        MealsGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            contentPadding = 22.dp,
            tintAlpha = 0.38f
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

/**
 * A navigation destination used by the shared frosted bottom bar.
 * Kept platform-neutral so the same navigation surface can be used by Android
 * and iOS hosts while each host keeps ownership of its actual navigation state.
 */
data class MealsNavigationItem(
    val label: String,
    val icon: MealsIconName
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
    Box(
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 18.dp).padding(bottom = 8.dp)
    ) {
        MealsLiquidGlassFrame(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = MealsLiquidGlass.NavigationShape,
            elevation = 10.dp,
            face = MealsLiquidGlass.controlFace(),
            hazeState = hazeState,
            blurStyle = MealsLiquidGlass.SurfaceBlurStyle,
            rim = MealsLiquidGlass.controlRim(),
            sideGlintHeight = 30.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(72.dp)
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
                    val interactionSource = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onItemSelected(index) },
                                role = Role.Tab
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(width = 18.dp, height = 3.dp)
                                .clip(CircleShape)
                                .background(if (selected) ForestDark else Color.Transparent)
                        )
                        Spacer(Modifier.height(4.dp))
                        MealsNavigationIcon(
                            name = item.icon,
                            selected = selected,
                            contentDescription = item.label,
                            modifier = Modifier.size(31.dp)
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
        }
    }
}

@Composable
fun MealsBackdrop(content: @Composable () -> Unit) {
    // The full-screen state is used by the floating navigation so it can blur
    // moving content. The card state is isolated to the atmospheric canvas,
    // preventing a card from sampling itself or neighbouring card outputs.
    val hazeState = rememberHazeState()
    val cardHazeState = rememberHazeState()

    CompositionLocalProvider(
        LocalMealsHazeState provides hazeState,
        LocalMealsCardHazeState provides cardHazeState
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(cardHazeState)
            ) {
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
