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
        val plannedRecipeIds = stringSetPreferencesKey("planned_recipe_ids")
        val checkedIngredientIds = stringSetPreferencesKey("checked_ingredient_ids")
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
                plannedRecipeIds = values[Keys.plannedRecipeIds] ?: emptySet(),
                checkedIngredientIds = values[Keys.checkedIngredientIds] ?: emptySet()
            )
        }

    suspend fun saveSettings(weeklyBudgetDzd: Int, wilaya: String) {
        dataStore.edit { values ->
            values[Keys.weeklyBudget] = weeklyBudgetDzd.coerceAtLeast(0)
            values[Keys.wilaya] = wilaya.trim().ifBlank { "الجزائر" }
        }
    }

    suspend fun toggleRecipe(recipeId: String) {
        dataStore.edit { values ->
            val selected = values[Keys.plannedRecipeIds]?.toMutableSet() ?: mutableSetOf()
            if (!selected.add(recipeId)) selected.remove(recipeId)
            values[Keys.plannedRecipeIds] = selected

            // Remove checked states for ingredients no longer present in the active plan.
            val activeIngredientKeys = SeedRecipes.all
                .filter { it.id in selected }
                .flatMap { recipe -> recipe.ingredients.map { it.id + "::" + it.unit } }
                .toSet()
            val checked = values[Keys.checkedIngredientIds]?.filterTo(mutableSetOf()) {
                it in activeIngredientKeys
            } ?: mutableSetOf()
            values[Keys.checkedIngredientIds] = checked
        }
    }

    suspend fun toggleIngredient(ingredientId: String) {
        dataStore.edit { values ->
            val checked = values[Keys.checkedIngredientIds]?.toMutableSet() ?: mutableSetOf()
            if (!checked.add(ingredientId)) checked.remove(ingredientId)
            values[Keys.checkedIngredientIds] = checked
        }
    }

    suspend fun clearPlan() {
        dataStore.edit { values ->
            values[Keys.plannedRecipeIds] = emptySet()
            values[Keys.checkedIngredientIds] = emptySet()
        }
    }
}
