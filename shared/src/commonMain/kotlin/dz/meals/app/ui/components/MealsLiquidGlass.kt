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
    /**
     * A luminous ivory frost shared by cards, hero surfaces, dropdowns and sheets.
     * Its opacity is strong enough to separate glass from pale blue/green artwork
     * while keeping the backdrop visible through the surface.
     */
    // Neutral ivory keeps the glass luminous without turning the blue theme yellow.
    val GlassTint = Color(0xFFFFFCF8)
    const val GlassTintOpacity = 0.42f

    /**
     * Original near-neutral control tint. Buttons use their own very light
     * gradient so they stay distinct from their parent without looking opaque.
     */
    val ControlTint = Color(0xFFFFFEFC)
    const val ControlTintOpacity = 0.44f

    /** Button faces remain lighter than the stronger 42% parent glass surfaces. */
    const val ButtonFaceOpacity = 0.18f

    val ControlBlurRadius = 12.dp
    val SurfaceBlurRadius = 10.dp
    // Keep modal frost consistent with cards without adding another blur layer.
    val ModalBlurRadius = 10.dp

    val ControlShape = RoundedCornerShape(15.dp)
    val CardShape = RoundedCornerShape(26.dp)
    val HeroShape = RoundedCornerShape(29.dp)
    val ModalShape = RoundedCornerShape(30.dp)
    val DropdownShape = RoundedCornerShape(19.dp)
    val NavigationShape = RoundedCornerShape(27.dp)

    val ControlElevation = 3.dp
    val SurfaceElevation = 2.dp
    val ModalElevation = 1.dp
    val DropdownElevation = 7.dp
    /**
     * Original subtle pearl gradient restored as one face layer. No Haze tint
     * is stacked underneath it.
     */
    fun controlFace(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = ControlTintOpacity + 0.06f),
            0.22f to ControlTint.copy(alpha = ControlTintOpacity),
            0.68f to Color(0xFFF5F1EB).copy(alpha = ControlTintOpacity - 0.06f),
            1f to Color.White.copy(alpha = ControlTintOpacity)
        )
    )

    /**
     * Button face: a restrained pearl wash, always lighter than the sheet's
     * 42% shared ivory tint so the blurred backdrop remains clearly visible.
     */
    fun buttonFace(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = ButtonFaceOpacity + 0.04f),
            0.22f to ControlTint.copy(alpha = ButtonFaceOpacity),
            0.68f to Color(0xFFF5F1EB).copy(alpha = ButtonFaceOpacity * 0.55f),
            1f to Color.White.copy(alpha = ButtonFaceOpacity)
        )
    )

    /** The sole translucent color layer shared by parent glass surfaces. */
    private fun sharedGlassFace(tint: Color = GlassTint): Brush =
        SolidColor((if (tint == Color.Transparent) GlassTint else tint).copy(alpha = GlassTintOpacity))

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

    /** Cards, hero surfaces and overlays all share exactly the same tint token. */
    fun cardFace(tint: Color = GlassTint): Brush = sharedGlassFace(tint)

    fun heroFace(tint: Color = GlassTint): Brush = sharedGlassFace(tint)

    fun modalFace(tint: Color = GlassTint): Brush = sharedGlassFace(tint)

    fun modalRim(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.99f),
            0.22f to Color.White.copy(alpha = 0.88f),
            0.56f to Color.White.copy(alpha = 0.52f),
            1f to Color.White.copy(alpha = 0.94f)
        )
    )

    fun illustrationFace(tint: Color = GlassTint): Brush = sharedGlassFace(tint)

    // Blur stays active without a fixed color effect: the backdrop itself
    // supplies the hue for every glass material on both Android and iOS.
    val ControlBlurStyle = HazeBlurStyle {
        blurRadius(12.dp)
        noiseFactor(0.014f)
    }

    val ModalBlurStyle = HazeBlurStyle {
        blurRadius(ModalBlurRadius)
        noiseFactor(0.005f)
    }

    val SurfaceBlurStyle = HazeBlurStyle {
        blurRadius(8.dp)
        noiseFactor(0.02f)
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
        moveTo(size.width * 0.15f, inset * 1.16f)
        cubicTo(
            size.width * 0.29f, inset * 0.46f,
            size.width * 0.69f, inset * 0.46f,
            size.width * 0.85f, inset * 1.12f
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
