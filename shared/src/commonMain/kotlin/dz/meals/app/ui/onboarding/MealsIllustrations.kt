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
private fun DrawScope.steam(x0: Float, y0: Float, h: Float, phase: Float, k: Int) {
    val w = sin(phase * TAU + k * 1.7f) * 3.5f
    val a = 0.62f + 0.3f * sin(phase * TAU + k)
    val p = Path().apply {
        moveTo(x0, y0)
        cubicTo(x0 - 9f + w, y0 - h * 0.33f, x0 + 9f - w, y0 - h * 0.66f, x0, y0 - h)
    }
    drawPath(p, Color(0xFFE2C8B0).copy(alpha = 0.35f), style = Stroke(7.5f, cap = StrokeCap.Round))
    drawPath(p, Color.White.copy(alpha = a.coerceIn(0f, 1f)), style = Stroke(4.5f, cap = StrokeCap.Round))
}

private fun DrawScope.checkMark(cx: Float, cy: Float, s: Float, color: Color = Color.White, width: Float = 5f) {
    val p = Path().apply {
        moveTo(cx - 10f * s, cy + 1f * s)
        lineTo(cx - 3f * s, cy + 8f * s)
        lineTo(cx + 11f * s, cy - 8f * s)
    }
    drawPath(p, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ───────────────────────────────── scenes ────────────────────────────────────

private fun DrawScope.drawBowl(phase: Float, soup: Boolean) {
    groundShadow(100f, 182f, 60f)
    // Opening of the bowl.
    drawOval(ClayLight, Offset(26f, 88f), Size(148f, 26f))
    drawOval(Color(0xFFB9683F), Offset(33f, 92f), Size(134f, 18f))

    if (soup) {
        drawOval(v(Color(0xFFF08A47), Color(0xFFD9532E), 92f, 110f), Offset(35f, 93f), Size(130f, 16f))
        for (i in 0 until 9) {
            drawCircle(Forest.copy(alpha = 0.85f), 1.9f, Offset(52f + i * 12f + (i % 2) * 3f, 100f + (i % 3) * 2f))
        }
        drawOval(Color.White.copy(alpha = 0.28f), Offset(58f, 95f), Size(34f, 4f))
    } else {
        // Couscous mound with grains and fresh toppings.
        val mound = Path().apply {
            moveTo(42f, 102f)
            cubicTo(48f, 58f, 152f, 58f, 158f, 102f)
            close()
        }
        drawPath(mound, v(Butter, ButterDark, 60f, 102f))
        for (i in 0 until 38) {
            val a = i * 2.39996f
            val rr = sqrt(i / 38f) * 40f
            drawCircle(Color(0xFFD9993F).copy(alpha = 0.55f), 1.9f, Offset(100f + cos(a) * rr, 88f + sin(a) * rr * 0.42f))
        }
        drawCircle(Gold, 4.5f, Offset(72f, 86f))
        drawCircle(Gold, 4.5f, Offset(130f, 88f))
        leaf(84f, 70f, 24f, 10f, -35f)
        leaf(94f, 64f, 20f, 9f, 18f, Leaf2)
        tomato(118f, 72f, 12f)
    }

    // Bowl body with a green band.
    val body = Path().apply {
        moveTo(27f, 101f)
        cubicTo(30f, 150f, 68f, 178f, 100f, 178f)
        cubicTo(132f, 178f, 170f, 150f, 173f, 101f)
        close()
    }
    drawPath(body, v(ClayLight, Clay, 101f, 178f))
    clipPath(body) {
        val band = Path().apply {
            moveTo(20f, 120f)
            cubicTo(60f, 138f, 140f, 138f, 180f, 120f)
            lineTo(180f, 132f)
            cubicTo(140f, 150f, 60f, 150f, 20f, 132f)
            close()
        }
        drawPath(band, Forest.copy(alpha = 0.88f))
        for (i in 0 until 7) {
            drawCircle(Color.White.copy(alpha = 0.5f), 2.2f, Offset(46f + i * 18f, 136f + sin(i * 0.9f) * 1f + (if (i in 2..4) 3f else 0f)))
        }
    }
    drawArc(
        Color(0xFFFBD9C0), 0f, 180f, false,
        Offset(27f, 89f), Size(146f, 24f),
        style = Stroke(7f, cap = StrokeCap.Round)
    )
    gloss(56f, 130f, 5f, 17f, 0.5f, 18f)

    val steamBase = if (soup) 84f else 54f
    steam(82f, steamBase, 28f, phase, 0)
    steam(100f, steamBase - 4f, 32f, phase, 1)
    steam(118f, steamBase, 28f, phase, 2)
    if (!soup) {
        sparkle(46f, 44f, 8f, phase, 0)
        sparkle(158f, 50f, 6f, phase, 1)
        sparkle(168f, 96f, 4f, phase, 2)
    }
}

private fun DrawScope.drawCoin(cx: Float, cy: Float, rx: Float, ry: Float) {
    drawOval(v(Color(0xFFFFE08A), Gold, cy - ry, cy + ry), Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f))
    drawOval(GoldDark.copy(alpha = 0.7f), Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f), style = Stroke(2f))
    drawOval(GoldDark.copy(alpha = 0.45f), Offset(cx - rx * 0.66f, cy - ry * 0.66f), Size(rx * 1.32f, ry * 1.32f), style = Stroke(1.6f))
    drawPath(sparklePath(cx, cy, ry * 0.5f), GoldDark.copy(alpha = 0.6f))
}

private fun DrawScope.drawCoinStack(cx: Float, baseY: Float, n: Int) {
    for (i in 0 until n) {
        val cy = baseY - i * 10f
        drawOval(GoldDark, Offset(cx - 26f, cy + 1f), Size(52f, 16f))
        drawRect(GoldDark, Offset(cx - 26f, cy + 8f - 0.5f), Size(52f, 1f))
        drawRect(Color(0xFFE9B54A), Offset(cx - 26f, cy), Size(52f, 9f))
        drawOval(Color(0xFFE9B54A), Offset(cx - 26f, cy + 1f), Size(52f, 16f))
        drawOval(v(Color(0xFFFFE08A), Gold, cy - 8f, cy + 8f), Offset(cx - 26f, cy - 8f), Size(52f, 16f))
        drawOval(GoldDark.copy(alpha = 0.5f), Offset(cx - 19f, cy - 5.5f), Size(38f, 11f), style = Stroke(1.6f))
    }
}

private fun DrawScope.drawBudget(phase: Float) {
    groundShadow(100f, 178f, 64f)
    // Wallet.
    drawRoundRect(v(Forest, ForestDark, 64f, 152f), Offset(34f, 64f), Size(116f, 88f), CornerRadius(22f))
    drawRoundRect(v(Color(0xFF63B085), Forest, 64f, 104f), Offset(34f, 64f), Size(116f, 42f), CornerRadius(22f))
    drawLine(
        Color.White.copy(alpha = 0.4f), Offset(48f, 112f), Offset(136f, 112f), 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
    )
    drawCircle(v(Color(0xFFF7C9A6), Tangerine, 100f, 124f), 12f, Offset(134f, 114f))
    drawCircle(Color.White.copy(alpha = 0.85f), 4f, Offset(134f, 114f))
    gloss(60f, 80f, 14f, 4f, 0.4f, -8f)
    // Coins.
    drawCoinStack(154f, 164f, 3)
    drawCoin(66f, 48f + sin(phase * TAU) * 4f, 22f, 22f)
    rotate(-18f, Offset(110f, 40f)) { drawCoin(110f, 40f - sin(phase * TAU + 1.2f) * 3f, 14f, 14f) }
    sparkle(30f, 40f, 8f, phase, 0)
    sparkle(172f, 70f, 7f, phase, 1)
    sparkle(92f, 24f, 5f, phase, 2)
}

private val PersonColors = listOf(
    Color(0xFFE9A07A), Forest, Lavender, Tangerine, Color(0xFF8DB8D8), Color(0xFFE7B8C5)
)

private fun DrawScope.person(cx: Float, feet: Float, k: Float, color: Color, kind: Int, pop: Float) {
    if (pop <= 0.01f) return
    scale(pop, Offset(cx, feet)) {
        val bw = 15f * k
        val bh = 40f * k
        groundShadow(cx, feet, 15f * k)
        drawRoundRect(
            v(lerp(color, Color.White, 0.3f), color, feet - bh, feet),
            Offset(cx - bw, feet - bh), Size(bw * 2f, bh), CornerRadius(bw)
        )
        val hr = 12f * k
        val hy = feet - bh - 7f * k
        when (kind) {
            0 -> { // headscarf
                val scarf = lerp(color, Ink, 0.25f)
                drawRoundRect(scarf, Offset(cx - bw * 0.9f, hy + hr * 0.5f), Size(bw * 1.8f, bh * 0.3f), CornerRadius(bw * 0.9f))
                drawCircle(scarf, hr * 1.18f, Offset(cx, hy))
                drawOval(Skin, Offset(cx - hr * 0.7f, hy - hr * 0.62f), Size(hr * 1.4f, hr * 1.36f))
            }
            1 -> { // short hair
                drawCircle(Skin, hr, Offset(cx, hy))
                drawArc(Hair, 180f, 180f, true, Offset(cx - hr * 1.04f, hy - hr * 1.04f), Size(hr * 2.08f, hr * 2.08f))
            }
            else -> { // buns
                drawCircle(Hair, hr * 0.36f, Offset(cx - hr * 0.92f, hy - hr * 0.6f))
                drawCircle(Hair, hr * 0.36f, Offset(cx + hr * 0.92f, hy - hr * 0.6f))
                drawCircle(Skin, hr, Offset(cx, hy))
                drawArc(Hair, 180f, 180f, true, Offset(cx - hr * 1.04f, hy - hr * 1.04f), Size(hr * 2.08f, hr * 2.08f))
            }
        }
        drawCircle(Hair, 1.3f * k, Offset(cx - 4f * k, hy + 1f * k))
        drawCircle(Hair, 1.3f * k, Offset(cx + 4f * k, hy + 1f * k))
        drawCircle(Color(0xFFF29C8E).copy(alpha = 0.55f), 2.4f * k, Offset(cx - 7f * k, hy + 4.5f * k))
        drawCircle(Color(0xFFF29C8E).copy(alpha = 0.55f), 2.4f * k, Offset(cx + 7f * k, hy + 4.5f * k))
        drawArc(
            Hair, 20f, 140f, false, Offset(cx - 3f * k, hy + 2f * k), Size(6f * k, 4f * k),
            style = Stroke(1.2f * k, cap = StrokeCap.Round)
        )
    }
}

private fun heartPath(cx: Float, cy: Float, s: Float) = Path().apply {
    moveTo(cx, cy + s * 0.9f)
    cubicTo(cx - s * 1.6f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.3f, cx, cy - s * 0.45f)
    cubicTo(cx + s * 0.7f, cy - s * 1.3f, cx + s * 1.6f, cy - s * 0.1f, cx, cy + s * 0.9f)
    close()
}
