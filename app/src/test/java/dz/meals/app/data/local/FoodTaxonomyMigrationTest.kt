package dz.meals.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FoodTaxonomyMigrationTest {

    @Test
    fun version15FoodRowsArePreservedAndReferenceNormalizedTaxonomy() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "food-taxonomy-" + UUID.randomUUID() + ".db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion15FoodItemsTable(db)
                        db.execSQL(
                            """
                            INSERT INTO food_items (
                                id, nameAr, nameEn, nameFr,
                                aliasesArCsv, aliasesEnCsv, aliasesFrCsv,
                                categoryId, subcategoryId,
                                categoryNameAr, categoryNameEn, categoryNameFr, subcategoryNameAr,
                                acceptedUnitsCsv, imageUrl, quantity, countryOfOrigin,
                                nutritionJson, sourceMappingsJson, recipeIngredientIdsCsv, sourceJson
                            ) VALUES (
                                'food-milk-uuid', 'حليب', 'Milk', 'Lait',
                                'حليب البقر', 'cow milk', 'lait de vache',
                                'foodcat-dairy', 'foodsub-dairy-milk',
                                'الألبان والأجبان', 'Dairy and cheese', 'Produits laitiers et fromages', 'الحليب',
                                'ml|l', '', NULL, '', '', '{}', 'recipe-milk', '{}'
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        error("The test fixture must start at Room v15.")
                    }

                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                })
                .build()
        )

        try {
            val db = helper.writableDatabase
            FoodTaxonomyMigration(context).migrate(db)

            assertEquals(22, count(db, "SELECT COUNT(*) FROM food_categories"))
            assertEquals(82, count(db, "SELECT COUNT(*) FROM food_subcategories"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM food_items"))
            assertEquals(
                "حليب",
                scalarString(db, "SELECT nameAr FROM food_items WHERE id = 'food-milk-uuid'")
            )
            assertEquals(
                "الألبان والأجبان",
                scalarString(db, "SELECT nameAr FROM food_categories WHERE id = 'foodcat-dairy'")
            )
            assertEquals(
                "الحليب",
                scalarString(db, "SELECT nameAr FROM food_subcategories WHERE id = 'foodsub-dairy-milk'")
            )

            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info('food_items')").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
            }
            assertTrue("categoryId must remain in food_items", "categoryId" in columns)
            assertTrue("subcategoryId must remain in food_items", "subcategoryId" in columns)
            assertFalse("localized category labels must be normalized", "categoryNameAr" in columns)
            assertFalse("localized category labels must be normalized", "categoryNameEn" in columns)
            assertFalse("localized category labels must be normalized", "categoryNameFr" in columns)
            assertFalse("localized subcategory labels must be normalized", "subcategoryNameAr" in columns)
            assertEquals(0, count(db, "SELECT COUNT(*) FROM pragma_foreign_key_check"))

            val invalidRelationRejected = runCatching {
                db.execSQL(
                    """
                    INSERT INTO food_items (
                        id, nameAr, nameEn, nameFr,
                        aliasesArCsv, aliasesEnCsv, aliasesFrCsv,
                        categoryId, subcategoryId, acceptedUnitsCsv, imageUrl, quantity,
                        countryOfOrigin, nutritionJson, sourceMappingsJson, recipeIngredientIdsCsv, sourceJson
                    ) VALUES (
                        'invalid-category-link', 'اختبار', 'Test', 'Test',
                        '', '', '', 'foodcat-vegetables', 'foodsub-dairy-milk',
                        'g', '', NULL, '', '', '', '', '{}'
                    )
                    """.trimIndent()
                )
            }.isFailure
            assertTrue("A subcategory must belong to its item's category", invalidRelationRejected)
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createVersion15FoodItemsTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE food_items (
                id TEXT NOT NULL PRIMARY KEY,
                nameAr TEXT NOT NULL,
                nameEn TEXT NOT NULL,
                nameFr TEXT NOT NULL,
                aliasesArCsv TEXT NOT NULL,
                aliasesEnCsv TEXT NOT NULL,
                aliasesFrCsv TEXT NOT NULL,
                categoryId TEXT NOT NULL,
                subcategoryId TEXT NOT NULL,
                categoryNameAr TEXT NOT NULL,
                categoryNameEn TEXT NOT NULL,
                categoryNameFr TEXT NOT NULL,
                subcategoryNameAr TEXT NOT NULL,
                acceptedUnitsCsv TEXT NOT NULL,
                imageUrl TEXT NOT NULL,
                quantity REAL,
                countryOfOrigin TEXT NOT NULL,
                nutritionJson TEXT NOT NULL,
                sourceMappingsJson TEXT NOT NULL,
                recipeIngredientIdsCsv TEXT NOT NULL,
                sourceJson TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun count(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun scalarString(db: SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
}
