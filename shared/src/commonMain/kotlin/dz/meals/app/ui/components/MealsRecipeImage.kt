package dz.meals.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
 * Shared recipe-photo surface. Requests are resized before reaching the device,
 * and a quiet branded placeholder remains visible while an image loads or fails.
 */
@Composable
fun MealsRecipeImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageWidth: Int = 360,
    cornerRadius: Dp = 14.dp,
    placeholderIconSize: Dp = 30.dp
) {
    val resolvedUrl = remember(imageUrl, imageWidth) {
        optimizedRecipeImageUrl(imageUrl, imageWidth)
    }
    var imageLoaded by remember(resolvedUrl) { mutableStateOf(false) }
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
        if (resolvedUrl.isNotBlank()) {
            AsyncImage(
                model = resolvedUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = imageAlpha },
                onState = { state ->
                    imageLoaded = state is AsyncImagePainter.State.Success
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

private fun optimizedRecipeImageUrl(sourceUrl: String, width: Int): String {
    if (!sourceUrl.startsWith("https://", ignoreCase = true)) return ""
    if (sourceUrl.startsWith("https://images.weserv.nl/", ignoreCase = true)) return sourceUrl

    val encodedUrl = buildString {
        val hex = "0123456789ABCDEF"
        sourceUrl.encodeToByteArray().forEach { byte ->
            val value = byte.toInt() and 0xff
            val isUnreserved = value in 0x41..0x5A ||
                value in 0x61..0x7A ||
                value in 0x30..0x39 ||
                value == 0x2D || value == 0x2E || value == 0x5F || value == 0x7E
            if (isUnreserved) {
                append(value.toChar())
            } else {
                append('%')
                append(hex[value ushr 4])
                append(hex[value and 0x0F])
            }
        }
    }
    return "https://images.weserv.nl/?url=$encodedUrl&w=${width.coerceIn(240, 960)}&q=60&output=webp"
}
