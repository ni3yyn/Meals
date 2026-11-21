package dz.meals.app.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dz.meals.app.shared.generated.resources.Res
import org.jetbrains.compose.resources.painterResource

/**
 * Meals' own rounded, multitone icon vocabulary.
 * These keep their warm palette instead of inheriting Material's monochrome tint.
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
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(name.drawable()),
        contentDescription = contentDescription,
        modifier = modifier
    )
}

private fun MealsIconName.drawable() = when (this) {
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
