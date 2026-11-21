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
        FavoriteRecipeEntity::class
    ],
    version = 2,
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

        @Volatile private var INSTANCE: MealsDatabase? = null

        fun getInstance(context: Context): MealsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MealsDatabase::class.java,
                    "meals_local.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
