package dz.meals.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dz.meals.app.data.AppPreferences
import dz.meals.app.data.MealsRepository
import dz.meals.app.data.MealsSnapshot
import dz.meals.app.data.PreferencesStore
import dz.meals.app.data.currentWeekStartIso
import dz.meals.app.ui.screens.PantryScreen
import dz.meals.app.ui.screens.PlanScreen
import dz.meals.app.ui.screens.RecipesScreen
import dz.meals.app.ui.screens.ShoppingScreen
import dz.meals.app.ui.theme.Cream
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class AppTab(val title: String) {
    Plan("الخطة"),
    Recipes("الوصفات"),
    Shopping("المشتريات"),
    Pantry("المخزون")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsApp() {
    val context = LocalContext.current
    val preferencesStore = remember(context) { PreferencesStore(context) }
    val repository = remember(context) { MealsRepository(context) }
    val preferences by preferencesStore.preferences.collectAsState(initial = AppPreferences())
    val scope = rememberCoroutineScope()
    val weekStartIso = remember { currentWeekStartIso() }
    val weekStart = remember(weekStartIso) { LocalDate.parse(weekStartIso) }

    var snapshot by remember { mutableStateOf(MealsSnapshot()) }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Plan) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var generationMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        snapshot = repository.snapshot(weekStartIso)
    }

    fun perform(action: suspend () -> Unit) {
        scope.launch {
            action()
            refresh()
        }
    }

    LaunchedEffect(repository, weekStartIso) {
        repository.ensureInitialized()
        refresh()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = Cream,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("مائدتي", style = MaterialTheme.typography.titleLarge)
                            Text(selectedTab.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "الإعدادات")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = selectedTab == AppTab.Plan,
                        onClick = { selectedTab = AppTab.Plan },
                        icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                        label = { Text("الخطة") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.Recipes,
                        onClick = { selectedTab = AppTab.Recipes },
                        icon = { Icon(Icons.Outlined.RestaurantMenu, contentDescription = null) },
                        label = { Text("الوصفات") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.Shopping,
                        onClick = { selectedTab = AppTab.Shopping },
                        icon = { Icon(Icons.Outlined.ShoppingCart, contentDescription = null) },
                        label = { Text("المشتريات") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppTab.Pantry,
                        onClick = { selectedTab = AppTab.Pantry },
                        icon = { Icon(Icons.Outlined.Kitchen, contentDescription = null) },
                        label = { Text("المخزون") }
                    )
                }
            }
        ) { innerPadding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                when (selectedTab) {
                    AppTab.Plan -> PlanScreen(
                        weekStart = weekStart,
                        preferences = preferences,
                        snapshot = snapshot,
                        isGenerating = isGenerating,
                        onGenerate = {
                            scope.launch {
                                isGenerating = true
                                try {
                                    val result = repository.generateWeek(
                                        weekStartIso = weekStartIso,
                                        weeklyBudgetDzd = preferences.weeklyBudgetDzd,
                                        householdServings = preferences.householdServings,
                                        allergyIds = preferences.allergyIds
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
                        onAdjustServings = { id, delta -> perform { repository.adjustMealServings(id, weekStartIso, delta) } }
                    )
                    AppTab.Recipes -> RecipesScreen(
                        recipes = snapshot.recipes,
                        ingredients = snapshot.ingredients,
                        allergyIds = preferences.allergyIds,
                        onAddRecipe = { id ->
                            perform { repository.addRecipeToNextSlot(weekStartIso, id, preferences.householdServings) }
                            selectedTab = AppTab.Plan
                        }
                    )
                    AppTab.Shopping -> ShoppingScreen(
                        snapshot = snapshot,
                        onToggleItem = { id, checked -> perform { repository.toggleShoppingItem(id, checked) } },
                        onAddCustomItem = { name, quantity, unit -> perform { repository.addCustomShoppingItem(name, quantity, unit) } },
                        onDeleteCustomItem = { id -> perform { repository.deleteCustomShoppingItem(id) } }
                    )
                    AppTab.Pantry -> PantryScreen(
                        ingredients = snapshot.ingredients,
                        pantry = snapshot.pantry,
                        onSave = { id, quantity, approximate -> perform { repository.updatePantry(id, quantity, approximate) } },
                        onRemove = { id -> perform { repository.updatePantry(id, null, false) } }
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            preferences = preferences,
            onDismiss = { showSettings = false },
            onSave = { budget, wilaya, servings, allergies ->
                scope.launch {
                    preferencesStore.saveSettings(budget, wilaya, servings, allergies)
                    showSettings = false
                    refresh()
                }
            }
        )
    }

    generationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { generationMessage = null },
            title = { Text("نتيجة إنشاء الخطة") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { generationMessage = null }) { Text("حسناً") } }
        )
    }
}

@Composable
private fun SettingsDialog(
    preferences: AppPreferences,
    onDismiss: () -> Unit,
    onSave: (Int, String, Int, Set<String>) -> Unit
) {
    var budgetText by rememberSaveable(preferences.weeklyBudgetDzd) { mutableStateOf(preferences.weeklyBudgetDzd.toString()) }
    var wilayaText by rememberSaveable(preferences.wilaya) { mutableStateOf(preferences.wilaya) }
    var servingsText by rememberSaveable(preferences.householdServings) { mutableStateOf(preferences.householdServings.toString()) }
    var allergies by rememberSaveable(preferences.allergyIds) { mutableStateOf(preferences.allergyIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات الأسرة والميزانية") },
        text = {
            Column {
                Text("الميزانية الأسبوعية بالدينار الجزائري")
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it.filter(Char::isDigit).take(9) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text("دج") }
                )
                Text("عدد الحصص المعتاد", modifier = Modifier.padding(top = 12.dp))
                OutlinedTextField(
                    value = servingsText,
                    onValueChange = { servingsText = it.filter(Char::isDigit).take(2) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text("الولاية", modifier = Modifier.padding(top = 12.dp))
                OutlinedTextField(value = wilayaText, onValueChange = { wilayaText = it.take(40) }, singleLine = true)
                Text("استبعاد مسببات الحساسية", modifier = Modifier.padding(top = 12.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                listOf("gluten" to "الغلوتين", "egg" to "البيض", "milk" to "الحليب").forEach { (id, label) ->
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = id in allergies,
                            onCheckedChange = { checked ->
                                allergies = if (checked) allergies + id else allergies - id
                            }
                        )
                        Text(label)
                    }
                }
                Text(
                    "قيود الحساسية شروط إلزامية لا يتجاوزها اقتراح الوجبات. لا نطلب حساباً أو رقم هاتف؛ بياناتك تبقى على الجهاز.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val budget = budgetText.toIntOrNull()
                    val servings = servingsText.toIntOrNull()
                    if (budget != null && servings != null && servings in 1..12) {
                        onSave(budget, wilayaText, servings, allergies)
                    }
                },
                enabled = budgetText.toIntOrNull() != null && servingsText.toIntOrNull() in 1..12
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
