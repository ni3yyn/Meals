package dz.meals.app.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dz.meals.app.data.local.MealsDatabase
import kotlinx.coroutines.runBlocking
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FoodCatalogueInstallerTest {

    @Test
    fun freshDatabaseInstallsTheEntireBundledFoodCatalogueAndTaxonomyOnce() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "food-catalogue-" + UUID.randomUUID() + ".db"
        val preferenceName = "food-catalogue-test-" + UUID.randomUUID()
        val database = MealsDatabase.createForTesting(context, databaseName)
        val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val installer = FoodCatalogueInstaller(context, database, preferences)

        try {
            assertEquals(FoodCatalogueInstaller.EXPECTED_ITEM_COUNT, installer.installIfNeeded())

            val dao = database.mealsDao()
            assertEquals(4_649, dao.countFoodItems())
            assertEquals(22, dao.countFoodCategories())
            assertEquals(82, dao.countFoodSubcategories())

            val db = database.openHelper.writableDatabase
            assertEquals(
                0,
                db.query(
                    """
                    SELECT COUNT(*)
                    FROM food_items AS food
                    LEFT JOIN food_categories AS category ON category.id = food.categoryId
                    LEFT JOIN food_subcategories AS subcategory
                      ON subcategory.id = food.subcategoryId
                     AND subcategory.categoryId = food.categoryId
                    WHERE category.id IS NULL OR subcategory.id IS NULL
                    """.trimIndent()
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getInt(0)
                }
            )
            assertEquals(
                0,
                db.query(
                    """
                    SELECT COUNT(*)
                    FROM food_categories
                    WHERE trim(nameAr) = '' OR trim(nameEn) = '' OR trim(nameFr) = ''
                    """.trimIndent()
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getInt(0)
                }
            )
            assertEquals(
                0,
                db.query("SELECT COUNT(*) FROM pragma_foreign_key_check").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getInt(0)
                }
            )

            val foodById = dao.getFoodItems().associateBy { it.id }
            val identityResolver = FoodIdentityResolver(context)
            val seedTaxonomyMismatches = SeedRecipes.ingredients.mapNotNull { seed ->
                val canonicalId = identityResolver.canonicalId(seed.id)
                val canonicalFood = foodById[canonicalId]
                when {
                    canonicalFood == null -> "${seed.id}: missing canonical food $canonicalId"
                    canonicalFood.categoryId != seed.categoryId ||
                        canonicalFood.subcategoryId != seed.subcategoryId ->
                        "${seed.id}: seed=(${seed.categoryId}, ${seed.subcategoryId}) " +
                            "catalogue=(${canonicalFood.categoryId}, ${canonicalFood.subcategoryId})"
                    else -> null
                }
            }
            assertTrue(
                "Seed ingredient taxonomy must match canonical food UUIDs: $seedTaxonomyMismatches",
                seedTaxonomyMismatches.isEmpty()
            )

            val tomatoPaste = foodById["d376b5b9-31c4-4007-bfac-309c1a7648a7"]
            assertEquals("foodcat-sauces", tomatoPaste?.categoryId)
            assertEquals("foodsub-sauces-tomato", tomatoPaste?.subcategoryId)

            // A second app start must be a no-op, not duplicate or recreate rows.
            assertEquals(0, installer.installIfNeeded())
            assertEquals(4_649, dao.countFoodItems())
            assertEquals(22, dao.countFoodCategories())
            assertEquals(82, dao.countFoodSubcategories())
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            preferences.edit().clear().commit()
        }
    }
}
