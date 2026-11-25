package dz.meals.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dz.meals.app.ui.theme.Border

/**
 * The named visual language for Meals: **Meals Liquid Glass**.
 *
 * Keep material, rim, reflection, blur and motion values here so buttons,
 * icon actions, search fields, cards, dialogs and navigation share one finish.
 */
object MealsLiquidGlass {
    /** Neutral white frost; theme-specific opacity is supplied by MealsBackdrop. */
    val GlassTint = Color.White
    const val GlassTintOpacity = 0.30f

    /** Control faces use the same neutral white, at a lighter opacity than cards. */
    val ControlTint = Color.White
    const val ControlTintOpacity = 0.22f

    /** Buttons stay the lightest glass layer, never competing with their parent. */
    const val ButtonFaceOpacity = 0.12f

    val ControlBlurRadius = 8.dp
    val SurfaceBlurRadius = 6.dp
    val ModalBlurRadius = 6.dp

    val ControlShape = RoundedCornerShape(15.dp)
    val CardShape = RoundedCornerShape(26.dp)
    val HeroShape = RoundedCornerShape(29.dp)
    val ModalShape = RoundedCornerShape(30.dp)
    val DropdownShape = RoundedCornerShape(19.dp)
    val NavigationShape = RoundedCornerShape(27.dp)

    val ControlElevation = 3.dp
    /** Buttons sit visibly above the sheet instead of sharing its surface plane. */
    val ButtonElevation = 5.dp
    val SurfaceElevation = 2.dp
    val ModalElevation = 1.dp
    val DropdownElevation = 7.dp
    /**
     * Original subtle pearl gradient restored as one face layer. No Haze tint
     * is stacked underneath it.
     */
    fun controlFace(opacity: Float = ControlTintOpacity): Brush {
        val faceOpacity = opacity.coerceIn(0.04f, 0.38f)
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = faceOpacity + 0.035f),
                0.22f to ControlTint.copy(alpha = faceOpacity),
                0.68f to Color.White.copy(alpha = (faceOpacity - 0.045f).coerceAtLeast(0.02f)),
                1f to Color.White.copy(alpha = faceOpacity)
            )
        )
    }

    /**
     * A lightly filled pearl lens. The crown catches more light while the
     * lower face falls away, so the button reads as a separate curved object
     * without becoming more opaque than its parent surface.
     */
    fun buttonFace(opacity: Float = ButtonFaceOpacity): Brush {
        val faceOpacity = opacity.coerceIn(0.04f, 0.24f)
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = (faceOpacity + 0.075f).coerceAtMost(0.27f)),
                0.14f to Color.White.copy(alpha = (faceOpacity + 0.045f).coerceAtMost(0.24f)),
                0.46f to ControlTint.copy(alpha = faceOpacity),
                0.82f to Color(0xFFEAE4DC).copy(alpha = faceOpacity * 0.44f),
                1f to Color.White.copy(alpha = faceOpacity * 0.76f)
            )
        )
    }

    /** One white translucent layer; opacity is resolved from the active wallpaper. */
    private fun sharedGlassFace(
        tint: Color = GlassTint,
        opacity: Float = GlassTintOpacity
    ): Brush = SolidColor(
        (if (tint == Color.Transparent) GlassTint else tint).copy(alpha = opacity.coerceIn(0.08f, 0.48f))
    )

    /** A neutral white optical edge; its intensity variation is drawn separately. */
    fun controlRim(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.98f),
            0.24f to Color.White.copy(alpha = 0.72f),
            0.58f to Color.White.copy(alpha = 0.38f),
            1f to Color.White.copy(alpha = 0.86f)
        )
    )

    @Suppress("UNUSED_PARAMETER")
    fun surfaceRim(lowerEdge: Color = Border): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.98f),
            0.30f to Color.White.copy(alpha = 0.72f),
            0.72f to Color.White.copy(alpha = 0.38f),
            1f to Color.White.copy(alpha = 0.86f)
        )
    )

    fun errorRim(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.98f),
            Color(0xFFE7B1AA).copy(alpha = 0.86f),
            Color(0xFFB64A45).copy(alpha = 0.88f)
        )
    )

    fun innerRim(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.78f),
            Color.White.copy(alpha = 0.32f),
            Color.White.copy(alpha = 0.54f)
        )
    )

    fun specular(): Brush = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.98f),
            Color.White.copy(alpha = 0.88f),
            Color.Transparent
        )
    )

    fun sideGlint(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.86f),
            Color.Transparent
        )
    )

    /** Cards, hero surfaces and overlays share white material with theme-aware opacity. */
    fun cardFace(tint: Color = GlassTint, opacity: Float = GlassTintOpacity): Brush =
        sharedGlassFace(tint, opacity)

    fun heroFace(tint: Color = GlassTint, opacity: Float = GlassTintOpacity): Brush =
        sharedGlassFace(tint, opacity)

    fun modalFace(tint: Color = GlassTint, opacity: Float = GlassTintOpacity): Brush =
        sharedGlassFace(tint, opacity)

    fun modalRim(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.99f),
            0.22f to Color.White.copy(alpha = 0.88f),
            0.56f to Color.White.copy(alpha = 0.52f),
            1f to Color.White.copy(alpha = 0.94f)
        )
    )

    fun illustrationFace(tint: Color = GlassTint, opacity: Float = GlassTintOpacity): Brush =
        sharedGlassFace(tint, opacity)

    // Blur stays active without a fixed color effect: the backdrop itself
    // supplies the hue for every glass material on both Android and iOS.
    val ControlBlurStyle = HazeBlurStyle {
        blurRadius(ControlBlurRadius)
        noiseFactor(0.004f)
    }

    val ModalBlurStyle = HazeBlurStyle {
        blurRadius(ModalBlurRadius)
        noiseFactor(0.002f)
    }

    val SurfaceBlurStyle = HazeBlurStyle {
        blurRadius(SurfaceBlurRadius)
        noiseFactor(0.004f)
    }
}


/**
 * Raised button optics: a curved crown catchlight, detached shoulder glints,
 * and a short lower caustic create the impression of thick polished glass.
 *
 * It deliberately does not trace the perimeter. The face is already clipped
 * by its glass frame, and the soft shadow supplies physical separation.
 * Brushes and paths are cached; no blur, noise texture, or shader is added.
 */
internal fun Modifier.mealsButtonOpticalEdges(
    baseRim: Brush,
    depth: Float = 1f
): Modifier = drawWithCache {
    val edgeDepth = depth.coerceIn(0.35f, 1f)
    val inset = 0.9.dp.toPx()
    val topRise = 0.025f * size.height
    val topShoulder = 0.16f * size.height
    val sideInset = 3.0.dp.toPx()
    val lowerInset = 3.1.dp.toPx()

    // A pronounced convex arc follows the lens curvature. Its ends sit lower
    // inside the rounded shoulders and the middle rises, avoiding a straight
    // horizontal streak across the top of the button.
    val crown = Path().apply {
        moveTo(size.width * 0.16f, topShoulder)
        cubicTo(
            size.width * 0.28f, topRise,
            size.width * 0.72f, topRise,
            size.width * 0.84f, topShoulder
        )
    }
    val innerCrown = Path().apply {
        moveTo(size.width * 0.25f, topShoulder * 0.92f)
        cubicTo(
            size.width * 0.36f, topShoulder * 0.48f,
            size.width * 0.64f, topShoulder * 0.48f,
            size.width * 0.75f, topShoulder * 0.92f
        )
    }
    val leftShoulder = Path().apply {
        moveTo(sideInset, size.height * 0.29f)
        cubicTo(
            inset * 1.0f, size.height * 0.39f,
            inset * 1.0f, size.height * 0.61f,
            sideInset, size.height * 0.71f
        )
    }
    val rightShoulder = Path().apply {
        moveTo(size.width - sideInset, size.height * 0.29f)
        cubicTo(
            size.width - inset, size.height * 0.39f,
            size.width - inset, size.height * 0.61f,
            size.width - sideInset, size.height * 0.71f
        )
    }
    val lowerCaustic = Path().apply {
        moveTo(size.width * 0.30f, size.height - lowerInset)
        cubicTo(
            size.width * 0.40f, size.height - inset * 0.62f,
            size.width * 0.60f, size.height - inset * 0.62f,
            size.width * 0.70f, size.height - lowerInset
        )
    }

    val crownBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.16f to Color.White.copy(alpha = 0.38f * edgeDepth),
            0.50f to Color.White.copy(alpha = 0.98f * edgeDepth),
            0.84f to Color.White.copy(alpha = 0.44f * edgeDepth),
            1f to Color.Transparent
        )
    )
    val innerCrownBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.22f to Color.White.copy(alpha = 0.12f * edgeDepth),
            0.50f to Color.White.copy(alpha = 0.30f * edgeDepth),
            0.78f to Color.White.copy(alpha = 0.12f * edgeDepth),
            1f to Color.Transparent
        )
    )
    val shoulderBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.22f to Color.White.copy(alpha = 0.36f * edgeDepth),
            0.50f to Color.White.copy(alpha = 0.78f * edgeDepth),
            0.78f to Color.White.copy(alpha = 0.34f * edgeDepth),
            1f to Color.Transparent
        ),
        startY = size.height * 0.29f,
        endY = size.height * 0.71f
    )
    val lowerBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.20f to Color(0xFFC8B8A5).copy(alpha = 0.14f * edgeDepth),
            0.50f to Color.White.copy(alpha = 0.44f * edgeDepth),
            0.80f to Color(0xFFC8B8A5).copy(alpha = 0.14f * edgeDepth),
            1f to Color.Transparent
        )
    )

    onDrawBehind {
        // Each reflection is a curved, localized stroke. No rectangular
        // gradient overlays are painted behind labels or icons.
        drawPath(
            path = crown,
            brush = crownBrush,
            alpha = edgeDepth,
            style = Stroke(width = 1.30.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = innerCrown,
            brush = innerCrownBrush,
            style = Stroke(width = 0.62.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = leftShoulder,
            brush = shoulderBrush,
            style = Stroke(width = 0.78.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = rightShoulder,
            brush = shoulderBrush,
            style = Stroke(width = 0.78.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = lowerCaustic,
            brush = lowerBrush,
            style = Stroke(width = 0.78.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Lightweight optical treatment shared by every Meals glass surface.
 *
 * A single fine perimeter defines the glass. The sense of thickness comes
 * from a few soft, localized reflections rather than a second inset border.
 * Everything is drawn from cached Compose paths and gradients, with no
 * refraction shader or additional blur pass.
 */
internal fun Modifier.mealsOpticalEdges(
    shape: Shape,
    baseRim: Brush,
    depth: Float = 1f,
    sideGlintHeight: Dp = 17.dp
): Modifier = drawWithCache {
    val edgeDepth = depth.coerceIn(0.35f, 1f)
    val outline = shape.createOutline(size, layoutDirection, this)
    val inset = 1.15.dp.toPx()
    // Slightly fuller single contour improves edge readability without stacking borders.
    val edgeWidth = 1.10.dp.toPx()
    val topReflectionWidth = 1.06.dp.toPx()
    val sideLength = sideGlintHeight.toPx().coerceAtMost(size.height * 0.58f)
    val sideTop = ((size.height - sideLength) * 0.5f).coerceAtLeast(inset * 2f)

    // Keep the face sheen concentrated near the light-facing upper edge.
    val topSurfaceSheen = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.23f * edgeDepth),
            0.30f to Color.White.copy(alpha = 0.088f * edgeDepth),
            1f to Color.Transparent
        ),
        startY = 0f,
        endY = (size.height * 0.32f).coerceAtLeast(1f)
    )

    // A short curved catchlight sits just inside the upper rim, not around
    // the entire surface, so it reads as a reflection rather than a border.
    val topReflection = Path().apply {
        moveTo(size.width * 0.16f, inset * 2.75f)
        cubicTo(
            size.width * 0.29f, inset * 0.30f,
            size.width * 0.71f, inset * 0.30f,
            size.width * 0.84f, inset * 2.70f
        )
    }
    val sideReflection = Path().apply {
        moveTo(inset * 0.94f, sideTop + sideLength * 0.14f)
        cubicTo(
            inset * 0.50f, sideTop + sideLength * 0.34f,
            inset * 0.50f, sideTop + sideLength * 0.68f,
            inset * 0.94f, sideTop + sideLength * 0.86f
        )
    }
    val lowerCornerReflection = Path().apply {
        moveTo(size.width * 0.73f, size.height - inset * 1.24f)
        cubicTo(
            size.width * 0.83f, size.height - inset * 0.70f,
            size.width * 0.92f, size.height - inset * 1.88f,
            size.width - inset * 1.24f, size.height * 0.78f
        )
    }

    val reflectionBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.18f to Color.White.copy(alpha = 0.46f * edgeDepth),
            0.48f to Color.White.copy(alpha = 0.88f * edgeDepth),
            0.82f to Color.White.copy(alpha = 0.50f * edgeDepth),
            1f to Color.Transparent
        )
    )
    // A broad, low-energy lens bloom adds a soft optical hotspot to the face;
    // it is not a second border or a color overlay.
    val lensBloom = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.13f * edgeDepth),
            Color.White.copy(alpha = 0.045f * edgeDepth),
            Color.Transparent
        ),
        center = Offset(size.width * 0.50f, size.height * 0.055f),
        radius = (size.width * 0.72f).coerceAtLeast(1f)
    )
    val sideBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.24f to Color.White.copy(alpha = 0.46f * edgeDepth),
            0.52f to Color.White.copy(alpha = 0.82f * edgeDepth),
            0.82f to Color.White.copy(alpha = 0.42f * edgeDepth),
            1f to Color.Transparent
        ),
        startY = sideTop,
        endY = sideTop + sideLength
    )

    onDrawBehind {
        drawRect(
            brush = topSurfaceSheen,
            size = Size(size.width, size.height * 0.32f)
        )
        drawRect(
            brush = lensBloom,
            size = Size(size.width, size.height * 0.28f)
        )

        // One clean, white contour replaces the former stacked full outlines.
        drawOutline(
            outline = outline,
            brush = baseRim,
            alpha = 0.90f * edgeDepth,
            style = Stroke(width = edgeWidth)
        )

        // Short reflections supply the soft optical depth without another rim.
        drawPath(
            path = topReflection,
            brush = reflectionBrush,
            style = Stroke(width = topReflectionWidth, cap = StrokeCap.Round)
        )
        drawPath(
            path = sideReflection,
            brush = sideBrush,
            style = Stroke(width = 0.68.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = lowerCornerReflection,
            brush = sideBrush,
            style = Stroke(width = 0.58.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
