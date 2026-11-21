package dz.meals.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.mealsDataStore: DataStore<Preferences> by preferencesDataStore(name = "meals_preferences")

class PreferencesStore(context: Context) {
    private val dataStore = context.applicationContext.mealsDataStore

    private object Keys {
        val weeklyBudget = intPreferencesKey("weekly_budget_dzd")
        val wilaya = stringPreferencesKey("wilaya")
        val householdServings = intPreferencesKey("household_servings")
        val allergyIds = stringSetPreferencesKey("allergy_ids")
        val mealSlots = stringSetPreferencesKey("meal_slots")
        val dislikedIngredientIds = stringSetPreferencesKey("disliked_ingredient_ids")
        val dietPattern = stringPreferencesKey("diet_pattern")
        val maxCookingMinutes = intPreferencesKey("max_cooking_minutes")
        val budgetBufferPercent = intPreferencesKey("budget_buffer_percent")
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
                householdServings = values[Keys.householdServings] ?: 3,
                allergyIds = values[Keys.allergyIds] ?: emptySet(),
                mealSlots = (values[Keys.mealSlots] ?: setOf("breakfast", "lunch", "dinner"))
                    .intersect(setOf("breakfast", "lunch", "dinner")),
                dislikedIngredientIds = values[Keys.dislikedIngredientIds] ?: emptySet(),
                dietPattern = values[Keys.dietPattern] ?: "none",
                maxCookingMinutes = (values[Keys.maxCookingMinutes] ?: 60).coerceIn(10, 240),
                budgetBufferPercent = (values[Keys.budgetBufferPercent] ?: 5).coerceIn(0, 30)
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
        budgetBufferPercent: Int = 5
    ) {
        dataStore.edit { values ->
            values[Keys.weeklyBudget] = weeklyBudgetDzd.coerceAtLeast(0)
            values[Keys.wilaya] = wilaya.trim().ifBlank { "الجزائر" }
            values[Keys.householdServings] = householdServings.coerceIn(1, 12)
            values[Keys.allergyIds] = allergyIds
            values[Keys.mealSlots] = mealSlots.intersect(setOf("breakfast", "lunch", "dinner"))
            values[Keys.dislikedIngredientIds] = dislikedIngredientIds
            values[Keys.dietPattern] = dietPattern.takeIf { it in setOf("none", "vegetarian", "vegan") } ?: "none"
            values[Keys.maxCookingMinutes] = maxCookingMinutes.coerceIn(10, 240)
            values[Keys.budgetBufferPercent] = budgetBufferPercent.coerceIn(0, 30)
        }
    }
}
