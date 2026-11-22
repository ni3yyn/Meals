package dz.meals.app.ui

import dz.meals.app.MealsStartupLogger

import android.content.Intent
import android.net.Uri

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dz.meals.app.BuildConfig
import dz.meals.app.data.AppPreferences
import dz.meals.app.data.AppUpdateChecker
import dz.meals.app.data.AppUpdateCheckResult
import dz.meals.app.data.AppUpdateInfo
import dz.meals.app.data.MealsRepository
import dz.meals.app.data.MealsSnapshot
import dz.meals.app.data.PreferencesStore
import dz.meals.app.data.currentWeekStartIso
import dz.meals.app.ui.components.*
import dz.meals.app.ui.icons.MealsIcon
import dz.meals.app.ui.icons.MealsIconName
import dev.chrisbanes.haze.hazeSource
import dz.meals.app.ui.screens.PantryScreen
import dz.meals.app.ui.screens.PlanScreen
import dz.meals.app.ui.screens.RecipesScreen
import dz.meals.app.ui.screens.ShoppingScreen
import dz.meals.app.ui.theme.Cream
import dz.meals.app.ui.theme.DEFAULT_MEALS_LOCALE_TAG
import dz.meals.app.ui.theme.mealsLayoutDirection
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val LATEST_APK_DOWNLOAD_URL = "https://github.com/ni3yyn/Meals/releases/latest/download/meals.apk"

private enum class AppTab(val title: String) {
    Plan("الخطة"),
    Recipes("الوصفات"),
    Shopping("المشتريات"),
    Pantry("المخزون")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsApp(localeTag: String = DEFAULT_MEALS_LOCALE_TAG) {
    val layoutDirection = remember(localeTag) { mealsLayoutDirection(localeTag) }
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MealsAppContent()
    }
}

@Composable
private fun MealsAppContent() {
    MealsStartupLogger.markOnce("MealsAppContent entered")
    val context = LocalContext.current
    val preferencesStore = remember(context) { PreferencesStore(context) }
    val repository = remember(context) { MealsRepository(context) }
    val updateChecker = remember { AppUpdateChecker() }
    // DataStore emits asynchronously. Start with setup marked complete so the temporary
    // default value cannot open first-run onboarding for returning users.
    val preferences by preferencesStore.preferences.collectAsState(
        initial = AppPreferences(hasCompletedSetup = true)
    )
    val scope = rememberCoroutineScope()
    var weekStartIso by rememberSaveable { mutableStateOf(currentWeekStartIso()) }
    val weekStart = remember(weekStartIso) { LocalDate.parse(weekStartIso) }

    var snapshot by remember { mutableStateOf(MealsSnapshot()) }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Plan) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var generationMessage by remember { mutableStateOf<String?>(null) }
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var isCheckingForUpdates by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("تحققي من توفر إصدار أحدث من التطبيق.") }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

    suspend fun refresh() {
        snapshot = repository.snapshot(weekStartIso, preferences.wilaya, preferences.mealSlots)
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
                    "تنزيل مائدتي"
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

    LaunchedEffect(repository, weekStartIso, preferences.wilaya, preferences.mealSlots) {
        MealsStartupLogger.markOnce("Initial repository initialization started")
        repository.ensureInitialized()
        MealsStartupLogger.markOnce("Initial repository initialization completed")
        refresh()
        MealsStartupLogger.markOnce("Initial meal snapshot loaded")
    }

    LaunchedEffect(preferences.hasCompletedSetup) {
        if (!preferences.hasCompletedSetup) showSettings = true
    }

    MealsBackdrop {
        val hazeState = requireNotNull(LocalMealsHazeState.current) {
            "MealsApp must be hosted inside MealsBackdrop so the shared Haze source is available."
        }
        val navigationItems = remember {
            listOf(
                MealsNavigationItem("الخطة", MealsIconName.Plan),
                MealsNavigationItem("الوصفات", MealsIconName.Cookbook),
                MealsNavigationItem("المشتريات", MealsIconName.Basket),
                MealsNavigationItem("المخزون", MealsIconName.Pantry)
            )
        }
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.dp)) {
                            Text("صباح الخير", style = MaterialTheme.typography.titleLarge)
                            Text("مائدتي · " + selectedTab.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    .hazeSource(hazeState, zIndex = 1f)
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
                        onOpenShoppingList = { selectedTab = AppTab.Shopping }
                    )
                    AppTab.Recipes -> RecipesScreen(
                        recipes = snapshot.recipes,
                        ingredients = snapshot.ingredients,
                        allergyIds = preferences.allergyIds,
                        favoriteRecipeIds = snapshot.favoriteRecipeIds,
                        onToggleFavorite = { id -> perform { repository.toggleFavorite(id) } },
                        onAddRecipe = { id ->
                            perform { repository.addRecipeToNextSlot(weekStartIso, id, preferences.householdServings) }
                            selectedTab = AppTab.Plan
                        }
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
                                putExtra(Intent.EXTRA_SUBJECT, "قائمة مشتريات مائدتي")
                                putExtra(Intent.EXTRA_TEXT, body)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(send, "مشاركة قائمة المشتريات"))
                            }
                        },
                        onToggleItem = { id, checked -> perform { repository.toggleShoppingItem(id, checked) } },
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
        if (showSettings) {
            SettingsDialog(
                preferences = preferences,
                ingredients = snapshot.ingredients,
                isFirstSetup = !preferences.hasCompletedSetup,
                onDismiss = { showSettings = false },
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
                        showSettings = false
                        refresh()
                    }
                },
                onSave = { budget, wilaya, servings, allergies, slots, dislikes, diet, maxMinutes, buffer, commune, shoppingChannel, preferredShop ->
                    scope.launch {
                        preferencesStore.saveSettings(
                            budget, wilaya, servings, allergies, slots, dislikes, diet, maxMinutes, buffer,
                            commune, shoppingChannel, preferredShop
                        )
                        showSettings = false
                        refresh()
                    }
                }
            )
        }

        if (showUpdateDialog) {
            MealsBottomSheet(
                titleText = "تحديث التطبيق",
                subtitle = "الإصدار الحالي ${BuildConfig.VERSION_NAME}",
                onDismissRequest = { showUpdateDialog = false }
            ) {
                MealsCard(
                    containerColor = dz.meals.app.ui.theme.SoftGreen,
                    borderColor = null,
                    contentPadding = 16.dp
                ) {
                    Text(
                        updateStatus,
                        color = dz.meals.app.ui.theme.ForestDark,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    availableUpdate?.let { update ->
                        Text(
                            "الإصدار الجديد: ${update.version}",
                            color = dz.meals.app.ui.theme.ForestDark,
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

        generationMessage?.let { message ->
            MealsBottomSheet(
                onDismissRequest = { generationMessage = null },
                title = { Text("نتيجة إنشاء الخطة") },
                text = { Text(message) },
                confirmButton = {
                    MealsPrimaryButton(
                        label = "حسناً",
                        onClick = { generationMessage = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }
        }


}

@Composable
private fun SettingsDialog(
    preferences: AppPreferences,
    onDismiss: () -> Unit,
    ingredients: List<dz.meals.app.data.IngredientMaster>,
    isFirstSetup: Boolean,
    onQuickStart: (Int, Int) -> Unit,
    onSave: (Int, String, Int, Set<String>, Set<String>, Set<String>, String, Int, Int, String, String, String) -> Unit
) {
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
        confirmEnabled = validStep,
        dismissLabel = when {
            step > 0 -> "السابق"
            isFirstSetup -> null
            else -> "إغلاق"
        },
        onDismissAction = { if (step > 0) step -= 1 else onDismiss() },
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
                        suffix = { Text("دج") }
                    )
                    Text("كم شخصاً نطبخ له عادة؟", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = servingsText,
                        onValueChange = { servingsText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("عدد الحصص") }
                    )
                    if (isFirstSetup) {
                        MealsCheckRow(
                            title = "لا توجد حساسية غذائية معروفة",
                            supportingText = "إن وُجدت حساسية أو كان هناك شك، أكملي الخطوات التالية أولاً.",
                            checked = noKnownAllergies,
                            onCheckedChange = { noKnownAllergies = it }
                        )
                        MealsPrimaryButton(
                            label = "ابدئي الآن",
                            onClick = {
                                val budget = budgetText.toIntOrNull()
                                val servings = servingsText.toIntOrNull()
                                if (noKnownAllergies && budget != null && budget > 0 && servings != null && servings in 1..12) {
                                    onQuickStart(budget, servings)
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
                    listOf("gluten" to "الغلوتين", "egg" to "البيض", "milk" to "الحليب").forEach { (id, label) ->
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
                        singleLine = true
                    )
                    ingredients.filter { ingredient ->
                        searchIngredient.isBlank() ||
                            ingredient.nameAr.contains(searchIngredient, true) ||
                            ingredient.nameFr.contains(searchIngredient, true) ||
                            ingredient.aliases.any { it.contains(searchIngredient, true) }
                    }.take(10).forEach { ingredient ->
                        MealsCheckRow(
                            title = ingredient.nameAr,
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
                        label = { Text("الوقت الأقصى بالدقائق") }
                    )
                    OutlinedTextField(
                        value = buffer,
                        onValueChange = { buffer = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("هامش أمان للميزانية (%)") }
                    )
                }
                4 -> {
                    Text("أين تتسوقين عادة؟", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = wilayaText,
                        onValueChange = { wilayaText = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("الولاية") }
                    )
                    OutlinedTextField(
                        value = communeText,
                        onValueChange = { communeText = it.take(60) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("البلدية (اختياري)") }
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
                        singleLine = true
                    )
                    MealsCard(
                        containerColor = dz.meals.app.ui.theme.SoftGreen,
                        borderColor = null,
                        contentPadding = 14.dp
                    ) {
                        Text(
                            "هذه المعلومات تساعدنا على تنظيم قائمة التسوق. لا نستخدم موقعك تلقائياً.",
                            color = dz.meals.app.ui.theme.ForestDark,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
