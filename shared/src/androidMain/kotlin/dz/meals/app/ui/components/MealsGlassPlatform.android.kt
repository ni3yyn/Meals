package dz.meals.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import com.styropyr0.prismal.drawPlainPrismalGlass
import com.styropyr0.prismal.effects.prismalBlur
import com.styropyr0.prismal.sources.PrismalGlassLayer
import com.styropyr0.prismal.sources.prismalGlassLayer
import com.styropyr0.prismal.sources.rememberPrismalGlassLayer

@Composable
actual fun rememberMealsGlassBackdrop(): MealsGlassBackdrop {
    val layer = rememberPrismalGlassLayer()
    return remember(layer) { MealsGlassBackdrop(layer) }
}

actual fun Modifier.mealsGlassSource(backdrop: MealsGlassBackdrop): Modifier {
    val layer = backdrop.platformHandle as? PrismalGlassLayer ?: return this
    return prismalGlassLayer(layer)
}

@Suppress("UNUSED_PARAMETER")
actual fun Modifier.mealsGlassBlur(
    backdrop: MealsGlassBackdrop?,
    hazeState: HazeState?,
    blurStyle: HazeBlurStyle?,
    shape: Shape,
    blurRadius: Dp
): Modifier {
    val layer = backdrop?.platformHandle as? PrismalGlassLayer ?: return this
    if (blurRadius <= 0.dp) return this

    // Only the backdrop blur is enabled. There is deliberately no prismalLens()
    // call, AGSL refraction shader, chromatic aberration, specular shader,
    // depth shadow, or dark inset layer in Meals' renderer.
    return drawPlainPrismalGlass(
        backdrop = layer,
        shape = { shape },
        effects = {
            prismalBlur(with(this) { blurRadius.toPx() })
        }
    )
}

@Suppress("UNUSED_PARAMETER")
actual fun Modifier.mealsHazeSource(state: HazeState, zIndex: Float): Modifier = this
