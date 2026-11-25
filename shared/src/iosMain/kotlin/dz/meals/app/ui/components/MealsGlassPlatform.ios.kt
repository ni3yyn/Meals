package dz.meals.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

@Composable
actual fun rememberMealsGlassBackdrop(): MealsGlassBackdrop =
    remember { MealsGlassBackdrop(null) }

@Suppress("UNUSED_PARAMETER")
actual fun Modifier.mealsGlassSource(backdrop: MealsGlassBackdrop): Modifier = this

@Suppress("UNUSED_PARAMETER")
actual fun Modifier.mealsGlassBlur(
    backdrop: MealsGlassBackdrop?,
    hazeState: HazeState?,
    blurStyle: HazeBlurStyle?,
    performanceMode: HazePerformanceMode,
    shape: Shape,
    blurRadius: Dp
): Modifier {
    val source = hazeState ?: return this
    val style = blurStyle ?: return this
    return hazeBlur(
        input = HazeInput.Sources(source),
        style = style,
        performanceMode = performanceMode
    )
}

actual fun Modifier.mealsHazeSource(state: HazeState, zIndex: Float): Modifier =
    hazeSource(state, zIndex = zIndex)


@Suppress("UNUSED_PARAMETER")
@Composable
actual fun MealsConfigureModalWindow() {
    // iOS presents the sheet in the existing window; no window setup is required.
}
