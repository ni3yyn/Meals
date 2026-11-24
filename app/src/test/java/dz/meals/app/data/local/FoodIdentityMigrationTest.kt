package dz.meals.app.data.local

import android.database.sqlite.SQLiteDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dz.meals.app.data.FoodIdentityResolver
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FoodIdentityMigrationTest {

    @Test
    fun room13DatabaseMigratesAllIngredientReferencesWithoutDroppingRows() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "food-identity-" + UUID.randomUUID() + ".db"
        val canonicalId = "4bd16c38-41fc-4ac0-a114-5c91c1151f7f"
        val legacyA = "orc-ing-2cf21cee44f130e71246"
        val legacyB = "orc-ing-396f0e4f8d06f7a8b63b"

        createVersion13Database(context.getDatabasePath(databaseName), legacyA, legacyB)

        val roomDatabase = MealsDatabase.createForTesting(context, databaseName)
        try {
            val db = roomDatabase.openHelper.writableDatabase

            assertEquals(17, db.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            })
            // The v14-to-v15 migration creates the generic food master table
            // without deleting or inventing catalogue rows during migration.
            assertEquals(0, count(db, "SELECT COUNT(*) FROM food_items"))
            assertEquals(22, count(db, "SELECT COUNT(*) FROM food_categories"))
            assertEquals(82, count(db, "SELECT COUNT(*) FROM food_subcategories"))
            assertEquals(
                "الألبان والأجبان",
                scalarString(db, "SELECT nameAr FROM food_categories WHERE id = 'foodcat-dairy'")
            )
            assertEquals(0, count(db, "SELECT COUNT(*) FROM pragma_foreign_key_check"))

            // Two recipe rows which converge on one UUID remain two rows with all
            // quantities, units, preparation notes and optional flags intact.
            assertEquals(4, count(db, "SELECT COUNT(*) FROM recipe_ingredients"))
            assertEquals(2, count(db, "SELECT COUNT(DISTINCT ingredientId) FROM recipe_ingredients"))
            assertEquals(6.5, scalarDouble(db, "SELECT SUM(quantityBase) FROM recipe_ingredients"), 0.0)
            val recipeKeys = mutableSetOf<String>()
            db.query("SELECT rowKey FROM recipe_ingredients").use { cursor ->
                while (cursor.moveToNext()) recipeKeys += cursor.getString(0)
            }
            assertEquals(setOf(legacyA, legacyB, "chicken"), recipeKeys)
            assertEquals(canonicalId, scalarString(db, "SELECT ingredientId FROM recipe_ingredients WHERE recipeId = 'focus-test-recipe' LIMIT 1"))

            val hiddenUntilCatalogueInstall = runBlocking {
                roomDatabase.mealsDao().getUnpublishedArabicCatalogueIngredientIds().toSet()
            }
            assertEquals(
                setOf("0fdd586e-27ec-46c1-b407-2b5925054e42"),
                hiddenUntilCatalogueInstall
            )

            // Pantry rows retain distinct primary keys even after their ingredient IDs merge.
            assertEquals(2, count(db, "SELECT COUNT(*) FROM pantry_items WHERE ingredientId = '$canonicalId'"))
            assertTrue(existsNonUniquePantryIndex(db))
            assertEquals(3, count(db, "SELECT COUNT(*) FROM pantry_items"))

            // Price observations, shopping state, cooked deductions and saved price
            // snapshots all retain their records while using the canonical UUID.
            assertEquals(2, count(db, "SELECT COUNT(*) FROM price_observations WHERE ingredientId = '$canonicalId'"))
            assertEquals(2, count(db, "SELECT COUNT(*) FROM plan_price_snapshots WHERE ingredientId = '$canonicalId'"))
            assertEquals(2, count(db, "SELECT COUNT(*) FROM cooked_meal_deductions"))
            assertEquals(1, count(db, "SELECT COUNT(DISTINCT ingredientId) FROM cooked_meal_deductions"))
            assertEquals(2, count(db, "SELECT COUNT(*) FROM cooked_meal_deductions WHERE pantryItemId IS NOT NULL"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM cooked_meal_deductions WHERE pantryItemId = 'pantry-row-a'"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM cooked_meal_deductions WHERE pantryItemId = 'pantry-row-b'"))

            val oldShoppingId = "2025-01-06::" + legacyA + "::g"
            db.query(
                "SELECT ingredientId, checked, purchaseQuantityOverride, storeNote, actualSpentDzd " +
                    "FROM shopping_items WHERE id = ?",
                arrayOf<Any?>(oldShoppingId)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(canonicalId, cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(4.0, cursor.getDouble(2), 0.0)
                assertEquals("احتفظ بهذه الملاحظة", cursor.getString(3))
                assertEquals(120.0, cursor.getDouble(4), 0.0)
            }
            assertEquals(3, count(db, "SELECT COUNT(*) FROM shopping_items"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM shopping_items WHERE userAdded = 1 AND ingredientId IS NULL"))

            // Ingredient variants merge under one UUID without discarding names;
            // an old locally-created ID which was absent from ingredients receives
            // a deterministic UUID and a placeholder master row instead of vanishing.
            assertEquals(1, count(db, "SELECT COUNT(*) FROM ingredients WHERE id = '$canonicalId'"))
            val aliases = scalarString(db, "SELECT aliasesCsv FROM ingredients WHERE id = '$canonicalId'")
            assertTrue(aliases.contains("اسم قديم ب"))
            assertEquals(
                "foodcat-other",
                scalarString(db, "SELECT categoryId FROM ingredients WHERE id = '$canonicalId'")
            )
            assertEquals(
                "foodsub-other-general",
                scalarString(db, "SELECT subcategoryId FROM ingredients WHERE id = '$canonicalId'")
            )
            val ingredientColumns = mutableSetOf<String>()
            db.query("PRAGMA table_info('ingredients')").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) ingredientColumns += cursor.getString(nameIndex)
            }
            assertTrue("Normalized category ID must be persisted", "categoryId" in ingredientColumns)
            assertTrue("Normalized subcategory ID must be persisted", "subcategoryId" in ingredientColumns)
            assertTrue("Localized/free-form category string must be removed", "category" !in ingredientColumns)
            val customId = FoodIdentityResolver(context).canonicalId("user-spice-legacy")
            assertTrue(customId.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM ingredients WHERE id = '$customId'"))
            assertEquals(
                "0fdd586e-27ec-46c1-b407-2b5925054e42",
                FoodIdentityResolver(context).canonicalId("chicken")
            )
        } finally {
            roomDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createVersion13Database(
        file: java.io.File,
        legacyA: String,
        legacyB: String
    ) {
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(
                "CREATE TABLE ingredients (" +
                    "id TEXT NOT NULL PRIMARY KEY, nameAr TEXT NOT NULL, nameFr TEXT NOT NULL, " +
                    "category TEXT NOT NULL, baseUnit TEXT NOT NULL, allergensCsv TEXT NOT NULL, aliasesCsv TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE recipes (" +
                    "id TEXT NOT NULL PRIMARY KEY, nameAr TEXT NOT NULL, nameFr TEXT NOT NULL, " +
                    "descriptionAr TEXT NOT NULL, descriptionFr TEXT NOT NULL, category TEXT NOT NULL, " +
                    "region TEXT NOT NULL, durationMinutes INTEGER NOT NULL, baseServings INTEGER NOT NULL, " +
                    "difficulty TEXT NOT NULL, allergensCsv TEXT NOT NULL, tagsCsv TEXT NOT NULL DEFAULT '', " +
                    "categoriesCsv TEXT NOT NULL DEFAULT '', dietaryCsv TEXT NOT NULL DEFAULT '', " +
                    "caloriesPerServing REAL, proteinGramsPerServing REAL, carbsGramsPerServing REAL, " +
                    "fatGramsPerServing REAL, imageUrl TEXT NOT NULL DEFAULT '', prepMinutes INTEGER, " +
                    "cookMinutes INTEGER, nativeName TEXT NOT NULL DEFAULT '', countryCode TEXT NOT NULL DEFAULT '', " +
                    "imageAuthor TEXT NOT NULL DEFAULT '', imageLicense TEXT NOT NULL DEFAULT '')"
            )
            db.execSQL(
                "CREATE TABLE recipe_ingredients (" +
                    "recipeId TEXT NOT NULL, ingredientId TEXT NOT NULL, quantityBase REAL NOT NULL, " +
                    "unit TEXT NOT NULL, prepNote TEXT NOT NULL, optional INTEGER NOT NULL, " +
                    "scalingRule TEXT NOT NULL DEFAULT 'linear', PRIMARY KEY(recipeId, ingredientId, unit))"
            )
            db.execSQL("CREATE INDEX index_recipe_ingredients_ingredientId ON recipe_ingredients(ingredientId)")
            db.execSQL(
                "CREATE TABLE recipe_steps (" +
                    "recipeId TEXT NOT NULL, stepNumber INTEGER NOT NULL, instructionAr TEXT NOT NULL, " +
                    "instructionFr TEXT NOT NULL, durationMinutes INTEGER, PRIMARY KEY(recipeId, stepNumber))"
            )
            db.execSQL(
                "CREATE TABLE plan_meals (" +
                    "id TEXT NOT NULL PRIMARY KEY, weekStartIso TEXT NOT NULL, dayIndex INTEGER NOT NULL, " +
                    "mealSlot TEXT NOT NULL, recipeId TEXT, servings INTEGER NOT NULL, locked INTEGER NOT NULL, " +
                    "isCooked INTEGER NOT NULL DEFAULT 0, isUserEdited INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE INDEX index_plan_meals_weekStartIso ON plan_meals(weekStartIso)")
            db.execSQL("CREATE INDEX index_plan_meals_recipeId ON plan_meals(recipeId)")
            db.execSQL(
                "CREATE TABLE pantry_items (" +
                    "id TEXT NOT NULL PRIMARY KEY, ingredientId TEXT NOT NULL, quantityBase REAL, " +
                    "unit TEXT NOT NULL, isApproximate INTEGER NOT NULL, note TEXT NOT NULL, expiryAtEpochMillis INTEGER)"
            )
            db.execSQL("CREATE UNIQUE INDEX index_pantry_items_ingredientId ON pantry_items(ingredientId)")
            db.execSQL(
                "CREATE TABLE price_observations (" +
                    "id TEXT NOT NULL PRIMARY KEY, ingredientId TEXT NOT NULL, packQuantityBase REAL NOT NULL, " +
                    "unit TEXT NOT NULL, packPriceDzd REAL NOT NULL, sourceLabel TEXT NOT NULL, " +
                    "locationLabel TEXT NOT NULL, observedAtEpochMillis INTEGER, priceKind TEXT NOT NULL, confidenceLabel TEXT NOT NULL)"
            )
            db.execSQL("CREATE INDEX index_price_observations_ingredientId ON price_observations(ingredientId)")
            db.execSQL("CREATE INDEX index_price_observations_observedAtEpochMillis ON price_observations(observedAtEpochMillis)")
            db.execSQL(
                "CREATE TABLE shopping_items (" +
                    "id TEXT NOT NULL PRIMARY KEY, ingredientId TEXT, customName TEXT, requiredQuantity REAL NOT NULL, " +
                    "pantryQuantity REAL NOT NULL, purchaseQuantity REAL NOT NULL, unit TEXT NOT NULL, " +
                    "consumedValueDzd REAL, cashOutlayDzd REAL, priceLabel TEXT, sourceLabel TEXT, " +
                    "isApproximatePantry INTEGER NOT NULL, checked INTEGER NOT NULL, userAdded INTEGER NOT NULL, " +
                    "updatedAtEpochMillis INTEGER NOT NULL, weekStartIso TEXT NOT NULL DEFAULT '', " +
                    "purchaseQuantityOverride REAL, storeNote TEXT NOT NULL DEFAULT '', " +
                    "isUnavailable INTEGER NOT NULL DEFAULT 0, actualSpentDzd REAL)"
            )
            db.execSQL("CREATE INDEX index_shopping_items_weekStartIso ON shopping_items(weekStartIso)")
            db.execSQL(
                "CREATE TABLE favorite_recipes (" +
                    "recipeId TEXT NOT NULL PRIMARY KEY, addedAtEpochMillis INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE plan_runs (" +
                    "weekStartIso TEXT NOT NULL PRIMARY KEY, engineVersion TEXT NOT NULL, " +
                    "generatedAtEpochMillis INTEGER NOT NULL, priceSnapshotAtEpochMillis INTEGER NOT NULL, " +
                    "budgetDzd INTEGER NOT NULL, wilaya TEXT NOT NULL, mealSlotsCsv TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE cooked_meal_deductions (" +
                    "mealId TEXT NOT NULL, ingredientId TEXT NOT NULL, unit TEXT NOT NULL, " +
                    "quantityBase REAL NOT NULL, PRIMARY KEY(mealId, ingredientId))"
            )
            db.execSQL(
                "CREATE TABLE plan_price_snapshots (" +
                    "weekStartIso TEXT NOT NULL, observationId TEXT NOT NULL, ingredientId TEXT NOT NULL, " +
                    "packQuantityBase REAL NOT NULL, unit TEXT NOT NULL, packPriceDzd REAL NOT NULL, " +
                    "sourceLabel TEXT NOT NULL, locationLabel TEXT NOT NULL, observedAtEpochMillis INTEGER, " +
                    "priceKind TEXT NOT NULL, confidenceLabel TEXT NOT NULL, PRIMARY KEY(weekStartIso, observationId))"
            )
            db.execSQL("CREATE INDEX index_plan_price_snapshots_ingredientId ON plan_price_snapshots(ingredientId)")
            db.execSQL(
                "CREATE TABLE recipe_attributions (" +
                    "recipeId TEXT NOT NULL PRIMARY KEY, sourceTitle TEXT NOT NULL, sourceUrl TEXT NOT NULL, " +
                    "revisionUrl TEXT NOT NULL, historyUrl TEXT NOT NULL, credit TEXT NOT NULL, " +
                    "sourceLicense TEXT NOT NULL, license TEXT NOT NULL, licenseUrl TEXT NOT NULL, changes TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE recipe_image_overrides (" +
                    "recipeId TEXT NOT NULL PRIMARY KEY, imageUrl TEXT NOT NULL, sourcePageUrl TEXT NOT NULL, " +
                    "author TEXT NOT NULL, credit TEXT NOT NULL, license TEXT NOT NULL, licenseUrl TEXT NOT NULL, " +
                    "selectedAtEpochMillis INTEGER NOT NULL)"
            )

            db.execSQL(
                "INSERT INTO ingredients VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(legacyA, "اسم قديم أ", "old name A", "مكوّن", "g", "", "")
            )
            db.execSQL(
                "INSERT INTO ingredients VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(legacyB, "اسم قديم ب", "old name B", "مكوّن", "g", "", "")
            )
            db.execSQL(
                "INSERT INTO ingredients VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("chicken", "دجاج", "poulet", "لحوم ودواجن", "g", "", "")
            )

            db.execSQL(
                "INSERT INTO recipe_ingredients VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("focus-test-recipe", legacyA, 2.0, "g", "ملاحظة أ", 0, "linear")
            )
            db.execSQL(
                "INSERT INTO recipe_ingredients VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("focus-test-recipe", legacyB, 3.0, "g", "ملاحظة ب", 1, "linear")
            )
            // This same food is used by a seed recipe too, so it must remain
            // available even while the community recipe catalogue is pending.
            db.execSQL(
                "INSERT INTO recipe_ingredients VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("seed-baseline", legacyA, 1.0, "g", "seed use", 0, "linear")
            )
            // This canonical product is used only by an imported recipe and
            // should remain hidden until the imported catalogue is complete.
            db.execSQL(
                "INSERT INTO recipe_ingredients VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("unitools-chicken-demo", "chicken", 0.5, "g", "imported use", 0, "linear")
            )
            db.execSQL(
                "INSERT INTO pantry_items VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("pantry-row-a", legacyA, 10.0, "g", 0, "stock A", null)
            )
            db.execSQL(
                "INSERT INTO pantry_items VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("pantry-row-b", legacyB, 5.0, "g", 0, "stock B", null)
            )
            db.execSQL(
                "INSERT INTO pantry_items VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("pantry-custom", "user-spice-legacy", 2.0, "g", 0, "custom stock", null)
            )
            db.execSQL(
                "INSERT INTO price_observations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("price-a", legacyA, 100.0, "g", 50.0, "store A", "Algiers", 1700000000000L, "user_reported", "low")
            )
            db.execSQL(
                "INSERT INTO price_observations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("price-b", legacyB, 200.0, "g", 80.0, "store B", "Algiers", 1700000001000L, "user_reported", "low")
            )
            db.execSQL(
                "INSERT INTO shopping_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "2025-01-06::" + legacyA + "::g", legacyA, "عنصر أ", 6.0, 1.0, 5.0, "g",
                    50.0, 100.0, "price", "store", 0, 1, 0, 1700000000000L, "2025-01-06",
                    4.0, "احتفظ بهذه الملاحظة", 0, 120.0
                )
            )
            db.execSQL(
                "INSERT INTO shopping_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "2025-01-06::" + legacyB + "::g", legacyB, "عنصر ب", 7.0, 2.0, 5.0, "g",
                    60.0, 110.0, "price", "store", 0, 0, 0, 1700000001000L, "2025-01-06",
                    2.0, "ملاحظة أخرى", 0, 70.0
                )
            )
            db.execSQL(
                "INSERT INTO shopping_items VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "custom-shopping-row", null, "عنصر مخصص", 0.0, 0.0, 1.0, "piece",
                    null, null, null, null, 0, 1, 1, 1700000002000L, "2025-01-06",
                    null, "", 0, null
                )
            )
            db.execSQL(
                "INSERT INTO cooked_meal_deductions VALUES (?, ?, ?, ?)",
                arrayOf<Any?>("meal-cooked-1", legacyA, "g", 1.0)
            )
            db.execSQL(
                "INSERT INTO cooked_meal_deductions VALUES (?, ?, ?, ?)",
                arrayOf<Any?>("meal-cooked-1", legacyB, "g", 2.0)
            )
            db.execSQL(
                "INSERT INTO plan_price_snapshots VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("2025-01-06", "price-a", legacyA, 100.0, "g", 50.0, "store A", "Algiers", 1700000000000L, "user_reported", "low")
            )
            db.execSQL(
                "INSERT INTO plan_price_snapshots VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("2025-01-06", "price-b", legacyB, 200.0, "g", 80.0, "store B", "Algiers", 1700000001000L, "user_reported", "low")
            )

            db.version = 13
        } finally {
            db.close()
        }
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun scalarDouble(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Double =
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getDouble(0)
        }

    private fun scalarString(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun existsNonUniquePantryIndex(db: androidx.sqlite.db.SupportSQLiteDatabase): Boolean =
        db.query("PRAGMA index_list('pantry_items')").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val uniqueIndex = cursor.getColumnIndexOrThrow("unique")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "index_pantry_items_ingredientId") {
                    assertEquals(0, cursor.getInt(uniqueIndex))
                    found = true
                }
            }
            found
        }
}
