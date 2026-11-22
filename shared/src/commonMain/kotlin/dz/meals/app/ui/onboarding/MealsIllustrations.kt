package dz.meals.app.ui.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
    val motionClock = LocalMealsOnboardingMotionClock.current
    val shown by animateFloatAsState(
        targetValue = count.toFloat(),
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
        label = "mealsFamilyCount"
    )

    Canvas(modifier.size(size)) {
        val phase = motionClock.value
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

private fun DrawScope.drawFamily(shown: Float, phase: Float) {
    // Rug.
    drawOval(Color(0xFFF6D9BF), Offset(12f, 140f), Size(176f, 34f))
    drawOval(Color(0xFFF1C9A6).copy(alpha = 0.75f), Offset(26f, 147f), Size(148f, 20f))

    val n = shown.coerceIn(1f, 6f)
    val spacing = min(34f, 150f / n)
    val drawn = ceil(shown).toInt().coerceIn(1, 6)
    for (i in 0 until drawn) {
        val x = 100f + (i - (n - 1f) / 2f) * spacing
        val kid = i >= 2
        person(x, 158f, if (kid) 0.82f else 1f, PersonColors[i % PersonColors.size], i % 3, (shown - i).coerceIn(0f, 1f))
    }
    val hy = 36f + sin(phase * TAU) * 3f
    drawPath(heartPath(100f, hy, 11f), Brush.verticalGradient(listOf(Color(0xFFF59A9A), Color(0xFFE56A6A)), hy - 12f, hy + 12f))
    gloss(94f, hy - 5f, 3.5f, 2.2f, 0.7f, -30f)
    sparkle(52f, 52f, 7f, phase, 0)
    sparkle(150f, 44f, 6f, phase, 1)
}

private fun DrawScope.drawBreakfast(phase: Float) {
    val c = Offset(100f, 106f)
    drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.40f), Color.Transparent), c, 92f), 92f, c)
    rotate(phase * 36f, c) {
        for (k in 0 until 10) {
            rotate(k * 36f, c) {
                drawLine(Gold, Offset(100f, 52f), Offset(100f, 64f), 6f, StrokeCap.Round)
            }
        }
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A0), Color(0xFFF8A94B)), Offset(92f, 96f), 50f), 34f, c)
    gloss(88f, 92f, 9f, 5f, 0.5f, -35f)
    // Hill.
    val hill = Path().apply {
        moveTo(8f, 188f)
        cubicTo(8f, 150f, 52f, 128f, 100f, 128f)
        cubicTo(148f, 128f, 192f, 150f, 192f, 188f)
        close()
    }
    drawPath(hill, v(Leaf, Color(0xFF8FC08C), 128f, 188f))
    leaf(34f, 150f, 22f, 9f, -30f, Leaf2)
    leaf(166f, 152f, 22f, 9f, 30f, Leaf2)
    // Glass of tea on the hill.
    groundShadow(100f, 172f, 28f)
    drawRoundRect(v(Color.White, Color(0xFFF1E4D6), 140f, 170f), Offset(78f, 140f), Size(44f, 31f), CornerRadius(11f))
    drawArc(Color(0xFFF1E4D6), -90f, 180f, false, Offset(112f, 146f), Size(22f, 17f), style = Stroke(4.5f))
    drawOval(Color(0xFFB4693A), Offset(80f, 141f), Size(40f, 8f))
    steam(92f, 138f, 24f, phase, 0)
    steam(108f, 138f, 28f, phase, 1)
    sparkle(40f, 60f, 7f, phase, 0)
    sparkle(164f, 70f, 6f, phase, 1)
}

private fun DrawScope.drawLunch(phase: Float) {
    groundShadow(100f, 178f, 66f)
    val c = Offset(100f, 106f)
    drawCircle(v(Color.White, Color(0xFFEFE1D2), 38f, 174f), 68f, c)
    drawCircle(Color(0xFFE6D5C4).copy(alpha = 0.75f), 68f, c, style = Stroke(1.6f))
    drawCircle(v(Color(0xFFFFFBF6), Color(0xFFF7EBDD), 56f, 156f), 50f, c)
    drawCircle(Color(0xFFE9DACA).copy(alpha = 0.7f), 50f, c, style = Stroke(1.4f))
    // Stew.
    rotate(25f, Offset(90f, 100f)) {
        drawOval(v(Color(0xFFC77A46), Color(0xFF8E4A28), 84f, 116f), Offset(64f, 86f), Size(54f, 30f))
        gloss(80f, 94f, 10f, 3.5f, 0.45f, 0f)
    }
    for ((i, p) in listOf(Offset(124f, 84f), Offset(132f, 102f), Offset(118f, 112f)).withIndex()) {
        drawCircle(v(Color(0xFFFFA654), Carrot, p.y - 9f, p.y + 9f), 9f, p)
        drawCircle(Color(0xFFFFC07A), 3.4f, p)
        if (i == 0) gloss(p.x - 3f, p.y - 4f, 2.5f, 1.6f, 0.6f, -30f)
    }
    for (i in 0 until 7) {
        val a = i * 0.9f
        drawCircle(Color(0xFF7DBA6B), 4.2f, Offset(80f + cos(a) * 11f, 122f + sin(a) * 6f))
    }
    leaf(106f, 76f, 20f, 8f, -25f, Leaf2)
    leaf(98f, 72f, 18f, 7f, 20f)
    // Cutlery.
    drawLine(Silver, Offset(20f, 104f), Offset(20f, 162f), 7f, StrokeCap.Round)
    drawRoundRect(Silver, Offset(11f, 84f), Size(18f, 24f), CornerRadius(8f))
    for (dx in listOf(13f, 20f, 27f)) drawLine(Silver, Offset(dx, 62f), Offset(dx, 88f), 4f, StrokeCap.Round)
    drawLine(Silver, Offset(180f, 92f), Offset(180f, 162f), 6f, StrokeCap.Round)
    drawOval(v(Color(0xFFE2E6EC), Silver, 64f, 96f), Offset(170f, 64f), Size(20f, 32f))
    sparkle(34f, 40f, 7f, phase, 0)
    sparkle(168f, 40f, 6f, phase, 1)
}

private fun DrawScope.drawDinner(phase: Float) {
    drawCircle(Brush.radialGradient(listOf(Lavender.copy(alpha = 0.45f), Color.Transparent), Offset(126f, 60f), 84f), 84f, Offset(126f, 60f))
    val r = 32f
    val a = Path().apply { addOval(Rect(Offset(128f - r, 58f - r), Size(r * 2f, r * 2f))) }
    val b = Path().apply { addOval(Rect(Offset(128f - r + r * 0.62f, 58f - r - r * 0.12f), Size(r * 2f, r * 2f))) }
    val moon = Path.combine(PathOperation.Difference, a, b)
    drawPath(moon, v(Color(0xFFFFEBB0), Color(0xFFF2C968), 26f, 90f))
    sparkle(66f, 40f, 8f, phase, 0, Color(0xFFFFF3C4))
    sparkle(172f, 112f, 6f, phase, 1, Color(0xFFFFF3C4))
    sparkle(96f, 78f, 4f, phase, 2, Color(0xFFFFF3C4))
    sparkle(30f, 92f, 5f, phase, 3, Color(0xFFFFF3C4))
    translate(left = 36f, top = 56f) {
        scale(0.66f, Offset.Zero) { drawBowl(phase, soup = true) }
    }
}

private fun DrawScope.drawAllergy(phase: Float) {
    groundShadow(100f, 178f, 70f)
    // Milk carton.
    val gable = Path().apply {
        moveTo(36f, 96f); lineTo(46f, 74f); lineTo(70f, 74f); lineTo(80f, 96f); close()
    }
    drawRoundRect(v(Color.White, Milk, 96f, 168f), Offset(36f, 94f), Size(44f, 74f), CornerRadius(6f))
    drawPath(gable, Color(0xFFD3E6F3))
    drawRoundRect(Sky, Offset(48f, 66f), Size(20f, 9f), CornerRadius(3f))
    drawCircle(Sky, 11f, Offset(58f, 130f))
    drawCircle(Color.White.copy(alpha = 0.6f), 3.2f, Offset(54f, 126f))
    drawRect(Color(0xFFCFE2F0).copy(alpha = 0.6f), Offset(70f, 96f), Size(10f, 72f))
    // Egg.
    val egg = Path().apply {
        moveTo(112f, 90f)
        cubicTo(130f, 90f, 142f, 124f, 142f, 140f)
        cubicTo(142f, 154f, 128f, 164f, 112f, 164f)
        cubicTo(96f, 164f, 82f, 154f, 82f, 140f)
        cubicTo(82f, 124f, 94f, 90f, 112f, 90f)
        close()
    }
    drawPath(egg, Brush.radialGradient(listOf(Color(0xFFFFF7EA), Color(0xFFF2D9B8)), Offset(102f, 122f), 52f))
    gloss(100f, 118f, 5f, 13f, 0.7f, 12f)
    // Wheat.
    val stem = Path().apply { moveTo(160f, 170f); cubicTo(162f, 140f, 154f, 110f, 156f, 74f) }
    drawPath(stem, Color(0xFFB98A3E), style = Stroke(3f, cap = StrokeCap.Round))
    for (k in 0 until 6) {
        val y = 80f + k * 14f
        val x = 156f + (k * 0.5f)
        rotate(-32f, Offset(x - 6f, y)) { drawOval(v(Color(0xFFF3CE6F), Color(0xFFD9A43F), y - 7f, y + 7f), Offset(x - 16f, y - 4.5f), Size(16f, 9f)) }
        rotate(32f, Offset(x + 6f, y)) { drawOval(v(Color(0xFFF3CE6F), Color(0xFFD9A43F), y - 7f, y + 7f), Offset(x, y - 4.5f), Size(16f, 9f)) }
    }
    drawOval(v(Color(0xFFF3CE6F), Color(0xFFD9A43F), 56f, 80f), Offset(151f, 56f), Size(10f, 22f))
    // "Safe" shield.
    val by = sin(phase * TAU) * 3f
    translate(top = by) {
        val shield = Path().apply {
            moveTo(100f, 14f)
            cubicTo(108f, 22f, 116f, 24f, 124f, 24f)
            cubicTo(124f, 46f, 116f, 58f, 100f, 64f)
            cubicTo(84f, 58f, 76f, 46f, 76f, 24f)
            cubicTo(84f, 24f, 92f, 22f, 100f, 14f)
            close()
        }
        drawPath(shield, v(Color(0xFF63B085), ForestDark, 14f, 64f))
        drawPath(shield, Color.White.copy(alpha = 0.5f), style = Stroke(2f, join = StrokeJoin.Round))
        checkMark(100f, 40f, 1f)
    }
    sparkle(30f, 50f, 6f, phase, 0)
    sparkle(174f, 52f, 6f, phase, 1)
}

private fun DrawScope.drawClock(phase: Float) {
    groundShadow(100f, 182f, 58f)
    // Handles and pot body.
    drawRoundRect(Color(0xFFC76A3C), Offset(26f, 118f), Size(26f, 13f), CornerRadius(6.5f))
    drawRoundRect(Color(0xFFC76A3C), Offset(148f, 118f), Size(26f, 13f), CornerRadius(6.5f))
    drawRoundRect(v(Color(0xFFF2A67C), Color(0xFFD97848), 100f, 168f), Offset(46f, 100f), Size(108f, 68f), CornerRadius(20f))
    gloss(64f, 128f, 5f, 18f, 0.4f, 8f)
    // Lid with a gentle rattle.
    val lift = sin(phase * TAU * 2f) * 1.6f
    translate(top = lift) {
        drawArc(v(Color(0xFFF7B58F), Color(0xFFE88B5A), 70f, 98f), 180f, 180f, true, Offset(44f, 70f), Size(112f, 56f))
        drawRoundRect(Color(0xFFE88B5A), Offset(42f, 96f), Size(116f, 9f), CornerRadius(4.5f))
        drawCircle(v(Color(0xFFFFE08A), Gold, 60f, 76f), 7f, Offset(100f, 68f))
    }
    steam(74f, 96f, 22f, phase, 0)
    steam(126f, 96f, 22f, phase, 1)
    // Timer badge.
    val bc = Offset(152f, 54f)
    drawCircle(Color(0xFF8F8278).copy(alpha = 0.2f), 27f, bc + Offset(0f, 3f))
    drawCircle(v(Color.White, Color(0xFFF4E8DB), 28f, 80f), 25f, bc)
    drawCircle(Tangerine, 25f, bc, style = Stroke(4.5f))
    for (k in 0 until 4) {
        rotate(k * 90f, bc) {
            drawLine(Ink.copy(alpha = 0.5f), Offset(bc.x, bc.y - 19f), Offset(bc.x, bc.y - 15f), 2f, StrokeCap.Round)
        }
    }
    rotate(phase * 360f, bc) { drawLine(TextAccent, bc, Offset(bc.x, bc.y - 17f), 3f, StrokeCap.Round) }
    rotate(phase * 30f + 60f, bc) { drawLine(Ink, bc, Offset(bc.x, bc.y - 11f), 3.6f, StrokeCap.Round) }
    drawCircle(Ink, 2.6f, bc)
    sparkle(34f, 56f, 7f, phase, 0)
}

private fun DrawScope.drawBasket(phase: Float) {
    groundShadow(100f, 184f, 62f)
    // Handle.
    drawArc(WoodDark, 180f, 180f, false, Offset(52f, 22f), Size(96f, 170f), style = Stroke(7f, cap = StrokeCap.Round))
    // Baguette (khobz).
    rotate(-16f, Offset(128f, 90f)) {
        drawRoundRect(v(Color(0xFFEFC48A), Color(0xFFD59A58), 46f, 138f), Offset(118f, 46f), Size(20f, 92f), CornerRadius(10f))
        for (i in 0 until 4) drawLine(Color(0xFFB9783A).copy(alpha = 0.6f), Offset(120f, 62f + i * 18f), Offset(136f, 70f + i * 18f), 2.4f, StrokeCap.Round)
    }
    // Greens.
    leaf(58f, 86f, 34f, 15f, -62f)
    leaf(70f, 74f, 34f, 15f, -88f, Leaf2)
    leaf(82f, 84f, 34f, 15f, -118f)
    leaf(70f, 92f, 30f, 13f, -90f, Leaf2)
    // Carrot.
    rotate(14f, Offset(112f, 108f)) {
        val carrot = Path().apply { moveTo(103f, 84f); lineTo(121f, 84f); lineTo(112f, 132f); close() }
        drawPath(carrot, v(Color(0xFFFFA654), Carrot, 84f, 132f))
        for (i in 0 until 3) drawLine(Color(0xFFD9701F).copy(alpha = 0.6f), Offset(105f + i, 92f + i * 10f), Offset(112f, 94f + i * 10f), 1.6f, StrokeCap.Round)
        leaf(108f, 80f, 14f, 6f, -70f, Leaf2)
        leaf(116f, 80f, 14f, 6f, -110f)
    }
    tomato(94f, 110f, 14f)
    tomato(134f, 114f, 12f)
    // Basket body.
    val body = Path().apply {
        moveTo(38f, 112f); lineTo(162f, 112f)
        cubicTo(160f, 140f, 152f, 168f, 142f, 174f)
        lineTo(58f, 174f)
        cubicTo(48f, 168f, 40f, 140f, 38f, 112f)
        close()
    }
    drawPath(body, v(Wood, WoodDark, 112f, 174f))
    clipPath(body) {
        for (i in 0 until 5) drawLine(WoodDark.copy(alpha = 0.5f), Offset(30f, 124f + i * 11f), Offset(170f, 124f + i * 11f), 2.6f)
        for (k in 0 until 11) drawLine(Color.White.copy(alpha = 0.16f), Offset(36f + k * 13f, 112f), Offset(32f + k * 14f, 174f), 2f)
    }
    drawRoundRect(v(Color(0xFFDDA877), Wood, 106f, 120f), Offset(32f, 106f), Size(136f, 14f), CornerRadius(7f))
    gloss(60f, 146f, 4f, 16f, 0.25f, 6f)
    sparkle(34f, 54f, 7f, phase, 0)
    sparkle(172f, 66f, 6f, phase, 1)
}

private fun DrawScope.drawMarket(phase: Float) {
    groundShadow(100f, 186f, 72f)
    // Poles.
    drawRoundRect(WoodDark, Offset(30f, 70f), Size(7f, 116f), CornerRadius(3f))
    drawRoundRect(WoodDark, Offset(163f, 70f), Size(7f, 116f), CornerRadius(3f))
    // Produce on the counter.
    tomato(48f, 130f, 11f); tomato(68f, 132f, 11f); tomato(58f, 118f, 11f)
    orange(98f, 132f, 11f); orange(118f, 134f, 11f); orange(108f, 120f, 11f)
    leaf(142f, 124f, 26f, 11f, -50f); leaf(152f, 124f, 26f, 11f, -120f, Leaf2)
    leaf(147f, 118f, 26f, 11f, -85f); leaf(147f, 130f, 24f, 10f, -90f, Leaf2)
    // Counter.
    drawRoundRect(v(Wood, WoodDark, 140f, 186f), Offset(22f, 140f), Size(156f, 46f), CornerRadius(10f))
    for (i in 0 until 3) drawLine(WoodDark.copy(alpha = 0.45f), Offset(28f, 154f + i * 11f), Offset(172f, 154f + i * 11f), 2f)
    // Striped awning.
    val sw = 128f / 6f
    val bw = 164f / 6f
    for (k in 0 until 6) {
        val xt = 36f + k * sw
        val xb = 18f + k * bw
        val col = if (k % 2 == 0) Tangerine else Paper
        val p = Path().apply {
            moveTo(xt, 32f); lineTo(xt + sw, 32f); lineTo(xb + bw, 76f); lineTo(xb, 76f); close()
        }
        drawPath(p, col)
        drawArc(col, 0f, 180f, true, Offset(xb, 76f - bw / 2f), Size(bw, bw))
    }
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), 32f, 76f), Offset(18f, 32f), Size(164f, 44f))
    drawRoundRect(Color(0xFFE08A5C), Offset(34f, 26f), Size(132f, 10f), CornerRadius(5f))
    sparkle(26f, 30f, 6f, phase, 0)
    sparkle(176f, 34f, 6f, phase, 1)
}

private fun DrawScope.drawWeek(phase: Float) {
    groundShadow(100f, 176f, 56f)
    drawRoundRect(Color(0xFF8F8278).copy(alpha = 0.12f), Offset(46f, 46f), Size(112f, 116f), CornerRadius(18f))
    drawRoundRect(v(Color.White, Color(0xFFFFF4E8), 40f, 152f), Offset(44f, 40f), Size(112f, 112f), CornerRadius(18f))
    drawRoundRect(v(Color(0xFFF7B58F), Tangerine, 40f, 66f), Offset(44f, 40f), Size(112f, 28f), CornerRadius(18f))
    drawRect(Tangerine, Offset(44f, 52f), Size(112f, 16f))
    drawRoundRect(Color(0xFF7A5C48), Offset(70f, 30f), Size(7f, 20f), CornerRadius(3.5f))
    drawRoundRect(Color(0xFF7A5C48), Offset(123f, 30f), Size(7f, 20f), CornerRadius(3.5f))
    val rows = listOf(Color(0xFFCFE6CF), Color(0xFFFFD9B8), Color(0xFFDCD3EE))
    val t = phase * 1.3f * 12f
    for (row in 0 until 3) {
        for (col in 0 until 4) {
            val idx = row * 4 + col
            val a = ((t - idx) / 1.5f).coerceIn(0f, 1f)
            val x = 54f + col * 24f
            val y = 78f + row * 22f
            drawRoundRect(Color(0xFFEFE4D8).copy(alpha = 0.6f), Offset(x, y), Size(20f, 16f), CornerRadius(5f))
            drawRoundRect(rows[row].copy(alpha = a), Offset(x, y), Size(20f, 16f), CornerRadius(5f))
        }
    }
    drawCircle(Color(0xFF8F8278).copy(alpha = 0.18f), 24f, Offset(150f, 150f))
    drawCircle(v(Color(0xFF63B085), ForestDark, 126f, 174f), 22f, Offset(148f, 146f))
    checkMark(148f, 146f, 0.9f)
    sparkle(30f, 58f, 8f, phase, 0)
    sparkle(172f, 52f, 6f, phase, 1)
    sparkle(26f, 130f, 5f, phase, 2)
}
