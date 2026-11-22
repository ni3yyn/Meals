package dz.meals.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val ControlShape = RoundedCornerShape(15.dp)
    val CardShape = RoundedCornerShape(26.dp)
    val HeroShape = RoundedCornerShape(29.dp)
    val NavigationShape = RoundedCornerShape(27.dp)

    val ControlElevation = 3.dp
    val SurfaceElevation = 2.dp
    val PressDurationMillis = 120
    const val PressedScale = 0.955f

    fun controlFace(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.54f),
            Color(0xFFF8FCF9).copy(alpha = 0.31f),
            Color(0xFFE4F0E7).copy(alpha = 0.19f),
            Color.White.copy(alpha = 0.38f)
        )
    )

    fun controlRim(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.99f),
            0.24f to Color.White.copy(alpha = 0.86f),
            0.58f to Color(0xFFDDEBE1).copy(alpha = 0.62f),
            1f to Color.White.copy(alpha = 0.94f)
        )
    )

    fun surfaceRim(lowerEdge: Color = Border): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.99f),
            Color.White.copy(alpha = 0.84f),
            lowerEdge.copy(alpha = 0.66f)
        )
    )

    fun innerRim(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.74f),
            Color.White.copy(alpha = 0.18f),
            Color(0xFFDDEBE1).copy(alpha = 0.30f)
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

    fun cardFace(
        tint: Color,
        lightContent: Boolean,
        alpha: Float
    ): Brush = Brush.verticalGradient(
        colors = if (lightContent) {
            listOf(
                Color.White.copy(alpha = 0.18f),
                tint.copy(alpha = alpha),
                tint.copy(alpha = 0.40f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.24f),
                tint.copy(alpha = alpha),
                tint.copy(alpha = 0.34f)
            )
        }
    )

    fun heroFace(tint: Color, alpha: Float): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.24f),
            tint.copy(alpha = alpha),
            tint.copy(alpha = (alpha + 0.07f).coerceAtMost(0.42f))
        )
    )

    fun illustrationFace(tint: Color): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.62f),
            tint.copy(alpha = 0.58f),
            tint.copy(alpha = 0.32f)
        )
    )

    val ControlBlurStyle = HazeBlurStyle {
        blurRadius(15.dp)
        noiseFactor(0.025f)
        colorEffects(
            listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.18f)))
        )
        fallbackColorEffect(
            HazeColorEffect.tint(Color(0xFFFFFEFC).copy(alpha = 0.54f))
        )
    }

    val SurfaceBlurStyle = HazeBlurStyle {
        blurRadius(25.dp)
        noiseFactor(0.045f)
        colorEffects(
            listOf(HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = 0.46f)))
        )
        fallbackColorEffect(
            HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = 0.92f))
        )
    }
}
