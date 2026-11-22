package dz.meals.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.shadow.Shadow
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle

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
    /** A softly frosted tint that still lets the blurred backdrop show through. */
    const val ModalOpacity = 0.32f

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
            color = Color(0xFF736C61).copy(
                alpha = (0.025f + depth * 0.004f).coerceIn(0.025f, 0.055f)
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
            0.18f to Color.White.copy(alpha = 0.98f),
            0.54f to Color.White.copy(alpha = 0.76f),
            0.82f to Color.White.copy(alpha = 0.88f),
            1f to Color.White.copy(alpha = 0.96f)
        )
    )

    fun surfaceRim(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 1f),
            0.16f to Color.White.copy(alpha = 0.96f),
            0.52f to Color.White.copy(alpha = 0.78f),
            0.84f to Color.White.copy(alpha = 0.90f),
            1f to Color.White.copy(alpha = 0.98f)
        )
    )

    fun errorRim(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 1f),
            Color.White.copy(alpha = 0.94f),
            Color.White.copy(alpha = 0.82f)
        )
    )

    fun innerRim(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.90f),
            Color.White.copy(alpha = 0.30f),
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

    /** Soft white-point reflection across the lens; it is not a tinted surface fill. */
    fun lensReflection(): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.15f),
            0.08f to Color.White.copy(alpha = 0.075f),
            0.22f to Color.White.copy(alpha = 0.018f),
            0.58f to Color.Transparent,
            0.86f to Color.Transparent,
            0.96f to Color.White.copy(alpha = 0.035f),
            1f to Color.White.copy(alpha = 0.09f)
        )
    )

    fun contentRim(): Brush = surfaceRim()

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

    fun modalRim(): Brush = surfaceRim()

    fun illustrationFace(tint: Color): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = SurfaceOpacity),
            tint.copy(alpha = SurfaceOpacity),
            tint.copy(alpha = SurfaceOpacity)
        )
    )

    /**
     * Shared lens optics for every Meals glass surface.
     * Keep the captured scene visible through shallow edge refraction, with a
     * gentle folded lens and restrained secondary edge detail. Haze owns the
     * platform renderer and fallback; the authored rim remains pure white.
     */
    fun lensStyle(shape: RoundedCornerShape): GlassStyle = GlassStyle.regular.then {
        shape(shape)
        tint(Color.White.copy(alpha = 0.075f))
        edgeShadow(Color.Transparent)
        specularIntensity(0.88f)
        ambientResponse(0.28f)
        edgeSoftness(0.7.dp)
        lightPosition(Alignment.TopStart)
        chromaticAberrationStrength(0f)
        optics(
            GlassOptics.regular.copy(
                refractionStrength = 0.80f,
                refractionDisplacement = 32.dp,
                refractionFoldStrength = 0.12f,
                refractionDetailIntensity = 0.10f
            )
        )
        pressed {
            lightingIntensity(1f)
            refractionMultiplier(1.07f)
            whitePointDelta(0.04f)
        }
    }

}
