package dz.meals.app.ui

import dz.meals.app.ui.components.MealsText as Text

import dz.meals.app.MealsStartupLogger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.res.painterResource

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable

import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.animation.slideOutVertically

import androidx.compose.animation.slideInVertically

import androidx.compose.animation.scaleOut

import androidx.compose.animation.scaleIn

import androidx.compose.animation.fadeOut

import androidx.compose.animation.fadeIn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade

import android.content.Intent
import android.net.Uri

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dz.meals.app.BuildConfig
import dz.meals.app.data.LocalCorpusRecipe
import dz.meals.app.data.LocalCorpusTranslation
import dz.meals.app.data.LocalRecipeCorpusRepository
import dz.meals.app.data.ArabicRecipeCatalogueDownloader
import dz.meals.app.data.ArabicRecipeCatalogueInstaller
import dz.meals.app.data.AppPreferences
import dz.meals.app.data.AppUpdateChecker
import dz.meals.app.data.AppUpdateCheckResult
import dz.meals.app.data.AppUpdateInfo
import dz.meals.app.data.MealsRepository
import dz.meals.app.ui.onboarding.MealsOnboarding
import dz.meals.app.data.MealsSnapshot
import dz.meals.app.data.PreferencesStore
import dz.meals.app.data.currentWeekStartIso
import dz.meals.app.ui.components.*
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dz.meals.app.ui.screens.PantryScreen
import dz.meals.app.ui.screens.PlanScreen
import dz.meals.app.ui.screens.RecipesScreen
import dz.meals.app.ui.screens.ShoppingScreen
import dz.meals.app.ui.theme.Cream
import dz.meals.app.ui.theme.LocalMealsLocaleTag
import dz.meals.app.ui.theme.mealsText
import dz.meals.app.ui.theme.DEFAULT_MEALS_LOCALE_TAG
import dz.meals.app.ui.theme.mealsLayoutDirection
import dz.meals.app.ui.theme.normalizeMealsLocaleTag
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.LocalDate

private const val LATEST_APK_DOWNLOAD_URL = "https://github.com/ni3yyn/Meals/releases/latest/download/meals.apk"

/**
 * Let scrolling content softly emerge from beneath the fixed top app bar.
 *
 * This is a true alpha mask on the content viewport, not a gradient overlay:
 * the first pixels at the header boundary are transparent and become fully
 * opaque over fadeHeight. No shadow, tint, or extra edge is drawn.
 */
private fun Modifier.fadeTopEdgeMask(fadeHeight: androidx.compose.ui.unit.Dp): Modifier =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        val fadePx = fadeHeight.toPx().coerceAtMost(size.height)
        if (fadePx > 0f && size.height > 0f) {
            val fadeEnd = (fadePx / size.height).coerceIn(0f, 1f)
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        fadeEnd to Color.Black,
                        1f to Color.Black
                    ),
                    startY = 0f,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

private enum class AppTab(val title: String) {
    Plan("الخطة"),
    Recipes("الوصفات"),
    Shopping("المشتريات"),
    Pantry("المخزون")
}

private enum class RecipeCataloguePhase {
    IDLE,
    DOWNLOADING,
    INSTALLING,
    READY,
    FAILED
}

@Composable
fun MidatiBrandLogo(
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 52.dp,
    showWordmark: Boolean = true
) {
    Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(9.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = dz.meals.app.R.drawable.ic_mida_mark),
            contentDescription = "Midati",
            modifier = Modifier.size(iconSize)
        )
        if (showWordmark) {
            Text(
                text = "Midati",
                color = dz.meals.app.ui.theme.Ink,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsApp(
    onInitialContentReady: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    MealsAppContent(onInitialContentReady = onInitialContentReady, onExit = onExit)
}

@Composable
private fun MealsAppContent(
    onInitialContentReady: () -> Unit,
    onExit: () -> Unit
) {
    MealsStartupLogger.markOnce("MealsAppContent entered")
    val context = LocalContext.current
    val preferencesStore = remember(context) { PreferencesStore(context) }
    val repository = remember(context) { MealsRepository(context) }
    val updateChecker = remember { AppUpdateChecker() }
    // Keep the first frame neutral until DataStore has emitted the real setup state.
    // This prevents a one-frame home-screen flash before first-run onboarding appears.
    val preferencesFlow = remember(preferencesStore) {
        preferencesStore.preferences.map { stored -> stored as AppPreferences? }
    }
    val storedPreferences by preferencesFlow.collectAsState(initial = null)
    val preferencesLoaded = storedPreferences != null
    val preferences = storedPreferences ?: AppPreferences(hasCompletedSetup = true)
    val appLanguage = preferences.appLanguage
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var languageTransitionTarget by remember { mutableStateOf<String?>(null) }

    fun requestLanguageChange(languageTag: String) {
        if (languageTransitionTarget != null) return
        val target = normalizeMealsLocaleTag(languageTag)
        if (target == normalizeMealsLocaleTag(appLanguage)) return
        keyboardController?.hide()
        languageTransitionTarget = target
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var weekStartIso by rememberSaveable { mutableStateOf(currentWeekStartIso()) }
    val weekStart = remember(weekStartIso) { LocalDate.parse(weekStartIso) }

    var snapshot by remember { mutableStateOf(MealsSnapshot()) }
    val arabicCatalogueDownloader = remember(context) { ArabicRecipeCatalogueDownloader(context) }
    val arabicCatalogueInstaller = remember(context) { ArabicRecipeCatalogueInstaller(context) }
    var cataloguePhase by remember { mutableStateOf(RecipeCataloguePhase.IDLE) }
    var catalogueDownloadedBytes by remember { mutableStateOf(0L) }
    var catalogueTotalBytes by remember { mutableStateOf(ArabicRecipeCatalogueDownloader.EXPECTED_BYTES) }
    var catalogueImportedRecipes by remember { mutableStateOf(0) }
    var catalogueExpectedRecipes by remember { mutableStateOf(ArabicRecipeCatalogueDownloader.EXPECTED_RECIPE_COUNT) }
    var catalogueError by remember { mutableStateOf<String?>(null) }
    var catalogueJob by remember { mutableStateOf<Job?>(null) }
    val recipeCorpusRepository = remember(context) { LocalRecipeCorpusRepository(context) }
    var showRecipeCorpusTester by rememberSaveable { mutableStateOf(false) }
    var recipeCorpusLoading by remember { mutableStateOf(false) }
    var recipeCorpusLoadError by remember { mutableStateOf<String?>(null) }
    var recipeCorpusRecipes by remember { mutableStateOf<List<LocalCorpusRecipe>>(emptyList()) }
    var recipeCorpusTranslations by remember { mutableStateOf<Map<String, LocalCorpusTranslation>>(emptyMap()) }
    var recipeCorpusTranslating by remember { mutableStateOf<Set<String>>(emptySet()) }
    var recipeCorpusTranslationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Plan) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showHouseholdSettings by rememberSaveable { mutableStateOf(false) }
    val settingsOverlayVisible = showSettings || showHouseholdSettings
    var isGenerating by remember { mutableStateOf(false) }
    var generationMessage by remember { mutableStateOf<String?>(null) }
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var hasSignalledInitialReadiness by remember { mutableStateOf(false) }
    var isCheckingForUpdates by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("تحققي من توفر إصدار أحدث من التطبيق.") }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

    BackHandler(
        enabled = showRecipeCorpusTester,
        onBack = { showRecipeCorpusTester = false }
    )

    BackHandler(
        enabled = preferencesLoaded && preferences.hasCompletedSetup &&
            !showSettings && !showHouseholdSettings && !showUpdateDialog &&
            !showExitConfirmation && !showRecipeCorpusTester,
        onBack = { showExitConfirmation = true }
    )

    suspend fun refresh() {
        snapshot = repository.snapshot(weekStartIso, preferences.wilaya, preferences.mealSlots)
    }

    fun ensureArabicRecipeCatalogue() {
        if (cataloguePhase == RecipeCataloguePhase.READY || catalogueJob?.isActive == true) return
        catalogueError = null
        catalogueJob = scope.launch {
            try {
                // Complete the existing seed setup before importing extra rows.
                // This also protects restored launches that reopen directly on Recipes.
                repository.ensureInitialized()
                if (arabicCatalogueInstaller.isInstalled()) {
                    refresh()
                    cataloguePhase = RecipeCataloguePhase.READY
                    return@launch
                }

                cataloguePhase = RecipeCataloguePhase.DOWNLOADING
                catalogueDownloadedBytes = 0L
                catalogueTotalBytes = ArabicRecipeCatalogueDownloader.EXPECTED_BYTES
                val file = arabicCatalogueDownloader.download { progress ->
                    catalogueDownloadedBytes = progress.downloadedBytes
                    catalogueTotalBytes = progress.totalBytes
                }

                cataloguePhase = RecipeCataloguePhase.INSTALLING
                catalogueImportedRecipes = 0
                catalogueExpectedRecipes = ArabicRecipeCatalogueDownloader.EXPECTED_RECIPE_COUNT
                arabicCatalogueInstaller.install(file) { completed, total ->
                    catalogueImportedRecipes = completed
                    catalogueExpectedRecipes = total
                }

                repository.invalidateCatalogueCache()
                refresh()
                cataloguePhase = RecipeCataloguePhase.READY
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                catalogueError = error.message ?: "تعذر تحميل مكتبة الوصفات. تحققي من الاتصال ثم أعيدي المحاولة."
                cataloguePhase = RecipeCataloguePhase.FAILED
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == AppTab.Recipes) ensureArabicRecipeCatalogue()
    }

    fun checkForAppUpdates() {
        if (isCheckingForUpdates) return
        scope.launch {
            isCheckingForUpdates = true
            updateStatus = "جارٍ التحقق من أحدث إصدار..."
            availableUpdate = null
            try {
                when (val result = updateChecker.checkForUpdates(BuildConfig.VERSION_NAME)) {
                    AppUpdateCheckResult.UpToDate -> {
                        updateStatus = "تطبيقك محدّث بالفعل، لديك أحدث إصدار مستقر."
                    }
                    is AppUpdateCheckResult.Available -> {
                        availableUpdate = result.update
                        updateStatus = "يتوفر تحديث جديد للتطبيق."
                    }
                }
            } catch (error: Exception) {
                updateStatus = error.message ?: "تعذر التحقق من التحديثات. حاولي مجدداً."
            } finally {
                isCheckingForUpdates = false
            }
        }
    }

    fun openLatestApk() {
        val url = availableUpdate?.downloadUrl ?: LATEST_APK_DOWNLOAD_URL
        try {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                    mealsText("تنزيل Midati", appLanguage)
                )
            )
        } catch (_: Exception) {
            updateStatus = "تعذر فتح رابط التنزيل. تحققي من وجود متصفح على الجهاز."
        }
    }

    fun perform(action: suspend () -> Unit) {
        scope.launch {
            action()
            refresh()
        }
    }

    LaunchedEffect(repository, weekStartIso, preferences.wilaya, preferences.mealSlots, preferencesLoaded) {
        try {
            MealsStartupLogger.markOnce("Initial repository initialization started")
            repository.ensureInitialized()
            MealsStartupLogger.markOnce("Initial repository initialization completed")
            refresh()
            MealsStartupLogger.markOnce("Initial meal snapshot loaded")
        } finally {
            // Keep the native splash only through the small local preferences
            // and first snapshot read; never add an arbitrary fixed delay.
            if (preferencesLoaded && !hasSignalledInitialReadiness) {
                hasSignalledInitialReadiness = true
                onInitialContentReady()
            }
        }
    }

    LaunchedEffect(generationMessage) {
        val message = generationMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        generationMessage = null
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides mealsLayoutDirection(appLanguage),
        LocalMealsLocaleTag provides appLanguage
    ) {
    MealsBackdrop {
        val hazeState = requireNotNull(LocalMealsHazeState.current) {
            "MealsApp must be hosted inside MealsBackdrop so the shared Haze source is available."
        }
        val screenGlassBackdrop = requireNotNull(LocalMealsScreenGlassBackdrop.current) {
            "MealsApp must be hosted inside MealsBackdrop so the shared glass source is available."
        }
        val navigationItems = remember {
            listOf(
                MealsNavigationItem("الخطة", MealsIconName.Plan),
                MealsNavigationItem("الوصفات", MealsIconName.Cookbook),
                MealsNavigationItem("المشتريات", MealsIconName.Basket),
                MealsNavigationItem("المخزون", MealsIconName.Pantry)
            )
        }
        AnimatedVisibility(
            visible = preferencesLoaded && preferences.hasCompletedSetup,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(
                animationSpec = tween(560, easing = FastOutSlowInEasing)
            ) + scaleIn(
                initialScale = 0.975f,
                animationSpec = tween(660, easing = FastOutSlowInEasing)
            ) + slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight / 44 },
                animationSpec = tween(660, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(animationSpec = tween(120))
        ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    MealsGlassSurface(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        tintColor = dz.meals.app.ui.theme.WarmSurface,
                        contentPadding = 13.dp,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(9.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            MealsIcon(MealsIconName.Check, contentDescription = null, modifier = Modifier.size(23.dp))
                            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, color = dz.meals.app.ui.theme.Ink)
                        }
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp)) {
                            Text("صباح الخير", style = MaterialTheme.typography.titleLarge)
                            Text(
                    "Midati · " + mealsText(selectedTab.title, appLanguage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    translate = false
                )
                        }
                    },
                    actions = {
                        MealsIconButton(
                            onClick = { showUpdateDialog = true },
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            MealsIcon(
                                MealsIconName.Update,
                                contentDescription = "تحديث التطبيق",
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        MealsIconButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            MealsIcon(
                                MealsIconName.Settings,
                                contentDescription = "الإعدادات",
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                MealsFrostedBottomBar(
                    items = navigationItems,
                    selectedIndex = selectedTab.ordinal,
                    onItemSelected = { index ->
                        selectedTab = AppTab.entries.getOrElse(index) { AppTab.Plan }
                    },
                    hazeState = hazeState
                )
            }
        ) { innerPadding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .then(if (settingsOverlayVisible) Modifier else Modifier.fadeTopEdgeMask(28.dp))
                    // Keep the shared modal sheet's blur source active behind Settings.
                    // Only the optional glass capture and edge mask are suspended for this overlay.
                    .mealsHazeSource(hazeState, zIndex = 1f)
                    .then(if (settingsOverlayVisible) Modifier else Modifier.mealsGlassSource(screenGlassBackdrop))
            ) {
                MealsBubblyTransition(
                    targetState = selectedTab,
                    modifier = Modifier.fillMaxSize()
                ) { targetTab ->
                    when (targetTab) {
                    AppTab.Plan -> PlanScreen(
                        weekStartIso = weekStartIso,
                        preferences = preferences,
                        snapshot = snapshot,
                        isGenerating = isGenerating,
                        onGenerate = { fillEmptyOnly ->
                            scope.launch {
                                isGenerating = true
                                try {
                                    val result = repository.generateWeek(
                                        weekStartIso = weekStartIso,
                                        weeklyBudgetDzd = preferences.weeklyBudgetDzd,
                                        householdServings = preferences.householdServings,
                                        allergyIds = preferences.allergyIds,
                                        selectedWilaya = preferences.wilaya,
                                        selectedMealSlots = preferences.mealSlots,
                                        dislikedIngredientIds = preferences.dislikedIngredientIds,
                                        dietPattern = preferences.dietPattern,
                                        maxCookingMinutes = preferences.maxCookingMinutes,
                                        budgetBufferPercent = preferences.budgetBufferPercent,
                                        fillEmptyOnly = fillEmptyOnly
                                    )
                                    generationMessage = result.message
                                    refresh()
                                } finally {
                                    isGenerating = false
                                }
                            }
                        },
                        onSetMeal = { day, slot, recipeId ->
                            perform {
                                repository.setMeal(weekStartIso, day, slot, recipeId, preferences.householdServings)
                            }
                        },
                        onToggleLock = { id -> perform { repository.toggleMealLock(id, weekStartIso) } },
                        onToggleCooked = { id -> perform { repository.toggleMealCooked(id, weekStartIso) } },
                        onAdjustServings = { id, delta -> perform { repository.adjustMealServings(id, weekStartIso, delta) } },
                        onPreviousWeek = { weekStartIso = LocalDate.parse(weekStartIso).minusWeeks(1).toString() },
                        onNextWeek = { weekStartIso = LocalDate.parse(weekStartIso).plusWeeks(1).toString() },
                    )
                    AppTab.Recipes -> RecipesScreen(
                        recipes = snapshot.recipes,
                        ingredients = snapshot.ingredients,
                        allergyIds = preferences.allergyIds,
                        favoriteRecipeIds = snapshot.favoriteRecipeIds,
                        onSearchRecipeImages = { recipe, query ->
                            repository.searchCommonsRecipeImages(recipe, query)
                        },
                        onAutoLoadRecipeImage = { recipe ->
                            repository.automaticallyFindAndSaveRecipeImage(recipe)
                            refresh()
                        },
                        onSaveRecipeImage = { recipeId, image ->
                            scope.launch {
                                try {
                                    repository.saveRecipeImage(recipeId, image)
                                    refresh()
                                    generationMessage = "تم حفظ صورة الوصفة مع بيانات الترخيص"
                                } catch (error: Exception) {
                                    snackbarHostState.showSnackbar(
                                        error.message ?: "تعذر حفظ صورة الوصفة."
                                    )
                                }
                            }
                        },
                        onToggleFavorite = { id -> perform { repository.toggleFavorite(id) } },
                        onAddRecipe = { id ->
                            generationMessage = "أُضيفت الوصفة إلى خطة الأسبوع"
                            perform { repository.addRecipeToNextSlot(weekStartIso, id, preferences.householdServings) }
                            selectedTab = AppTab.Plan
                        },
                        catalogueProgress = when (cataloguePhase) {
                            RecipeCataloguePhase.DOWNLOADING ->
                                0.90f * (catalogueDownloadedBytes.toFloat() / catalogueTotalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                            RecipeCataloguePhase.INSTALLING ->
                                0.90f + 0.10f * (catalogueImportedRecipes.toFloat() / catalogueExpectedRecipes.coerceAtLeast(1)).coerceIn(0f, 1f)
                            else -> null
                        },
                        catalogueStatusMessage = when (cataloguePhase) {
                            RecipeCataloguePhase.DOWNLOADING -> "جارٍ تنزيل مكتبة الوصفات…"
                            RecipeCataloguePhase.INSTALLING -> "نضيف الوصفات إلى مكتبة Midati…  $catalogueImportedRecipes / $catalogueExpectedRecipes"
                            else -> null
                        },
                        catalogueError = catalogueError,
                        onRetryCatalogue = { ensureArabicRecipeCatalogue() },
                        onOpenRecipeCorpusTester = if (BuildConfig.DEBUG) {
                            {
                                showRecipeCorpusTester = true
                                if (recipeCorpusRecipes.isEmpty() && !recipeCorpusLoading) {
                                    scope.launch {
                                        recipeCorpusLoading = true
                                        recipeCorpusLoadError = null
                                        try {
                                            recipeCorpusRecipes = recipeCorpusRepository.loadRecipes()
                                        } catch (error: Exception) {
                                            recipeCorpusLoadError = error.message ?: "تعذر تحميل بيانات الوصفات."
                                        } finally {
                                            recipeCorpusLoading = false
                                        }
                                    }
                                }
                            }
                        } else null
                    )
                    AppTab.Shopping -> ShoppingScreen(
                        snapshot = snapshot,
                        ingredients = snapshot.ingredients,
                        wilaya = preferences.wilaya,
                        commune = preferences.commune,
                        shoppingChannel = preferences.shoppingChannel,
                        preferredShop = preferences.preferredShop,
                        onShareList = { body ->
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, mealsText("قائمة مشتريات Midati", appLanguage))
                                putExtra(Intent.EXTRA_TEXT, body)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(send, mealsText("مشاركة قائمة المشتريات", appLanguage)))
                            }
                        },
                        onToggleItem = { id, checked ->
                            generationMessage = if (checked) "تم جمع العنصر من قائمة المشتريات" else "أُعيد العنصر إلى قائمة المشتريات"
                            perform { repository.toggleShoppingItem(id, checked) }
                        },
                        onAddCustomItem = { name, quantity, unit, estimatedPrice ->
                            perform { repository.addCustomShoppingItem(weekStartIso, name, quantity, unit, estimatedPrice) }
                        },
                        onEditCustomItem = { id, name, quantity, unit ->
                            perform { repository.updateCustomShoppingItem(id, name, quantity, unit) }
                        },
                        onUpdateItemDetails = { id, purchaseQty, note, unavailable, actual ->
                            perform { repository.updateShoppingItemDetails(id, purchaseQty, note, unavailable, actual) }
                        },
                        onDeleteCustomItem = { id -> perform { repository.deleteCustomShoppingItem(id) } },
                        onRecordPrice = { id, pack, amount, context ->
                            perform { repository.recordPriceObservation(id, pack, amount, preferences.wilaya, context) }
                        }
                    )
                    AppTab.Pantry -> PantryScreen(
                        ingredients = snapshot.ingredients,
                        pantry = snapshot.pantry,
                        onSave = { id, quantity, approximate, note, expiry ->
                            perform { repository.updatePantry(id, quantity, approximate, note, expiry) }
                        },
                        onRemove = { id -> perform { repository.updatePantry(id, null, false) } }
                    )
                    }
                }
            }
        }
        }

        AnimatedVisibility(
            visible = preferencesLoaded && !preferences.hasCompletedSetup,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 1.012f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ),
            exit = fadeOut(
                animationSpec = tween(500, easing = FastOutSlowInEasing)
            ) + scaleOut(
                targetScale = 1.024f,
                animationSpec = tween(620, easing = FastOutSlowInEasing)
            ) + slideOutVertically(
                targetOffsetY = { fullHeight -> -fullHeight / 42 },
                animationSpec = tween(620, easing = FastOutSlowInEasing)
            )
        ) {
            MealsOnboarding(
                initial = preferences,
                ingredients = snapshot.ingredients,
                appLanguage = appLanguage,
                onLanguageSelected = { languageTag ->
                    requestLanguageChange(languageTag)
                },
                onQuickStart = { budget, servings ->
                    scope.launch {
                        preferencesStore.saveSettings(
                            budget,
                            preferences.wilaya,
                            servings,
                            emptySet(),
                            preferences.mealSlots,
                            emptySet(),
                            "none",
                            preferences.maxCookingMinutes,
                            preferences.budgetBufferPercent,
                            preferences.commune,
                            preferences.shoppingChannel,
                            preferences.preferredShop
                        )
                        refresh()
                    }
                },
                onFinish = { result ->
                    scope.launch {
                        preferencesStore.saveSettings(
                            result.weeklyBudgetDzd,
                            result.wilaya,
                            result.householdServings,
                            result.allergyIds,
                            result.mealSlots,
                            result.dislikedIngredientIds,
                            result.dietPattern,
                            result.maxCookingMinutes,
                            result.budgetBufferPercent,
                            result.commune,
                            result.shoppingChannel,
                            result.preferredShop
                        )
                        refresh()
                    }
                },
                backHandler = { enabled, onBack ->
                    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
                }
            )
        }

        if (showSettings) {
            SettingsDialog(
                appLanguage = appLanguage,
                onLanguageSelected = { languageTag ->
                    val target = normalizeMealsLocaleTag(languageTag)
                    preferencesStore.setAppLanguage(target)
                    preferencesStore.preferences.first {
                        normalizeMealsLocaleTag(it.appLanguage) == target
                    }
                },
                onDismiss = { showSettings = false },
                onOpenHouseholdSettings = {
                    showSettings = false
                    showHouseholdSettings = true
                },
                onRestartOnboarding = {
                    scope.launch {
                        // Reopen the first-run flow without clearing the household's saved data.
                        preferencesStore.restartOnboarding()
                        selectedTab = AppTab.Plan
                        showSettings = false
                    }
                }
            )
        }

        if (showHouseholdSettings) {
            HouseholdPreferencesDialog(
                preferences = preferences,
                ingredients = snapshot.ingredients,
                isFirstSetup = false,
                onDismiss = { showHouseholdSettings = false },
                onQuickStart = { budget, servings ->
                    scope.launch {
                        preferencesStore.saveSettings(
                            budget,
                            preferences.wilaya,
                            servings,
                            emptySet(),
                            preferences.mealSlots,
                            emptySet(),
                            "none",
                            preferences.maxCookingMinutes,
                            preferences.budgetBufferPercent,
                            preferences.commune,
                            preferences.shoppingChannel,
                            preferences.preferredShop
                        )
                        showHouseholdSettings = false
                        refresh()
                    }
                },
                onSave = { budget, wilaya, servings, allergies, slots, dislikes, diet, maxMinutes, buffer, commune, shoppingChannel, preferredShop ->
                    scope.launch {
                        preferencesStore.saveSettings(
                            budget, wilaya, servings, allergies, slots, dislikes, diet, maxMinutes, buffer,
                            commune, shoppingChannel, preferredShop
                        )
                        showHouseholdSettings = false
                        refresh()
                    }
                }
            )
        }

        if (showRecipeCorpusTester && BuildConfig.DEBUG) {
            Dialog(
                onDismissRequest = { showRecipeCorpusTester = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                RecipeCorpusTesterScreen(
                    recipes = recipeCorpusRecipes,
                    isLoading = recipeCorpusLoading,
                    loadError = recipeCorpusLoadError,
                    translations = recipeCorpusTranslations,
                    translatingKeys = recipeCorpusTranslating,
                    translationErrors = recipeCorpusTranslationErrors,
                    onSelectRecipe = { recipe ->
                        if (recipe.key !in recipeCorpusTranslations) {
                            scope.launch {
                                val cached = recipeCorpusRepository.loadCachedTranslation(recipe)
                                if (cached != null) {
                                    recipeCorpusTranslations = recipeCorpusTranslations + (recipe.key to cached)
                                }
                            }
                        }
                    },
                    onTranslate = { recipe ->
                        if (recipe.key !in recipeCorpusTranslating) {
                            scope.launch {
                                recipeCorpusTranslating = recipeCorpusTranslating + recipe.key
                                recipeCorpusTranslationErrors = recipeCorpusTranslationErrors - recipe.key
                                try {
                                    val translation = recipeCorpusRepository.translateAndCache(recipe)
                                    recipeCorpusTranslations = recipeCorpusTranslations + (recipe.key to translation)
                                } catch (error: Exception) {
                                    recipeCorpusTranslationErrors = recipeCorpusTranslationErrors +
                                        (recipe.key to (error.message ?: "تعذرت ترجمة الوصفة."))
                                } finally {
                                    recipeCorpusTranslating = recipeCorpusTranslating - recipe.key
                                }
                            }
                        }
                    },
                    onClose = { showRecipeCorpusTester = false }
                )
            }
        }

        if (showUpdateDialog) {
            MealsBottomSheet(
                titleText = "تحديث التطبيق",
                subtitle = "الإصدار الحالي ${BuildConfig.VERSION_NAME}",
                onDismissRequest = { showUpdateDialog = false }
            ) {
                MealsCard(
                    containerColor = dz.meals.app.ui.theme.WarmSurface,
                    borderColor = null,
                    contentPadding = 16.dp
                ) {
                    Text(
                        updateStatus,
                        color = dz.meals.app.ui.theme.Ink,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    availableUpdate?.let { update ->
                        Text(
                            "الإصدار الجديد: ${update.version}",
                            color = dz.meals.app.ui.theme.Ink,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                ) {
                    MealsPrimaryButton(
                        label = if (isCheckingForUpdates) "جارٍ التحقق..." else "فحص التحديثات",
                        onClick = { checkForAppUpdates() },
                        modifier = Modifier.weight(1f),
                        enabled = !isCheckingForUpdates,
                        icon = MealsIconName.Refresh
                    )
                    MealsSecondaryButton(
                        label = "تنزيل",
                        onClick = { openLatestApk() },
                        modifier = Modifier.weight(1f),
                        icon = MealsIconName.Download
                    )
                }
            }
        }

        if (showExitConfirmation) {
            MealsBottomSheet(
                titleText = "الخروج من Midati؟",
                subtitle = "هل تريد إغلاق التطبيق الآن؟",
                onDismissRequest = { showExitConfirmation = false },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(9.dp)
                    ) {
                        MidatiBrandLogo(
                            modifier = Modifier.padding(vertical = 2.dp),
                            iconSize = 58.dp
                        )
                        Text(
                            "ستبقى خطتك ومخزونك محفوظين على هذا الجهاز.",
                            color = dz.meals.app.ui.theme.MutedInk,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmLabel = "خروج",
                onConfirm = onExit,
                confirmActionClosesSheet = true,
                dismissLabel = "البقاء",
                onDismissAction = { showExitConfirmation = false },
                dismissActionClosesSheet = true,
                showCloseButton = false
            )
        }


        languageTransitionTarget?.let { targetLanguage ->
            LanguageTransitionOverlay(
                targetLanguage = targetLanguage,
                activeLanguage = appLanguage,
                onCommitLanguage = { languageTag ->
                    preferencesStore.setAppLanguage(languageTag)
                },
                onFinished = {
                    if (languageTransitionTarget == targetLanguage) {
                        languageTransitionTarget = null
                    }
                }
            )
        }
        }


    }
}

@Composable
private fun LanguageTransitionOverlay(
    targetLanguage: String,
    activeLanguage: String,
    onCommitLanguage: suspend (String) -> Unit,
    onFinished: () -> Unit
) {
    val coverage = remember(targetLanguage) { Animatable(0f) }
    val activeLanguageState = rememberUpdatedState(activeLanguage)
    var commitFailed by remember(targetLanguage) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    BackHandler(enabled = true, onBack = {})

    LaunchedEffect(targetLanguage) {
        coverage.snapTo(0f)
        coverage.animateTo(
            targetValue = 1f,
            animationSpec = tween(560, easing = FastOutSlowInEasing)
        )

        try {
            // Persist only after the old-language UI is fully covered.
            onCommitLanguage(targetLanguage)
            snapshotFlow { activeLanguageState.value }
                .first { normalizeMealsLocaleTag(it) == normalizeMealsLocaleTag(targetLanguage) }

            // Let direction, text shaping and screen measurements settle behind the glass.
            withFrameNanos { }
            withFrameNanos { }
            delay(260)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            commitFailed = true
            delay(760)
        }

        coverage.animateTo(
            targetValue = 0f,
            animationSpec = tween(520, easing = FastOutSlowInEasing)
        )
        onFinished()
    }

    val isTargetLanguageActive =
        normalizeMealsLocaleTag(activeLanguage) == normalizeMealsLocaleTag(targetLanguage)
    val statusMessage = when {
        commitFailed -> "تعذر تغيير اللغة"
        isTargetLanguageActive -> "تم تغيير اللغة"
        else -> "جارٍ تغيير اللغة..."
    }
    val supportingMessage = when {
        commitFailed -> "حاولي مجددًا من إعدادات التطبيق."
        isTargetLanguageActive -> "أصبحت Midati جاهزة بلغتك المفضلة."
        else -> "نعيد ترتيب الواجهة واتجاهها لتناسب لغتك."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            )
            .graphicsLayer {
                alpha = coverage.value
                val settle = 1.025f - (0.025f * coverage.value)
                scaleX = settle
                scaleY = settle
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dz.meals.app.ui.theme.Paper,
                        Cream,
                        dz.meals.app.ui.theme.WarmSurface
                    )
                )
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        MealsLiquidGlassLoadingExperience(
            title = statusMessage,
            message = supportingMessage,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = coverage.value.coerceIn(0f, 1f)
                    val breathe = 0.985f + (0.015f * coverage.value)
                    scaleX = breathe
                    scaleY = breathe
                }
        )
    }
}

@Composable
private fun SettingsDialog(
    appLanguage: String,
    onLanguageSelected: suspend (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenHouseholdSettings: () -> Unit,
    onRestartOnboarding: () -> Unit
) {
    val dismissSheet = mealsSheetDismissAction()
    var languageChangeTarget by remember { mutableStateOf<String?>(null) }
    var operationTarget by remember { mutableStateOf<String?>(null) }
    var languageChangeError by remember { mutableStateOf<String?>(null) }
    var closeAfterLoadingExit by remember { mutableStateOf(false) }
    val currentAppLanguage by rememberUpdatedState(appLanguage)
    val isLanguageChanging = languageChangeTarget != null

    // The sheet calls this effect only after its content has faded away, the
    // same glass surface has expanded, and the loading experience has entered.
    LaunchedEffect(operationTarget) {
        val target = operationTarget ?: return@LaunchedEffect
        try {
            onLanguageSelected(target)
            snapshotFlow { normalizeMealsLocaleTag(currentAppLanguage) }
                .first { it == normalizeMealsLocaleTag(target) }

            // Wait for locale direction, typography and layout to commit before reversing.
            withFrameNanos { }
            withFrameNanos { }
            closeAfterLoadingExit = true
            languageChangeTarget = null
            operationTarget = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            languageChangeError = "تعذر تغيير اللغة. حاولي مرة أخرى."
            languageChangeTarget = null
            operationTarget = null
        }
    }

    MealsBottomSheet(
        titleText = "الإعدادات",
        subtitle = "إعدادات تجربتك على Midati",
        onDismissRequest = { if (!isLanguageChanging) onDismiss() },
        isLoading = isLanguageChanging,
        expandToFullScreenWhileLoading = true,
        restoreContentAfterLoadingExit = !closeAfterLoadingExit,
        onLoadingStarted = {
            if (operationTarget == null) {
                operationTarget = languageChangeTarget
            }
        },
        onLoadingExitFinished = {
            if (closeAfterLoadingExit) {
                closeAfterLoadingExit = false
                dismissSheet { onDismiss() }
            }
        },
        loadingContent = {
            MealsLiquidGlassLoadingExperience(
                title = "جارٍ تغيير اللغة",
                message = "نجهّز Midati بلغتك الجديدة.",
                modifier = Modifier.fillMaxSize()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp)
        ) {
            languageChangeError?.let { error ->
                Text(
                    text = mealsText(error),
                    color = dz.meals.app.ui.theme.TextAccent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
            }

            SettingsSectionHeading("التفضيلات")

            Text(
                text = mealsText("لغة التطبيق"),
                color = dz.meals.app.ui.theme.Ink,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                text = mealsText("لغة الواجهة المعتمدة حاليًا"),
                color = dz.meals.app.ui.theme.MutedInk,
                style = MaterialTheme.typography.bodySmall
            )
            MealsSubtabsIndicator(
                items = listOf("العربية", "English"),
                selected = if (appLanguage.startsWith("en", ignoreCase = true)) "English" else "العربية",
                onSelect = { selected ->
                    val targetLanguage = if (selected == "English") "en" else "ar-DZ"
                    if (
                        languageChangeTarget == null &&
                        normalizeMealsLocaleTag(targetLanguage) != normalizeMealsLocaleTag(appLanguage)
                    ) {
                        languageChangeError = null
                        closeAfterLoadingExit = false
                        languageChangeTarget = normalizeMealsLocaleTag(targetLanguage)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            SettingsHairline()
            SettingsPreferenceRow(
                title = "المظهر",
                description = "التبديل بين الوضع الفاتح والداكن",
                value = "فاتح",
                icon = MealsIconName.Settings,
                showSwitch = true
            )

            SettingsSectionHeading("تفضيلات التخطيط")
            SettingsActionRow(
                title = "تفضيلات الأسرة",
                description = "الميزانية والوجبات والحساسية وعدد الحصص",
                icon = MealsIconName.Wallet,
                onClick = onOpenHouseholdSettings
            )

            SettingsSectionHeading("حول Midati")
            SettingsUnavailableLink(
                title = "موقع Midati",
                icon = MealsIconName.Cookbook
            )
            SettingsUnavailableLink(
                title = "سياسة الخصوصية",
                icon = MealsIconName.Lock
            )
            SettingsUnavailableLink(
                title = "شروط الاستخدام",
                icon = MealsIconName.Info
            )

            SettingsSectionHeading("إعداد التطبيق")
            MealsPrimaryButton(
                label = "إعادة بدء التهيئة",
                onClick = onRestartOnboarding,
                modifier = Modifier.fillMaxWidth(),
                icon = MealsIconName.Refresh
            )
            Text(
                text = "ستُفتح خطوات البداية مجددًا، مع الاحتفاظ بخطتك وبياناتك المحفوظة.",
                color = dz.meals.app.ui.theme.MutedInk,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 3.dp)
            )
        }
    }
}

@Composable
private fun SettingsSectionHeading(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 7.dp, bottom = 1.dp),
        color = dz.meals.app.ui.theme.BodyInk,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
    )
}

@Composable
private fun SettingsPreferenceRow(
    title: String,
    description: String,
    value: String,
    glyph: String? = null,
    icon: MealsIconName? = null,
    showSwitch: Boolean = false,
    showSelector: Boolean = false
) {
    val layoutDirection = LocalLayoutDirection.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(dz.meals.app.ui.theme.WarmSurface),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            if (glyph != null) {
                Text(
                    text = glyph,
                    color = dz.meals.app.ui.theme.TextAccent,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                )
            } else if (icon != null) {
                MealsIcon(icon, contentDescription = null, modifier = Modifier.size(27.dp))
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp)
        ) {
            Text(
                title,
                color = dz.meals.app.ui.theme.Ink,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                description,
                color = dz.meals.app.ui.theme.MutedInk,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.End,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            when {
                showSwitch -> Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        value,
                        color = dz.meals.app.ui.theme.BodyInk,
                        style = MaterialTheme.typography.labelMedium
                    )
                    SettingsDisabledSwitch()
                }
                showSelector -> Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(dz.meals.app.ui.theme.WarmSurface)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        value,
                        color = dz.meals.app.ui.theme.BodyInk,
                        style = MaterialTheme.typography.labelMedium
                    )
                    MealsIcon(
                        MealsIconName.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).graphicsLayer {
                            rotationZ = if (layoutDirection == LayoutDirection.Rtl) 270f else 90f
                        }
                    )
                }
                else -> Text(
                    value,
                    color = dz.meals.app.ui.theme.BodyInk,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            SettingsComingSoonTag()
        }
    }
}

@Composable
private fun SettingsDisabledSwitch() {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 23.dp)
            .clip(CircleShape)
            .background(dz.meals.app.ui.theme.Border.copy(alpha = 0.92f)),
        contentAlignment = androidx.compose.ui.Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(17.dp)
                .clip(CircleShape)
                .background(dz.meals.app.ui.theme.Paper)
        )
    }
}

@Composable
private fun SettingsComingSoonTag() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(dz.meals.app.ui.theme.SoftOrange)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = "قريبًا",
            color = dz.meals.app.ui.theme.TextAccent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsUnavailableLink(
    title: String,
    icon: MealsIconName
) {
    MealsTextButton(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        enabled = false
    ) {
        MealsIcon(icon, contentDescription = null, modifier = Modifier.size(23.dp))
        Text(
            title,
            color = dz.meals.app.ui.theme.MutedInk,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        SettingsComingSoonTag()
        MealsIcon(
            MealsIconName.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    icon: MealsIconName,
    onClick: () -> Unit
) {
    MealsTextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        MealsIcon(icon, contentDescription = null, modifier = Modifier.size(25.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp)
        ) {
            Text(
                title,
                color = dz.meals.app.ui.theme.Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                description,
                color = dz.meals.app.ui.theme.MutedInk,
                style = MaterialTheme.typography.bodySmall
            )
        }
        MealsIcon(
            MealsIconName.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsHairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .height(1.dp)
            .background(dz.meals.app.ui.theme.Border.copy(alpha = 0.8f))
    )
}

@Composable
private fun HouseholdPreferencesDialog(
    preferences: AppPreferences,
    onDismiss: () -> Unit,
    ingredients: List<dz.meals.app.data.IngredientMaster>,
    isFirstSetup: Boolean,
    onQuickStart: (Int, Int) -> Unit,
    onSave: (Int, String, Int, Set<String>, Set<String>, Set<String>, String, Int, Int, String, String, String) -> Unit
) {
    val appLanguage = LocalMealsLocaleTag.current
    var step by rememberSaveable { mutableStateOf(0) }
    var noKnownAllergies by rememberSaveable { mutableStateOf(false) }
    var budgetText by rememberSaveable(preferences.weeklyBudgetDzd) { mutableStateOf(preferences.weeklyBudgetDzd.toString()) }
    var wilayaText by rememberSaveable(preferences.wilaya) { mutableStateOf(preferences.wilaya) }
    var communeText by rememberSaveable(preferences.commune) { mutableStateOf(preferences.commune) }
    var shoppingChannel by rememberSaveable(preferences.shoppingChannel) { mutableStateOf(preferences.shoppingChannel) }
    var preferredShop by rememberSaveable(preferences.preferredShop) { mutableStateOf(preferences.preferredShop) }
    var servingsText by rememberSaveable(preferences.householdServings) { mutableStateOf(preferences.householdServings.toString()) }
    var allergies by rememberSaveable(preferences.allergyIds) { mutableStateOf(preferences.allergyIds) }
    var slots by rememberSaveable(preferences.mealSlots) { mutableStateOf(preferences.mealSlots) }
    var dislikes by rememberSaveable(preferences.dislikedIngredientIds) { mutableStateOf(preferences.dislikedIngredientIds) }
    var diet by rememberSaveable(preferences.dietPattern) { mutableStateOf(preferences.dietPattern) }
    var maxMinutes by rememberSaveable(preferences.maxCookingMinutes) { mutableStateOf(preferences.maxCookingMinutes.toString()) }
    var buffer by rememberSaveable(preferences.budgetBufferPercent) { mutableStateOf(preferences.budgetBufferPercent.toString()) }
    var searchIngredient by rememberSaveable { mutableStateOf("") }

    val validStep = when (step) {
        0 -> (budgetText.toIntOrNull() ?: 0) > 0 && (servingsText.toIntOrNull() ?: 0) in 1..12
        1 -> slots.isNotEmpty()
        3 -> (maxMinutes.toIntOrNull() ?: 0) in 10..240 && (buffer.toIntOrNull() ?: -1) in 0..30
        else -> true
    }

    MealsBottomSheet(
        titleText = if (isFirstSetup) "لنجهّز أسبوعك معاً" else "إعدادات الأسرة",
        subtitle = listOf("الميزانية والأسرة", "الوجبات", "ما يناسب عائلتك", "تفضيلات الطبخ", "مكان التسوق")[step],
        onDismissRequest = { if (!isFirstSetup) onDismiss() },
        confirmLabel = if (step == 4) "حفظ وابدأ" else "التالي",
        onConfirm = {
            if (step < 4) {
                step += 1
            } else {
                val budget = budgetText.toIntOrNull()
                val servings = servingsText.toIntOrNull()
                if (budget != null && servings != null && servings in 1..12) {
                    onSave(
                        budget, wilayaText, servings, allergies, slots, dislikes, diet,
                        maxMinutes.toIntOrNull() ?: 60, buffer.toIntOrNull() ?: 5,
                        communeText, shoppingChannel, preferredShop
                    )
                }
            }
        },
        confirmActionClosesSheet = step == 4,
        confirmEnabled = validStep,
        dismissLabel = when {
            step > 0 -> "السابق"
            isFirstSetup -> null
            else -> "إغلاق"
        },
        onDismissAction = { if (step > 0) step -= 1 else onDismiss() },
        dismissActionClosesSheet = step == 0,
        showCloseButton = !isFirstSetup
    ) {
        MealsProgressBar(
            progress = (step + 1) / 5f,
            label = "الخطوة " + (step + 1) + " من 5"
        )

        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(13.dp)
        ) {
            when (step) {
                0 -> {
                    MealsCard(
                        containerColor = dz.meals.app.ui.theme.SoftPink,
                        borderColor = null,
                        contentPadding = 16.dp
                    ) {
                        Text(
                            "إعداد بسيط لمرة واحدة، وبعدها نرتّب لكِ الوجبات وقائمة التسوق تلقائياً.",
                            color = dz.meals.app.ui.theme.Ink,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "لا نحتاج إلى حساب أو رقم هاتف. تبقى تفضيلاتك على هذا الجهاز.",
                            color = dz.meals.app.ui.theme.MutedInk,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text("كم ميزانيتك للأسبوع؟", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { budgetText = it.filter(Char::isDigit).take(9) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("الميزانية بالدينار الجزائري") },
                        suffix = { Text("دج") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
                    )
                    Text("كم شخصاً نطبخ له عادة؟", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = servingsText,
                        onValueChange = { servingsText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("عدد الحصص") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
                    )
                    if (isFirstSetup) {
                        MealsCheckRow(
                            title = "لا توجد حساسية غذائية معروفة",
                            supportingText = "إن وُجدت حساسية أو كان هناك شك، أكملي الخطوات التالية أولاً.",
                            checked = noKnownAllergies,
                            onCheckedChange = { noKnownAllergies = it }
                        )
                        val dismissSheet = mealsSheetDismissAction()
                        MealsPrimaryButton(
                            label = "ابدئي الآن",
                            onClick = {
                                val budget = budgetText.toIntOrNull()
                                val servings = servingsText.toIntOrNull()
                                if (noKnownAllergies && budget != null && budget > 0 && servings != null && servings in 1..12) {
                                    dismissSheet { onQuickStart(budget, servings) }
                                }
                            },
                            enabled = noKnownAllergies && validStep,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "سنستخدم اختيارات افتراضية لبقية التفضيلات. يمكنك تعديلها لاحقاً من الإعدادات.",
                            color = dz.meals.app.ui.theme.MutedInk,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                1 -> {
                    Text("ما الوجبات التي تحبين التخطيط لها؟", style = MaterialTheme.typography.titleMedium)
                    Text("اختاري ما تحتاجينه فقط، ويمكنك تغييره لاحقاً.", color = dz.meals.app.ui.theme.MutedInk, style = MaterialTheme.typography.bodySmall)
                    MealsCheckRow(
                        title = "الفطور",
                        supportingText = "وجبة الصباح",
                        checked = "breakfast" in slots,
                        onCheckedChange = { slots = if (it) slots + "breakfast" else slots - "breakfast" }
                    )
                    MealsCheckRow(
                        title = "الغداء",
                        supportingText = "الوجبة الرئيسية",
                        checked = "lunch" in slots,
                        onCheckedChange = { slots = if (it) slots + "lunch" else slots - "lunch" }
                    )
                    MealsCheckRow(
                        title = "العشاء",
                        supportingText = "وجبة المساء",
                        checked = "dinner" in slots,
                        onCheckedChange = { slots = if (it) slots + "dinner" else slots - "dinner" }
                    )
                }
                2 -> {
                    Text("هل هناك أطعمة يجب تجنبها؟", style = MaterialTheme.typography.titleMedium)
                    Text("نستخدم هذه الاختيارات لاستبعاد الوصفات التي قد لا تناسب عائلتك.", color = dz.meals.app.ui.theme.MutedInk, style = MaterialTheme.typography.bodySmall)
                    listOf(
                        "gluten" to "الغلوتين",
                        "egg" to "البيض",
                        "milk" to "الحليب",
                        "tree_nuts" to "المكسرات",
                        "celery" to "الكرفس",
                        "sulphites" to "الكبريتيت",
                        "fish" to "السمك",
                        "mustard" to "الخردل",
                        "crustaceans" to "القشريات",
                        "soy" to "فول الصويا",
                        "sesame" to "السمسم",
                        "molluscs" to "الرخويات",
                        "peanuts" to "الفول السوداني"
                    ).forEach { (id, label) ->
                        MealsCheckRow(
                            title = label,
                            checked = id in allergies,
                            onCheckedChange = { allergies = if (it) allergies + id else allergies - id }
                        )
                    }
                    Text("النمط الغذائي", style = MaterialTheme.typography.titleMedium)
                    listOf(
                        "none" to "عادي",
                        "vegetarian" to "نباتي",
                        "vegan" to "نباتي صرف"
                    ).forEach { (id, label) ->
                        MealsChoiceTile(
                            title = label,
                            selected = diet == id,
                            onClick = { diet = id },
                            icon = MealsIconName.Cookbook
                        )
                    }
                }
                3 -> {
                    Text("هل توجد مكونات تفضلين تقليلها؟", style = MaterialTheme.typography.titleMedium)
                    Text("هذه تفضيلات مرنة، وليست قيود حساسية.", color = dz.meals.app.ui.theme.MutedInk, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = searchIngredient,
                        onValueChange = { searchIngredient = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ابحثي عن مكوّن") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
                    )
                    ingredients.filter { ingredient ->
                        !ingredient.id.startsWith("orc-ing-") && (
                            searchIngredient.isBlank() ||
                                ingredient.nameAr.contains(searchIngredient, true) ||
                                mealsText(ingredient.nameAr, appLanguage).contains(searchIngredient, true) ||
                                ingredient.nameFr.contains(searchIngredient, true) ||
                                ingredient.aliases.any { it.contains(searchIngredient, true) }
                        )
                    }.take(10).forEach { ingredient ->
                        MealsCheckRow(
                            title = mealsText(ingredient.nameAr, appLanguage),
                            supportingText = ingredient.nameFr,
                            checked = ingredient.id in dislikes,
                            onCheckedChange = { checked ->
                                dislikes = if (checked) dislikes + ingredient.id else dislikes - ingredient.id
                            }
                        )
                    }
                    Text("كم دقيقة تفضلين لقضاءها في الطبخ؟", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = maxMinutes,
                        onValueChange = { maxMinutes = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("الوقت الأقصى بالدقائق") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
                    )
                    OutlinedTextField(
                        value = buffer,
                        onValueChange = { buffer = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("هامش أمان للميزانية (%)") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
                    )
                }
                4 -> {
                    Text("أين تتسوقين عادة؟", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = wilayaText,
                        onValueChange = { wilayaText = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("الولاية") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
                    )
                    OutlinedTextField(
                        value = communeText,
                        onValueChange = { communeText = it.take(60) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("البلدية (اختياري)") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
                    )
                    Text("مكان التسوق المعتاد", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp)
                    ) {
                        listOf(
                            "market" to "السوق",
                            "grocery" to "بقالة",
                            "supermarket" to "متجر كبير",
                            "butcher" to "ملحمة",
                            "poultry" to "دجاج",
                            "fishmonger" to "سمك",
                            "bakery" to "مخبزة",
                            "other" to "أخرى"
                        ).forEach { channel ->
                            androidx.compose.material3.FilterChip(
                                selected = shoppingChannel == channel.first,
                                onClick = { shoppingChannel = channel.first },
                                label = { Text(channel.second) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = preferredShop,
                        onValueChange = { preferredShop = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("اسم المحل المفضل (اختياري)") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
                    )
                    MealsCard(
                        containerColor = dz.meals.app.ui.theme.WarmSurface,
                        borderColor = null,
                        contentPadding = 14.dp
                    ) {
                        Text(
                            "هذه المعلومات تساعدنا على تنظيم قائمة التسوق. لا نستخدم موقعك تلقائياً.",
                            color = dz.meals.app.ui.theme.Ink,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
