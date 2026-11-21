package dz.meals.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
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
import dz.meals.app.data.PreferencesStore
import dz.meals.app.data.SeedRecipes
import dz.meals.app.data.buildShoppingList
import dz.meals.app.ui.screens.HomeScreen
import dz.meals.app.ui.screens.RecipesScreen
import dz.meals.app.ui.screens.ShoppingScreen
import dz.meals.app.ui.theme.Cream
import kotlinx.coroutines.launch

private enum class AppTab {
    Home, Recipes, Shopping
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsApp() {
    val context = LocalContext.current
    val store = remember(context) { PreferencesStore(context) }
    val preferences by store.preferences.collectAsState(initial = AppPreferences())
    val scope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val plannedRecipes = remember(preferences.plannedRecipeIds) {
        SeedRecipes.all.filter { it.id in preferences.plannedRecipeIds }
    }
    val shoppingItems = remember(plannedRecipes) { buildShoppingList(plannedRecipes) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = Cream,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("مائدتي", style = MaterialTheme.typography.titleLarge)
                            Text(
                                when (selectedTab) {
                                    AppTab.Home -> "وجباتك وميزانيتك في مكان واحد"
                                    AppTab.Recipes -> "اكتشف وصفات تناسب يومك"
                                    AppTab.Shopping -> "كل ما تحتاجه في قائمة واحدة"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        selected = selectedTab == AppTab.Home,
                        onClick = { selectedTab = AppTab.Home },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                        label = { Text("الرئيسية") }
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
                }
            }
        ) { innerPadding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                when (selectedTab) {
                    AppTab.Home -> HomeScreen(
                        preferences = preferences,
                        plannedRecipes = plannedRecipes,
                        onOpenRecipes = { selectedTab = AppTab.Recipes },
                        onOpenShopping = { selectedTab = AppTab.Shopping },
                        onRemoveRecipe = { id -> scope.launch { store.toggleRecipe(id) } }
                    )
                    AppTab.Recipes -> RecipesScreen(
                        recipes = SeedRecipes.all,
                        plannedRecipeIds = preferences.plannedRecipeIds,
                        onToggleRecipe = { id -> scope.launch { store.toggleRecipe(id) } }
                    )
                    AppTab.Shopping -> ShoppingScreen(
                        items = shoppingItems,
                        checkedIds = preferences.checkedIngredientIds,
                        onToggleItem = { id -> scope.launch { store.toggleIngredient(id) } },
                        onClearPlan = { scope.launch { store.clearPlan() } }
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            preferences = preferences,
            onDismiss = { showSettings = false },
            onSave = { budget, wilaya ->
                scope.launch {
                    store.saveSettings(budget, wilaya)
                    showSettings = false
                }
            }
        )
    }
}

@Composable
private fun SettingsDialog(
    preferences: AppPreferences,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit
) {
    var budgetText by rememberSaveable(preferences.weeklyBudgetDzd) {
        mutableStateOf(preferences.weeklyBudgetDzd.toString())
    }
    var wilayaText by rememberSaveable(preferences.wilaya) {
        mutableStateOf(preferences.wilaya)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات خطتك") },
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
                Text("الولاية", modifier = Modifier.padding(top = 14.dp))
                OutlinedTextField(
                    value = wilayaText,
                    onValueChange = { wilayaText = it.take(40) },
                    singleLine = true,
                    placeholder = { Text("مثلاً: الجلفة") }
                )
                Text(
                    "لا نطلب حساباً أو رقم هاتف. تبقى هذه الإعدادات محفوظة على جهازك.",
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val budget = budgetText.toIntOrNull()
                    if (budget != null) onSave(budget, wilayaText)
                },
                enabled = budgetText.toIntOrNull() != null
            ) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
