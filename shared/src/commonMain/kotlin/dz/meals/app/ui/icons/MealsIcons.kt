package dz.meals.app.ui.icons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.MutedInk

/**
 * Meals' bespoke vector vocabulary. These are artwork owned by the app,
 * not glyphs from a reusable third-party icon pack. Every source vector is
 * stroke-only; selected state changes tint and motion, never to a filled glyph.
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

@Composable
fun MealsIcon(
    name: MealsIconName,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    Image(
        painter = painterResource(name.drawable()),
        contentDescription = contentDescription,
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier.graphicsLayer {
            scaleX = 1.10f
            scaleY = 1.10f
        }
    )
}

@Composable
fun MealsNavigationIcon(
    name: MealsIconName,
    selected: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.13f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mealsNavigationIconScale"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) Forest else MutedInk,
        animationSpec = tween(durationMillis = 150),
        label = "mealsNavigationIconTint"
    )

    Image(
        painter = painterResource(name.drawable()),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
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
