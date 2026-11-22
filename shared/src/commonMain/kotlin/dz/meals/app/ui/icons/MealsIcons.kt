package dz.meals.app.ui.icons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import com.composables.icons.lucide.*

import dz.meals.app.ui.theme.Forest
import dz.meals.app.ui.theme.ForestDark
import dz.meals.app.ui.theme.MutedInk

/**
 * Semantic icon names used throughout Meals.
 *
 * Every entry resolves to a stroke-only Lucide vector. Selected states change
 * tint and motion only; the app never swaps an outline for a filled glyph.
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
    Icon(
        imageVector = name.imageVector(),
        contentDescription = contentDescription,
        tint = tint ?: ForestDark,
        modifier = modifier
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

    Icon(
        imageVector = name.imageVector(),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

private fun MealsIconName.imageVector(): ImageVector = when (this) {
    MealsIconName.Plan -> Lucide.CalendarDays
    MealsIconName.Cookbook -> Lucide.BookOpen
    MealsIconName.Basket -> Lucide.ShoppingBasket
    MealsIconName.Pantry -> Lucide.PackageOpen
    MealsIconName.Settings -> Lucide.Settings2
    MealsIconName.Update -> Lucide.ArrowDownToLine
    MealsIconName.Download -> Lucide.Download
    MealsIconName.Refresh -> Lucide.RefreshCw
    MealsIconName.Check -> Lucide.Check
    MealsIconName.ChevronRight -> Lucide.ChevronRight
    MealsIconName.Close -> Lucide.X
    MealsIconName.Add -> Lucide.Plus
    MealsIconName.Wallet -> Lucide.Wallet
    MealsIconName.ArrowBack -> Lucide.ArrowLeft
    MealsIconName.ArrowForward -> Lucide.ArrowRight
    MealsIconName.Lock -> Lucide.Lock
    MealsIconName.LockOpen -> Lucide.LockOpen
    MealsIconName.Meal -> Lucide.Soup
    MealsIconName.Search -> Lucide.Search
    MealsIconName.Star, MealsIconName.StarOutline -> Lucide.Star
    MealsIconName.Clock -> Lucide.Clock
    MealsIconName.Info -> Lucide.Info
    MealsIconName.Edit -> Lucide.Pencil
    MealsIconName.Delete -> Lucide.Trash2
}
