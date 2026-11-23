package dz.meals.app.ui.components

import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
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

@Suppress("DEPRECATION")
private fun configureLegacyDialogNavigationBar(window: android.view.Window) {
    if (Build.VERSION.SDK_INT >= 35) return

    window.navigationBarColor = AndroidColor.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
        window.navigationBarDividerColor = AndroidColor.TRANSPARENT
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val MealsBottomSheetMotionScheme = object : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.80f, stiffness = 430f)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.76f, stiffness = 560f)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.88f, stiffness = 320f)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 1600f)

    // ModalBottomSheet uses this token for hide(). The softer spring removes its abrupt snap.
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.90f, stiffness = 420f)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 800f)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun MealsBottomSheetMotionProvider(content: @Composable () -> Unit) {
    MaterialTheme(
        motionScheme = MealsBottomSheetMotionScheme,
        content = content
    )
}

@Composable
actual fun MealsModalWindowBlur(enableBlurBehind: Boolean) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return

    // Preserve edge-to-edge and navigation glyph styling for every modal sheet.
    DisposableEffect(window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        configureLegacyDialogNavigationBar(window)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
        }
        onDispose { }
    }

    // Settings opts out of blur-behind: the static glass face and rim remain,
    // while Android avoids an additional full-window blur during sheet motion.
    if (enableBlurBehind) {
        val blurRadiusPx = with(LocalDensity.current) { 18.dp.roundToPx() }
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
}
