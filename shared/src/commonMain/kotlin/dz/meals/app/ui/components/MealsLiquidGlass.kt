package dz.meals.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawOutline
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dz.meals.app.ui.theme.Border

/**
 * The named visual language for Meals: **Meals Liquid Glass**.
 *
 * Keep material, rim, reflection, blur and motion values here so buttons,
 * icon actions, search fields, cards, dialogs and navigation share one finish.
 */
object MealsLiquidGlass {
    /** One shared transparency level for everyday glass faces. */
    const val SurfaceOpacity = 0.20f
    /** Control faces are more opaque than cards so actions remain distinct on glass surfaces. */
    const val ControlOpacity = 0.34f
    /** A stronger translucent tint keeps the blurred page visible while preserving legibility. */
    const val ModalOpacity = 0.24f

    val ControlBlurRadius = 12.dp
    val SurfaceBlurRadius = 8.dp
    val ModalBlurRadius = 20.dp

    val ControlShape = RoundedCornerShape(15.dp)
    val CardShape = RoundedCornerShape(26.dp)
    val HeroShape = RoundedCornerShape(29.dp)
    val ModalShape = RoundedCornerShape(30.dp)
    val NavigationShape = RoundedCornerShape(27.dp)

    val ControlElevation = 3.dp
    val SurfaceElevation = 2.dp
    val ModalElevation = 1.dp
    fun controlFace(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.42f),
            0.22f to Color(0xFFFFFEFC).copy(alpha = 0.36f),
            0.68f to Color(0xFFF5F1EB).copy(alpha = 0.30f),
            1f to Color.White.copy(alpha = 0.36f)
        )
    )

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

    fun cardFace(tint: Color): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = SurfaceOpacity),
            tint.copy(alpha = SurfaceOpacity),
            tint.copy(alpha = SurfaceOpacity)
        )
    )

    fun heroFace(tint: Color): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = SurfaceOpacity),
            tint.copy(alpha = SurfaceOpacity),
            tint.copy(alpha = SurfaceOpacity)
        )
    )

    fun modalFace(tint: Color = Color(0xFFFFF8F1)): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = ModalOpacity),
            tint.copy(alpha = ModalOpacity),
            tint.copy(alpha = ModalOpacity)
        )
    )

    fun modalRim(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.98f),
            0.22f to Color.White.copy(alpha = 0.82f),
            0.56f to Color.White.copy(alpha = 0.42f),
            1f to Color.White.copy(alpha = 0.90f)
        )
    )

    fun illustrationFace(tint: Color): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = SurfaceOpacity),
            tint.copy(alpha = SurfaceOpacity),
            tint.copy(alpha = SurfaceOpacity)
        )
    )

    val ControlBlurStyle = HazeBlurStyle {
        blurRadius(12.dp)
        noiseFactor(0.014f)
        colorEffects(
            listOf(HazeColorEffect.tint(Color.White.copy(alpha = ControlOpacity)))
        )
        fallbackColorEffect(
            HazeColorEffect.tint(Color(0xFFFFFEFC).copy(alpha = ControlOpacity))
        )
    }

    val ModalBlurStyle = HazeBlurStyle {
        blurRadius(20.dp)
        noiseFactor(0.015f)
        colorEffects(
            listOf(HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = ModalOpacity)))
        )
        fallbackColorEffect(
            HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = ModalOpacity))
        )
    }

    val SurfaceBlurStyle = HazeBlurStyle {
        blurRadius(8.dp)
        noiseFactor(0.02f)
        colorEffects(
            listOf(HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = SurfaceOpacity)))
        )
        fallbackColorEffect(
            HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = SurfaceOpacity))
        )
    }
}


/**
 * Lightweight optical treatment shared by every Meals glass surface.
 *
 * It builds dimensionality from cached Compose paths and gradients rather
 * than a refraction shader: a soft edge bloom, non-uniform outer catchlight,
 * inset bevel, curved top reflection and a short side glint.
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
    val innerSize = Size(
        width = (size.width - inset * 2f).coerceAtLeast(1f),
        height = (size.height - inset * 2f).coerceAtLeast(1f)
    )
    val innerOutline = shape.createOutline(innerSize, layoutDirection, this)
    val edgeWidth = 1.35.dp.toPx()
    val innerWidth = 0.68.dp.toPx()
    val reflectionWidth = 1.15.dp.toPx()
    val sideLength = sideGlintHeight.toPx().coerceAtMost(size.height * 0.58f)
    val sideTop = ((size.height - sideLength) * 0.5f).coerceAtLeast(inset * 2f)

    val edgeCatchlight = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.98f * edgeDepth),
            0.12f to Color.White.copy(alpha = 0.78f * edgeDepth),
            0.34f to Color.White.copy(alpha = 0.22f * edgeDepth),
            0.60f to Color.White.copy(alpha = 0.09f * edgeDepth),
            0.82f to Color.White.copy(alpha = 0.42f * edgeDepth),
            1f to Color.White.copy(alpha = 0.88f * edgeDepth)
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    val innerBevel = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.82f * edgeDepth),
            0.18f to Color.White.copy(alpha = 0.42f * edgeDepth),
            0.58f to Color.White.copy(alpha = 0.13f * edgeDepth),
            1f to Color.White.copy(alpha = 0.58f * edgeDepth)
        ),
        startY = 0f,
        endY = size.height
    )
    val topSurfaceSheen = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.18f * edgeDepth),
            0.30f to Color.White.copy(alpha = 0.08f * edgeDepth),
            1f to Color.Transparent
        ),
        startY = 0f,
        endY = (size.height * 0.34f).coerceAtLeast(1f)
    )
    val topReflection = Path().apply {
        moveTo(size.width * 0.14f, inset * 1.22f)
        cubicTo(
            size.width * 0.28f, inset * 0.48f,
            size.width * 0.68f, inset * 0.48f,
            size.width * 0.86f, inset * 1.18f
        )
    }
    val fineReflection = Path().apply {
        moveTo(size.width * 0.24f, inset * 2.08f)
        cubicTo(
            size.width * 0.38f, inset * 1.72f,
            size.width * 0.63f, inset * 1.72f,
            size.width * 0.76f, inset * 2.06f
        )
    }
    val sideReflection = Path().apply {
        moveTo(inset * 0.94f, sideTop + sideLength * 0.12f)
        cubicTo(
            inset * 0.48f, sideTop + sideLength * 0.34f,
            inset * 0.48f, sideTop + sideLength * 0.70f,
            inset * 0.94f, sideTop + sideLength * 0.88f
        )
    }
    val lowerCornerReflection = Path().apply {
        moveTo(size.width * 0.72f, size.height - inset * 1.30f)
        cubicTo(
            size.width * 0.82f, size.height - inset * 0.74f,
            size.width * 0.91f, size.height - inset * 2.10f,
            size.width - inset * 1.28f, size.height * 0.78f
        )
    }
    val reflectionBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.16f to Color.White.copy(alpha = 0.56f * edgeDepth),
            0.48f to Color.White.copy(alpha = 0.96f * edgeDepth),
            0.82f to Color.White.copy(alpha = 0.62f * edgeDepth),
            1f to Color.Transparent
        )
    )
    val sideBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.24f to Color.White.copy(alpha = 0.72f * edgeDepth),
            0.52f to Color.White.copy(alpha = 0.92f * edgeDepth),
            0.82f to Color.White.copy(alpha = 0.54f * edgeDepth),
            1f to Color.Transparent
        ),
        startY = sideTop,
        endY = sideTop + sideLength
    )

    onDrawBehind {
        // A restrained upper-face wash gives the surface a curved, light-catching crown.
        drawRect(
            brush = topSurfaceSheen,
            size = Size(size.width, size.height * 0.34f)
        )

        // The soft outer catchlight sits beneath a more precise, directional white rim.
        drawOutline(
            outline = outline,
            color = Color.White.copy(alpha = 0.12f * edgeDepth),
            style = Stroke(width = 2.5.dp.toPx())
        )
        drawOutline(
            outline = outline,
            brush = baseRim,
            style = Stroke(width = 0.72.dp.toPx())
        )
        drawOutline(
            outline = outline,
            brush = edgeCatchlight,
            style = Stroke(
                width = edgeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Displacing the second contour inward creates the visible glass bevel/thickness.
        withTransform({
            translate(left = inset, top = inset)
        }) {
            drawOutline(
                outline = innerOutline,
                brush = innerBevel,
                style = Stroke(width = innerWidth)
            )
        }

        // Curved reflections avoid the appearance of a single generic horizontal stroke.
        drawPath(
            path = topReflection,
            brush = reflectionBrush,
            style = Stroke(width = reflectionWidth, cap = StrokeCap.Round)
        )
        drawPath(
            path = fineReflection,
            brush = reflectionBrush,
            style = Stroke(width = 0.46.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = sideReflection,
            brush = sideBrush,
            style = Stroke(width = 0.8.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = lowerCornerReflection,
            brush = sideBrush,
            style = Stroke(width = 0.72.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
