package dz.meals.app.data

import android.content.Context
import dz.meals.app.data.local.MealsDatabase
import dz.meals.app.data.local.RecipeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlgerianRecipeCatalogueInstallerTest {

    @Test
    fun installsAllAlgerianRecipesIntoRoomWithoutReplacingOtherCatalogueRows() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "algerian-recipes-${UUID.randomUUID()}.db"
        val foodPreferences = context.getSharedPreferences(
            "algerian-food-test-${UUID.randomUUID()}",
            Context.MODE_PRIVATE
        )
        val recipePreferences = context.getSharedPreferences(
            "algerian-recipe-test-${UUID.randomUUID()}",
            Context.MODE_PRIVATE
        )
        val database = MealsDatabase.createForTesting(context, databaseName)
        val foodInstaller = FoodCatalogueInstaller(context, database, foodPreferences)
        val installer = AlgerianRecipeCatalogueInstaller(context, database, recipePreferences)

        try {
            foodInstaller.installIfNeeded()
            val dao = database.mealsDao()
            dao.insertRecipes(
                listOf(
                    RecipeEntity(
                        id = "seed-test-recipe",
                        nameAr = "وصفة اختبار",
                        nameFr = "Test recipe",
                        descriptionAr = "",
                        descriptionFr = "",
                        category = "تقليدي",
                        region = "",
                        durationMinutes = 0,
                        baseServings = 2,
                        difficulty = "سهل",
                        allergensCsv = ""
                    )
                )
            )

            // An unrelated recipe must survive the collection import unchanged.
            val originalSeedCount = dao.getRecipes().size
            assertEquals(1, originalSeedCount)

            assertEquals(AlgerianRecipeCatalogueInstaller.EXPECTED_RECIPE_COUNT, installer.install())
            assertTrue(installer.isInstalled())
            assertEquals(187, dao.countAlgerianCatalogueRecipes())
            assertEquals(2_175, dao.getRecipeIngredients().count {
                it.recipeId.startsWith(AlgerianRecipeCatalogueInstaller.RECIPE_ID_PREFIX)
            })
            assertEquals(1_672, dao.getRecipeSteps().count {
                it.recipeId.startsWith(AlgerianRecipeCatalogueInstaller.RECIPE_ID_PREFIX)
            })
            assertEquals(187, dao.getRecipeAttributions().count {
                it.recipeId.startsWith(AlgerianRecipeCatalogueInstaller.RECIPE_ID_PREFIX)
            })
            assertEquals(originalSeedCount + 187, dao.getRecipes().size)

            val algerianRecipe = dao.getRecipes().first {
                it.id.startsWith(AlgerianRecipeCatalogueInstaller.RECIPE_ID_PREFIX)
            }
            assertTrue(algerianRecipe.nameAr.isNotBlank())
            assertEquals("DZ", algerianRecipe.countryCode)

            val missingNutritionRecipe = dao.getRecipes().first {
                it.id.startsWith(AlgerianRecipeCatalogueInstaller.RECIPE_ID_PREFIX) &&
                    it.caloriesPerServing == null
            }
            assertNull(missingNutritionRecipe.caloriesPerServing)

            val foreignKeyErrors = database.openHelper.writableDatabase
                .query("PRAGMA foreign_key_check")
                .use { cursor ->
                    var count = 0
                    while (cursor.moveToNext()) count++
                    count
                }
            assertEquals(0, foreignKeyErrors)

            // The same revision is a no-op on the next app start.
            assertEquals(0, installer.install())
            assertEquals(187, dao.countAlgerianCatalogueRecipes())
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            foodPreferences.edit().clear().commit()
            recipePreferences.edit().clear().commit()
        }
    }
}
