package dz.meals.app.ui.icons

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dz.meals.app.ui.theme.mealsText
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

/**
 * The shared Midati icon vocabulary. Every interface icon is rendered from the
 * same bundled PNG family; callers must not tint the multi-colour artwork.
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
    Share,
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
 * Static artwork for buttons, cards, labels and other app actions.
 * Keep the supplied modifier responsible for the rendered size.
 */
@Composable
fun MealsIcon(
    name: MealsIconName,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    if (name == MealsIconName.Share) {
        val translatedDescription = contentDescription?.let { mealsText(it) }
        val shareModifier = if (translatedDescription == null) {
            modifier
        } else {
            modifier.semantics { this.contentDescription = translatedDescription }
        }
        MealsShareGlyph(shareModifier)
        return
    }

    val layoutDirection = LocalLayoutDirection.current
    val shouldMirror = layoutDirection == LayoutDirection.Rtl && name in setOf(
        MealsIconName.ArrowBack,
        MealsIconName.ArrowForward,
        MealsIconName.ChevronRight
    )
    Image(
        painter = painterResource(name.drawable()),
        contentDescription = contentDescription?.let { mealsText(it) },
        modifier = modifier.graphicsLayer {
            if (shouldMirror) scaleX = -1f
        }
    )
}


@Composable
private fun MealsShareGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val connector = Color(0xFF8D6849)
        val nodeFill = Color(0xFFE9C99F)
        val nodeHighlight = Color(0xFFFFF7E9)
        val left = Offset(size.width * 0.27f, size.height * 0.50f)
        val upper = Offset(size.width * 0.72f, size.height * 0.25f)
        val lower = Offset(size.width * 0.72f, size.height * 0.75f)
        val nodeRadius = diameter * 0.145f

        drawLine(
            color = connector,
            start = left,
            end = upper,
            strokeWidth = diameter * 0.075f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = connector,
            start = left,
            end = lower,
            strokeWidth = diameter * 0.075f,
            cap = StrokeCap.Round
        )
        listOf(left, upper, lower).forEach { center ->
            drawCircle(color = connector, radius = nodeRadius * 1.22f, center = center)
            drawCircle(color = nodeFill, radius = nodeRadius, center = center)
            drawCircle(color = nodeHighlight, radius = nodeRadius * 0.48f, center = center)
        }
    }
}

/**
 * Navigation artwork uses the exact same PNG as every other occurrence.
 * Selection keeps the existing subtle spring response without swapping in
 * a separate animation asset or a visually different icon.
 */
@Composable
fun MealsNavigationIcon(
    name: MealsIconName,
    selected: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    if (name == MealsIconName.Share) {
        val translatedDescription = mealsText(contentDescription)
        MealsShareGlyph(
            modifier.semantics { this.contentDescription = translatedDescription }
        )
        return
    }

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mealsNavigationIconScale"
    )

    val layoutDirection = LocalLayoutDirection.current
    val shouldMirror = layoutDirection == LayoutDirection.Rtl && name in setOf(
        MealsIconName.ArrowBack,
        MealsIconName.ArrowForward,
        MealsIconName.ChevronRight
    )
    Image(
        painter = painterResource(name.drawable()),
        contentDescription = mealsText(contentDescription),
        modifier = modifier.graphicsLayer {
            scaleX = scale * if (shouldMirror) -1f else 1f
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
    MealsIconName.Share -> error("Share is rendered as a custom vector glyph.")
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
