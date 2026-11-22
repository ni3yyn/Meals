package dz.meals.app.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dz.meals.app.data.AppPreferences
import dz.meals.app.data.IngredientMaster
import dz.meals.app.shared.format.formatDzd
import dz.meals.app.shared.generated.resources.Res
import dz.meals.app.shared.generated.resources.meals_kitchen_background
import dz.meals.app.ui.components.MealsAnimatedCount
import dz.meals.app.ui.components.MealsCard
import dz.meals.app.ui.components.MealsCheckRow
import dz.meals.app.ui.components.MealsGlassSurface
import dz.meals.app.ui.components.MealsIconButton
import dz.meals.app.ui.components.MealsLiquidGlassTextField
import dz.meals.app.ui.components.MealsPressable
import dz.meals.app.ui.components.MealsPrimaryButton
import dz.meals.app.ui.components.MealsSecondaryButton
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.ui.theme.Cream
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.Leaf
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftOrange
import dz.meals.app.ui.theme.Tangerine
import dz.meals.app.ui.theme.TextAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything the first-run flow collects; mirrors the arguments of \`saveSettings\`. */
data class MealsOnboardingResult(
    val weeklyBudgetDzd: Int,
    val wilaya: String,
    val householdServings: Int,
    val allergyIds: Set<String>,
    val mealSlots: Set<String>,
    val dislikedIngredientIds: Set<String>,
    val dietPattern: String,
    val maxCookingMinutes: Int,
    val budgetBufferPercent: Int,
    val commune: String,
    val shoppingChannel: String,
    val preferredShop: String
)

private const val PageCount = 8
private const val LastPage = PageCount - 1

/** A small floating glass bubble that orbits the hero illustration. */
private class Satellite(
    val dx: Float,
    val dy: Float,
    val size: Float,
    val phase: Float,
    val icon: MealsIconName? = null,
    val art: MealsArt? = null
)

/**
 * Full-screen, first-run experience: one idea per page, a big illustration
 * inside a refraction-free Liquid Glass bubble, and the same glass cards and
 * buttons as the rest of the app. Must be hosted inside \`MealsBackdrop\`.
 *
 * @param backHandler lets the platform host wire its system-back handling
 * (for example Android's \`BackHandler\`) without this module depending on it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealsOnboarding(
    initial: AppPreferences,
    ingredients: List<IngredientMaster>,
    onQuickStart: (budget: Int, servings: Int) -> Unit,
    onFinish: (MealsOnboardingResult) -> Unit,
    modifier: Modifier = Modifier,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> }
) {
    var budget by rememberSaveable { mutableStateOf(initial.weeklyBudgetDzd.coerceAtLeast(500)) }
    var servings by rememberSaveable { mutableStateOf(initial.householdServings.coerceIn(1, 12)) }
    var wilaya by rememberSaveable { mutableStateOf(initial.wilaya) }
    var commune by rememberSaveable { mutableStateOf(initial.commune) }
    var channel by rememberSaveable { mutableStateOf(initial.shoppingChannel) }
    var shop by rememberSaveable { mutableStateOf(initial.preferredShop) }
    var diet by rememberSaveable { mutableStateOf(initial.dietPattern) }
    var maxMinutes by rememberSaveable { mutableStateOf(initial.maxCookingMinutes.coerceIn(10, 240)) }
    var buffer by rememberSaveable { mutableStateOf(initial.budgetBufferPercent.coerceIn(0, 30)) }
    var noKnownAllergies by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // Sets are kept in plain state: they are small and only matter during this one session.
    var slots by remember { mutableStateOf(initial.mealSlots) }
    var allergies by remember { mutableStateOf(initial.allergyIds) }
    var dislikes by remember { mutableStateOf(initial.dislikedIngredientIds) }
    var pendingFinish by remember { mutableStateOf<(() -> Unit)?>(null) }

    val pagerState = rememberPagerState(pageCount = { PageCount })
    val scope = rememberCoroutineScope()
    val current = pagerState.currentPage
    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page.coerceIn(0, LastPage)) }
    }

    val valid = when (current) {
        1 -> budget > 0
        2 -> servings in 1..12
        3 -> slots.isNotEmpty()
        5 -> maxMinutes in 10..240 && buffer in 0..30
        else -> true
    }

    backHandler(current > 0 && pendingFinish == null) { goTo(current - 1) }

    LaunchedEffect(pendingFinish) {
        val action = pendingFinish ?: return@LaunchedEffect
        delay(2400) // let the "preparing your week" scene play
        action()
    }

    Box(modifier.fillMaxSize()) {
        // Same artwork the glass samples, so the app underneath is fully covered.
        Image(
            painter = painterResource(Res.drawable.meals_kitchen_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.38f), Color.Transparent, Cream.copy(alpha = 0.55f))
                )
            )
        )
