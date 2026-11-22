package dz.meals.app.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.ForestDark
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.Leaf
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.Tangerine
import dz.meals.app.ui.theme.TextAccent
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Vector illustrations for onboarding, drawn entirely with Compose primitives
 * (no bitmaps), so they stay crisp at any size and render identically on
 * Android and iOS. Every scene is authored on a 200 x 200 canvas and scaled.
 */
enum class MealsArt {
    Bowl, Budget, Family, Breakfast, Lunch, Dinner, Allergy, Clock, Basket, Market, Week
}

@Composable
fun MealsIllustration(
    art: MealsArt,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    /** Number of people drawn by [MealsArt.Family]; ignored by other scenes. */
    count: Int = 3
) {
    val transition = rememberInfiniteTransition(label = "mealsIllustration")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "mealsIllustrationPhase"
    )
    val shown by animateFloatAsState(
        targetValue = count.toFloat(),
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
        label = "mealsFamilyCount"
    )

    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 200f
        scale(s, Offset.Zero) {
            when (art) {
                MealsArt.Bowl -> drawBowl(phase, soup = false)
                MealsArt.Budget -> drawBudget(phase)
                MealsArt.Family -> drawFamily(shown, phase)
                MealsArt.Breakfast -> drawBreakfast(phase)
                MealsArt.Lunch -> drawLunch(phase)
                MealsArt.Dinner -> drawDinner(phase)
                MealsArt.Allergy -> drawAllergy(phase)
                MealsArt.Clock -> drawClock(phase)
                MealsArt.Basket -> drawBasket(phase)
                MealsArt.Market -> drawMarket(phase)
                MealsArt.Week -> drawWeek(phase)
            }
        }
    }
}

// ───────────────────────────── palette & helpers ─────────────────────────────

private const val TAU = (2.0 * PI).toFloat()

private val Tomato = Color(0xFFE5614B)
private val TomatoDark = Color(0xFFC94533)
private val Carrot = Color(0xFFF28B3A)
private val Butter = Color(0xFFFBE3A1)
private val ButterDark = Color(0xFFEFC46A)
private val Skin = Color(0xFFF2C6A0)
private val Hair = Color(0xFF4A3426)
private val Gold = Color(0xFFF6CF63)
private val GoldDark = Color(0xFFDFA03A)
private val Clay = Color(0xFFE08A5C)
private val ClayLight = Color(0xFFF6C3A2)
private val Wood = Color(0xFFC98E5E)
private val WoodDark = Color(0xFFA06A3F)
private val Silver = Color(0xFFC9CED6)
private val Milk = Color(0xFFEAF4FB)
private val Sky = Color(0xFF9CC7E4)
private val Lavender = Color(0xFFB8A9DA)
private val Leaf2 = Color(0xFF5FA676)

private fun v(top: Color, bottom: Color, y0: Float, y1: Float) =
    Brush.verticalGradient(listOf(top, bottom), startY = y0, endY = y1)

private fun DrawScope.groundShadow(cx: Float, cy: Float, rx: Float, ry: Float = rx * 0.11f) {
    drawOval(Color(0xFF8F8278).copy(alpha = 0.09f), Offset(cx - rx * 1.16f, cy - ry * 1.5f), Size(rx * 2.32f, ry * 3f))
    drawOval(Color(0xFF8F8278).copy(alpha = 0.16f), Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f))
}

private fun DrawScope.gloss(cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float = 0.45f, deg: Float = -20f) {
    rotate(deg, Offset(cx, cy)) {
        drawOval(Color.White.copy(alpha = alpha), Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f))
    }
}

private fun sparklePath(cx: Float, cy: Float, r: Float) = Path().apply {
    moveTo(cx, cy - r)
    quadraticTo(cx, cy, cx + r, cy)
    quadraticTo(cx, cy, cx, cy + r)
    quadraticTo(cx, cy, cx - r, cy)
    quadraticTo(cx, cy, cx, cy - r)
    close()
}

private fun DrawScope.sparkle(cx: Float, cy: Float, r: Float, phase: Float, k: Int, color: Color = Color.White) {
    val a = 0.55f + 0.45f * sin(phase * TAU + k * 2.1f)
    val s = 0.8f + 0.2f * sin(phase * TAU + k * 2.1f)
    drawPath(sparklePath(cx, cy, r * s), color.copy(alpha = a.coerceIn(0f, 1f)))
}

private fun DrawScope.leaf(cx: Float, cy: Float, w: Float, h: Float, deg: Float, color: Color = Forest) {
    rotate(deg, Offset(cx, cy)) {
        drawOval(
            v(lerp(color, Color.White, 0.25f), color, cy - h / 2f, cy + h / 2f),
            Offset(cx - w / 2f, cy - h / 2f),
            Size(w, h)
        )
        drawLine(Color.White.copy(alpha = 0.35f), Offset(cx - w / 2f + 2f, cy), Offset(cx + w / 2f - 2f, cy), 1.2f, StrokeCap.Round)
    }
}

private fun DrawScope.tomato(cx: Float, cy: Float, r: Float) {
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFF28067), Tomato, TomatoDark),
            center = Offset(cx - r * 0.3f, cy - r * 0.35f),
            radius = r * 1.5f
        ),
        r, Offset(cx, cy)
    )
    leaf(cx - r * 0.35f, cy - r * 0.86f, r * 0.9f, r * 0.36f, -20f)
    leaf(cx + r * 0.35f, cy - r * 0.86f, r * 0.9f, r * 0.36f, 20f)
    gloss(cx - r * 0.4f, cy - r * 0.35f, r * 0.22f, r * 0.14f, 0.55f, -35f)
}

private fun DrawScope.orange(cx: Float, cy: Float, r: Float) {
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFB060), Carrot, Color(0xFFD9701F)), Offset(cx - r * 0.3f, cy - r * 0.35f), r * 1.5f),
        r, Offset(cx, cy)
    )
    drawCircle(Color(0xFF5FA676), r * 0.12f, Offset(cx, cy - r * 0.92f))
    gloss(cx - r * 0.4f, cy - r * 0.35f, r * 0.22f, r * 0.14f, 0.5f, -35f)
}
