package dz.meals.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A shared, lightweight screen transition with a soft spring pop.
 *
 * The short upward drift and restrained scale give screen changes a soft,
 * bubbly feel without making tab navigation feel heavy or slow.
 */
@Composable
fun <T> MealsBubblyTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    label: String = "mealsBubblyTransition",
    content: @Composable AnimatedContentScope.(T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(durationMillis = 160)) +
                    scaleIn(
                        initialScale = 0.94f,
                        animationSpec = spring(
                            dampingRatio = 0.76f,
                            stiffness = 520f
                        )
                    ) +
                    slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight / 40 },
                        animationSpec = spring(
                            dampingRatio = 0.84f,
                            stiffness = 520f
                        )
                    )
                ) togetherWith (
                fadeOut(animationSpec = tween(durationMillis = 95)) +
                    scaleOut(
                        targetScale = 0.985f,
                        animationSpec = tween(durationMillis = 105)
                    )
                )
        },
        contentAlignment = contentAlignment,
        label = label,
        content = content
    )
}
