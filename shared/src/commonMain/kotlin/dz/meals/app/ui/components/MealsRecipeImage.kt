package dz.meals.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.ui.theme.WarmSurface

/**
 * Shared recipe-photo surface. Loads source thumbnails directly, without an
 * external proxy, and uses the persistent Coil disk cache configured by Android.
 */
@Composable
fun MealsRecipeImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") imageWidth: Int = 360,
    cornerRadius: Dp = 14.dp,
    placeholderIconSize: Dp = 30.dp
) {
    val directUrl = remember(imageUrl) {
        imageUrl.trim().takeIf { it.startsWith("https://", ignoreCase = true) }.orEmpty()
    }
    var imageLoaded by remember(directUrl) { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (imageLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "recipeImageReveal"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(WarmSurface),
        contentAlignment = Alignment.Center
    ) {
        if (directUrl.isNotBlank()) {
            AsyncImage(
                model = directUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = imageAlpha },
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> imageLoaded = true
                        is AsyncImagePainter.State.Error -> {
                            imageLoaded = false
                            val host = directUrl.substringAfter("://").substringBefore("/")
                            println("MidatiRecipeImages: thumbnail_load_failed host=$host")
                        }
                        else -> imageLoaded = false
                    }
                }
            )
        }
        if (imageAlpha < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WarmSurface.copy(alpha = 1f - imageAlpha)),
                contentAlignment = Alignment.Center
            ) {
                MealsIcon(
                    name = MealsIconName.Meal,
                    contentDescription = null,
                    modifier = Modifier
                        .size(placeholderIconSize)
                        .graphicsLayer { alpha = 1f - imageAlpha }
                )
            }
        }
    }
}
