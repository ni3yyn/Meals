package dz.meals.app.ui.components

import dz.meals.app.ui.components.MealsText as Text

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface

import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheetProperties


import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.rememberHazeState
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Cream
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.ForestDark
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftOrange
import dz.meals.app.ui.theme.TextAccent
import dz.meals.app.ui.theme.WarmSurface
import dz.meals.app.ui.theme.mealsText
import dz.meals.app.ui.theme.SoftPink
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsNavigationIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.shared.generated.resources.Res
import dz.meals.app.shared.generated.resources.meals_kitchen_background
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

private val LargeTileShape = RoundedCornerShape(24.dp)
private val MealsCardShape = MealsLiquidGlass.CardShape

/**
 * The shared elevated surface used for budgets, summaries and grouped content.
 *
 * Each card uses Haze's source-backed Blur with a shared restrained style,
 * a translucent porcelain tint, fine white rim and no platform elevation
 * shadow. The dedicated card source contains only the fixed kitchen artwork.
 */
@Composable
private fun MealsLiquidGlassFrame(
    modifier: Modifier = Modifier,
    shape: Shape,
    elevation: Dp,
    face: Brush,
    hazeState: HazeState? = null,
    blurStyle: HazeBlurStyle? = null,
    blurRadius: Dp = MealsLiquidGlass.SurfaceBlurRadius,
    glassBackdrop: MealsGlassBackdrop? = null,
    /** Use source-backed Haze when the surface lives in a separate modal window. */
    forceHazeBlur: Boolean = false,
    rim: Brush = MealsLiquidGlass.controlRim(),
    sideGlintHeight: Dp = 17.dp,
    content: @Composable BoxScope.() -> Unit
) {
    // Prismal captures within the activity window. ModalBottomSheet is hosted
    // in a separate dialog window, so it must consume Haze's cross-window source.
    val resolvedGlassBackdrop = if (forceHazeBlur) {
        null
    } else {
        glassBackdrop
            ?: LocalMealsCardGlassBackdrop.current
            ?: LocalMealsScreenGlassBackdrop.current
    }
    val isPressed = LocalMealsGlassPressed.current
    val opticalDepth by animateFloatAsState(
        targetValue = if (isPressed) 0.78f else 1f,
        animationSpec = tween(durationMillis = 85),
        label = "mealsGlassOpticalDepth"
    )
    val pressGlow by animateFloatAsState(
        targetValue = if (isPressed) 0.12f else 0f,
        animationSpec = tween(durationMillis = 65),
        label = "mealsGlassPressGlow"
    )
    val surfaceModifier = modifier
        .shadow(
            elevation = elevation * opticalDepth,
            shape = shape,
            clip = false,
            ambientColor = Color(0xFFB7AA9F).copy(alpha = 0.13f),
            spotColor = Color(0xFF8F8278).copy(alpha = 0.075f)
        )
        .clip(shape)
        .mealsGlassBlur(
            backdrop = resolvedGlassBackdrop,
            hazeState = hazeState,
            blurStyle = blurStyle,
            shape = shape,
            blurRadius = blurRadius
        )
        .background(face, shape)
        // The lift in brightness sits on the glass face, behind its content,
        // so labels and icons stay crisp while the white optical rim catches light.
        .background(Color.White.copy(alpha = pressGlow), shape)
        .mealsOpticalEdges(
            shape = shape,
            baseRim = rim,
            depth = 1f,
            sideGlintHeight = sideGlintHeight
        )

    Box(modifier = surfaceModifier, contentAlignment = Alignment.TopStart) {
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
    val face = remember(containerColor) {
        MealsLiquidGlass.cardFace(containerColor)
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

/**
 * Small, non-interactive progress bead using the exact control-glass material
 * used by Meals buttons: translucent face, white optical rim and soft depth.
 */
@Composable
fun MealsGlassDot(
    selected: Boolean,
    completed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val diameter by animateDpAsState(
        targetValue = if (selected) 16.dp else 10.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "mealsGlassDotSize"
    )
    val face = when {
        selected -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.72f),
                SoftOrange.copy(alpha = 0.76f),
                SoftOrange.copy(alpha = 0.48f)
            )
        )
        completed -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.62f),
                SoftOrange.copy(alpha = 0.46f),
                Color.White.copy(alpha = 0.36f)
            )
        )
        else -> MealsLiquidGlass.controlFace()
    }

    MealsLiquidGlassFrame(
        modifier = modifier.size(diameter),
        shape = CircleShape,
        elevation = if (selected) MealsLiquidGlass.ControlElevation else MealsLiquidGlass.SurfaceElevation,
        face = face,
        hazeState = LocalMealsHazeState.current ?: LocalMealsCardHazeState.current,
        blurStyle = MealsLiquidGlass.ControlBlurStyle,
        blurRadius = MealsLiquidGlass.ControlBlurRadius,
        rim = MealsLiquidGlass.controlRim(),
        sideGlintHeight = 9.dp
    ) {}
}


/**
 * Shared pearl-glass progress capsule for guided flows and compact summaries.
 *
 * The single-surface drawing approach is inspired by PrismalAGSL's public
 * Compose progress component. Meals keeps its own Haze glass frame and paints
 * the moving warm fill directly into the track, with no refraction or shaders.
 */
@Composable
fun MealsLiquidGlassProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 24.dp,
    progressColor: Color = Color(0xFFE6D1B7),
    contentDescription: String = "التقدم"
) {
    val localizedContentDescription = mealsText(contentDescription)
    val targetProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(
            durationMillis = 560,
            easing = FastOutSlowInEasing
        ),
        label = "mealsLiquidGlassProgress"
    )
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val trackBrush = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.34f),
                0.34f to Color(0xFFF0E8DE).copy(alpha = 0.30f),
                0.76f to Color(0xFFDCCFC1).copy(alpha = 0.22f),
                1f to Color.White.copy(alpha = 0.30f)
            )
        )
    }
    val fillBrush = remember(progressColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color(0xFFFFFEFA).copy(alpha = 0.96f),
                0.20f to Color(0xFFF7EBDD).copy(alpha = 0.97f),
                0.62f to progressColor.copy(alpha = 0.94f),
                1f to Color(0xFFCDB596).copy(alpha = 0.91f)
            )
        )
    }
    val highlightBrush = remember {
        Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.20f to Color.White.copy(alpha = 0.36f),
                0.50f to Color.White.copy(alpha = 0.82f),
                0.80f to Color.White.copy(alpha = 0.36f),
                1f to Color.Transparent
            )
        )
    }

    MealsLiquidGlassFrame(
        modifier = modifier
            .height(height)
            .progressSemantics(targetProgress)
            .semantics { this.contentDescription = localizedContentDescription },
        shape = CircleShape,
        elevation = 2.dp,
        face = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.70f),
                0.28f to Color(0xFFFFF9F1).copy(alpha = 0.58f),
                0.72f to Color(0xFFE9DED1).copy(alpha = 0.38f),
                1f to Color.White.copy(alpha = 0.50f)
            )
        ),
        hazeState = LocalMealsHazeState.current ?: LocalMealsCardHazeState.current,
        blurStyle = MealsLiquidGlass.ControlBlurStyle,
        blurRadius = MealsLiquidGlass.ControlBlurRadius,
        rim = MealsLiquidGlass.controlRim(),
        sideGlintHeight = height * 0.72f
    ) {
        // Track and fill share one glass surface, avoiding stacked blur passes.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 3.dp)
        ) {
            val radius = size.height * 0.5f
            drawRoundRect(
                brush = trackBrush,
                size = size,
                cornerRadius = CornerRadius(radius, radius)
            )

            val fillWidth = size.width * animatedProgress
            if (fillWidth > 0.5.dp.toPx()) {
                val fillStart = if (isRtl) size.width - fillWidth else 0f
                val fillRadius = minOf(radius, fillWidth * 0.5f)
                drawRoundRect(
                    brush = fillBrush,
                    topLeft = Offset(fillStart, 0f),
                    size = Size(fillWidth, size.height),
                    cornerRadius = CornerRadius(fillRadius, fillRadius)
                )

                // A thin inset reflection creates a glass face without a second
                // border or any bending/displacement of the background.
                val inset = (fillWidth * 0.08f).coerceAtMost(7.dp.toPx())
                val reflectionWidth = (fillWidth - inset * 2f).coerceAtLeast(0f)
                if (reflectionWidth > 2.dp.toPx()) {
                    drawRoundRect(
                        brush = highlightBrush,
                        topLeft = Offset(fillStart + inset, 1.4.dp.toPx()),
                        size = Size(reflectionWidth, 2.2.dp.toPx()),
                        cornerRadius = CornerRadius(1.1.dp.toPx(), 1.1.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Shared Midati loading treatment for long-running sheet actions and full-screen
 * transitions. The motion is intentionally soft: warm pearl surfaces orbit
 * a single-drawn glass core, using the modal's existing backdrop instead of
 * stacking additional blur passes or generic progress spinners.
 *
 * Callers own the message and operation lifecycle. This component owns only
 * the reusable visual language and its continuous, low-amplitude motion.
 */
@Composable
fun MealsLiquidGlassLoadingExperience(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier
) {
    // One animated value invalidates only the Canvas draw pass. The dialog's
    // existing modal glass provides the blur; nested Haze frames here would
    // duplicate blur work several times during an already animated transition.
    val motion = rememberInfiniteTransition(label = "mealsLiquidGlassLoading")
    val orbit = motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing)
        ),
        label = "mealsLiquidGlassLoadingOrbit"
    )

    val haloBrush = remember {
        Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.70f),
                Color(0xFFE8D8C8).copy(alpha = 0.24f),
                Color.Transparent
            )
        )
    }
    val coreBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.96f),
                Color(0xFFFFF8EF).copy(alpha = 0.88f),
                Color(0xFFE3D1BE).copy(alpha = 0.76f)
            )
        )
    }
    val beadBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.98f),
                Color(0xFFF8EBDD).copy(alpha = 0.92f),
                Color(0xFFD9C3AC).copy(alpha = 0.84f)
            )
        )
    }
    val sweepBrush = remember {
        Brush.sweepGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.98f),
                Color(0xFFD7C1A9).copy(alpha = 0.72f),
                Color.Transparent
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(Modifier.size(154.dp)) {
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            val minDimension = size.minDimension
            val coreRadius = minDimension * 0.235f
            val orbitRadius = minDimension * 0.405f
            val rotation = orbit.value

            // A single pass draws the warm diffused halo, glass core, optical
            // rim, moving specular arc, and three small pearl satellites.
            drawCircle(
                brush = haloBrush,
                center = center,
                radius = minDimension * 0.49f
            )
            drawCircle(
                brush = coreBrush,
                center = center,
                radius = coreRadius
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.94f),
                center = center,
                radius = coreRadius,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawArc(
                brush = sweepBrush,
                startAngle = rotation - 238f,
                sweepAngle = 218f,
                useCenter = false,
                topLeft = Offset(
                    center.x - coreRadius * 0.78f,
                    center.y - coreRadius * 0.78f
                ),
                size = Size(coreRadius * 1.56f, coreRadius * 1.56f),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )

            repeat(3) { index ->
                val angle = (rotation + index * 120f) * 0.0174532925f
                val satelliteCenter = Offset(
                    x = center.x + kotlin.math.cos(angle.toDouble()).toFloat() * orbitRadius,
                    y = center.y + kotlin.math.sin(angle.toDouble()).toFloat() * orbitRadius
                )
                val satelliteRadius = when (index) {
                    0 -> 6.dp.toPx()
                    1 -> 5.dp.toPx()
                    else -> 4.dp.toPx()
                }
                drawCircle(
                    brush = beadBrush,
                    center = satelliteCenter,
                    radius = satelliteRadius
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.90f),
                    center = satelliteCenter,
                    radius = satelliteRadius,
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            // A stationary upper glint makes the pearl read as a curved lens.
            drawCircle(
                color = Color.White.copy(alpha = 0.92f),
                center = Offset(center.x - coreRadius * 0.30f, center.y - coreRadius * 0.48f),
                radius = 3.dp.toPx()
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Midati",
            color = TextAccent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = mealsText(title),
            color = Ink,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        message?.takeIf { it.isNotBlank() }?.let { supporting ->
            Spacer(Modifier.height(3.dp))
            Text(
                text = mealsText(supporting),
                color = MutedInk,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

val LocalMealsHazeState = staticCompositionLocalOf<HazeState?> { null }
private val LocalMealsSheetDismissAction =
    staticCompositionLocalOf<(afterDismiss: () -> Unit) -> Unit> {
        { afterDismiss -> afterDismiss() }
    }

/**
 * Returns the active sheet's animated close action from inside its content slots.
 * The supplied action runs only after the sheet has completed its hide animation.
 */
@Composable
fun mealsSheetDismissAction(): (afterDismiss: () -> Unit) -> Unit =
    LocalMealsSheetDismissAction.current
val LocalMealsCardHazeState = staticCompositionLocalOf<HazeState?> { null }
val LocalMealsCardGlassBackdrop = staticCompositionLocalOf<MealsGlassBackdrop?> { null }
val LocalMealsScreenGlassBackdrop = staticCompositionLocalOf<MealsGlassBackdrop?> { null }
internal val LocalMealsGlassPressed = staticCompositionLocalOf { false }


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
    contentPadding: Dp = 18.dp,
    shape: RoundedCornerShape = MealsLiquidGlass.HeroShape,
    content: @Composable ColumnScope.() -> Unit
) {
    val hazeState = LocalMealsCardHazeState.current
    val face = remember(tintColor) {
        MealsLiquidGlass.heroFace(tintColor)
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


/**
 * Canonical Meals touch surface: one visible spring-compressed press,
 * porcelain highlight and clean release for every reusable control.
 */
@Composable
fun MealsPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Button,
    shape: Shape = MealsLiquidGlass.ControlShape,
    /** When non-null, exposes checked state through toggleable semantics. */
    checked: Boolean? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val interactionPressed by source.collectIsPressedAsState()
    val (pointerPressed, setPointerPressed) = remember { mutableStateOf(false) }
    val pressed = enabled && (interactionPressed || pointerPressed)

    // Observe the raw DOWN/UP stream alongside the semantic click/toggle
    // modifier. It only drives visual state, before gesture recognition can
    // postpone feedback. A tiny release tail keeps quick taps visible.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.958f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.72f,
            stiffness = 900f
        ),
        label = "mealsSharedPressScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (pressed) 0.55.dp.toPx() else 0f
            }
            .clip(shape)
            .pointerInput(enabled) {
                if (!enabled) {
                    setPointerPressed(false)
                    return@pointerInput
                }

                coroutineScope {
                    var releaseJob: Job? = null
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        releaseJob?.cancel()
                        setPointerPressed(true)

                        var completedTap = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (!change.pressed) {
                                    completedTap = true
                                    break
                                }

                                // Let vertical/horizontal scrolling take over cleanly.
                                val delta = change.position - down.position
                                val touchSlop = viewConfiguration.touchSlop
                                if (
                                    delta.x * delta.x + delta.y * delta.y >
                                    touchSlop * touchSlop
                                ) {
                                    break
                                }
                            }
                        } finally {
                            if (completedTap) {
                                releaseJob = launch {
                                    delay(72)
                                    setPointerPressed(false)
                                }
                            } else {
                                setPointerPressed(false)
                            }
                        }
                    }
                }
            }
            .let { touchModifier ->
                if (checked == null) {
                    touchModifier.clickable(
                        interactionSource = source,
                        indication = null,
                        enabled = enabled,
                        role = role,
                        onClick = onClick
                    )
                } else {
                    touchModifier.toggleable(
                        value = checked,
                        interactionSource = source,
                        indication = null,
                        enabled = enabled,
                        role = role,
                        onValueChange = { onClick() }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalMealsGlassPressed provides pressed) {
            content()
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
    contentColor: Color = Ink,
    content: @Composable () -> Unit
) {
    val contentModifier = if (fixedSize != null) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxWidth().defaultMinSize(minHeight = minHeight)
    }

    // CRITICAL SIZING CONTRACT — KEEP THIS BRANCH DYNAMIC.
    // Text controls must wrap to their own content height. A bottom sheet places
    // these controls inside a height-bounded Column; fillMaxSize()/fillMaxHeight()
    // here makes each ordinary button consume the remaining sheet height, which
    // forces even tiny sheets to expand to the maximum and makes their buttons huge.
    // AI AGENTS / MAINTAINERS: DO NOT replace this conditional with fillMaxSize(),
    // fillMaxHeight(), or a fixed height. Only fixedSize icon controls may fill
    // their supplied bounds. Normal buttons must fill width and wrap vertically.
    // wrapContentHeight() clears inherited minimum height while preserving the max ceiling.
    val frameModifier = if (fixedSize != null) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxWidth().wrapContentHeight()
    }

    MealsPressable(
        onClick = onClick,
        modifier = modifier.then(
            if (fixedSize != null) Modifier.size(fixedSize)
            else Modifier.defaultMinSize(minHeight = minHeight)
        ),
        enabled = enabled
    ) {
        MealsLiquidGlassFrame(
            modifier = frameModifier,
            shape = MealsLiquidGlass.ControlShape,
            elevation = if (enabled) MealsLiquidGlass.ControlElevation else 0.dp,
            face = MealsLiquidGlass.controlFace(),
            hazeState = LocalMealsCardHazeState.current ?: LocalMealsHazeState.current,
            blurStyle = MealsLiquidGlass.ControlBlurStyle,
            blurRadius = MealsLiquidGlass.ControlBlurRadius,
            rim = MealsLiquidGlass.controlRim(),
            sideGlintHeight = 17.dp
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
                color = Ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
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
    icon: MealsIconName? = null,
    contentColor: Color = Ink
) {
    MealsLiquidGlassControl(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = 48.dp,
        horizontalPadding = 16.dp,
        verticalPadding = 10.dp,
        contentColor = contentColor
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
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MealsTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ink,
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

/**
 * Reusable Meals dropdown: one branded glass trigger and one source-blurred
 * menu surface. It intentionally keeps option rows flat, reserving the glass
 * rim for the menu perimeter rather than stacking cards inside the popup.
 */
@Composable
fun <T> MealsGlassDropdown(
    selectedOption: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "اختر...",
    optionSupportingText: ((T) -> String?)? = null,
    leadingIcon: MealsIconName? = null,
    enabled: Boolean = true,
    maxMenuHeight: Dp = 300.dp
) {
    var expanded by remember { mutableStateOf(false) }
    val layoutDirection = LocalLayoutDirection.current
    val arrowRotation by animateFloatAsState(
        targetValue = when {
            layoutDirection == LayoutDirection.Rtl && expanded -> 90f
            layoutDirection == LayoutDirection.Rtl -> 270f
            expanded -> 270f
            else -> 90f
        },
        animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
        label = "mealsDropdownArrowRotation"
    )

    BoxWithConstraints(modifier = modifier) {
        val menuMinWidth = maxWidth

        MealsPressable(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            shape = MealsLiquidGlass.ControlShape
        ) {
            MealsLiquidGlassFrame(
                modifier = Modifier.fillMaxWidth(),
                shape = MealsLiquidGlass.ControlShape,
                elevation = if (enabled) MealsLiquidGlass.ControlElevation else 0.dp,
                face = MealsLiquidGlass.controlFace(),
                hazeState = LocalMealsHazeState.current ?: LocalMealsCardHazeState.current,
                blurStyle = MealsLiquidGlass.ControlBlurStyle,
                blurRadius = MealsLiquidGlass.ControlBlurRadius,
                forceHazeBlur = true,
                rim = MealsLiquidGlass.controlRim(),
                sideGlintHeight = 17.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (leadingIcon != null) {
                        MealsIcon(
                            name = leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(25.dp)
                        )
                    }

                    Text(
                        text = selectedOption?.let(optionLabel) ?: placeholder,
                        modifier = Modifier.weight(1f),
                        color = if (selectedOption == null) MutedInk else Ink,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selectedOption == null) FontWeight.Medium else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    MealsIcon(
                        name = MealsIconName.ChevronRight,
                        contentDescription = if (expanded) "إغلاق القائمة" else "فتح القائمة",
                        modifier = Modifier
                            .size(23.dp)
                            .graphicsLayer { rotationZ = arrowRotation }
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = menuMinWidth)
                .heightIn(max = maxMenuHeight),
            shape = MealsLiquidGlass.DropdownShape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = null
        ) {
            MealsLiquidGlassFrame(
                modifier = Modifier.fillMaxWidth(),
                shape = MealsLiquidGlass.DropdownShape,
                elevation = MealsLiquidGlass.DropdownElevation,
                face = MealsLiquidGlass.modalFace(Color(0xFFFFF8F1)),
                hazeState = LocalMealsHazeState.current ?: LocalMealsCardHazeState.current,
                blurStyle = MealsLiquidGlass.ModalBlurStyle,
                blurRadius = MealsLiquidGlass.ModalBlurRadius,
                forceHazeBlur = true,
                rim = MealsLiquidGlass.modalRim(),
                sideGlintHeight = 25.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 7.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (options.isEmpty()) {
                        Text(
                            text = "لا توجد خيارات متاحة",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            color = MutedInk,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        options.forEach { option ->
                            val selected = option == selectedOption
                            MealsPressable(
                                onClick = {
                                    onOptionSelected(option)
                                    expanded = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp)
                                    .padding(horizontal = 2.dp),
                                shape = RoundedCornerShape(11.dp)
                            ) {
                                val pressed = LocalMealsGlassPressed.current
                                val rowShape = RoundedCornerShape(11.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(rowShape)
                                        .background(
                                            when {
                                                selected -> SoftOrange.copy(alpha = 0.58f)
                                                pressed -> Color.White.copy(alpha = 0.30f)
                                                else -> Color.Transparent
                                            },
                                            rowShape
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            text = optionLabel(option),
                                            color = if (selected) TextAccent else Ink,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        optionSupportingText?.invoke(option)
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { supporting ->
                                                Text(
                                                    text = supporting,
                                                    color = MutedInk,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                    }

                                    if (selected) {
                                        MealsIcon(
                                            name = MealsIconName.Check,
                                            contentDescription = "محدد",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
    val textDirection = if (
        keyboardOptions.keyboardType == KeyboardType.Decimal ||
        keyboardOptions.keyboardType == KeyboardType.Number
    ) TextDirection.Ltr else TextDirection.Content
    val rim = if (isError) MealsLiquidGlass.errorRim() else MealsLiquidGlass.controlRim()
    MealsLiquidGlassFrame(
        modifier = modifier.fillMaxWidth(),
        shape = MealsLiquidGlass.ControlShape,
        elevation = if (enabled) MealsLiquidGlass.SurfaceElevation else 0.dp,
        face = MealsLiquidGlass.controlFace(),
        hazeState = LocalMealsCardHazeState.current ?: LocalMealsHazeState.current,
        blurStyle = MealsLiquidGlass.ControlBlurStyle,
        blurRadius = MealsLiquidGlass.ControlBlurRadius,
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
                    cursorBrush = SolidColor(TextAccent),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = if (enabled) Ink else MutedInk,
                        textDirection = textDirection
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
        blurRadius = MealsLiquidGlass.ControlBlurRadius,
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
                cursorBrush = SolidColor(TextAccent),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink, textDirection = TextDirection.Content),
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
        targetValue = if (checked) TextAccent else Color.White.copy(alpha = 0.34f),
        animationSpec = tween(durationMillis = 180),
        label = "mealsCheckboxFill"
    )
    val edgeColor by animateColorAsState(
        targetValue = if (checked) TextAccent else Color.White.copy(alpha = 0.96f),
        animationSpec = tween(durationMillis = 180),
        label = "mealsCheckboxEdge"
    )
    val checkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 210),
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
            if (checked || checkProgress > 0.001f) {
                Canvas(
                    modifier = Modifier.size(14.dp).graphicsLayer { alpha = checkProgress }
                ) {
                    // Draw the check in two strokes so completion feels like
                    // a quick hand-drawn confirmation, not a pop-in glyph.
                    val start = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.52f)
                    val corner = androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.76f)
                    val end = androidx.compose.ui.geometry.Offset(size.width * 0.86f, size.height * 0.24f)
                    val firstLength = 0.38f
                    val strokeWidth = size.minDimension * 0.15f
                    if (checkProgress > 0f) {
                        val firstProgress = (checkProgress / firstLength).coerceIn(0f, 1f)
                        drawLine(
                            Color.White,
                            start,
                            androidx.compose.ui.geometry.Offset(
                                start.x + (corner.x - start.x) * firstProgress,
                                start.y + (corner.y - start.y) * firstProgress
                            ),
                            strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                    if (checkProgress > firstLength) {
                        val secondProgress = ((checkProgress - firstLength) / (1f - firstLength)).coerceIn(0f, 1f)
                        drawLine(
                            Color.White,
                            corner,
                            androidx.compose.ui.geometry.Offset(
                                corner.x + (end.x - corner.x) * secondProgress,
                                corner.y + (end.y - corner.y) * secondProgress
                            ),
                            strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
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
    val shape = RoundedCornerShape(18.dp)
    MealsPressable(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        role = Role.Checkbox,
        shape = shape,
        checked = checked
    ) {
        MealsCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (checked) WarmSurface else Paper,
            contentPadding = 0.dp,
            borderColor = if (checked) TextAccent.copy(alpha = 0.32f) else Border.copy(alpha = 0.72f),
            shape = shape,
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
    MealsPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = LargeTileShape
    ) {
        MealsCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (selected) SoftOrange else Paper,
            contentPadding = 0.dp,
            borderColor = if (selected) TextAccent.copy(alpha = 0.48f) else Border.copy(alpha = 0.72f),
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
                    modifier = Modifier.size(27.dp).clip(CircleShape).background(TextAccent),
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
}

@Composable
fun MealsProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (label != null) {
            Text(label, color = dz.meals.app.ui.theme.TextAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
            color = TextAccent,
            trackColor = SoftOrange
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
    background: Color = WarmSurface,
    size: Dp = 54.dp
) {
    MealsLiquidGlassFrame(
        modifier = modifier.size(size),
        shape = CircleShape,
        elevation = MealsLiquidGlass.ControlElevation,
        face = MealsLiquidGlass.illustrationFace(background),
        hazeState = LocalMealsCardHazeState.current ?: LocalMealsHazeState.current,
        blurStyle = MealsLiquidGlass.ControlBlurStyle,
        blurRadius = MealsLiquidGlass.ControlBlurRadius,
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


/**
 * Shared glass material for every overlay surface: sheets and the navigation
 * drawer use the same translucent face, white gradient rim and specular edge.
 */
@Composable
private fun MealsModalSurface(
    modifier: Modifier = Modifier,
    tintColor: Color = Color(0xFFFFF8F1),
    contentPadding: Dp = 22.dp,
    shape: Shape = MealsLiquidGlass.ModalShape,
    elevation: Dp = MealsLiquidGlass.ModalElevation,
    hazeStateOverride: HazeState? = null,
    glassBackdropOverride: MealsGlassBackdrop? = null,
    forceHazeBlur: Boolean = false,
    fillHeight: Boolean = false,
    padNavigationBars: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val hazeState = hazeStateOverride ?: LocalMealsCardHazeState.current
    val face = remember(tintColor) {
        MealsLiquidGlass.modalFace(tintColor)
    }

    MealsLiquidGlassFrame(
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        face = face,
        hazeState = hazeState,
        blurStyle = MealsLiquidGlass.ModalBlurStyle,
        blurRadius = MealsLiquidGlass.ModalBlurRadius,
        glassBackdrop = glassBackdropOverride,
        forceHazeBlur = forceHazeBlur,
        rim = MealsLiquidGlass.modalRim(),
        sideGlintHeight = 30.dp
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                modifier = (if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .then(if (padNavigationBars) Modifier.navigationBarsPadding() else Modifier)
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

/**
 * The only modal presentation in Meals. All former alerts, dialogs and edit
 * modals now use this sheet. By default it wraps short content naturally and
 * grows only as needed, up to 87% of the available height. Immersive flows may
 * explicitly request a fixed viewport and a body that fills the remaining space.
 */
private enum class MealsSheetVisualPhase {
    Content,
    ContentExit,
    Loading,
    LoadingExit
}

/**
 * The only modal presentation in Meals. It remains content-driven by default.
 *
 * Loading-aware sheets use a staged sequence:
 * 1. Fade the current content away.
 * 2. Expand the existing glass surface.
 * 3. Reveal loading content and notify the operation owner.
 * 4. On completion, fade loading away, contract, restore content and notify
 *    the owner so it can close or reuse the sheet.
 *
 * Do not replace the default dynamic sizing with fillMaxHeight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    titleText: String? = null,
    title: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmActionClosesSheet: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    onDismissAction: (() -> Unit)? = null,
    dismissActionClosesSheet: Boolean = false,
    showCloseButton: Boolean = true,
    /** Opt-in fixed height for immersive flows such as guided cooking. */
    fixedHeightFraction: Float? = null,
    /** Makes the text slot occupy the space between the header and footer. */
    fillBody: Boolean = false,
    /** Enables the reusable, staged loading presentation inside this sheet. */
    isLoading: Boolean = false,
    /** Expands the same glass surface to the available viewport while loading. */
    expandToFullScreenWhileLoading: Boolean = false,
    /** Called after expansion and loading-content entrance, when work should begin. */
    onLoadingStarted: (() -> Unit)? = null,
    /** Called after the exit sequence restores the content and resting height. */
    onLoadingExitFinished: (() -> Unit)? = null,
    /** Custom loading UI rendered inside the existing glass surface. */
    loadingContent: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()
    val dismissStarted = remember { mutableStateOf(false) }
    val activeLoading = isLoading && loadingContent != null
    var visualPhase by remember { mutableStateOf(MealsSheetVisualPhase.Content) }
    val expansion = remember { Animatable(0f) }
    val onLoadingStartedState = rememberUpdatedState(onLoadingStarted)
    val onLoadingExitFinishedState = rememberUpdatedState(onLoadingExitFinished)

    LaunchedEffect(activeLoading, expandToFullScreenWhileLoading) {
        if (activeLoading && visualPhase == MealsSheetVisualPhase.Content) {
            // Clear the old UI first. Expanding and crossfading at the same time
            // made the modal relayout and render two full compositions per frame.
            visualPhase = MealsSheetVisualPhase.ContentExit
            delay(130)

            if (expandToFullScreenWhileLoading) {
                expansion.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 380,
                        easing = FastOutSlowInEasing
                    )
                )
            }

            visualPhase = MealsSheetVisualPhase.Loading
            // Let the loading surface finish its short fade before starting work.
            delay(145)
            onLoadingStartedState.value?.invoke()
        } else if (!activeLoading && visualPhase != MealsSheetVisualPhase.Content) {
            // Reverse the same sequence: clear the loader, restore natural height,
            // then reveal the original content before the caller dismisses the sheet.
            visualPhase = MealsSheetVisualPhase.LoadingExit
            delay(125)

            if (expandToFullScreenWhileLoading) {
                expansion.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 330,
                        easing = FastOutSlowInEasing
                    )
                )
            }

            visualPhase = MealsSheetVisualPhase.Content
            delay(170)
            onLoadingExitFinishedState.value?.invoke()
        }
    }

    val dismissSheet: (afterDismiss: () -> Unit) -> Unit = remember(
        sheetState,
        sheetScope,
        dismissStarted
    ) {
        { afterDismiss ->
            if (!dismissStarted.value) {
                dismissStarted.value = true
                sheetScope.launch {
                    sheetState.hide()
                    if (!sheetState.isVisible) {
                        afterDismiss()
                    } else {
                        dismissStarted.value = false
                    }
                }
            }
        }
    }
    val topShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

    MealsBottomSheetMotionProvider {
        CompositionLocalProvider(LocalMealsSheetDismissAction provides dismissSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    if (!interactionLocked) onDismissRequest()
                },
                sheetGesturesEnabled = !interactionLocked,
                properties = ModalBottomSheetProperties(
                    shouldDismissOnBackPress = !interactionLocked,
                    shouldDismissOnClickOutside = !interactionLocked
                ),
                contentWindowInsets = {
                    BottomSheetDefaults.windowInsets
                        .only(WindowInsetsSides.Top)
                        .union(WindowInsets.ime)
                },
                sheetState = sheetState,
                shape = topShape,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                scrimColor = Color(0xFFFFF8F1).copy(alpha = 0.14f),
                tonalElevation = 0.dp,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 8.dp)
                            .size(width = 42.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.96f),
                                        Color.White.copy(alpha = 0.60f)
                                    )
                                )
                            )
                    )
                }
            ) {
                MealsModalWindowBlur()

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    // Regular sheets remain natural-height and are capped at 87%.
                    // AI AGENTS: preserve this default content-driven sizing contract.
                    val maxSheetHeight = maxHeight * 0.87f
                    val fixedSheetHeight = fixedHeightFraction
                        ?.coerceIn(0.72f, 0.94f)
                        ?.let { maxHeight * it }
                    val density = LocalDensity.current
                    val naturalSheetHeightPx = remember { mutableStateOf(0) }
                    val measuredNaturalHeight = naturalSheetHeightPx.value
                        .takeIf { it > 0 }
                        ?.let { with(density) { it.toDp() } }
                    val restingSheetHeight = fixedSheetHeight
                        ?: measuredNaturalHeight
                        ?: maxSheetHeight
                    val animatedSheetHeight = restingSheetHeight +
                        (maxHeight - restingSheetHeight) * expansion.value
                    val loadingSheetHeight = if (expandToFullScreenWhileLoading) {
                        animatedSheetHeight
                    } else {
                        restingSheetHeight
                    }
                    val isInLoadingTransition = visualPhase != MealsSheetVisualPhase.Content
                    val interactionLocked = activeLoading || isInLoadingTransition
                    val sheetHeightModifier = when {
                        expandToFullScreenWhileLoading && isInLoadingTransition ->
                            Modifier.height(animatedSheetHeight)
                        fixedSheetHeight != null ->
                            Modifier.height(fixedSheetHeight)
                        else ->
                            Modifier.heightIn(max = maxSheetHeight)
                    }
                    val contentHeightModifier = when {
                        fixedSheetHeight != null -> Modifier.height(fixedSheetHeight)
                        else -> Modifier.wrapContentHeight()
                    }
                    val maxBodyHeight = ((fixedSheetHeight ?: maxSheetHeight) - 164.dp)
                        .coerceAtLeast(120.dp)

                    MealsModalSurface(
                        modifier = modifier
                            .fillMaxWidth()
                            .then(sheetHeightModifier)
                            .onSizeChanged { size ->
                                if (
                                    !activeLoading &&
                                    visualPhase == MealsSheetVisualPhase.Content &&
                                    expansion.value <= 0.001f &&
                                    fixedSheetHeight == null &&
                                    naturalSheetHeightPx.value != size.height
                                ) {
                                    naturalSheetHeightPx.value = size.height
                                }
                            },
                        contentPadding = 0.dp,
                        shape = topShape,
                        elevation = MealsLiquidGlass.ModalElevation,
                        hazeStateOverride = LocalMealsHazeState.current ?: LocalMealsCardHazeState.current,
                        glassBackdropOverride = null,
                        forceHazeBlur = true,
                        padNavigationBars = true
                    ) {
                        AnimatedContent(
                            targetState = visualPhase,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (visualPhase == MealsSheetVisualPhase.Content) contentHeightModifier
                                    else Modifier.height(loadingSheetHeight)
                                ),
                            transitionSpec = {
                                fadeIn(
                                    animationSpec = tween(
                                        durationMillis = 155,
                                        easing = FastOutSlowInEasing
                                    )
                                ) togetherWith fadeOut(
                                    animationSpec = tween(
                                        durationMillis = 120,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            },
                            contentAlignment = Alignment.Center,
                            label = "mealsBottomSheetLoadingSequence"
                        ) { phase ->
                            when (phase) {
                                MealsSheetVisualPhase.Content -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (fixedSheetHeight != null) Modifier.fillMaxHeight()
                                                else Modifier.heightIn(max = maxSheetHeight)
                                            )
                                            .padding(horizontal = 22.dp, vertical = 18.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        if (titleText != null || title != null || subtitle != null || showCloseButton) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    titleText?.let {
                                                        Text(
                                                            it,
                                                            style = MaterialTheme.typography.titleLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Ink
                                                        )
                                                    }
                                                    title?.invoke()
                                                    subtitle?.let {
                                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MutedInk)
                                                    }
                                                }
                                                if (showCloseButton) {
                                                    MealsIconButton(
                                                        onClick = { dismissSheet { onDismissRequest() } },
                                                        modifier = Modifier.size(44.dp),
                                                        size = 44.dp
                                                    ) {
                                                        MealsIcon(
                                                            MealsIconName.Close,
                                                            contentDescription = "إغلاق",
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (text != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(
                                                        if (fillBody) Modifier.weight(1f)
                                                        else Modifier.heightIn(max = maxBodyHeight)
                                                    )
                                            ) {
                                                text()
                                            }
                                        }

                                        content()

                                        if (confirmButton != null || dismissButton != null) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                confirmButton?.let { button ->
                                                    Box(modifier = Modifier.weight(1f)) { button() }
                                                }
                                                dismissButton?.let { button ->
                                                    Box(modifier = Modifier.weight(1f)) { button() }
                                                }
                                            }
                                        }

                                        if (confirmLabel != null || dismissLabel != null) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (confirmLabel != null && onConfirm != null) {
                                                    MealsPrimaryButton(
                                                        label = confirmLabel,
                                                        onClick = {
                                                            if (confirmActionClosesSheet) {
                                                                dismissSheet { onConfirm() }
                                                            } else {
                                                                onConfirm()
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        enabled = confirmEnabled
                                                    )
                                                }
                                                if (dismissLabel != null) {
                                                    MealsSecondaryButton(
                                                        label = dismissLabel,
                                                        onClick = {
                                                            when {
                                                                onDismissAction == null ->
                                                                    dismissSheet { onDismissRequest() }
                                                                dismissActionClosesSheet ->
                                                                    dismissSheet { onDismissAction() }
                                                                else -> onDismissAction()
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                MealsSheetVisualPhase.ContentExit,
                                MealsSheetVisualPhase.LoadingExit -> {
                                    Box(Modifier.fillMaxSize())
                                }

                                MealsSheetVisualPhase.Loading -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        loadingContent?.invoke(this)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A navigation drawer keeps its drawer interaction but shares the glass modal material. */
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
                MealsModalSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = drawerShape,
                    contentPadding = 20.dp,
                    glassBackdropOverride = LocalMealsScreenGlassBackdrop.current,
                    fillHeight = true
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
 * A navigation capsule whose actual upper contour rises into a moving glass
 * crest. The crest is part of the rim/clip shape, not a detached decoration.
 */
private class MealsNavigationRimShape(
    private val bumpCenter: Dp,
    private val bumpWidth: Dp,
    private val rise: Dp = 6.5.dp,
    private val topEdgeInset: Dp = 8.dp
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): Outline {
        val width = size.width
        val height = size.height
        val radius = with(density) { 26.dp.toPx() }
            .coerceAtMost(minOf(width / 2f, height / 2f))
        val minimumInsetPx = with(density) { 1.dp.toPx() }
        val edgeY = with(density) { topEdgeInset.toPx() }
            .coerceIn(minimumInsetPx, height * 0.2f)
        val crestY = (edgeY - with(density) { rise.toPx() })
            .coerceAtLeast(minimumInsetPx)
        val center = with(density) { bumpCenter.toPx() }
            .coerceIn(radius, width - radius)
        val crestWidth = with(density) { bumpWidth.toPx() }
            .coerceAtMost((width - radius * 2f).coerceAtLeast(0f))
        val left = (center - crestWidth / 2f).coerceIn(radius, width - radius)
        val right = (center + crestWidth / 2f).coerceIn(radius, width - radius)
        val cornerControl = radius * 0.55228475f
        val shoulderControl = crestWidth * 0.18f

        val path = Path().apply {
            moveTo(radius, edgeY)
            lineTo(left, edgeY)
            cubicTo(
                left + shoulderControl, edgeY,
                center - shoulderControl, crestY,
                center, crestY
            )
            cubicTo(
                center + shoulderControl, crestY,
                right - shoulderControl, edgeY,
                right, edgeY
            )
            lineTo(width - radius, edgeY)
            cubicTo(
                width - radius + cornerControl, edgeY,
                width, edgeY + radius - cornerControl,
                width, edgeY + radius
            )
            lineTo(width, height - radius)
            cubicTo(
                width, height - radius + cornerControl,
                width - radius + cornerControl, height,
                width - radius, height
            )
            lineTo(radius, height)
            cubicTo(
                radius - cornerControl, height,
                0f, height - radius + cornerControl,
                0f, height - radius
            )
            lineTo(0f, edgeY + radius)
            cubicTo(
                0f, edgeY + radius - cornerControl,
                radius - cornerControl, edgeY,
                radius, edgeY
            )
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Floating glass navigation capsule. The lens tracks logical tab order from
 * the layout's start edge, while the moving upper crest is positioned in
 * physical left-to-right coordinates so RTL and LTR share the same geometry.
 */
@Composable
fun MealsFrostedBottomBar(
    items: List<MealsNavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 18.dp).padding(bottom = 8.dp)
    ) {
        val itemCount = items.size.coerceAtLeast(1)
        val tabGap = 2.dp
        val tabSlotWidth = (maxWidth - 14.dp - tabGap * (itemCount - 1)) / itemCount
        val activeIndex = selectedIndex.coerceIn(0, itemCount - 1)
        val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current ==
            androidx.compose.ui.unit.LayoutDirection.Rtl

        // TopStart is on the right in RTL. Offset from that anchor must be
        // negative as the selected logical index advances to tabs on the left.
        val indicatorIndex by animateFloatAsState(
            targetValue = activeIndex.toFloat(),
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "mealsNavIndicatorPosition"
        )
        val indicatorDirection = if (isRtl) -1f else 1f

        // The rim shape is expressed in physical X coordinates, unlike TopStart.
        // In RTL the first logical tab is the rightmost physical slot.
        val visualIndex = if (isRtl) itemCount - 1 - activeIndex else activeIndex
        val bumpWidth = (tabSlotWidth * 0.66f).coerceIn(38.dp, 50.dp)
        val bumpTargetX = 7.dp +
            tabSlotWidth * visualIndex +
            tabGap * visualIndex +
            (tabSlotWidth - bumpWidth) / 2
        val bumpOffsetX by animateDpAsState(
            targetValue = bumpTargetX,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 430f),
            label = "mealsNavGlassBumpPosition"
        )
        val navigationShape = remember(bumpOffsetX, bumpWidth) {
            MealsNavigationRimShape(
                bumpCenter = bumpOffsetX + bumpWidth / 2,
                bumpWidth = bumpWidth
            )
        }

        MealsLiquidGlassFrame(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = navigationShape,
            elevation = 10.dp,
            face = MealsLiquidGlass.controlFace(),
            hazeState = hazeState,
            blurStyle = MealsLiquidGlass.SurfaceBlurStyle,
            blurRadius = MealsLiquidGlass.SurfaceBlurRadius,
            glassBackdrop = LocalMealsScreenGlassBackdrop.current,
            rim = MealsLiquidGlass.controlRim(),
            sideGlintHeight = 30.dp
        ) {
            if (items.isNotEmpty()) {
                val indicatorShape = RoundedCornerShape(18.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 7.dp, top = 12.dp)
                        .width(tabSlotWidth)
                        .height(55.dp)
                        .graphicsLayer {
                            // Layer-only read avoids rebuilding navbar content per frame.
                            translationX = (
                                (tabSlotWidth + tabGap) * indicatorIndex * indicatorDirection
                            ).toPx()
                        }
                        .clip(indicatorShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.48f),
                                    SoftOrange.copy(alpha = 0.28f),
                                    Color.White.copy(alpha = 0.18f)
                                )
                            )
                        )
                        .border(
                            width = 0.65.dp,
                            shape = indicatorShape,
                            brush = MealsLiquidGlass.innerRim()
                        )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(72.dp)
                    .padding(horizontal = 7.dp)
                    .padding(top = 12.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(tabGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == activeIndex
                    val foreground by animateColorAsState(
                        targetValue = if (selected) Ink else MutedInk,
                        label = "mealsNavForeground$index"
                    )
                    val labelFontSize by animateFloatAsState(
                        targetValue = if (selected) 14f else 13f,
                        animationSpec = tween(durationMillis = 210),
                        label = "mealsNavLabelSize$index"
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
                        MealsNavigationIcon(
                            name = item.icon,
                            selected = selected,
                            contentDescription = item.label,
                            modifier = Modifier.size(35.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = foreground,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = labelFontSize.sp
                            ),
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
    // moving content. The card state is isolated to the fixed background artwork,
    // preventing a card from sampling itself or neighbouring card outputs.
    val hazeState = rememberHazeState()
    val cardHazeState = rememberHazeState()
    val cardGlassBackdrop = rememberMealsGlassBackdrop()
    val screenGlassBackdrop = rememberMealsGlassBackdrop()

    CompositionLocalProvider(
        LocalMealsHazeState provides hazeState,
        LocalMealsCardHazeState provides cardHazeState,
        LocalMealsCardGlassBackdrop provides cardGlassBackdrop,
        LocalMealsScreenGlassBackdrop provides screenGlassBackdrop
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .mealsHazeSource(cardHazeState)
                    .mealsGlassSource(cardGlassBackdrop)
            ) {
                // This sits behind the Scaffold, so it stays fixed while tab content scrolls.
                Image(
                    painter = painterResource(Res.drawable.meals_kitchen_background),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .mealsHazeSource(hazeState)
                )
            }
            content()
        }
    }
}


/**
 * Compact rolling value transition for quantities and counters. The number
 * moves vertically with a short fade so updates feel immediate but readable.
 */
@Composable
fun MealsAnimatedCount(
    value: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(animationSpec = tween(130)) +
                slideInVertically(animationSpec = tween(170)) { it / 3 }) togetherWith
                (fadeOut(animationSpec = tween(90)) +
                    slideOutVertically(animationSpec = tween(120)) { -it / 3 })
        },
        label = "mealsAnimatedCount"
    ) { displayedValue ->
        Text(
            text = displayedValue,
            color = color,
            style = style,
            fontWeight = fontWeight,
            maxLines = 1
        )
    }
}
