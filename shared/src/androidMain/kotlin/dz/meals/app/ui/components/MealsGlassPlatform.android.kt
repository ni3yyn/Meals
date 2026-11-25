package dz.meals.app.ui.components

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
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

actual fun Modifier.mealsGlassBlur(
    backdrop: MealsGlassBackdrop?,
    hazeState: HazeState?,
    blurStyle: HazeBlurStyle?,
    performanceMode: HazePerformanceMode,
    shape: Shape,
    blurRadius: Dp
): Modifier {
    val layer = backdrop?.platformHandle as? PrismalGlassLayer
    if (layer != null) {
        if (blurRadius <= 0.dp) return this

        // Regular in-window glass keeps Prismal's existing lightweight blur path.
        // Modal sheets pass no Prismal layer and use Haze sources across the dialog boundary.
        return drawPlainPrismalGlass(
            backdrop = layer,
            shape = { shape },
            effects = {
                prismalBlur(with(this) { blurRadius.toPx() })
            }
        )
    }

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

@Suppress("DEPRECATION")
private fun configureLegacyDialogNavigationBar(window: android.view.Window) {
    if (Build.VERSION.SDK_INT >= 35) return

    window.navigationBarColor = AndroidColor.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
        window.navigationBarDividerColor = AndroidColor.TRANSPARENT
    }
}

@Composable
actual fun MealsConfigureModalWindow() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return

    // Haze owns the sheet's source-backed blur. Avoid FLAG_BLUR_BEHIND here:
    // it blurs the entire screen in a second, independent cross-window pass.
    DisposableEffect(window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        configureLegacyDialogNavigationBar(window)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
        }
        onDispose { }
    }
}
