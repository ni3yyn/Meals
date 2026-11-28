package dz.meals.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarketProductIngredientMatchMigrationTest {

    @Test
    fun migrationAddsIndexedBridgeAndEnforcesBothReferences() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "product-ingredient-v19-${UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE market_products (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("CREATE TABLE ingredients (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("INSERT INTO market_products (id) VALUES ('product-1')")
                        db.execSQL("INSERT INTO ingredients (id) VALUES ('ingredient-1')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        error("Fixture must start at Room v18.")
                    }

                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                })
                .build()
        )

        try {
            val db = helper.writableDatabase
            MealsDatabase.MIGRATION_18_19.migrate(db)

            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info('market_product_ingredient_matches')").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
            }
            assertEquals(
                setOf(
                    "productId", "ingredientId", "status", "matchMethod",
                    "confidenceScore", "rationale", "algorithmVersion",
                    "reviewedAtEpochMillis", "updatedAtEpochMillis"
                ),
                columns
            )

            db.execSQL(
                """
                INSERT INTO market_product_ingredient_matches (
                    productId, ingredientId, status, matchMethod, confidenceScore,
                    rationale, algorithmVersion, reviewedAtEpochMillis, updatedAtEpochMillis
                ) VALUES ('product-1', 'ingredient-1', 'VERIFIED', 'MANUAL', 1.0, 'reviewed', 'v1', 1, 2)
                """.trimIndent()
            )
            assertEquals(1, scalarInt(db, "SELECT COUNT(*) FROM market_product_ingredient_matches WHERE status = 'VERIFIED'"))

            val unknownProductRejected = runCatching {
                db.execSQL(
                    """
                    INSERT INTO market_product_ingredient_matches (
                        productId, ingredientId, status, matchMethod, confidenceScore,
                        rationale, algorithmVersion, reviewedAtEpochMillis, updatedAtEpochMillis
                    ) VALUES ('unknown', 'ingredient-1', 'VERIFIED', 'MANUAL', 1.0, '', 'v1', 1, 2)
                    """.trimIndent()
                )
            }.isFailure
            assertTrue(unknownProductRejected)

            val unknownIngredientRejected = runCatching {
                db.execSQL(
                    """
                    INSERT INTO market_product_ingredient_matches (
                        productId, ingredientId, status, matchMethod, confidenceScore,
                        rationale, algorithmVersion, reviewedAtEpochMillis, updatedAtEpochMillis
                    ) VALUES ('product-2', 'unknown', 'SUGGESTED', 'FUZZY', 0.8, '', 'v1', NULL, 2)
                    """.trimIndent()
                )
            }.isFailure
            assertTrue(unknownIngredientRejected)
            assertEquals(0, scalarInt(db, "SELECT COUNT(*) FROM pragma_foreign_key_check"))
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun scalarInt(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
