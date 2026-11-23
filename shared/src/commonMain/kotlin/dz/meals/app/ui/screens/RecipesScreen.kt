package dz.meals.app.ui.screens

import dz.meals.app.ui.components.MealsText as Text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dz.meals.app.data.IngredientMaster
import dz.meals.app.data.MealRecipe
import dz.meals.app.ui.components.MealsBottomSheet
import dz.meals.app.ui.components.MealsCard
import dz.meals.app.ui.components.MealsFilterChips
import dz.meals.app.ui.components.MealsIconAction
import dz.meals.app.ui.components.MealsLiquidGlassProgressBar
import dz.meals.app.ui.components.MealsPrimaryButton
import dz.meals.app.ui.components.MealsSearchBar
import dz.meals.app.ui.components.MealsSecondaryButton
import dz.meals.app.ui.components.MealsSubtabsIndicator
import dz.meals.app.ui.components.MealsTextButton
import dz.meals.app.ui.components.mealsSheetDismissAction
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.ui.theme.Border
import dz.meals.app.ui.theme.Ink
import dz.meals.app.ui.theme.MutedInk
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen
import dz.meals.app.ui.theme.WarmSurface
import kotlin.math.abs
import kotlin.math.roundToLong
private val recipeCategories = listOf("الكل", "اقتصادي", "سريع", "تقليدي", "عائلي")

@Composable
fun RecipesScreen(
    recipes: List<MealRecipe>,
    ingredients: List<IngredientMaster>,
    allergyIds: Set<String>,
    favoriteRecipeIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onAddRecipe: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("الكل") }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var selectedRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var cookingRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var cookingStepIndex by rememberSaveable { mutableStateOf(0) }
    var cookingServings by rememberSaveable { mutableStateOf(2) }
    var cookingCompleted by rememberSaveable { mutableStateOf(false) }
    var cookingStepDirection by rememberSaveable { mutableStateOf(1) }
    var browseDirection by rememberSaveable { mutableStateOf(1) }
    val ingredientNames = ingredients.associateBy { it.id }

    fun selectCategory(nextCategory: String) {
        if (nextCategory != category) {
            browseDirection = if (
                recipeCategories.indexOf(nextCategory) >= recipeCategories.indexOf(category)
            ) 1 else -1
            category = nextCategory
        }
    }

    fun selectFavorites(nextFavorites: Boolean) {
        if (nextFavorites != showFavorites) {
            browseDirection = if (nextFavorites) 1 else -1
            showFavorites = nextFavorites
        }
    }

    val browsePage = showFavorites to category

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("وصفات من مطبخنا", style = MaterialTheme.typography.headlineMedium)
            Text("ابحث بالعربية أو الفرنسية أو بأسماء المكونات المحلية.", color = MutedInk, style = MaterialTheme.typography.bodyMedium)
            MealsSearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = "ابحث عن وصفة أو مكوّن...",
                modifier = Modifier.fillMaxWidth()
            )
            MealsSubtabsIndicator(
                items = listOf("كل الوصفات", "المفضلة"),
                selected = if (showFavorites) "المفضلة" else "كل الوصفات",
                onSelect = { selectFavorites(it == "المفضلة") }
            )
            MealsFilterChips(
                items = recipeCategories,
                selected = category,
                onSelect = { selectCategory(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        AnimatedContent(
            targetState = browsePage,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(showFavorites, category) {
                    var dragDistance = 0f
                    val swipeThreshold = 72.dp.toPx()
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            dragDistance += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            if (abs(dragDistance) >= swipeThreshold) {
                                when {
                                    showFavorites && dragDistance > 0f -> selectFavorites(false)
                                    !showFavorites -> {
                                        val currentIndex = recipeCategories.indexOf(category)
                                        val nextIndex = currentIndex + if (dragDistance < 0f) 1 else -1
                                        when {
                                            nextIndex in recipeCategories.indices ->
                                                selectCategory(recipeCategories[nextIndex])
                                            dragDistance < 0f &&
                                                currentIndex == recipeCategories.lastIndex ->
                                                selectFavorites(true)
                                        }
                                    }
                                }
                            }
                            dragDistance = 0f
                        },
                        onDragCancel = { dragDistance = 0f }
                    )
                },
            transitionSpec = {
                val movingForward = browseDirection > 0
                val arrivalSpring = spring<IntOffset>(
                    dampingRatio = 0.82f,
                    stiffness = 390f
                )
                (slideInHorizontally(
                    animationSpec = arrivalSpring,
                    initialOffsetX = { width ->
                        if (movingForward) width / 5 else -width / 5
                    }
                ) + fadeIn(animationSpec = tween(155, delayMillis = 18)) +
                    scaleIn(
                        initialScale = 0.975f,
                        animationSpec = spring(dampingRatio = 0.80f, stiffness = 430f)
                    )) togetherWith
                    (slideOutHorizontally(
                        animationSpec = spring(dampingRatio = 0.94f, stiffness = 560f),
                        targetOffsetX = { width ->
                            if (movingForward) -width / 12 else width / 12
                        }
                    ) + fadeOut(animationSpec = tween(125)) +
                        scaleOut(
                            targetScale = 0.989f,
                            animationSpec = tween(150, easing = FastOutSlowInEasing)
                        ))
            },
            contentAlignment = Alignment.TopStart,
            label = "recipeBrowseSwipe"
        ) { page ->
            key(page) {
                val listState = rememberLazyListState()
                val filtered = recipes.filter { recipe ->
                    val aliases = recipe.ingredients.flatMap {
                        ingredientNames[it.ingredientId]?.aliases.orEmpty()
                    }
                    recipe.allergens.intersect(allergyIds).isEmpty() &&
                        (!page.first || recipe.id in favoriteRecipeIds) &&
                        (page.second == "الكل" || recipe.category == page.second) &&
                        (query.isBlank() || recipe.nameAr.contains(query.trim(), true) ||
                            recipe.nameFr.contains(query.trim(), true) ||
                            recipe.descriptionAr.contains(query.trim(), true) ||
                            recipe.descriptionFr.contains(query.trim(), true) ||
                            aliases.any { it.contains(query.trim(), true) })
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 0.dp,
                        bottom = 122.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { recipe ->
                        MealsCard(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { selectedRecipeId = recipe.id }
                            ),
                            containerColor = Paper,
                            borderColor = Border,
                            contentPadding = 14.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(WarmSurface, RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MealsIcon(
                                            MealsIconName.Meal,
                                            contentDescription = null,
                                            modifier = Modifier.size(33.dp)
                                        )
                                    }
                                    Text(
                                        text = recipe.nameAr,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Ink
                                    )
                                    MealsIconAction(onClick = { onToggleFavorite(recipe.id) }) {
                                        MealsIcon(
                                            name = if (recipe.id in favoriteRecipeIds) MealsIconName.Star else MealsIconName.StarOutline,
                                            contentDescription = if (recipe.id in favoriteRecipeIds) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MealsIcon(
                                        MealsIconName.Clock,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        recipe.durationMinutes.toString() + " د",
                                        color = MutedInk,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        recipe.baseServings.toString() + " حصص",
                                        color = MutedInk,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        recipe.category,
                                        color = dz.meals.app.ui.theme.TextAccent,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MealsIconAction(onClick = { selectedRecipeId = recipe.id }) {
                                        MealsIcon(
                                            MealsIconName.Info,
                                            contentDescription = "تفاصيل الوصفة",
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    MealsIconAction(onClick = { onAddRecipe(recipe.id) }) {
                                        MealsIcon(
                                            MealsIconName.Add,
                                            contentDescription = "أضف إلى الأسبوع",
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                if (page.first && favoriteRecipeIds.isEmpty()) "لم تحفظ وصفات في المفضلة بعد."
                                else if (recipes.any { it.allergens.intersect(allergyIds).isNotEmpty() }) "لا توجد وصفات مطابقة. قد تكون قيود الحساسية تستبعد بعض النتائج."
                                else "لا توجد وصفات مطابقة لبحثك.",
                                color = MutedInk,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }
            }
        }
    }

    val detail = selectedRecipeId?.let { id -> recipes.firstOrNull { it.id == id } }
    if (detail != null) {
        MealsBottomSheet(
            onDismissRequest = { selectedRecipeId = null },
            title = { Text(detail.nameAr) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text(detail.descriptionAr, color = MutedInk) }
                    item { Text("المكونات", fontWeight = FontWeight.Bold) }
                    items(detail.ingredients) { ingredient ->
                        val master = ingredientNames[ingredient.ingredientId]
                        Text(
                            "• " + (master?.nameAr ?: ingredient.ingredientId) +
                                " — " + ingredient.quantityBase.toString() + " " + ingredient.unit
                        )
                    }
                    item { Text("طريقة التحضير", fontWeight = FontWeight.Bold) }
                    items(detail.stepsAr) { step ->
                        Text((detail.stepsAr.indexOf(step) + 1).toString() + ". " + step)
                    }
                    item { Text("الحساسية: " + if (detail.allergens.isEmpty()) "لا توجد مسببات مسجلة" else detail.allergens.joinToString("، ")) }
                }
            },
            confirmButton = {
                val dismissSheet = mealsSheetDismissAction()
                MealsPrimaryButton(
                    label = "أضف للخطة",
                    onClick = { dismissSheet { onAddRecipe(detail.id); selectedRecipeId = null } },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                val dismissSheet = mealsSheetDismissAction()
                MealsSecondaryButton(
                    label = "ابدأ الطبخ",
                    onClick = {
                        dismissSheet {
                            cookingRecipeId = detail.id
                            cookingStepIndex = 0
                            cookingStepDirection = 1
                            cookingServings = detail.baseServings
                            cookingCompleted = false
                            selectedRecipeId = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    val cookingRecipe = cookingRecipeId?.let { id -> recipes.firstOrNull { it.id == id } }
    if (cookingRecipe != null) {
        val stepCount = cookingRecipe.stepsAr.size
        val safeStepIndex = cookingStepIndex.coerceIn(0, (stepCount - 1).coerceAtLeast(0))

        MealsBottomSheet(
            onDismissRequest = {
                cookingRecipeId = null
                cookingCompleted = false
            },
            title = { Text(cookingRecipe.nameAr) },
            subtitle = if (cookingCompleted) {
                "اكتمل التحضير"
            } else if (stepCount > 0) {
                "اسحبي للتنقل بين الخطوات"
            } else {
                "وضع الطبخ"
            },
            fixedHeightFraction = 0.88f,
            fillBody = true,
            text = {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Kept outside swipe transitions and the step list: this header
                    // stays anchored while the cooking content moves or scrolls.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (cookingCompleted) "اكتمل التحضير" else "التقدم في الوصفة",
                                style = MaterialTheme.typography.labelLarge,
                                color = MutedInk
                            )
                            Text(
                                when {
                                    cookingCompleted -> "$stepCount / $stepCount"
                                    stepCount == 0 -> "لا توجد خطوات"
                                    else -> "${safeStepIndex + 1} / $stepCount"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = dz.meals.app.ui.theme.TextAccent
                            )
                        }
                        MealsLiquidGlassProgressBar(
                            progress = when {
                                cookingCompleted -> 1f
                                stepCount == 0 -> 0f
                                else -> (safeStepIndex + 1).toFloat() / stepCount.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            height = 24.dp,
                            contentDescription = "التقدم في خطوات الوصفة"
                        )
                    }

                    AnimatedContent(
                    targetState = cookingCompleted,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(cookingCompleted, cookingStepIndex, stepCount) {
                            var dragDistance = 0f
                            val swipeThreshold = 72.dp.toPx()
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    dragDistance += dragAmount
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (!cookingCompleted && stepCount > 0 &&
                                        abs(dragDistance) >= swipeThreshold
                                    ) {
                                        when {
                                            dragDistance < 0f && safeStepIndex < stepCount - 1 -> {
                                                cookingStepDirection = 1
                                                cookingStepIndex = safeStepIndex + 1
                                            }
                                            dragDistance < 0f && safeStepIndex == stepCount - 1 -> {
                                                cookingCompleted = true
                                            }
                                            dragDistance > 0f && safeStepIndex > 0 -> {
                                                cookingStepDirection = -1
                                                cookingStepIndex = safeStepIndex - 1
                                            }
                                        }
                                    }
                                    dragDistance = 0f
                                },
                                onDragCancel = { dragDistance = 0f }
                            )
                        },
                    transitionSpec = {
                        val enteringSpring = spring<IntOffset>(
                            dampingRatio = 0.84f,
                            stiffness = 360f
                        )
                        (slideInVertically(
                            animationSpec = enteringSpring,
                            initialOffsetY = { height ->
                                if (targetState) height / 10 else -height / 12
                            }
                        ) + fadeIn(tween(185, delayMillis = 12)) +
                            scaleIn(
                                initialScale = 0.975f,
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 390f)
                            )) togetherWith
                            (slideOutVertically(
                                animationSpec = spring(dampingRatio = 0.94f, stiffness = 520f),
                                targetOffsetY = { height ->
                                    if (targetState) -height / 14 else height / 10
                                }
                            ) + fadeOut(tween(135)) +
                                scaleOut(
                                    targetScale = 0.99f,
                                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                                ))
                    },
                    label = "cookingCompletionTransition"
                ) { completed ->
                    if (completed) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(116.dp)
                                    .background(SoftGreen, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                MealsIcon(
                                    MealsIconName.Check,
                                    contentDescription = "اكتملت خطوات الطبخ",
                                    modifier = Modifier.size(78.dp)
                                )
                            }
                            Spacer(Modifier.height(22.dp))
                            Text(
                                "أحسنتِ!",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Ink
                            )
                            Text(
                                "أصبحت وجبتك جاهزة",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = dz.meals.app.ui.theme.TextAccent
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                cookingRecipe.nameAr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink
                            )
                            Text(
                                "أكملتِ جميع خطوات التحضير بنجاح. حان وقت الاستمتاع بوجبتك!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedInk
                            )
                            Spacer(Modifier.height(20.dp))
                            Text(
                                "اكتملت جميع الخطوات · $stepCount من $stepCount",
                                style = MaterialTheme.typography.labelMedium,
                                color = MutedInk
                            )
                        }
                    } else {
                        AnimatedContent(
                            targetState = safeStepIndex,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                val movingForward = cookingStepDirection > 0
                                val incomingSpring = spring<IntOffset>(
                                    dampingRatio = 0.80f,
                                    stiffness = 410f
                                )
                                (slideInHorizontally(
                                    animationSpec = incomingSpring,
                                    initialOffsetX = { width ->
                                        if (movingForward) width / 5 else -width / 5
                                    }
                                ) + fadeIn(tween(150, delayMillis = 22)) +
                                    scaleIn(
                                        initialScale = 0.972f,
                                        animationSpec = spring(
                                            dampingRatio = 0.78f,
                                            stiffness = 440f
                                        )
                                    )) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = 0.92f,
                                            stiffness = 540f
                                        ),
                                        targetOffsetX = { width ->
                                            if (movingForward) -width / 12 else width / 12
                                        }
                                    ) + fadeOut(tween(125)) +
                                        scaleOut(
                                            targetScale = 0.988f,
                                            animationSpec = tween(
                                                145,
                                                easing = FastOutSlowInEasing
                                            )
                                        ))
                            },
                            label = "cookingStepSlide"
                        ) { visibleStepIndex ->
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(15.dp)
                            ) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            if (stepCount == 0) "لا توجد خطوات مسجلة بعد"
                                            else "الخطوة ${visibleStepIndex + 1}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = dz.meals.app.ui.theme.TextAccent
                                        )
                                        Text(
                                            cookingRecipe.stepsAr.getOrNull(visibleStepIndex)
                                                ?: "يمكنك مراجعة المكونات أو إغلاق وضع الطبخ.",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Ink
                                        )
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "عدد الحصص",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Ink
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            MealsTextButton(
                                                onClick = {
                                                    cookingServings = (cookingServings - 1).coerceAtLeast(1)
                                                },
                                                enabled = cookingServings > 1
                                            ) { Text("−") }
                                            dz.meals.app.ui.components.MealsAnimatedCount(
                                                value = cookingServings.toString(),
                                                color = Ink,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            MealsTextButton(
                                                onClick = {
                                                    cookingServings = (cookingServings + 1).coerceAtMost(12)
                                                },
                                                enabled = cookingServings < 12
                                            ) { Text("+") }
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        "المكونات لهذه الحصص",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Ink
                                    )
                                }
                                items(cookingRecipe.ingredients) { ingredient ->
                                    val master = ingredientNames[ingredient.ingredientId]
                                    val amount = ingredient.quantityBase * cookingServings.toDouble() /
                                        cookingRecipe.baseServings.coerceAtLeast(1).toDouble()
                                    val unitLabel = when (ingredient.unit) {
                                        "g" -> "غ"
                                        "ml" -> "مل"
                                        "piece" -> "حبة"
                                        else -> ingredient.unit
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            master?.nameAr ?: ingredient.ingredientId,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Ink
                                        )
                                        Text(
                                            amount.prettyQuantity() + " " + unitLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MutedInk
                                        )
                                    }
                                }

                                if (cookingRecipe.stepsFr.getOrNull(visibleStepIndex).orEmpty().isNotBlank()) {
                                    item {
                                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text(
                                                "بالفرنسية",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MutedInk
                                            )
                                            Text(
                                                cookingRecipe.stepsFr[visibleStepIndex],
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MutedInk
                                            )
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(8.dp)) }
                            }
                        }
                    }
                    }
                }
            },
            confirmButton = {
                if (cookingCompleted) {
                    val dismissSheet = mealsSheetDismissAction()
                    MealsPrimaryButton(
                        label = "إنهاء",
                        onClick = {
                            dismissSheet {
                                cookingRecipeId = null
                                cookingCompleted = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val dismissSheet = mealsSheetDismissAction()
                    MealsPrimaryButton(
                        label = when {
                            stepCount == 0 -> "إغلاق"
                            safeStepIndex < stepCount - 1 -> "التالي"
                            else -> "إنهاء الطبخ"
                        },
                        onClick = {
                            when {
                                stepCount == 0 -> dismissSheet {
                                    cookingRecipeId = null
                                    cookingCompleted = false
                                }
                                safeStepIndex < stepCount - 1 -> {
                                    cookingStepDirection = 1
                                    cookingStepIndex = safeStepIndex + 1
                                }
                                else -> cookingCompleted = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = if (cookingCompleted) {
                {
                    MealsSecondaryButton(
                        label = "مراجعة الخطوات",
                        onClick = {
                            cookingCompleted = false
                            cookingStepDirection = -1
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (stepCount > 0) {
                {
                    MealsSecondaryButton(
                        label = "السابق",
                        onClick = {
                            cookingStepDirection = -1
                            cookingStepIndex = (safeStepIndex - 1).coerceAtLeast(0)
                        },
                        enabled = safeStepIndex > 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                null
            }
        )
    }
}

private fun Double.prettyQuantity(): String {
    if (this % 1.0 == 0.0) return toLong().toString()
    val roundedTenths = (this * 10.0).roundToLong()
    val whole = roundedTenths / 10
    val fraction = kotlin.math.abs(roundedTenths % 10)
    return "$whole.$fraction"
}
