package dz.meals.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        IngredientEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        RecipeStepEntity::class,
        PlanMealEntity::class,
        PantryItemEntity::class,
        PriceObservationEntity::class,
        ShoppingItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MealsDatabase : RoomDatabase() {
    abstract fun mealsDao(): MealsDao

    companion object {
        @Volatile private var INSTANCE: MealsDatabase? = null

        fun getInstance(context: Context): MealsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MealsDatabase::class.java,
                    "meals_local.db"
                ).build().also { INSTANCE = it }
            }
    }
}
