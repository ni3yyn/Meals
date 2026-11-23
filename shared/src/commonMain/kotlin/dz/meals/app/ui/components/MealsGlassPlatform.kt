package dz.meals.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle

/**
 * Opaque handle to the platform's reusable backdrop capture.
 *
 * Android stores a PrismalGlassLayer here. iOS keeps the handle empty and uses
 * the existing Haze implementation through the same API.
 */
class MealsGlassBackdrop internal constructor(internal val platformHandle: Any?)

/** Creates one reusable backdrop capture for a material layer, not one per control. */
@Composable
expect fun rememberMealsGlassBackdrop(): MealsGlassBackdrop

/** Marks the composable subtree that glass surfaces in the same layer may sample. */
expect fun Modifier.mealsGlassSource(backdrop: MealsGlassBackdrop): Modifier

/**
 * Applies the platform's backdrop blur. Android uses PrismalAGSL; iOS retains
 * Haze. Refraction is intentionally not part of this API.
 */
expect fun Modifier.mealsGlassBlur(
    backdrop: MealsGlassBackdrop?,
    hazeState: HazeState?,
    blurStyle: HazeBlurStyle?,
    shape: Shape,
    blurRadius: Dp
): Modifier

/** Haze source registration is retained only on platforms using the Haze fallback. */
expect fun Modifier.mealsHazeSource(
    state: HazeState,
    zIndex: Float = 0f
): Modifier

/**
 * Adds a subtle native blur-behind effect to the platform dialog hosting a modal sheet.
 * Platforms without a separate modal window can keep this as a no-op.
 */
@Composable
expect fun MealsModalWindowBlur()

/** Applies the sheet-specific motion curve without changing the app-wide theme. */
@Composable
expect fun MealsBottomSheetMotionProvider(content: @Composable () -> Unit)
