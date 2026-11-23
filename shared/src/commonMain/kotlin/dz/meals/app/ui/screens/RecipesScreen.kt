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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dz.meals.app.data.IngredientMaster
import dz.meals.app.data.MealRecipe
import dz.meals.app.data.RecipeImageCandidate
import dz.meals.app.data.scaleRecipeIngredientQuantity
import coil3.compose.AsyncImage
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
import dz.meals.app.ui.theme.isLogicalForwardSwipe
import dz.meals.app.ui.theme.isMealsEnglish
import dz.meals.app.ui.theme.bidiIsolate
import dz.meals.app.ui.theme.logicalAnimationDirectionSign
import dz.meals.app.ui.theme.mealsUnitLabel
import dz.meals.app.ui.theme.mealsRecipeIngredientQuantity
import dz.meals.app.shared.format.formatHouseholdQuantity
import dz.meals.app.ui.theme.Paper
import dz.meals.app.ui.theme.SoftGreen
import dz.meals.app.ui.theme.WarmSurface
import dz.meals.app.ui.theme.LocalMealsLocaleTag
import dz.meals.app.ui.theme.mealsText
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
private val recipeCategories = listOf("الكل", "اقتصادي", "سريع", "تقليدي", "عائلي")


/**
 * Route public recipe photos through a resizing/compression cache. This prevents
 * the client from requesting original-resolution recipe images directly.
 */
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

private val unitoolsIngredientFallbackNames = mapOf(
    "ackee" to ("فاكهة الأكي" to "Ackee"),
    "oil" to ("زيت" to "Oil"),
    "onion" to ("بصل" to "Onion"),
    "pepper" to ("فلفل" to "Pepper"),
    "saltfish" to ("سمك مملح" to "Saltfish"),
    "scotchbonnet" to ("فلفل سكوتش بونيت" to "Scotch bonnet pepper"),
    "springonion" to ("بصل أخضر" to "Spring onion"),
    "thyme" to ("زعتر" to "Thyme"),
    "tomato" to ("طماطم" to "Tomato")
)

private fun recipeIngredientDisplayName(
    master: IngredientMaster?,
    ingredientId: String,
    localeTag: String
): String {
    val english = isMealsEnglish(localeTag)
    val translatedName = if (english) {
        master?.nameFr?.takeIf { it.isNotBlank() } ?: master?.nameAr?.takeIf { it.isNotBlank() }
    } else {
        master?.nameAr?.takeIf { it.isNotBlank() } ?: master?.nameFr?.takeIf { it.isNotBlank() }
    }
    if (translatedName != null) return translatedName

    val sourceId = ingredientId.substringAfter("unitools-", "").substringAfterLast('/')
    val knownNames = unitoolsIngredientFallbackNames[sourceId]
    return when {
        knownNames != null && english -> knownNames.second
        knownNames != null -> knownNames.first
        english -> "Ingredient"
        else -> "مكوّن"
    }
}

@Composable
fun RecipesScreen(
    recipes: List<MealRecipe>,
    ingredients: List<IngredientMaster>,
    allergyIds: Set<String>,
    favoriteRecipeIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onAddRecipe: (String) -> Unit,
    onSearchRecipeImages: (suspend (MealRecipe, String) -> List<RecipeImageCandidate>)? = null,
    onSaveRecipeImage: ((String, RecipeImageCandidate) -> Unit)? = null,
    onAutoLoadRecipeImage: (suspend (MealRecipe) -> Unit)? = null,
    onOpenRecipeCorpusTester: (() -> Unit)? = null,
    catalogueProgress: Float? = null,
    catalogueStatusMessage: String? = null,
    catalogueError: String? = null,
    onRetryCatalogue: (() -> Unit)? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("الكل") }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var selectedRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var imageSearchOpen by rememberSaveable { mutableStateOf(false) }
    var imageSearchQuery by rememberSaveable { mutableStateOf("") }
    var imageSearchResults by remember { mutableStateOf<List<RecipeImageCandidate>>(emptyList()) }
    var imageSearchLoading by remember { mutableStateOf(false) }
    var imageSearchError by remember { mutableStateOf<String?>(null) }
    var automaticImageLoadingRecipeId by remember { mutableStateOf<String?>(null) }
    var automaticImageError by remember { mutableStateOf<String?>(null) }
    var automaticImageErrorRecipeId by remember { mutableStateOf<String?>(null) }
    var automaticImageRetryNonce by remember { mutableStateOf(0) }
    val attemptedAutomaticImageRecipeIds = remember { mutableSetOf<String>() }
    val imageSearchScope = rememberCoroutineScope()
    var isCookingMode by rememberSaveable { mutableStateOf(false) }
    var cookingStepIndex by rememberSaveable { mutableStateOf(0) }
    var recipeServings by rememberSaveable { mutableStateOf(2) }
    var cookingServings by rememberSaveable { mutableStateOf(2) }
    var cookingCompleted by rememberSaveable { mutableStateOf(false) }
    var cookingStepDirection by rememberSaveable { mutableStateOf(1) }
    var browseDirection by rememberSaveable { mutableStateOf(1) }
    val ingredientNames = remember(ingredients) { ingredients.associateBy { it.id } }
    val layoutDirection = LocalLayoutDirection.current
    val appLocaleTag = LocalMealsLocaleTag.current
    val uriHandler = LocalUriHandler.current
    val availableCategories = remember(recipes) {
        (recipeCategories + recipes.map { it.category.trim() }.filter { it.isNotBlank() })
            .distinct()
            .take(14)
    }

    fun selectCategory(nextCategory: String) {
        if (nextCategory != category) {
            browseDirection = if (
                availableCategories.indexOf(nextCategory) >= availableCategories.indexOf(category)
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

    LaunchedEffect(selectedRecipeId) {
        imageSearchOpen = false
        isCookingMode = false
        recipeServings = recipes.firstOrNull { it.id == selectedRecipeId }?.baseServings?.coerceIn(1, 12) ?: 2
        cookingCompleted = false
        cookingStepIndex = 0
        imageSearchQuery = ""
        imageSearchResults = emptyList()
        imageSearchError = null
        imageSearchLoading = false
        automaticImageLoadingRecipeId = null
        automaticImageError = null
        automaticImageErrorRecipeId = null
    }

    fun openManualImageSearch(recipe: MealRecipe) {
        imageSearchQuery = recipe.nameFr.takeIf {
            it.isNotBlank() && it != recipe.nameAr
        } ?: recipe.nativeName.ifBlank { recipe.nameAr }
        imageSearchResults = emptyList()
        imageSearchError = null
        imageSearchOpen = true
    }

    fun searchRecipeImages(recipe: MealRecipe) {
        val search = onSearchRecipeImages ?: return
        val term = imageSearchQuery.trim()
        if (term.isBlank() || imageSearchLoading) return
        imageSearchLoading = true
        imageSearchError = null
        imageSearchResults = emptyList()
        imageSearchScope.launch {
            try {
                imageSearchResults = search(recipe, term)
                if (imageSearchResults.isEmpty()) {
                    imageSearchError = "لم نعثر على صور مرخّصة تطابق هذا البحث. جرّب اسماً آخر."
                }
            } catch (error: Exception) {
                imageSearchError = error.message ?: "تعذر البحث عن الصور حالياً."
            } finally {
                imageSearchLoading = false
            }
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

            if (catalogueProgress != null && catalogueStatusMessage != null) {
                MealsCard(
                    containerColor = WarmSurface,
                    borderColor = null,
                    contentPadding = 14.dp
                ) {
                    Text(
                        catalogueStatusMessage,
                        color = Ink,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    MealsLiquidGlassProgressBar(
                        progress = catalogueProgress,
                        height = 19.dp,
                        contentDescription = "تنزيل مكتبة الوصفات"
                    )
                    Text(
                        (catalogueProgress * 100f).toInt().coerceIn(0, 100).toString() + "%",
                        color = MutedInk,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            catalogueError?.let { error ->
                MealsCard(
                    containerColor = WarmSurface,
                    borderColor = null,
                    contentPadding = 14.dp
                ) {
                    Text(
                        "تعذر تحميل مكتبة الوصفات",
                        color = Ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(error, color = MutedInk, style = MaterialTheme.typography.bodySmall)
                    onRetryCatalogue?.let { retry ->
                        MealsSecondaryButton(
                            label = "إعادة المحاولة",
                            onClick = retry,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            onOpenRecipeCorpusTester?.let { openTester ->
                MealsSecondaryButton(
                    label = "اختبار الترجمة والبيانات",
                    onClick = openTester,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
                items = availableCategories,
                selected = category,
                onSelect = { selectCategory(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        AnimatedContent(
            targetState = browsePage,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(showFavorites, category, layoutDirection) {
                    var dragDistance = 0f
                    val swipeThreshold = 72.dp.toPx()
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            dragDistance += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            if (abs(dragDistance) >= swipeThreshold) {
                                val movingForward = isLogicalForwardSwipe(dragDistance, layoutDirection)
                                when {
                                    showFavorites && !movingForward -> selectFavorites(false)
                                    !showFavorites -> {
                                        val currentIndex = availableCategories.indexOf(category)
                                        val nextIndex = currentIndex + if (movingForward) 1 else -1
                                        when {
                                            nextIndex in availableCategories.indices ->
                                                selectCategory(availableCategories[nextIndex])
                                            movingForward && currentIndex == availableCategories.lastIndex ->
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
                val directionSign = logicalAnimationDirectionSign(layoutDirection)
                val arrivalSpring = spring<IntOffset>(
                    dampingRatio = 0.82f,
                    stiffness = 390f
                )
                (slideInHorizontally(
                    animationSpec = arrivalSpring,
                    initialOffsetX = { width ->
                        (if (movingForward) width / 5 else -width / 5) * directionSign
                    }
                ) + fadeIn(animationSpec = tween(155, delayMillis = 18)) +
                    scaleIn(
                        initialScale = 0.975f,
                        animationSpec = spring(dampingRatio = 0.80f, stiffness = 430f)
                    )) togetherWith
                    (slideOutHorizontally(
                        animationSpec = spring(dampingRatio = 0.94f, stiffness = 560f),
                        targetOffsetX = { width ->
                            (if (movingForward) -width / 12 else width / 12) * directionSign
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
                val filtered = remember(
                    recipes,
                    allergyIds,
                    page,
                    favoriteRecipeIds,
                    query,
                    appLocaleTag,
                    ingredientNames
                ) {
                    recipes.filter { recipe ->
                        val ingredientMasters = recipe.ingredients.mapNotNull {
                            ingredientNames[it.ingredientId]
                        }
                        val searchableIngredientNames = ingredientMasters.flatMap { master ->
                            listOf(master.nameAr, master.nameFr, mealsText(master.nameAr, appLocaleTag)) +
                                master.aliases
                        }
                        recipe.allergens.intersect(allergyIds).isEmpty() &&
                            (!page.first || recipe.id in favoriteRecipeIds) &&
                            (page.second == "الكل" || recipe.category == page.second) &&
                            (query.isBlank() || recipe.nameAr.contains(query.trim(), true) ||
                                mealsText(recipe.nameAr, appLocaleTag).contains(query.trim(), true) ||
                                recipe.nameFr.contains(query.trim(), true) ||
                                recipe.descriptionAr.contains(query.trim(), true) ||
                                mealsText(recipe.descriptionAr, appLocaleTag).contains(query.trim(), true) ||
                                recipe.descriptionFr.contains(query.trim(), true) ||
                                searchableIngredientNames.any { it.contains(query.trim(), true) } ||
                                recipe.tags.any { it.contains(query.trim(), true) })
                    }
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

    LaunchedEffect(detail?.id, detail?.imageUrl, automaticImageRetryNonce) {
        val recipe = detail ?: return@LaunchedEffect
        val autoLoad = onAutoLoadRecipeImage ?: return@LaunchedEffect
        if (recipe.imageUrl.startsWith("https://", ignoreCase = true)) return@LaunchedEffect
        if (!attemptedAutomaticImageRecipeIds.add(recipe.id)) return@LaunchedEffect

        automaticImageLoadingRecipeId = recipe.id
        automaticImageError = null
        automaticImageErrorRecipeId = null
        try {
            autoLoad(recipe)
        } catch (cancelled: CancellationException) {
            attemptedAutomaticImageRecipeIds.remove(recipe.id)
            throw cancelled
        } catch (error: Exception) {
            attemptedAutomaticImageRecipeIds.remove(recipe.id)
            automaticImageError = error.message ?: "تعذر العثور على صورة مناسبة حالياً."
            automaticImageErrorRecipeId = recipe.id
        } finally {
            if (automaticImageLoadingRecipeId == recipe.id) {
                automaticImageLoadingRecipeId = null
            }
        }
    }

    if (detail != null) {
        MealsBottomSheet(
            onDismissRequest = { selectedRecipeId = null },
            title = { Text(if (imageSearchOpen) "اختيار صورة للوصفة" else detail.nameAr) },
            subtitle = when {
                imageSearchOpen -> "صور مرخّصة ومناسبة للوصفة"
                isCookingMode && cookingCompleted -> "اكتمل التحضير"
                isCookingMode -> "اسحبي للتنقل بين الخطوات"
                else -> null
            },
            // Keep the recipe actions anchored above the system navigation area.
            // Long recipe content scrolls independently instead of pushing the footer off-screen.
            fixedHeightFraction = 0.88f,
            fillBody = true,
            text = {
                AnimatedContent(
                    targetState = when {
                        imageSearchOpen -> 1
                        isCookingMode -> 2
                        else -> 0
                    },
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val directionSign = logicalAnimationDirectionSign(layoutDirection) *
                            if (targetState > initialState) 1 else -1
                        (slideInHorizontally(
                            animationSpec = tween(250),
                            initialOffsetX = { width -> directionSign * width / 5 }
                        ) + fadeIn(tween(160, delayMillis = 12))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(190),
                                targetOffsetX = { width -> -directionSign * width / 10 }
                            ) + fadeOut(tween(135)))
                    },
                    label = "recipeSheetPage"
                ) { page ->
                if (page == 1) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                "ابحث في Wikimedia Commons عن صورة لهذا الطبق. نعرض الصور ذات تراخيص إعادة الاستخدام الواضحة فقط.",
                                color = MutedInk,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        item {
                            MealsSearchBar(
                                value = imageSearchQuery,
                                onValueChange = { imageSearchQuery = it },
                                placeholder = "اسم الطبق أو وصف الصورة...",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MealsPrimaryButton(
                                    label = if (imageSearchLoading) "جارٍ البحث…" else "ابحث عن صور",
                                    onClick = { searchRecipeImages(detail) },
                                    enabled = !imageSearchLoading && imageSearchQuery.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                )
                                MealsSecondaryButton(
                                    label = "رجوع",
                                    onClick = { imageSearchOpen = false },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (imageSearchLoading) {
                            item {
                                Text(
                                    "نبحث في صور Wikimedia Commons ونراجع تراخيصها…",
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        imageSearchError?.let { message ->
                            item {
                                Text(
                                    message,
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        items(imageSearchResults, key = { it.sourcePageUrl }) { candidate ->
                            MealsCard(
                                containerColor = WarmSurface,
                                borderColor = null,
                                contentPadding = 10.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    AsyncImage(
                                        model = optimizedRecipeImageUrl(candidate.imageUrl, width = 320),
                                        contentDescription = candidate.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(112.dp)
                                            .height(112.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            candidate.title,
                                            color = Ink,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "المؤلف: " + candidate.author,
                                            color = MutedInk,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            "الترخيص: " + candidate.license,
                                            color = MutedInk,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            "صفحة المصدر",
                                            color = dz.meals.app.ui.theme.TextAccent,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.clickable {
                                                runCatching { uriHandler.openUri(candidate.sourcePageUrl) }
                                            }
                                        )
                                        MealsPrimaryButton(
                                            label = "استخدام هذه الصورة",
                                            onClick = {
                                                onSaveRecipeImage?.invoke(detail.id, candidate)
                                                imageSearchOpen = false
                                            },
                                            enabled = onSaveRecipeImage != null,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (page == 2) {
                    RecipeCookingFlow(
                        recipe = detail,
                        ingredientNames = ingredientNames,
                        localeTag = appLocaleTag,
                        servings = recipeServings,
                        stepIndex = cookingStepIndex,
                        onStepIndexChange = { cookingStepIndex = it },
                        stepDirection = cookingStepDirection,
                        onStepDirectionChange = { cookingStepDirection = it },
                        completed = cookingCompleted,
                        onCompletedChange = { cookingCompleted = it },
                        layoutDirection = layoutDirection
                    )
                } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (detail.imageUrl.startsWith("https://", ignoreCase = true)) {
                        item {
                            AsyncImage(
                                model = optimizedRecipeImageUrl(detail.imageUrl, width = 720),
                                contentDescription = detail.nameAr,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(188.dp)
                                    .clip(RoundedCornerShape(17.dp))
                            )
                        }
                    } else {
                        item {
                            val autoFailed = automaticImageErrorRecipeId == detail.id
                            val autoSearchExpected = onAutoLoadRecipeImage != null && !autoFailed
                            val isAutoLoading = automaticImageLoadingRecipeId == detail.id ||
                                (autoSearchExpected && detail.id !in attemptedAutomaticImageRecipeIds)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(188.dp)
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(WarmSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(9.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    if (isAutoLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(27.dp),
                                            color = dz.meals.app.ui.theme.TextAccent,
                                            strokeWidth = 2.5.dp
                                        )
                                        Text(
                                            "نبحث عن صورة مناسبة ونضيفها تلقائياً…",
                                            color = MutedInk,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else if (autoFailed) {
                                        Text(
                                            "تعذر العثور على صورة مناسبة تلقائياً",
                                            color = Ink,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            automaticImageError.orEmpty(),
                                            color = MutedInk,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    } else {
                                        Text(
                                            "لا توجد صورة لهذه الوصفة بعد",
                                            color = MutedInk,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (onSearchRecipeImages != null && onSaveRecipeImage != null) {
                        val hasImage = detail.imageUrl.startsWith("https://", ignoreCase = true)
                        val autoFailed = automaticImageErrorRecipeId == detail.id
                        if (hasImage || onAutoLoadRecipeImage == null || autoFailed) {
                            item {
                                if (autoFailed && onAutoLoadRecipeImage != null && !hasImage) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MealsSecondaryButton(
                                            label = "إعادة المحاولة",
                                            onClick = {
                                                attemptedAutomaticImageRecipeIds.remove(detail.id)
                                                automaticImageError = null
                                                automaticImageErrorRecipeId = null
                                                automaticImageRetryNonce += 1
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        MealsSecondaryButton(
                                            label = "بحث يدوي",
                                            onClick = { openManualImageSearch(detail) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    MealsSecondaryButton(
                                        label = if (hasImage) "ابحث عن صورة أخرى" else "ابحث وأضف صورة",
                                        onClick = { openManualImageSearch(detail) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    if (detail.imageAuthor.isNotBlank() || detail.imageLicense.isNotBlank()) {
                        item {
                            Text(
                                "الصورة: " + listOf(detail.imageCredit.ifBlank { detail.imageAuthor }, detail.imageLicense)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                color = MutedInk,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (detail.imageSourcePageUrl.isNotBlank()) {
                        item {
                            Text(
                                "فتح مصدر الصورة",
                                color = dz.meals.app.ui.theme.TextAccent,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable {
                                    runCatching { uriHandler.openUri(detail.imageSourcePageUrl) }
                                }
                            )
                        }
                    }
                    if (detail.imageLicenseUrl.isNotBlank()) {
                        item {
                            Text(
                                "عرض ترخيص الصورة: " + detail.imageLicense,
                                color = MutedInk,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable {
                                    runCatching { uriHandler.openUri(detail.imageLicenseUrl) }
                                }
                            )
                        }
                    }
                    item { Text(detail.descriptionAr, color = MutedInk) }
                    if (detail.nativeName.isNotBlank()) {
                        item {
                            Text(
                                "الاسم الأصلي: " + detail.nativeName,
                                color = MutedInk,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    item {
                        val facts = buildList {
                            detail.prepMinutes?.let { add("التحضير" to formatRecipeTime(it)) }
                            detail.cookMinutes?.let { add("الطهي" to formatRecipeTime(it)) }
                            if (detail.durationMinutes > 0) {
                                add("الإجمالي" to formatRecipeTime(detail.durationMinutes))
                            }
                            add("الحصص" to detail.baseServings.toString())
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            facts.forEachIndexed { index, fact ->
                                if (index > 0) {
                                    Spacer(
                                        Modifier
                                            .width(1.dp)
                                            .height(30.dp)
                                            .background(Border.copy(alpha = 0.55f))
                                    )
                                }
                                RecipeDetailFact(
                                    label = fact.first,
                                    value = fact.second,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    if (detail.region.isNotBlank()) {
                        item {
                            Text(
                                "المطبخ: " + detail.region,
                                color = MutedInk,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (detail.categories.isNotEmpty()) {
                        item {
                            Text(
                                "الفئات: " + detail.categories.distinct().joinToString("، "),
                                color = MutedInk,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (detail.dietary.isNotEmpty()) {
                        item {
                            Text(
                                "النمط الغذائي: " + detail.dietary.distinct().joinToString("، "),
                                color = MutedInk,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (detail.category.isNotBlank()) {
                        item {
                            Text(
                                "التصنيف الرئيسي: " + detail.category,
                                color = MutedInk,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (detail.difficulty.isNotBlank() && detail.difficulty != "غير محدد") {
                        item {
                            Text(
                                "مستوى الصعوبة: " + detail.difficulty,
                                color = MutedInk,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (
                        detail.caloriesPerServing != null ||
                        detail.proteinGramsPerServing != null ||
                        detail.carbsGramsPerServing != null ||
                        detail.fatGramsPerServing != null
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text(
                                    "القيمة الغذائية لكل حصة",
                                    color = Ink,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val nutritionFacts = listOf(
                                    "السعرات" to formatNutritionValue(detail.caloriesPerServing, "ك.س"),
                                    "بروتين" to formatNutritionValue(detail.proteinGramsPerServing, "غ"),
                                    "كربوهيدرات" to formatNutritionValue(detail.carbsGramsPerServing, "غ"),
                                    "دهون" to formatNutritionValue(detail.fatGramsPerServing, "غ")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    nutritionFacts.forEachIndexed { index, fact ->
                                        if (index > 0) {
                                            Spacer(
                                                Modifier
                                                    .width(1.dp)
                                                    .height(32.dp)
                                                    .background(Border.copy(alpha = 0.45f))
                                            )
                                        }
                                        RecipeDetailFact(
                                            label = fact.first,
                                            value = fact.second,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("المكونات", color = Ink, fontWeight = FontWeight.Bold)
                            RecipeServingsControl(
                                servings = recipeServings,
                                onServingsChange = { recipeServings = it },
                                localeTag = appLocaleTag
                            )
                        }
                    }
                    items(detail.ingredients) { ingredient ->
                        val master = ingredientNames[ingredient.ingredientId]
                        val ingredientName = mealsText(
                            recipeIngredientDisplayName(master, ingredient.ingredientId, appLocaleTag),
                            appLocaleTag
                        )
                        val scaledQuantity = scaleRecipeIngredientQuantity(
                            ingredient.quantityBase,
                            recipeServings.toDouble() / detail.baseServings.coerceAtLeast(1).toDouble(),
                            ingredient.scalingRule
                        )
                        val quantityLabel = if (scaledQuantity > 0.0) {
                            mealsRecipeIngredientQuantity(
                                scaledQuantity,
                                ingredient.unit,
                                appLocaleTag
                            )
                        } else ""
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    "• " + bidiIsolate(ingredientName),
                                    modifier = Modifier.weight(1f),
                                    color = Ink,
                                    style = MaterialTheme.typography.bodyMedium,
                                    translate = false
                                )
                                if (quantityLabel.isNotBlank()) {
                                    Text(
                                        bidiIsolate(quantityLabel),
                                        color = dz.meals.app.ui.theme.TextAccent,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        translate = false
                                    )
                                }
                            }
                            if (ingredient.prepNote.isNotBlank()) {
                                Text(
                                    mealsText(ingredient.prepNote, appLocaleTag),
                                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else if (quantityLabel.isBlank()) {
                                Text(
                                    if (isMealsEnglish(appLocaleTag)) "To taste" else "حسب الحاجة",
                                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    if (detail.tags.isNotEmpty()) {
                        item {
                            Text(
                                "وسوم الوصفة: " + detail.tags.distinct().joinToString("، "),
                                color = MutedInk,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    item { Text("طريقة التحضير", fontWeight = FontWeight.Bold) }
                    itemsIndexed(detail.stepsAr) { index, step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                bidiIsolate((index + 1).toString()) + ". " + mealsText(step, appLocaleTag),
                                modifier = Modifier.weight(1f),
                                translate = false
                            )
                            detail.stepMinutes.getOrNull(index)?.let { minutes ->
                                Text(
                                    formatRecipeTime(minutes),
                                    color = MutedInk,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    item {
                        val allergenLabels = detail.allergens.map { allergen ->
                            when (allergen) {
                                "gluten" -> "الغلوتين"
                                "egg" -> "البيض"
                                "milk" -> "الحليب"
                                "tree_nuts" -> "المكسرات"
                                "celery" -> "الكرفس"
                                "sulphites" -> "الكبريتيت"
                                "fish" -> "السمك"
                                "mustard" -> "الخردل"
                                "crustaceans" -> "القشريات"
                                "soy" -> "فول الصويا"
                                "sesame" -> "السمسم"
                                "molluscs" -> "الرخويات"
                                "peanuts" -> "الفول السوداني"
                                else -> allergen
                            }
                        }
                        val allergenText = if (allergenLabels.isEmpty()) {
                            mealsText("لا توجد مسببات مسجلة", appLocaleTag)
                        } else {
                            allergenLabels.joinToString(if (isMealsEnglish(appLocaleTag)) ", " else "، ")
                        }
                        Text(
                            mealsText("الحساسية: ", appLocaleTag) + allergenText,
                            translate = false
                        )
                    }
                    detail.attribution?.let { attribution ->
                        item {
                            MealsCard(
                                containerColor = WarmSurface,
                                borderColor = null,
                                contentPadding = 13.dp
                            ) {
                                Text(
                                    "المصدر والترخيص",
                                    color = Ink,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    attribution.credit + " · " + attribution.sourceTitle,
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (attribution.sourceUrl.isNotBlank()) {
                                    Text(
                                        "فتح المصدر الأصلي",
                                        color = dz.meals.app.ui.theme.TextAccent,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.clickable {
                                            runCatching { uriHandler.openUri(attribution.sourceUrl) }
                                        }
                                    )
                                }
                                if (attribution.sourceLicense.isNotBlank()) {
                                    Text(
                                        "ترخيص المصدر: " + attribution.sourceLicense,
                                        color = MutedInk,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Text(
                                    attribution.license,
                                    color = MutedInk,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.clickable {
                                        if (attribution.licenseUrl.isNotBlank()) {
                                            runCatching { uriHandler.openUri(attribution.licenseUrl) }
                                        }
                                    }
                                )
                                if (attribution.revisionUrl.isNotBlank()) {
                                    Text(
                                        "مراجعة الوصفة الأصلية",
                                        color = dz.meals.app.ui.theme.TextAccent,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.clickable {
                                            runCatching { uriHandler.openUri(attribution.revisionUrl) }
                                        }
                                    )
                                }
                                if (attribution.historyUrl.isNotBlank()) {
                                    Text(
                                        "سجل تعديلات المصدر",
                                        color = dz.meals.app.ui.theme.TextAccent,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.clickable {
                                            runCatching { uriHandler.openUri(attribution.historyUrl) }
                                        }
                                    )
                                }
                                if (attribution.changes.isNotBlank()) {
                                    Text(
                                        attribution.changes,
                                        color = MutedInk,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
                }
                }
            },
            confirmButton = {
                when {
                    imageSearchOpen -> MealsSecondaryButton(
                        label = "رجوع إلى الوصفة",
                        onClick = { imageSearchOpen = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                    isCookingMode -> MealsPrimaryButton(
                        label = when {
                            cookingCompleted -> "العودة للوصفة"
                            detail.stepsAr.isEmpty() -> "العودة للوصفة"
                            cookingStepIndex >= detail.stepsAr.lastIndex -> "إنهاء التحضير"
                            else -> "التالي"
                        },
                        onClick = {
                            when {
                                cookingCompleted || detail.stepsAr.isEmpty() -> {
                                    isCookingMode = false
                                    cookingCompleted = false
                                }
                                cookingStepIndex < detail.stepsAr.lastIndex -> {
                                    cookingStepDirection = 1
                                    cookingStepIndex += 1
                                }
                                else -> cookingCompleted = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    else -> {
                        val dismissSheet = mealsSheetDismissAction()
                        MealsPrimaryButton(
                            label = "أضف للخطة",
                            onClick = { dismissSheet { onAddRecipe(detail.id); selectedRecipeId = null } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            dismissButton = {
                when {
                    imageSearchOpen -> Unit
                    isCookingMode -> MealsSecondaryButton(
                        label = if (cookingCompleted) "مراجعة الخطوات" else "تفاصيل الوصفة",
                        onClick = {
                            if (cookingCompleted) {
                                cookingCompleted = false
                            } else {
                                isCookingMode = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    else -> MealsSecondaryButton(
                        label = "ابدأ الطبخ",
                        onClick = {
                            cookingStepIndex = 0
                            cookingStepDirection = 1
                            cookingServings = recipeServings
                            cookingCompleted = false
                            isCookingMode = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

}


@Composable
private fun RecipeServingsControl(
    servings: Int,
    onServingsChange: (Int) -> Unit,
    localeTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (isMealsEnglish(localeTag)) "Servings" else "الحصص",
            color = MutedInk,
            style = MaterialTheme.typography.labelMedium
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(WarmSurface, CircleShape)
                .clickable(enabled = servings > 1) { onServingsChange((servings - 1).coerceAtLeast(1)) },
            contentAlignment = Alignment.Center
        ) {
            Text("−", color = if (servings > 1) Ink else MutedInk, fontWeight = FontWeight.Bold)
        }
        Text(
            servings.toString(),
            color = Ink,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(WarmSurface, CircleShape)
                .clickable(enabled = servings < 12) { onServingsChange((servings + 1).coerceAtMost(12)) },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = if (servings < 12) Ink else MutedInk, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecipeCookingFlow(
    recipe: MealRecipe,
    ingredientNames: Map<String, IngredientMaster>,
    localeTag: String,
    servings: Int,
    stepIndex: Int,
    onStepIndexChange: (Int) -> Unit,
    stepDirection: Int,
    onStepDirectionChange: (Int) -> Unit,
    completed: Boolean,
    onCompletedChange: (Boolean) -> Unit,
    layoutDirection: LayoutDirection
) {
    val stepCount = recipe.stepsAr.size
    val safeStepIndex = stepIndex.coerceIn(0, (stepCount - 1).coerceAtLeast(0))

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    completed -> "اكتمل التحضير"
                    stepCount == 0 -> "طريقة التحضير"
                    else -> "الخطوة الحالية"
                },
                color = MutedInk,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                when {
                    completed -> "$stepCount / $stepCount"
                    stepCount == 0 -> "لا توجد خطوات"
                    else -> "${safeStepIndex + 1} / $stepCount"
                },
                color = dz.meals.app.ui.theme.TextAccent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }

        MealsLiquidGlassProgressBar(
            progress = when {
                completed -> 1f
                stepCount == 0 -> 0f
                else -> (safeStepIndex + 1).toFloat() / stepCount.toFloat()
            },
            modifier = Modifier.fillMaxWidth(),
            height = 14.dp,
            contentDescription = "التقدم في خطوات الوصفة"
        )

        AnimatedContent(
            targetState = completed,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(completed, safeStepIndex, stepCount, layoutDirection) {
                    var dragDistance = 0f
                    val swipeThreshold = 72.dp.toPx()
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            dragDistance += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            if (!completed && stepCount > 0 &&
                                abs(dragDistance) >= swipeThreshold
                            ) {
                                val movingForward = isLogicalForwardSwipe(dragDistance, layoutDirection)
                                when {
                                    movingForward && safeStepIndex < stepCount - 1 -> {
                                        onStepDirectionChange(1)
                                        onStepIndexChange(safeStepIndex + 1)
                                    }
                                    movingForward && safeStepIndex == stepCount - 1 -> {
                                        onCompletedChange(true)
                                    }
                                    !movingForward && safeStepIndex > 0 -> {
                                        onStepDirectionChange(-1)
                                        onStepIndexChange(safeStepIndex - 1)
                                    }
                                }
                            }
                            dragDistance = 0f
                        },
                        onDragCancel = { dragDistance = 0f }
                    )
                },
            transitionSpec = {
                (slideInVertically(
                    animationSpec = tween(250),
                    initialOffsetY = { height -> if (targetState) height / 12 else -height / 12 }
                ) + fadeIn(tween(165))) togetherWith
                    (slideOutVertically(
                        animationSpec = tween(180),
                        targetOffsetY = { height -> if (targetState) -height / 14 else height / 14 }
                    ) + fadeOut(tween(130)))
            },
            label = "cookingCompletion"
        ) { isComplete ->
            when {
                isComplete -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .background(SoftGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            MealsIcon(
                                MealsIconName.Check,
                                contentDescription = "اكتمل الطبخ",
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "أحسنتِ!",
                            color = Ink,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "أصبحت وجبتك جاهزة",
                            color = dz.meals.app.ui.theme.TextAccent,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            recipe.nameAr,
                            color = MutedInk,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                stepCount == 0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "لا توجد خطوات تحضير مسجلة لهذه الوصفة بعد.",
                            color = MutedInk,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                else -> {
                    AnimatedContent(
                        targetState = safeStepIndex,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val sign = logicalAnimationDirectionSign(layoutDirection)
                            val enteringDirection = sign * if (stepDirection > 0) 1 else -1
                            (slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = 0.86f,
                                    stiffness = 390f
                                ),
                                initialOffsetX = { width -> enteringDirection * width / 4 }
                            ) + fadeIn(tween(155, delayMillis = 12)) + scaleIn(
                                initialScale = 0.985f,
                                animationSpec = tween(210)
                            )) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(170),
                                    targetOffsetX = { width -> -enteringDirection * width / 9 }
                                ) + fadeOut(tween(125)))
                        },
                        label = "cookingStep"
                    ) { visibleStepIndex ->
                        LazyColumn(
                            state = rememberLazyListState(),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(15.dp)
                        ) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "الخطوة",
                                            color = dz.meals.app.ui.theme.TextAccent,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            " ${visibleStepIndex + 1}",
                                            color = Ink,
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.displaySmall
                                        )
                                        Spacer(Modifier.weight(1f))
                                        recipe.stepMinutes.getOrNull(visibleStepIndex)?.let { minutes ->
                                            Text(
                                                formatRecipeTime(minutes),
                                                color = MutedInk,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                    Text(
                                        mealsText(
                                            recipe.stepsAr.getOrNull(visibleStepIndex).orEmpty(),
                                            localeTag
                                        ),
                                        color = Ink,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                            }

                            items(recipe.ingredients) { ingredient ->
                                val master = ingredientNames[ingredient.ingredientId]
                                val factor = servings.toDouble() /
                                    recipe.baseServings.coerceAtLeast(1).toDouble()
                                val amount = scaleRecipeIngredientQuantity(
                                    ingredient.quantityBase,
                                    factor,
                                    ingredient.scalingRule
                                )
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            bidiIsolate(
                                                mealsText(
                                                    recipeIngredientDisplayName(
                                                        master,
                                                        ingredient.ingredientId,
                                                        localeTag
                                                    ),
                                                    localeTag
                                                )
                                            ),
                                            modifier = Modifier.weight(1f),
                                            color = Ink,
                                            style = MaterialTheme.typography.bodyMedium,
                                            translate = false
                                        )
                                        Text(
                                            bidiIsolate(
                                                mealsRecipeIngredientQuantity(
                                                    amount,
                                                    ingredient.unit,
                                                    localeTag
                                                )
                                            ),
                                            color = dz.meals.app.ui.theme.TextAccent,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            translate = false
                                        )
                                    }
                                    if (ingredient.prepNote.isNotBlank()) {
                                        Text(
                                            mealsText(ingredient.prepNote, localeTag),
                                            modifier = Modifier.padding(start = 12.dp),
                                            color = MutedInk,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            item {
                                Text(
                                    "اسحبي أفقياً للانتقال إلى الخطوة التالية",
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                                    color = MutedInk,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeDetailFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            label,
            color = MutedInk,
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            value,
            color = Ink,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun formatRecipeTime(minutes: Int): String {
    val safeMinutes = minutes.coerceAtLeast(0)
    if (safeMinutes < 60) return "$safeMinutes د"
    val hours = safeMinutes / 60
    val remainder = safeMinutes % 60
    return if (remainder == 0) "$hours س" else "$hours س و $remainder د"
}

private fun formatNutritionValue(value: Double?, unit: String): String =
    value?.let { it.prettyQuantity() + " " + unit } ?: "—"

private fun Double.prettyQuantity(): String {
    if (this % 1.0 == 0.0) return toLong().toString()
    val roundedTenths = (this * 10.0).roundToLong()
    val whole = roundedTenths / 10
    val fraction = kotlin.math.abs(roundedTenths % 10)
    return "$whole.$fraction"
}
