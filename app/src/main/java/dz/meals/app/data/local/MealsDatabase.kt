package dz.meals.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dz.meals.app.data.FoodIdentityResolver

@Database(
    entities = [
        IngredientEntity::class,
        FoodCategoryEntity::class,
        FoodSubcategoryEntity::class,
        FoodItemEntity::class,
        MarketProductEntity::class,
        MarketProductSourceEntity::class,
        MarketPriceObservationEntity::class,
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
        RecipeImageOverrideEntity::class,
        MarketProductIngredientMatchEntity::class,
        PlanningPeriodEntity::class,
        PlanningPeriodMealEntity::class,
        PlanIngredientAdjustmentEntity::class,
        PlanMarketPriceSnapshotEntity::class
    ],
    version = 20,
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

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS food_items (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "nameAr TEXT NOT NULL, " +
                        "nameEn TEXT NOT NULL, " +
                        "nameFr TEXT NOT NULL, " +
                        "aliasesArCsv TEXT NOT NULL, " +
                        "aliasesEnCsv TEXT NOT NULL, " +
                        "aliasesFrCsv TEXT NOT NULL, " +
                        "categoryId TEXT NOT NULL, " +
                        "subcategoryId TEXT NOT NULL, " +
                        "categoryNameAr TEXT NOT NULL, " +
                        "categoryNameEn TEXT NOT NULL, " +
                        "categoryNameFr TEXT NOT NULL, " +
                        "subcategoryNameAr TEXT NOT NULL, " +
                        "acceptedUnitsCsv TEXT NOT NULL, " +
                        "imageUrl TEXT NOT NULL, " +
                        "quantity REAL, " +
                        "countryOfOrigin TEXT NOT NULL, " +
                        "nutritionJson TEXT NOT NULL, " +
                        "sourceMappingsJson TEXT NOT NULL, " +
                        "recipeIngredientIdsCsv TEXT NOT NULL, " +
                        "sourceJson TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val oldCount = db.query("SELECT COUNT(*) FROM ingredients").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                db.execSQL(
                    """
                    CREATE TABLE ingredients_normalized (
                        id TEXT NOT NULL,
                        nameAr TEXT NOT NULL,
                        nameFr TEXT NOT NULL,
                        categoryId TEXT NOT NULL,
                        subcategoryId TEXT NOT NULL,
                        baseUnit TEXT NOT NULL,
                        allergensCsv TEXT NOT NULL,
                        aliasesCsv TEXT NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(categoryId, subcategoryId)
                            REFERENCES food_subcategories(categoryId, id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                // The generic catalogue may not have been installed before this
                // offline migration. The installer re-resolves these valid
                // "other" IDs against the full catalogue immediately afterwards.
                db.execSQL(
                    """
                    INSERT INTO ingredients_normalized (
                        id, nameAr, nameFr, categoryId, subcategoryId,
                        baseUnit, allergensCsv, aliasesCsv
                    )
                    SELECT
                        old.id, old.nameAr, old.nameFr,
                        COALESCE(food.categoryId, 'foodcat-other'),
                        COALESCE(food.subcategoryId, 'foodsub-other-general'),
                        old.baseUnit, old.allergensCsv, old.aliasesCsv
                    FROM ingredients AS old
                    LEFT JOIN food_items AS food ON food.id = old.id
                    """.trimIndent()
                )
                val newCount = db.query("SELECT COUNT(*) FROM ingredients_normalized").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                if (newCount != oldCount) {
                    throw IllegalStateException("Ingredient taxonomy migration copied $newCount of $oldCount rows.")
                }
                db.execSQL("DROP TABLE ingredients")
                db.execSQL("ALTER TABLE ingredients_normalized RENAME TO ingredients")
                db.execSQL(
                    "CREATE INDEX index_ingredients_categoryId_subcategoryId " +
                        "ON ingredients(categoryId, subcategoryId)"
                )
                val foreignKeyErrors = db.query("PRAGMA foreign_key_check").use { cursor ->
                    var count = 0
                    while (cursor.moveToNext()) count++
                    count
                }
                if (foreignKeyErrors != 0) {
                    throw IllegalStateException("Ingredient taxonomy migration created $foreignKeyErrors foreign-key violations.")
                }
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS market_products (
                        id TEXT NOT NULL PRIMARY KEY,
                        identityKey TEXT NOT NULL,
                        name TEXT NOT NULL,
                        nameVariantsJson TEXT NOT NULL,
                        brand TEXT,
                        gtin TEXT,
                        imageUrl TEXT NOT NULL,
                        imageUrlsJson TEXT NOT NULL,
                        description TEXT NOT NULL,
                        packageQuantity REAL,
                        packageUnit TEXT NOT NULL,
                        categoryNamesJson TEXT NOT NULL,
                        sourceCount INTEGER NOT NULL,
                        foodScopeReviewNeeded INTEGER NOT NULL,
                        foodScopeReviewReason TEXT NOT NULL,
                        sourceRowCount INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS market_product_sources (
                        id TEXT NOT NULL PRIMARY KEY,
                        productId TEXT NOT NULL,
                        source TEXT NOT NULL,
                        storeName TEXT NOT NULL,
                        storeUrl TEXT NOT NULL,
                        city TEXT NOT NULL,
                        wilaya TEXT NOT NULL,
                        storeAddress TEXT NOT NULL,
                        sourceProductKey TEXT NOT NULL,
                        sourceProductId TEXT NOT NULL,
                        sourceProductKeysJson TEXT NOT NULL,
                        sourceProductIdsJson TEXT NOT NULL,
                        sku TEXT,
                        gtinRaw TEXT,
                        rawBrand TEXT,
                        mpn TEXT,
                        manufacturer TEXT,
                        productUrl TEXT NOT NULL,
                        alternateUrlsJson TEXT NOT NULL,
                        alternateLocaleUrlsJson TEXT NOT NULL,
                        categorySlugPathsJson TEXT NOT NULL,
                        variationSkusJson TEXT NOT NULL,
                        enrichmentMetadataJson TEXT NOT NULL,
                        FOREIGN KEY(productId) REFERENCES market_products(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS market_price_observations (
                        id TEXT NOT NULL PRIMARY KEY,
                        productId TEXT NOT NULL,
                        sourceListingId TEXT NOT NULL,
                        priceDzd REAL,
                        priceMinDzd REAL,
                        priceMaxDzd REAL,
                        priceDisplayRaw TEXT NOT NULL,
                        currencyCode TEXT NOT NULL,
                        observedAtEpochMillis INTEGER,
                        priceSource TEXT NOT NULL,
                        priceStatus TEXT NOT NULL,
                        availabilityRaw TEXT NOT NULL,
                        stockQuantityRaw TEXT NOT NULL,
                        locationHint TEXT NOT NULL,
                        sourceNotice TEXT NOT NULL,
                        FOREIGN KEY(productId) REFERENCES market_products(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(sourceListingId) REFERENCES market_product_sources(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_market_products_gtin ON market_products(gtin)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_market_products_name ON market_products(name)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_market_products_identityKey ON market_products(identityKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_market_product_sources_productId ON market_product_sources(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_market_product_sources_source ON market_product_sources(source)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_market_product_sources_sku ON market_product_sources(sku)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_market_product_sources_source_sourceProductKey ON market_product_sources(source, sourceProductKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_market_price_observations_productId ON market_price_observations(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_market_price_observations_sourceListingId ON market_price_observations(sourceListingId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_market_price_observations_observedAtEpochMillis ON market_price_observations(observedAtEpochMillis)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_market_price_observations_sourceListingId_observedAtEpochMillis ON market_price_observations(sourceListingId, observedAtEpochMillis)")
            }
        }


        internal val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS market_product_ingredient_matches (
                        productId TEXT NOT NULL PRIMARY KEY,
                        ingredientId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        matchMethod TEXT NOT NULL,
                        confidenceScore REAL,
                        rationale TEXT NOT NULL,
                        algorithmVersion TEXT NOT NULL,
                        reviewedAtEpochMillis INTEGER,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        FOREIGN KEY(productId) REFERENCES market_products(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(ingredientId) REFERENCES ingredients(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_market_product_ingredient_matches_ingredientId ON market_product_ingredient_matches(ingredientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_market_product_ingredient_matches_status_ingredientId ON market_product_ingredient_matches(status, ingredientId)")
            }
        }

        internal val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS planning_periods (
                        id TEXT NOT NULL PRIMARY KEY,
                        periodType TEXT NOT NULL,
                        startDateIso TEXT NOT NULL,
                        endDateIso TEXT NOT NULL,
                        budgetDzd INTEGER NOT NULL,
                        wilaya TEXT NOT NULL,
                        mealSlotsCsv TEXT NOT NULL,
                        engineVersion TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        priceSnapshotAtEpochMillis INTEGER NOT NULL,
                        marketPriceSnapshotCaptured INTEGER NOT NULL DEFAULT 0,
                        householdServings INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_planning_periods_periodType_startDateIso " +
                        "ON planning_periods(periodType, startDateIso)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_planning_periods_endDateIso " +
                        "ON planning_periods(endDateIso)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS planning_period_meals (
                        id TEXT NOT NULL PRIMARY KEY,
                        periodId TEXT NOT NULL,
                        dateIso TEXT NOT NULL,
                        mealSlot TEXT NOT NULL,
                        recipeId TEXT,
                        servings INTEGER NOT NULL,
                        locked INTEGER NOT NULL,
                        isCooked INTEGER NOT NULL,
                        isUserEdited INTEGER NOT NULL,
                        FOREIGN KEY(periodId) REFERENCES planning_periods(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_planning_period_meals_periodId ON planning_period_meals(periodId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_planning_period_meals_dateIso ON planning_period_meals(dateIso)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_planning_period_meals_recipeId ON planning_period_meals(recipeId)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_planning_period_meals_periodId_dateIso_mealSlot " +
                        "ON planning_period_meals(periodId, dateIso, mealSlot)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plan_ingredient_adjustments (
                        periodId TEXT NOT NULL,
                        ingredientId TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        adjustmentType TEXT NOT NULL,
                        availableQuantity REAL NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(periodId, ingredientId, unit),
                        FOREIGN KEY(periodId) REFERENCES planning_periods(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(ingredientId) REFERENCES ingredients(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_ingredient_adjustments_ingredientId ON plan_ingredient_adjustments(ingredientId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plan_market_price_snapshots (
                        periodId TEXT NOT NULL,
                        sourceListingId TEXT NOT NULL,
                        productId TEXT NOT NULL,
                        ingredientId TEXT NOT NULL,
                        productName TEXT NOT NULL,
                        packageQuantity REAL,
                        packageUnit TEXT NOT NULL,
                        mappingStatus TEXT NOT NULL,
                        mappingReviewedAtEpochMillis INTEGER,
                        priceDzd REAL,
                        priceMinDzd REAL,
                        priceMaxDzd REAL,
                        currencyCode TEXT NOT NULL,
                        observedAtEpochMillis INTEGER,
                        priceStatus TEXT NOT NULL,
                        availabilityRaw TEXT NOT NULL,
                        storeName TEXT NOT NULL,
                        source TEXT NOT NULL,
                        wilaya TEXT NOT NULL,
                        sourceNotice TEXT NOT NULL,
                        PRIMARY KEY(periodId, sourceListingId),
                        FOREIGN KEY(periodId) REFERENCES planning_periods(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_market_price_snapshots_ingredientId ON plan_market_price_snapshots(ingredientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_market_price_snapshots_productId ON plan_market_price_snapshots(productId)")

                // Preserve every already-generated week. These rows are explicitly
                // WEEK periods; no old weekly plan is expanded or re-labelled as a month.
                db.execSQL(
                    """
                    INSERT INTO planning_periods (
                        id, periodType, startDateIso, endDateIso, budgetDzd, wilaya,
                        mealSlotsCsv, engineVersion, createdAtEpochMillis,
                        priceSnapshotAtEpochMillis, householdServings, updatedAtEpochMillis
                    )
                    SELECT
                        'WEEK:' || weekStartIso, 'WEEK', weekStartIso,
                        date(weekStartIso, '+6 days'), budgetDzd, wilaya,
                        mealSlotsCsv, engineVersion, generatedAtEpochMillis,
                        priceSnapshotAtEpochMillis, 1, generatedAtEpochMillis
                    FROM plan_runs
                    """
                )
                // Some older installations may contain manually edited meals
                // without a matching plan_runs row. Keep those rows accessible too.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO planning_periods (
                        id, periodType, startDateIso, endDateIso, budgetDzd, wilaya,
                        mealSlotsCsv, engineVersion, createdAtEpochMillis,
                        priceSnapshotAtEpochMillis, householdServings, updatedAtEpochMillis
                    )
                    SELECT
                        'WEEK:' || weekStartIso, 'WEEK', weekStartIso,
                        date(weekStartIso, '+6 days'), 0, '',
                        COALESCE(group_concat(DISTINCT mealSlot), ''),
                        'legacy-adapter', 0, 0, 1, 0
                    FROM plan_meals
                    WHERE weekStartIso NOT IN (SELECT weekStartIso FROM plan_runs)
                    GROUP BY weekStartIso
                    """
                )
                db.execSQL(
                    """
                    INSERT INTO planning_period_meals (
                        id, periodId, dateIso, mealSlot, recipeId, servings,
                        locked, isCooked, isUserEdited
                    )
                    SELECT
                        id, 'WEEK:' || weekStartIso,
                        date(weekStartIso, '+' || dayIndex || ' days'),
                        mealSlot, recipeId, servings, locked, isCooked, isUserEdited
                    FROM plan_meals
                    """
                )
            }
        }

        @Volatile private var INSTANCE: MealsDatabase? = null

        fun getInstance(context: Context): MealsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: createDatabase(context, "meals_local.db").also { INSTANCE = it }
            }

        /** Opens an isolated database through the production migration chain. */
        @androidx.annotation.Keep
        internal fun createForTesting(context: Context, databaseName: String): MealsDatabase =
            createDatabase(context, databaseName)

        private fun createDatabase(context: Context, databaseName: String): MealsDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MealsDatabase::class.java,
                databaseName
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                FoodIdentityMigration(FoodIdentityResolver(context.applicationContext)),
                MIGRATION_14_15,
                FoodTaxonomyMigration(context.applicationContext),
                MIGRATION_16_17,
                MIGRATION_17_18,
                MIGRATION_18_19,
                MIGRATION_19_20
            ).build()
    }
}
