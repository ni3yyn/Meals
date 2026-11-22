package dz.meals.app.ui.components

import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
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
        performanceMode = HazePerformanceMode.Default
    )
}

actual fun Modifier.mealsHazeSource(state: HazeState, zIndex: Float): Modifier =
    hazeSource(state, zIndex = zIndex)

@Composable
actual fun MealsModalWindowBlur() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    val blurRadiusPx = with(LocalDensity.current) { 18.dp.roundToPx() }

    // ModalBottomSheet owns a separate Dialog window; configuring the Activity
    // window alone does not affect it. Apply edge-to-edge navigation once, before
    // the first dialog frame, so Android never inserts its default solid/scrim bar.
    // This is a static window configuration: no navigation-bar alpha animation,
    // scrim interpolation, or per-frame system-bar updates are used.
    DisposableEffect(window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = AndroidColor.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.navigationBarDividerColor = AndroidColor.TRANSPARENT
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            // Match the app's warm, light surfaces with dark navigation glyphs.
            isAppearanceLightNavigationBars = true
        }

        onDispose { }
    }

    // Keep the optional blur-behind effect independent from system-bar state.
    // A density change may update blur radius, but never touches nav-bar opacity.
    DisposableEffect(window, blurRadiusPx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hadBlurBehindFlag =
                (window.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
            val previousBlurRadius = window.attributes.blurBehindRadius

            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = blurRadiusPx
            }

            onDispose {
                window.attributes = window.attributes.apply {
                    blurBehindRadius = previousBlurRadius
                }
                if (!hadBlurBehindFlag) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        } else {
            onDispose { }
        }
    }
}
