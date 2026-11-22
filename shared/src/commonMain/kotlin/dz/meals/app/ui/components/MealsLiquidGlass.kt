package dz.meals.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.shadow.Shadow
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
    const val ControlOpacity = 0.46f
    /** Content cards use a quiet porcelain material rather than functional Liquid Glass. */
    const val ContentOpacity = 0.94f
    /** Modal material is more substantial on the bright ivory backdrop. */
    const val ModalOpacity = 0.72f

    val ControlShape = RoundedCornerShape(15.dp)
    val CardShape = RoundedCornerShape(26.dp)
    val HeroShape = RoundedCornerShape(29.dp)
    val ModalShape = RoundedCornerShape(30.dp)
    val NavigationShape = RoundedCornerShape(27.dp)

    val ControlElevation = 3.dp
    val SurfaceElevation = 4.dp
    val ModalElevation = 6.dp

    /**
     * A warm, diffuse shadow that complements the bright double rim.
     * Radius and opacity rise gently with surface size/elevation so small
     * controls stay quiet while sheets and hero surfaces feel more lifted.
     */
    fun softShadow(elevation: Dp): Shadow {
        val depth = elevation.value.coerceAtLeast(0f)
        return Shadow(
            radius = (10f + depth * 1.5f).dp,
            spread = 0.dp,
            color = Color(0xFF686257).copy(
                alpha = (0.045f + depth * 0.006f).coerceIn(0.045f, 0.085f)
            ),
            offset = DpOffset(
                x = 0.dp,
                y = (2f + depth * 0.5f).dp
            )
        )
    }
    fun controlFace(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = ControlOpacity),
            Color(0xFFF3F7F0).copy(alpha = ControlOpacity),
            Color(0xFFDDEBDD).copy(alpha = ControlOpacity),
            Color(0xFFF5F7F1).copy(alpha = ControlOpacity)
        )
    )

    /** A confident branded fill for the single most important action in a group. */
    fun primaryActionFace(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF438C62),
            Color(0xFF2B6F4B),
            Color(0xFF245D3F)
        )
    )

    fun controlRim(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 1f),
            0.22f to Color.White.copy(alpha = 0.94f),
            0.56f to Color(0xFFC9D9CB).copy(alpha = 0.90f),
            1f to Color(0xFF718B77)
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

    fun contentRim(rimColor: Color = Color(0xFFE1D9CD)): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.94f),
            Color(0xFFECE6DC),
            rimColor
        )
    )

    fun cardFace(tint: Color): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.98f),
            tint.copy(alpha = ContentOpacity),
            tint.copy(alpha = 0.92f)
        )
    )

    fun heroFace(tint: Color): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.98f),
            tint.copy(alpha = ContentOpacity),
            tint.copy(alpha = 0.92f)
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
            0f to Color.White.copy(alpha = 1f),
            0.22f to Color.White.copy(alpha = 0.94f),
            0.56f to Color(0xFFE6F1E9).copy(alpha = 0.78f),
            1f to Color.White.copy(alpha = 0.98f)
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
        blurRadius(5.dp)
        noiseFactor(0.002f)
        colorEffects(
            listOf(HazeColorEffect.tint(Color.White.copy(alpha = ControlOpacity)))
        )
        fallbackColorEffect(
            HazeColorEffect.tint(Color(0xFFFFFEFC).copy(alpha = ControlOpacity))
        )
    }

    val ModalBlurStyle = HazeBlurStyle {
        blurRadius(8.dp)
        noiseFactor(0.002f)
        colorEffects(
            listOf(HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = ModalOpacity)))
        )
        fallbackColorEffect(
            HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = ModalOpacity))
        )
    }

    val SurfaceBlurStyle = HazeBlurStyle {
        blurRadius(3.dp)
        noiseFactor(0.002f)
        colorEffects(
            listOf(HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = SurfaceOpacity)))
        )
        fallbackColorEffect(
            HazeColorEffect.tint(Color(0xFFFFFCF7).copy(alpha = SurfaceOpacity))
        )
    }
}
