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
     * One very subtle neutral tint shared by cards, hero surfaces, dropdowns
     * and sheets. Kept at the original low surface opacity and drawn only once.
     */
    val GlassTint = Color(0xFFFFFCF7)
    const val GlassTintOpacity = 0.20f

    /**
     * Original near-neutral control tint. Buttons use their own very light
     * gradient so they stay distinct from their parent without looking opaque.
     */
    val ControlTint = Color(0xFFFFFEFC)
    const val ControlTintOpacity = 0.36f

    val ControlBlurRadius = 12.dp
    val SurfaceBlurRadius = 8.dp
    // A compact blur retains the frosted glass while keeping modal transitions light.
    val ModalBlurRadius = 8.dp

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

    /** The sole translucent color layer shared by parent glass surfaces. */
    private fun sharedGlassFace(tint: Color = GlassTint): Brush =
        SolidColor((if (tint == Color.Transparent) GlassTint else tint).copy(alpha = GlassTintOpacity))

    /** A neutral white optical edge; its intensity variation is drawn separately. */
    fun controlRim(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.96f),
            0.24f to Color.White.copy(alpha = 0.62f),
            0.58f to Color.White.copy(alpha = 0.30f),
            1f to Color.White.copy(alpha = 0.78f)
        )
    )

    @Suppress("UNUSED_PARAMETER")
    fun surfaceRim(lowerEdge: Color = Border): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.92f),
            0.30f to Color.White.copy(alpha = 0.58f),
            0.72f to Color.White.copy(alpha = 0.26f),
            1f to Color.White.copy(alpha = 0.74f)
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
            0f to Color.White.copy(alpha = 0.98f),
            0.22f to Color.White.copy(alpha = 0.82f),
            0.56f to Color.White.copy(alpha = 0.42f),
            1f to Color.White.copy(alpha = 0.90f)
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
    val edgeWidth = 0.86.dp.toPx()
    val topReflectionWidth = 0.82.dp.toPx()
    val sideLength = sideGlintHeight.toPx().coerceAtMost(size.height * 0.58f)
    val sideTop = ((size.height - sideLength) * 0.5f).coerceAtLeast(inset * 2f)

    // Keep the face sheen concentrated near the light-facing upper edge.
    val topSurfaceSheen = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.15f * edgeDepth),
            0.30f to Color.White.copy(alpha = 0.055f * edgeDepth),
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
            0.18f to Color.White.copy(alpha = 0.34f * edgeDepth),
            0.48f to Color.White.copy(alpha = 0.72f * edgeDepth),
            0.82f to Color.White.copy(alpha = 0.38f * edgeDepth),
            1f to Color.Transparent
        )
    )
    val sideBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.24f to Color.White.copy(alpha = 0.34f * edgeDepth),
            0.52f to Color.White.copy(alpha = 0.66f * edgeDepth),
            0.82f to Color.White.copy(alpha = 0.30f * edgeDepth),
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
