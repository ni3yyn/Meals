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
    /** One shared transparency level for every glass face and blur tint. */
    const val SurfaceOpacity = 0.20f

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
            Color.White.copy(alpha = SurfaceOpacity),
            Color(0xFFF8FCF9).copy(alpha = SurfaceOpacity),
            Color(0xFFE4F0E7).copy(alpha = SurfaceOpacity),
            Color.White.copy(alpha = SurfaceOpacity)
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

    fun errorRim(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.98f),
            Color(0xFFE7B1AA).copy(alpha = 0.86f),
            Color(0xFFB64A45).copy(alpha = 0.88f)
        )
    )

    fun innerRim(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.74f),
            Color.White.copy(alpha = SurfaceOpacity),
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

    fun illustrationFace(tint: Color): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = SurfaceOpacity),
            tint.copy(alpha = SurfaceOpacity),
            tint.copy(alpha = SurfaceOpacity)
        )
    )

    val ControlBlurStyle = HazeBlurStyle {
        blurRadius(15.dp)
        noiseFactor(0.025f)
        colorEffects(
            listOf(HazeColorEffect.tint(Color.White.copy(alpha = SurfaceOpacity)))
        )
        fallbackColorEffect(
            HazeColorEffect.tint(Color(0xFFFFFEFC).copy(alpha = SurfaceOpacity))
        )
    }

    val SurfaceBlurStyle = HazeBlurStyle {
        blurRadius(25.dp)
        noiseFactor(0.045f)
        colorEffects(
            listOf(HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = SurfaceOpacity)))
        )
        fallbackColorEffect(
            HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = SurfaceOpacity))
        )
    }
}
