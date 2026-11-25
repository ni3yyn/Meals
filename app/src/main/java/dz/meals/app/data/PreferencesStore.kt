package dz.meals.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import dz.meals.app.ui.theme.DEFAULT_MEALS_LOCALE_TAG
import dz.meals.app.ui.theme.ENGLISH_MEALS_LOCALE_TAG
import java.io.IOException

private val Context.mealsDataStore: DataStore<Preferences> by preferencesDataStore(name = "meals_preferences")

class PreferencesStore(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.mealsDataStore
    private val foodIdentityResolver = FoodIdentityResolver(appContext)

    private object Keys {
        val weeklyBudget = intPreferencesKey("weekly_budget_dzd")
        val wilaya = stringPreferencesKey("wilaya")
        val commune = stringPreferencesKey("commune")
        val shoppingChannel = stringPreferencesKey("shopping_channel")
        val preferredShop = stringPreferencesKey("preferred_shop")
        val householdServings = intPreferencesKey("household_servings")
        val allergyIds = stringSetPreferencesKey("allergy_ids")
        val mealSlots = stringSetPreferencesKey("meal_slots")
        val dislikedIngredientIds = stringSetPreferencesKey("disliked_ingredient_ids")
        val dietPattern = stringPreferencesKey("diet_pattern")
        val maxCookingMinutes = intPreferencesKey("max_cooking_minutes")
        val budgetBufferPercent = intPreferencesKey("budget_buffer_percent")
        val hasCompletedSetup = booleanPreferencesKey("has_completed_setup")
        val appLanguage = stringPreferencesKey("app_language")
        val kitchenBackground = stringPreferencesKey("kitchen_background")
    }

    val preferences: Flow<AppPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { values ->
            AppPreferences(
                weeklyBudgetDzd = values[Keys.weeklyBudget] ?: 15_000,
                wilaya = values[Keys.wilaya] ?: "الجزائر",
                commune = values[Keys.commune] ?: "",
                shoppingChannel = values[Keys.shoppingChannel] ?: "market",
                preferredShop = values[Keys.preferredShop] ?: "",
                householdServings = values[Keys.householdServings] ?: 3,
                allergyIds = values[Keys.allergyIds] ?: emptySet(),
                mealSlots = (values[Keys.mealSlots] ?: setOf("breakfast", "lunch", "dinner"))
                    .intersect(setOf("breakfast", "lunch", "dinner")),
                dislikedIngredientIds = (values[Keys.dislikedIngredientIds] ?: emptySet())
                    .mapTo(linkedSetOf(), foodIdentityResolver::canonicalId),
                dietPattern = values[Keys.dietPattern] ?: "none",
                maxCookingMinutes = (values[Keys.maxCookingMinutes] ?: 60).coerceIn(10, 240),
                budgetBufferPercent = (values[Keys.budgetBufferPercent] ?: 5).coerceIn(0, 30),
                hasCompletedSetup = values[Keys.hasCompletedSetup] ?: false,
                appLanguage = normalizeStoredLanguage(values[Keys.appLanguage]),
                kitchenBackground = values[Keys.kitchenBackground]
                    ?.takeIf { it == MEALS_BACKGROUND_CLASSIC || it == MEALS_BACKGROUND_PINK || it == MEALS_BACKGROUND_BLUE }
                    ?: MEALS_BACKGROUND_PINK
            )
        }

    suspend fun saveSettings(
        weeklyBudgetDzd: Int,
        wilaya: String,
        householdServings: Int,
        allergyIds: Set<String>,
        mealSlots: Set<String> = setOf("breakfast", "lunch", "dinner"),
        dislikedIngredientIds: Set<String> = emptySet(),
        dietPattern: String = "none",
        maxCookingMinutes: Int = 60,
        budgetBufferPercent: Int = 5,
        commune: String = "",
        shoppingChannel: String = "market",
        preferredShop: String = ""
    ) {
        dataStore.edit { values ->
            values[Keys.weeklyBudget] = weeklyBudgetDzd.coerceAtLeast(0)
            values[Keys.wilaya] = wilaya.trim().ifBlank { "الجزائر" }
            values[Keys.commune] = commune.trim().take(60)
            values[Keys.shoppingChannel] = shoppingChannel.takeIf {
                it in setOf("market", "grocery", "supermarket", "butcher", "poultry", "fishmonger", "bakery", "other")
            } ?: "market"
            values[Keys.preferredShop] = preferredShop.trim().take(80)
            values[Keys.householdServings] = householdServings.coerceIn(1, 12)
            values[Keys.allergyIds] = allergyIds
            values[Keys.mealSlots] = mealSlots.intersect(setOf("breakfast", "lunch", "dinner"))
            values[Keys.dislikedIngredientIds] = dislikedIngredientIds
                .mapTo(linkedSetOf(), foodIdentityResolver::canonicalId)
            values[Keys.dietPattern] = dietPattern.takeIf { it in setOf("none", "vegetarian", "vegan") } ?: "none"
            values[Keys.maxCookingMinutes] = maxCookingMinutes.coerceIn(10, 240)
            values[Keys.budgetBufferPercent] = budgetBufferPercent.coerceIn(0, 30)
            values[Keys.hasCompletedSetup] = true
        }
    }

    suspend fun restartOnboarding() {
        // Keep saved preferences and meal data; only reopen first-run setup.
        dataStore.edit { values ->
            values[Keys.hasCompletedSetup] = false
        }
    }
    
    suspend fun setAppLanguage(languageTag: String) {
        val normalized = when (languageTag.lowercase().substringBefore('-')) {
            "en" -> ENGLISH_MEALS_LOCALE_TAG
            "ar" -> DEFAULT_MEALS_LOCALE_TAG
            else -> return
        }
        dataStore.edit { values -> values[Keys.appLanguage] = normalized }
    }

    suspend fun setKitchenBackground(background: String) {
        val normalized = background.takeIf {
            it == MEALS_BACKGROUND_CLASSIC || it == MEALS_BACKGROUND_PINK
        } ?: MEALS_BACKGROUND_PINK
        dataStore.edit { values -> values[Keys.kitchenBackground] = normalized }
    }

    private fun normalizeStoredLanguage(languageTag: String?): String =
        when (languageTag?.lowercase()?.substringBefore('-')) {
            "en" -> ENGLISH_MEALS_LOCALE_TAG
            "ar" -> DEFAULT_MEALS_LOCALE_TAG
            else -> DEFAULT_MEALS_LOCALE_TAG
        }
}
