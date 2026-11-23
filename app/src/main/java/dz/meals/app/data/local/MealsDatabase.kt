package dz.meals.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        IngredientEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        RecipeStepEntity::class,
        PlanMealEntity::class,
        PantryItemEntity::class,
        PriceObservationEntity::class,
        ShoppingItemEntity::class,
        FavoriteRecipeEntity::class,
        PlanRunEntity::class,
        CookedMealDeductionEntity::class,
        PlanPriceSnapshotEntity::class,
        RecipeAttributionEntity::class,
        RecipeImageOverrideEntity::class
    ],
    version = 13,
    exportSchema = true
)
abstract class MealsDatabase : RoomDatabase() {
    abstract fun mealsDao(): MealsDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS favorite_recipes (" +
                        "recipeId TEXT NOT NULL PRIMARY KEY, " +
                        "addedAtEpochMillis INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN purchaseQuantityOverride REAL")
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN storeNote TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN isUnavailable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN actualSpentDzd REAL")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS plan_runs (" +
                        "weekStartIso TEXT NOT NULL PRIMARY KEY, " +
                        "engineVersion TEXT NOT NULL, " +
                        "generatedAtEpochMillis INTEGER NOT NULL, " +
                        "priceSnapshotAtEpochMillis INTEGER NOT NULL, " +
                        "budgetDzd INTEGER NOT NULL, " +
                        "wilaya TEXT NOT NULL, " +
                        "mealSlotsCsv TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN weekStartIso TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_items_weekStartIso ON shopping_items(weekStartIso)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_meals ADD COLUMN isCooked INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cooked_meal_deductions (" +
                        "mealId TEXT NOT NULL, " +
                        "ingredientId TEXT NOT NULL, " +
                        "unit TEXT NOT NULL, " +
                        "quantityBase REAL NOT NULL, " +
                        "PRIMARY KEY(mealId, ingredientId))"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_meals ADD COLUMN isUserEdited INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS plan_price_snapshots (" +
                        "weekStartIso TEXT NOT NULL, " +
                        "observationId TEXT NOT NULL, " +
                        "ingredientId TEXT NOT NULL, " +
                        "packQuantityBase REAL NOT NULL, " +
                        "unit TEXT NOT NULL, " +
                        "packPriceDzd REAL NOT NULL, " +
                        "sourceLabel TEXT NOT NULL, " +
                        "locationLabel TEXT NOT NULL, " +
                        "observedAtEpochMillis INTEGER, " +
                        "priceKind TEXT NOT NULL, " +
                        "confidenceLabel TEXT NOT NULL, " +
                        "PRIMARY KEY(weekStartIso, observationId))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_price_snapshots_ingredientId ON plan_price_snapshots(ingredientId)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recipe_attributions (" +
                        "recipeId TEXT NOT NULL PRIMARY KEY, " +
                        "sourceTitle TEXT NOT NULL, " +
                        "sourceUrl TEXT NOT NULL, " +
                        "revisionUrl TEXT NOT NULL, " +
                        "historyUrl TEXT NOT NULL, " +
                        "credit TEXT NOT NULL, " +
                        "sourceLicense TEXT NOT NULL, " +
                        "license TEXT NOT NULL, " +
                        "licenseUrl TEXT NOT NULL, " +
                        "changes TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN tagsCsv TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN categoriesCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recipes ADD COLUMN dietaryCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recipes ADD COLUMN caloriesPerServing REAL")
                db.execSQL("ALTER TABLE recipes ADD COLUMN proteinGramsPerServing REAL")
                db.execSQL("ALTER TABLE recipes ADD COLUMN carbsGramsPerServing REAL")
                db.execSQL("ALTER TABLE recipes ADD COLUMN fatGramsPerServing REAL")
                db.execSQL("ALTER TABLE recipes ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN prepMinutes INTEGER")
                db.execSQL("ALTER TABLE recipes ADD COLUMN cookMinutes INTEGER")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN nativeName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recipes ADD COLUMN countryCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recipes ADD COLUMN imageAuthor TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recipes ADD COLUMN imageLicense TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recipe_ingredients ADD COLUMN scalingRule TEXT NOT NULL DEFAULT 'linear'")
                db.execSQL("ALTER TABLE recipe_steps ADD COLUMN durationMinutes INTEGER")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recipe_image_overrides (" +
                        "recipeId TEXT NOT NULL PRIMARY KEY, " +
                        "imageUrl TEXT NOT NULL, " +
                        "sourcePageUrl TEXT NOT NULL, " +
                        "author TEXT NOT NULL, " +
                        "credit TEXT NOT NULL, " +
                        "license TEXT NOT NULL, " +
                        "licenseUrl TEXT NOT NULL, " +
                        "selectedAtEpochMillis INTEGER NOT NULL)"
                )
            }
        }

        @Volatile private var INSTANCE: MealsDatabase? = null

        fun getInstance(context: Context): MealsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MealsDatabase::class.java,
                    "meals_local.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13).build().also { INSTANCE = it }
            }
    }
}
