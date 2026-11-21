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
        PlanRunEntity::class
    ],
    version = 3,
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

        @Volatile private var INSTANCE: MealsDatabase? = null

        fun getInstance(context: Context): MealsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MealsDatabase::class.java,
                    "meals_local.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
