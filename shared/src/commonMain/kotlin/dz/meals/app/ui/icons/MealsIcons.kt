package dz.meals.app.ui.icons

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dz.meals.app.shared.generated.resources.Res
import dz.meals.app.shared.generated.resources.meals_icon_add
import dz.meals.app.shared.generated.resources.meals_icon_arrow_back
import dz.meals.app.shared.generated.resources.meals_icon_arrow_forward
import dz.meals.app.shared.generated.resources.meals_icon_basket
import dz.meals.app.shared.generated.resources.meals_icon_check
import dz.meals.app.shared.generated.resources.meals_icon_chevron_right
import dz.meals.app.shared.generated.resources.meals_icon_clock
import dz.meals.app.shared.generated.resources.meals_icon_close
import dz.meals.app.shared.generated.resources.meals_icon_cookbook
import dz.meals.app.shared.generated.resources.meals_icon_delete
import dz.meals.app.shared.generated.resources.meals_icon_download
import dz.meals.app.shared.generated.resources.meals_icon_edit
import dz.meals.app.shared.generated.resources.meals_icon_info
import dz.meals.app.shared.generated.resources.meals_icon_lock
import dz.meals.app.shared.generated.resources.meals_icon_lock_open
import dz.meals.app.shared.generated.resources.meals_icon_meal
import dz.meals.app.shared.generated.resources.meals_icon_pantry
import dz.meals.app.shared.generated.resources.meals_icon_plan
import dz.meals.app.shared.generated.resources.meals_icon_refresh
import dz.meals.app.shared.generated.resources.meals_icon_search
import dz.meals.app.shared.generated.resources.meals_icon_settings
import dz.meals.app.shared.generated.resources.meals_icon_star
import dz.meals.app.shared.generated.resources.meals_icon_star_outline
import dz.meals.app.shared.generated.resources.meals_icon_update
import dz.meals.app.shared.generated.resources.meals_icon_wallet
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The shared Meals icon vocabulary, rendered with With Icons' soft-3D assets.
 * Artwork is multi-colour and volume-shaded, so callers must not tint it.
 */
enum class MealsIconName {
    Plan,
    Cookbook,
    Basket,
    Pantry,
    Settings,
    Update,
    Download,
    Refresh,
    Check,
    ChevronRight,
    Close,
    Add,
    Wallet,
    ArrowBack,
    ArrowForward,
    Lock,
    LockOpen,
    Meal,
    Search,
    Star,
    StarOutline,
    Clock,
    Info,
    Edit,
    Delete
}

/**
 * Static soft-3D artwork for buttons, cards, labels and other app actions.
 * Keep the supplied modifier responsible for the rendered size.
 */
@Composable
fun MealsIcon(
    name: MealsIconName,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(name.drawable()),
        contentDescription = contentDescription,
        modifier = modifier
    )
}

/**
 * A destination icon with a small spring response and a one-shot soft-3D
 * animation on entry. The cookbook morphs from a closed book to an open one.
 * When a destination becomes inactive, its calm resting artwork is shown.
 */
@Composable
fun MealsNavigationIcon(
    name: MealsIconName,
    selected: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mealsNavigationIconScale"
    )
    val animatedModifier = modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    val animationFile = name.navigationAnimationFile()

    if (animationFile == null) {
        Image(
            painter = painterResource(name.drawable()),
            contentDescription = contentDescription,
            modifier = animatedModifier
        )
        return
    }

    val compositionResult = rememberLottieComposition(animationFile) {
        LottieCompositionSpec.JsonString(
            Res.readBytes("files/$animationFile").decodeToString()
        )
    }
    val composition by compositionResult
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = selected,
        restartOnPlay = true,
        iterations = 1,
        speed = 1.04f
    )

    if (selected && composition != null) {
        Image(
            painter = rememberLottiePainter(
                composition = composition,
                progress = { progress },
                enableExpressions = false
            ),
            contentDescription = contentDescription,
            modifier = animatedModifier
        )
    } else {
        Image(
            painter = painterResource(name.drawable()),
            contentDescription = contentDescription,
            modifier = animatedModifier
        )
    }
}

private fun MealsIconName.navigationAnimationFile(): String? = when (this) {
    MealsIconName.Plan -> "meals_nav_plan.json"
    MealsIconName.Cookbook -> "meals_nav_cookbook.json"
    MealsIconName.Basket -> "meals_nav_basket.json"
    MealsIconName.Pantry -> "meals_nav_pantry.json"
    else -> null
}

private fun MealsIconName.drawable(): DrawableResource = when (this) {
    MealsIconName.Plan -> Res.drawable.meals_icon_plan
    MealsIconName.Cookbook -> Res.drawable.meals_icon_cookbook
    MealsIconName.Basket -> Res.drawable.meals_icon_basket
    MealsIconName.Pantry -> Res.drawable.meals_icon_pantry
    MealsIconName.Settings -> Res.drawable.meals_icon_settings
    MealsIconName.Update -> Res.drawable.meals_icon_update
    MealsIconName.Download -> Res.drawable.meals_icon_download
    MealsIconName.Refresh -> Res.drawable.meals_icon_refresh
    MealsIconName.Check -> Res.drawable.meals_icon_check
    MealsIconName.ChevronRight -> Res.drawable.meals_icon_chevron_right
    MealsIconName.Close -> Res.drawable.meals_icon_close
    MealsIconName.Add -> Res.drawable.meals_icon_add
    MealsIconName.Wallet -> Res.drawable.meals_icon_wallet
    MealsIconName.ArrowBack -> Res.drawable.meals_icon_arrow_back
    MealsIconName.ArrowForward -> Res.drawable.meals_icon_arrow_forward
    MealsIconName.Lock -> Res.drawable.meals_icon_lock
    MealsIconName.LockOpen -> Res.drawable.meals_icon_lock_open
    MealsIconName.Meal -> Res.drawable.meals_icon_meal
    MealsIconName.Search -> Res.drawable.meals_icon_search
    MealsIconName.Star -> Res.drawable.meals_icon_star
    MealsIconName.StarOutline -> Res.drawable.meals_icon_star_outline
    MealsIconName.Clock -> Res.drawable.meals_icon_clock
    MealsIconName.Info -> Res.drawable.meals_icon_info
    MealsIconName.Edit -> Res.drawable.meals_icon_edit
    MealsIconName.Delete -> Res.drawable.meals_icon_delete
}
