package dz.meals.app.ui.onboarding

import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dz.meals.app.ui.theme.Tangerine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val DefaultMealsOnboardingMotion = mutableStateOf(0f)
internal val LocalMealsOnboardingMotionClock = staticCompositionLocalOf<State<Float>> {
    DefaultMealsOnboardingMotion
}

/**
 * A Meals Liquid Glass bubble.
 *
 * The material is deliberately **refraction-free**: nothing behind the bubble is
 * bent or displaced. The glass look comes only from (1) a clear body that gets
 * brighter towards its edge, (2) a warm bounce of light along the lower edge,
 * (3) a white double rim and (4) crisp specular glints. That keeps it cheap
 * enough to draw many bubbles on both Android and iOS, and it matches the
 * rim/specular language already used by [dz.meals.app.ui.components.MealsLiquidGlass].
 */
@Composable
fun MealsGlassBubble(
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFFFF8F1),
    floatDistance: Dp = 7.dp,
    /** 0..1 phase offset; all bubbles share one frame clock. */
    phase: Float = 0f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val motionClock = LocalMealsOnboardingMotionClock.current
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                // Match both axes to the same whole-cycle frequency. The old
                // 0.8x horizontal frequency jumped when the 0..1 clock reset.
                if (floatDistance > 0.dp) {
                    val angle = (motionClock.value + phase) * 2f * PI.toFloat()
                    val distance = floatDistance.toPx()
                    translationY = sin(angle) * distance
                    translationX = cos(angle) * distance * 0.35f
                } else {
                    translationX = 0f
                    translationY = 0f
                }
            }
            .drawBehind {
                drawGlassBubble(
                    center = center,
                    radius = this.size.minDimension / 2f * 0.96f,
                    tint = tint
                )
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Draws one refraction-free glass bubble. Shared by [MealsGlassBubble] and the
 * ambient bubble field so every bubble has an identical finish.
 */
internal fun DrawScope.drawGlassBubble(
    center: Offset,
    radius: Float,
    tint: Color = Color(0xFFFFF8F1),
    alpha: Float = 1f
) {
    fun Color.a(v: Float) = copy(alpha = (v * alpha).coerceIn(0f, 1f))
    val r = radius
    val c = center

    // Soft contact halo under the bubble (a shadow, not a distortion).
    val haloCenter = Offset(c.x, c.y + r * 0.22f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF8F8278).a(0.13f), Color.Transparent),
            center = haloCenter,
            radius = r * 1.14f
        ),
        radius = r * 1.14f,
        center = haloCenter
    )

    // Clear body: transparent in the middle, brighter at the edge.
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.White.a(0.10f),
                0.60f to tint.a(0.20f),
                0.88f to Color.White.a(0.36f),
                1f to Color.White.a(0.54f)
            ),
            center = c,
            radius = r
        ),
        radius = r,
        center = c
    )

    // Warm bounce light gathered along the lower edge.
    val bounceCenter = Offset(c.x + r * 0.22f, c.y + r * 0.66f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Tangerine.a(0.34f), Tangerine.a(0.10f), Color.Transparent),
            center = bounceCenter,
            radius = r * 0.78f
        ),
        radius = r,
        center = c
    )

    // Broad soft glare in the upper-left.
    val glareCenter = Offset(c.x - r * 0.30f, c.y - r * 0.44f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.a(0.52f), Color.Transparent),
            center = glareCenter,
            radius = r * 0.56f
        ),
        radius = r,
        center = c
    )

    // Double rim: bright outer edge, quieter inner edge.
    val outer = r * 0.032f
    drawCircle(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.a(0.98f),
                0.30f to Color.White.a(0.60f),
                0.62f to Color.White.a(0.34f),
                1f to Color.White.a(0.82f)
            ),
            startY = c.y - r,
            endY = c.y + r
        ),
        radius = r - outer / 2f,
        center = c,
        style = Stroke(width = outer)
    )
    drawCircle(
        color = Color.White.a(0.26f),
        radius = r * 0.90f,
        center = c,
        style = Stroke(width = r * 0.014f)
    )

    // Specular crescent, upper-left.
    val crescentInset = r * 0.15f
    drawArc(
        brush = Brush.sweepGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.56f to Color.White.a(0.30f),
                0.66f to Color.White.a(0.96f),
                0.74f to Color.White.a(0.30f),
                1f to Color.Transparent
            ),
            center = c
        ),
        startAngle = 196f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = Offset(c.x - r + crescentInset, c.y - r + crescentInset),
        size = Size((r - crescentInset) * 2f, (r - crescentInset) * 2f),
        style = Stroke(width = r * 0.075f, cap = StrokeCap.Round)
    )
    // Tiny glint dot beside the crescent.
    drawCircle(
        color = Color.White.a(0.92f),
        radius = r * 0.042f,
        center = Offset(c.x - r * 0.62f, c.y - r * 0.06f)
    )
    // Counter glint, lower-right.
    drawArc(
        color = Color.White.a(0.55f),
        startAngle = 18f,
        sweepAngle = 38f,
        useCenter = false,
        topLeft = Offset(c.x - r * 0.86f, c.y - r * 0.86f),
        size = Size(r * 1.72f, r * 1.72f),
        style = Stroke(width = r * 0.034f, cap = StrokeCap.Round)
    )
}

/**
 * A slow field of tiny drifting bubbles for the onboarding background. One
 * canvas, one animation clock, no per-bubble layout cost.
 */
@Composable
fun MealsAmbientBubbles(
    modifier: Modifier = Modifier,
    count: Int = 5
) {
    val motionClock = LocalMealsOnboardingMotionClock.current
    Canvas(modifier.fillMaxSize()) {
        val t = motionClock.value
        val visibleCount = count.coerceIn(0, 9)
        for (i in 0 until visibleCount) {
            val seedX = ((i * 37) % 100) / 100f
            val radius = (7f + ((i * 13) % 19)) * density
            val phaseOffset = (i * 0.173f) % 1f
            val speed = 1 + (i % 3)
            val progress = (t * speed + phaseOffset).let { it - floor(it) }
            val y = size.height * (1.08f - progress * 1.2f)
            val wobble = sin(progress * 2f * PI.toFloat() + i) * 14.dp.toPx()
            val x = size.width * (0.06f + seedX * 0.88f) + wobble
            val fadeIn = (progress * 4f).coerceIn(0f, 1f)
            val fadeOut = ((1f - progress) * 4f).coerceIn(0f, 1f)
            val fade = fadeIn * fadeOut * 0.55f
            if (fade <= 0.01f) continue

            val center = Offset(x, y)
            // Ambient bubbles are deliberately lightweight: flat translucent
            // bodies, one fine rim and one short glint. Hero bubbles retain the
            // full multi-layer glass treatment in drawGlassBubble().
            drawCircle(
                color = Color.White.copy(alpha = 0.10f * fade),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.54f * fade),
                radius = radius * 0.96f,
                center = center,
                style = Stroke(width = 0.9.dp.toPx())
            )
            drawArc(
                color = Color.White.copy(alpha = 0.75f * fade),
                startAngle = 202f,
                sweepAngle = 48f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.72f, center.y - radius * 0.72f),
                size = Size(radius * 1.44f, radius * 1.44f),
                style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
