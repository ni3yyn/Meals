package dz.meals.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import dz.meals.app.ui.components.MealsGlassDot
import dz.meals.app.ui.components.MealsLiquidGlassProgressBar
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything the first-run flow collects; mirrors the arguments of `saveSettings`. */
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
 * buttons as the rest of the app. Must be hosted inside `MealsBackdrop`.
 *
 * @param backHandler lets the platform host wire its system-back handling
 * (for example Android's `BackHandler`) without this module depending on it.
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

    // One frame clock drives every floating bubble and vector illustration.
    // Children read its State in graphics/draw layers, avoiding a recomposition
    // cascade on every frame and dozens of independent animation clocks.
    val motionTransition = rememberInfiniteTransition(label = "mealsOnboardingMotion")
    val motionClock = motionTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 12_000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "mealsOnboardingMotionClock"
    )

    val pagerState = rememberPagerState(pageCount = { PageCount })
    val scope = rememberCoroutineScope()
    val current = pagerState.currentPage
    fun goTo(page: Int) {
        scope.launch {
            pagerState.animateScrollToPage(
                page = page.coerceIn(0, LastPage),
                animationSpec = spring(
                    dampingRatio = 0.84f,
                    stiffness = 390f
                )
            )
        }
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

    CompositionLocalProvider(LocalMealsOnboardingMotionClock provides motionClock) {
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
        MealsAmbientBubbles()

        AnimatedContent(
            targetState = pendingFinish != null,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (targetState) {
                    (
                        fadeIn(tween(420, easing = FastOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 0.94f,
                                animationSpec = tween(580, easing = FastOutSlowInEasing)
                            ) +
                            slideInVertically(
                                initialOffsetY = { height -> height / 24 },
                                animationSpec = tween(580, easing = FastOutSlowInEasing)
                            )
                    ) togetherWith (
                        fadeOut(tween(260, easing = FastOutSlowInEasing)) +
                            scaleOut(
                                targetScale = 1.035f,
                                animationSpec = tween(360, easing = FastOutSlowInEasing)
                            ) +
                            slideOutVertically(
                                targetOffsetY = { height -> -height / 28 },
                                animationSpec = tween(360, easing = FastOutSlowInEasing)
                            )
                    )
                } else {
                    (
                        fadeIn(tween(360, easing = FastOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 1.012f,
                                animationSpec = tween(360, easing = FastOutSlowInEasing)
                            )
                    ) togetherWith fadeOut(tween(220, easing = FastOutSlowInEasing))
                }
            },
            label = "mealsOnboardingFinishTransition"
        ) { finishing ->
            if (finishing) {
                Finale()
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 18.dp)
                ) {
                    Header(
                        current = current,
                        progress = (
                            pagerState.currentPage.toFloat() +
                                pagerState.currentPageOffsetFraction + 1f
                            ).div(PageCount).coerceIn(1f / PageCount, 1f),
                        onBack = { goTo(current - 1) }
                    )

                    val layoutDirection = LocalLayoutDirection.current
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // Keep pager state controlled so an invalid form page
                            // cannot be skipped, but still accept natural horizontal
                            // swipes. Child controls (sliders/text fields) retain
                            // their own drag gestures when they consume the pointer.
                            .pointerInput(current, valid, layoutDirection) {
                                var dragDistance = 0f
                                val swipeThreshold = 72.dp.toPx()
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { change, dragAmount ->
                                        dragDistance += dragAmount
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        if (abs(dragDistance) >= swipeThreshold) {
                                            val movingForward = if (
                                                layoutDirection == LayoutDirection.Rtl
                                            ) dragDistance > 0f else dragDistance < 0f
                                            when {
                                                movingForward && valid && current < LastPage ->
                                                    goTo(current + 1)
                                                !movingForward && current > 0 ->
                                                    goTo(current - 1)
                                            }
                                        }
                                        dragDistance = 0f
                                    },
                                    onDragCancel = { dragDistance = 0f }
                                )
                            },
                        userScrollEnabled = false,
                        beyondViewportPageCount = 0
                    ) { page ->
                        val pageOffset = {
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        }
                        when (page) {
                            0 -> StepLayout(
                                pageOffset = pageOffset,
                                title = "أهلاً بك في Midati",
                                subtitle = "نرتّب لكِ وجبات الأسبوع وقائمة التسوق حسب ميزانية أسرتك.",
                                art = MealsArt.Bowl,
                                satellites = listOf(
                                    Satellite(-0.42f, -0.30f, 0.24f, 0.10f, MealsIconName.Plan),
                                    Satellite(0.44f, 0.28f, 0.22f, 0.55f, MealsIconName.Basket),
                                    Satellite(0.36f, -0.42f, 0.16f, 0.30f, MealsIconName.Cookbook)
                                )
                            ) {
                                InfoRow(MealsIconName.Lock, "بدون حساب أو رقم هاتف", "تبقى تفضيلاتك على هذا الجهاز فقط.")
                                InfoRow(MealsIconName.Check, "يعمل بدون إنترنت", "الخطة وقائمة التسوق متاحتان دائماً.")
                                InfoRow(MealsIconName.Wallet, "ضمن ميزانيتك", "نختار الوصفات بما يناسب ما تريدين إنفاقه.")
                            }

                            1 -> StepLayout(
                                pageOffset = pageOffset,
                                title = "كم ميزانيتك للأسبوع؟",
                                subtitle = "نبني الخطة وقائمة التسوق ضمن هذا المبلغ.",
                                art = MealsArt.Budget,
                                satellites = listOf(
                                    Satellite(-0.42f, 0.30f, 0.22f, 0.20f, MealsIconName.Wallet),
                                    Satellite(0.44f, -0.30f, 0.20f, 0.60f, MealsIconName.Basket),
                                    Satellite(-0.30f, -0.44f, 0.14f, 0.40f, MealsIconName.Star)
                                )
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    StepperButton("−") { budget = (budget - 500).coerceAtLeast(500) }
                                    MealsAnimatedCount(
                                        value = budget.formatDzd(),
                                        color = Ink,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    StepperButton("+") { budget = (budget + 500).coerceAtMost(500_000) }
                                }
                                Slider(
                                    value = budget.coerceIn(3_000, 60_000).toFloat(),
                                    onValueChange = { budget = (it / 500f).roundToInt() * 500 },
                                    valueRange = 3_000f..60_000f,
                                    colors = glassSliderColors()
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(5_000, 8_000, 12_000, 20_000, 30_000).forEach { preset ->
                                        BubbleChip(preset.formatDzd(), budget == preset) { budget = preset }
                                    }
                                }
                            }

                            2 -> StepLayout(
                                pageOffset = pageOffset,
                                title = "كم شخصاً نطبخ له؟",
                                subtitle = "نضبط الكميات وقائمة التسوق على عدد الحصص.",
                                art = MealsArt.Family,
                                artCount = servings,
                                satellites = listOf(
                                    Satellite(-0.42f, -0.28f, 0.22f, 0.15f, MealsIconName.Meal),
                                    Satellite(0.44f, 0.30f, 0.18f, 0.65f, MealsIconName.Star)
                                )
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    StepperButton("−") { servings = (servings - 1).coerceAtLeast(1) }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        MealsAnimatedCount(
                                            value = servings.toString(),
                                            color = Ink,
                                            style = MaterialTheme.typography.displayMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text("أشخاص نطبخ لهم عادةً", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                                    }
                                    StepperButton("+") { servings = (servings + 1).coerceAtMost(12) }
                                }
                                MealsCheckRow(
                                    title = "لا توجد حساسية غذائية معروفة",
                                    supportingText = "إن وُجدت حساسية أو كان هناك شك، تابعي الخطوات التالية.",
                                    checked = noKnownAllergies,
                                    onCheckedChange = { noKnownAllergies = it }
                                )
                                if (noKnownAllergies) {
                                    MealsSecondaryButton(
                                        label = "إعداد سريع بالقيم الافتراضية",
                                        onClick = { pendingFinish = { onQuickStart(budget, servings) } },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = budget > 0 && servings in 1..12
                                    )
                                }
                            }

                            3 -> StepLayout(
                                pageOffset = pageOffset,
                                title = "ما الوجبات التي نخطط لها؟",
                                subtitle = "اختاري ما تحتاجينه فقط، ويمكنك تغييره لاحقاً.",
                                art = MealsArt.Lunch,
                                satellites = listOf(
                                    Satellite(-0.44f, -0.26f, 0.26f, 0.10f, art = MealsArt.Breakfast),
                                    Satellite(0.44f, 0.26f, 0.26f, 0.55f, art = MealsArt.Dinner)
                                )
                            ) {
                                SlotTile(MealsArt.Breakfast, "الفطور", "وجبة الصباح", "breakfast" in slots) {
                                    slots = if ("breakfast" in slots) slots - "breakfast" else slots + "breakfast"
                                }
                                SlotTile(MealsArt.Lunch, "الغداء", "الوجبة الرئيسية", "lunch" in slots) {
                                    slots = if ("lunch" in slots) slots - "lunch" else slots + "lunch"
                                }
                                SlotTile(MealsArt.Dinner, "العشاء", "وجبة المساء", "dinner" in slots) {
                                    slots = if ("dinner" in slots) slots - "dinner" else slots + "dinner"
                                }
                            }

                            4 -> StepLayout(
                                pageOffset = pageOffset,
                                title = "هل هناك ما يجب تجنبه؟",
                                subtitle = "نستبعد الوصفات التي قد لا تناسب عائلتك.",
                                art = MealsArt.Allergy,
                                satellites = listOf(
                                    Satellite(-0.42f, 0.30f, 0.20f, 0.20f, MealsIconName.Info),
                                    Satellite(0.44f, -0.28f, 0.20f, 0.60f, MealsIconName.Check)
                                )
                            ) {
                                Text("الحساسية الغذائية", style = MaterialTheme.typography.titleMedium, color = Ink)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("gluten" to "الغلوتين", "egg" to "البيض", "milk" to "الحليب").forEach { (id, label) ->
                                        BubbleChip(label, id in allergies) {
                                            allergies = if (id in allergies) allergies - id else allergies + id
                                        }
                                    }
                                }
                                Text("النمط الغذائي", style = MaterialTheme.typography.titleMedium, color = Ink)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("none" to "عادي", "vegetarian" to "نباتي", "vegan" to "نباتي صرف").forEach { (id, label) ->
                                        BubbleChip(label, diet == id) { diet = id }
                                    }
                                }
                            }

                            5 -> StepLayout(
                                pageOffset = pageOffset,
                                title = "كم تفضلين أن تطبخي؟",
                                subtitle = "نختار وصفات تناسب وقتك وميزانيتك.",
                                art = MealsArt.Clock,
                                satellites = listOf(
                                    Satellite(-0.42f, -0.28f, 0.22f, 0.15f, MealsIconName.Clock),
                                    Satellite(0.44f, 0.30f, 0.20f, 0.60f, MealsIconName.Meal)
                                )
                            ) {
                                Text("أقصى وقت للطبخ", style = MaterialTheme.typography.titleMedium, color = Ink)
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MealsAnimatedCount(
                                        value = maxMinutes.toString(),
                                        color = Ink,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text("دقيقة", color = MutedInk, style = MaterialTheme.typography.bodyMedium)
                                }
                                Slider(
                                    value = maxMinutes.toFloat(),
                                    onValueChange = { maxMinutes = ((it / 5f).roundToInt() * 5).coerceIn(10, 240) },
                                    valueRange = 10f..240f,
                                    colors = glassSliderColors()
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(20, 30, 45, 60, 90).forEach { m ->
                                        BubbleChip("$m د", maxMinutes == m) { maxMinutes = m }
                                    }
                                }
                                Text("هامش أمان الميزانية", style = MaterialTheme.typography.titleMedium, color = Ink)
                                Text(
                                    "نحتفظ بنسبة $buffer% كاحتياط حتى لا تتجاوزي ميزانيتك.",
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Slider(
                                    value = buffer.toFloat(),
                                    onValueChange = { buffer = it.roundToInt().coerceIn(0, 30) },
                                    valueRange = 0f..30f,
                                    colors = glassSliderColors()
                                )
                            }

                            6 -> StepLayout(
                                pageOffset = pageOffset,
                                title = "مكونات تفضلين تقليلها؟",
                                subtitle = "اختياري، وهي تفضيلات مرنة وليست قيود حساسية.",
                                art = MealsArt.Basket,
                                satellites = listOf(
                                    Satellite(-0.42f, 0.30f, 0.20f, 0.25f, MealsIconName.Search),
                                    Satellite(0.44f, -0.28f, 0.20f, 0.65f, MealsIconName.Pantry)
                                )
                            ) {
                                MealsLiquidGlassTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    label = { Text("ابحثي عن مكوّن") }
                                )
                                val visible = remember(query, ingredients, dislikes) {
                                    val matches = ingredients.filter { ing ->
                                        query.isBlank() ||
                                            ing.nameAr.contains(query, true) ||
                                            ing.nameFr.contains(query, true) ||
                                            ing.aliases.any { it.contains(query, true) }
                                    }
                                    (matches.filter { it.id in dislikes } + matches.filter { it.id !in dislikes }).take(18)
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    visible.forEach { ing ->
                                        BubbleChip(ing.nameAr, ing.id in dislikes) {
                                            dislikes = if (ing.id in dislikes) dislikes - ing.id else dislikes + ing.id
                                        }
                                    }
                                }
                                if (dislikes.isNotEmpty()) {
                                    Text(
                                        "تم اختيار ${dislikes.size}",
                                        color = TextAccent,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            else -> StepLayout(
                                pageOffset = pageOffset,
                                title = "أين تتسوقين عادة؟",
                                subtitle = "تساعدنا على تنظيم قائمة التسوق. لا نستخدم موقعك تلقائياً.",
                                art = MealsArt.Market,
                                satellites = listOf(
                                    Satellite(-0.42f, -0.28f, 0.22f, 0.10f, MealsIconName.Basket),
                                    Satellite(0.44f, 0.30f, 0.18f, 0.55f, MealsIconName.Wallet)
                                )
                            ) {
                                MealsLiquidGlassTextField(
                                    value = wilaya,
                                    onValueChange = { wilaya = it.take(40) },
                                    label = { Text("الولاية") }
                                )
                                MealsLiquidGlassTextField(
                                    value = commune,
                                    onValueChange = { commune = it.take(60) },
                                    label = { Text("البلدية (اختياري)") }
                                )
                                Text("مكان التسوق المعتاد", style = MaterialTheme.typography.titleMedium, color = Ink)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "market" to "السوق", "grocery" to "بقالة", "supermarket" to "متجر كبير",
                                        "butcher" to "ملحمة", "poultry" to "دجاج", "fishmonger" to "سمك",
                                        "bakery" to "مخبزة", "other" to "أخرى"
                                    ).forEach { (id, label) ->
                                        BubbleChip(label, channel == id) { channel = id }
                                    }
                                }
                                MealsLiquidGlassTextField(
                                    value = shop,
                                    onValueChange = { shop = it.take(80) },
                                    label = { Text("اسم المحل المفضل (اختياري)") }
                                )
                            }
                        }
                    }

                    MealsPrimaryButton(
                        label = when (current) {
                            0 -> "هيا نبدأ"
                            LastPage -> "حفظ وابدأ"
                            else -> "التالي"
                        },
                        onClick = {
                            if (current < LastPage) {
                                goTo(current + 1)
                            } else {
                                val result = MealsOnboardingResult(
                                    weeklyBudgetDzd = budget,
                                    wilaya = wilaya.ifBlank { initial.wilaya },
                                    householdServings = servings,
                                    allergyIds = allergies,
                                    mealSlots = slots,
                                    dislikedIngredientIds = dislikes,
                                    dietPattern = diet,
                                    maxCookingMinutes = maxMinutes,
                                    budgetBufferPercent = buffer,
                                    commune = commune,
                                    shoppingChannel = channel,
                                    preferredShop = shop
                                )
                                pendingFinish = { onFinish(result) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp),
                        enabled = valid,
                        showArrow = current != LastPage
                    )
                }
            }
        }
        }
    }
}

// ─────────────────────────────── building blocks ───────────────────────────────

@Composable
private fun Header(current: Int, progress: Float, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp)) {
            if (current > 0) {
                // In a right-to-left layout "back" points to the right, which is the app's ArrowForward.
                MealsIconButton(onClick = onBack) {
                    MealsIcon(MealsIconName.ArrowForward, contentDescription = "رجوع", modifier = Modifier.size(26.dp))
                }
            }
        }
        MealsOnboardingProgress(
            current = current,
            progress = progress,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun MealsOnboardingProgress(
    current: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val active = (current + 1).coerceIn(1, PageCount)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Midati",
                color = TextAccent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${active.toString().padStart(2, '0')} / ${PageCount.toString().padStart(2, '0')}",
                color = MutedInk,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        MealsLiquidGlassProgressBar(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
            height = 18.dp,
            contentDescription = "التقدم في الإعداد"
        )
    }
}

/** Hero illustration in a glass bubble on top, title and a glass panel below. */
@Composable
private fun StepLayout(
    pageOffset: () -> Float,
    title: String,
    subtitle: String,
    art: MealsArt,
    satellites: List<Satellite>,
    artCount: Int = 3,
    panel: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelMax = maxHeight * 0.52f
        Column(
            Modifier.fillMaxSize().graphicsLayer {
                // Follow pager motion only at the layer level. A little directional
                // parallax, soft fade and restrained scale make each page feel
                // buoyant without dimming it out or shifting the form excessively.
                val offset = pageOffset().coerceIn(-1f, 1f)
                val away = abs(offset)
                alpha = 1f - away * 0.16f
                translationX = -offset * 12.dp.toPx()
                translationY = away * 10.dp.toPx()
                val pageScale = lerp(1f, 0.975f, away)
                scaleX = pageScale
                scaleY = pageScale
            }
        ) {
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val heroSize = min(maxWidth.value, maxHeight.value).dp
                Box(
                    Modifier.size(heroSize).graphicsLayer {
                        val away = abs(pageOffset()).coerceIn(0f, 1f)
                        val s = lerp(1f, 0.94f, away)
                        scaleX = s
                        scaleY = s
                    },
                    contentAlignment = Alignment.Center
                ) {
                    MealsGlassBubble(size = heroSize * 0.9f, floatDistance = 6.dp) {
                        MealsIllustration(art, size = heroSize * 0.62f, count = artCount)
                    }
                    satellites.forEach { s ->
                        MealsGlassBubble(
                            size = heroSize * s.size,
                            modifier = Modifier.align(Alignment.Center).offset(x = heroSize * s.dx, y = heroSize * s.dy),
                            floatDistance = 5.dp,
                            phase = s.phase
                        ) {
                            val inner = heroSize * s.size * 0.62f
                            when {
                                s.art != null -> MealsIllustration(s.art, size = inner * 1.2f)
                                s.icon != null -> MealsIcon(s.icon, contentDescription = null, modifier = Modifier.size(inner))
                            }
                        }
                    }
                }
            }
            Text(
                title,
                modifier = Modifier.fillMaxWidth(),
                color = Ink,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                color = MutedInk,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            MealsGlassSurface(
                modifier = Modifier.fillMaxWidth().heightIn(max = panelMax),
                contentPadding = 16.dp
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = panel
                )
            }
        }
    }
}

@Composable
private fun InfoRow(icon: MealsIconName, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MealsGlassBubble(size = 44.dp, floatDistance = 0.dp) {
            MealsIcon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleSmall)
            Text(text, color = MutedInk, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    MealsIconButton(onClick = onClick, size = 52.dp) {
        Text(symbol, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun glassSliderColors() = SliderDefaults.colors(
    thumbColor = TextAccent,
    activeTrackColor = TextAccent,
    inactiveTrackColor = SoftOrange
)

/** A light glass pill used for every single- or multi-select choice. */
@Composable
private fun BubbleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val face = if (selected) {
        Brush.verticalGradient(listOf(Color(0xFFFFE3CC), Color(0xFFFFD2B0)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.66f), Color.White.copy(alpha = 0.36f)))
    }
    val rim = if (selected) {
        Brush.verticalGradient(listOf(TextAccent.copy(alpha = 0.75f), TextAccent.copy(alpha = 0.42f)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.98f), Color.White.copy(alpha = 0.5f)))
    }
    MealsPressable(onClick = onClick, shape = CircleShape) {
        Row(
            modifier = Modifier
                .background(face, CircleShape)
                .border(1.2.dp, rim, CircleShape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selected) MealsIcon(MealsIconName.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, color = Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SlotTile(art: MealsArt, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    MealsPressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape) {
        MealsCard(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, if (selected) TextAccent.copy(alpha = 0.55f) else Color.Transparent, shape),
            containerColor = if (selected) SoftOrange else Paper,
            contentPadding = 0.dp,
            shape = shape,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MealsGlassBubble(size = 58.dp, floatDistance = 0.dp) {
                    MealsIllustration(art, size = 40.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, color = MutedInk, style = MaterialTheme.typography.bodySmall)
                }
                MealsGlassBubble(size = 30.dp, floatDistance = 0.dp, tint = if (selected) Leaf else Color(0xFFFFF8F1)) {
                    if (selected) MealsIcon(MealsIconName.Check, contentDescription = "محدد", modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** Short celebratory scene shown while the first plan is being prepared. */
@Composable
private fun Finale() {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val heroSize = min(maxWidth.value, 340f).dp
            Box(Modifier.size(heroSize), contentAlignment = Alignment.Center) {
                MealsGlassBubble(size = heroSize * 0.86f, floatDistance = 6.dp) {
                    MealsIllustration(MealsArt.Week, size = heroSize * 0.58f)
                }
                MealsGlassBubble(
                    size = heroSize * 0.26f,
                    modifier = Modifier.align(Alignment.Center).offset(x = heroSize * -0.40f, y = heroSize * -0.30f),
                    phase = 0.2f, floatDistance = 5.dp
                ) { MealsIllustration(MealsArt.Breakfast, size = heroSize * 0.2f) }
                MealsGlassBubble(
                    size = heroSize * 0.26f,
                    modifier = Modifier.align(Alignment.Center).offset(x = heroSize * 0.42f, y = heroSize * 0.02f),
                    phase = 0.55f, floatDistance = 5.dp
                ) { MealsIllustration(MealsArt.Lunch, size = heroSize * 0.2f) }
                MealsGlassBubble(
                    size = heroSize * 0.22f,
                    modifier = Modifier.align(Alignment.Center).offset(x = heroSize * -0.34f, y = heroSize * 0.36f),
                    phase = 0.8f, floatDistance = 5.dp
                ) { MealsIllustration(MealsArt.Dinner, size = heroSize * 0.17f) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "نجهّز أسبوعك…",
            color = Ink,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        MealsGlassLoadingDots()
        Spacer(Modifier.height(10.dp))
        Text(
            "نختار وصفات تناسب ميزانيتك وتفضيلاتك.",
            color = MutedInk,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MealsGlassLoadingDots() {
    val motionClock = LocalMealsOnboardingMotionClock.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        // Three complete waves per shared 12-second clock keep
                        // every dot's position identical when the clock loops.
                        val angle = (motionClock.value * 3f + index * 0.25f) * 6.2831855f
                        val wave = sin(angle)
                        val pulse = (cos(angle) + 1f) * 0.5f
                        translationY = -wave * 5.dp.toPx()
                        translationX = cos(angle) * 1.2.dp.toPx()
                        val scale = 0.82f + pulse * 0.42f
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.72f + pulse * 0.28f
                    },
                contentAlignment = Alignment.Center
            ) {
                MealsGlassDot(selected = false)
            }
        }
    }
}