package dz.meals.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dz.meals.app.data.FoodIdentityResolver

/**
 * Re-keys ingredient references to stable food UUIDs while preserving local records.
 *
 * Recipe occurrences and cooked-meal deductions get explicit row keys because
 * distinct legacy ingredient IDs can intentionally resolve to the same food UUID.
 */
internal class FoodIdentityMigration(
    private val identityResolver: FoodIdentityResolver
) : Migration(13, 14) {

    override fun migrate(db: SupportSQLiteDatabase) {
        val legacyIds = linkedSetOf<String>()
        val unitsByLegacyId = linkedMapOf<String, MutableSet<String>>()

        collectIds(db, "ingredients", "id", legacyIds)
        listOf(
            "recipe_ingredients",
            "pantry_items",
            "price_observations",
            "shopping_items",
            "cooked_meal_deductions",
            "plan_price_snapshots"
        ).forEach { table ->
            collectIds(db, table, "ingredientId", legacyIds)
        }
        collectUnits(db, unitsByLegacyId)

        // Resolve all identifiers before the first write. A missing/corrupt asset
        // fails the migration inside Room's transaction, leaving v13 untouched.
        val canonicalByLegacyId = legacyIds.associateWith(identityResolver::canonicalId)

        // The old unique index cannot represent legacy rows which now share one UUID.
        db.execSQL("DROP INDEX IF EXISTS index_pantry_items_ingredientId")

        rebuildRecipeIngredients(db, canonicalByLegacyId)
        rebuildCookedMealDeductions(db, canonicalByLegacyId)

        updateIngredientReferences(db, "pantry_items", canonicalByLegacyId)
        updateIngredientReferences(db, "price_observations", canonicalByLegacyId)
        updateIngredientReferences(db, "shopping_items", canonicalByLegacyId)
        updateIngredientReferences(db, "plan_price_snapshots", canonicalByLegacyId)

        rebuildIngredients(db, canonicalByLegacyId, unitsByLegacyId)

        db.execSQL("CREATE INDEX IF NOT EXISTS index_pantry_items_ingredientId ON pantry_items(ingredientId)")
    }

    private fun collectIds(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
        destination: MutableSet<String>
    ) {
        db.query("SELECT DISTINCT $column FROM $table WHERE $column IS NOT NULL").use { cursor ->
            val index = cursor.getColumnIndexOrThrow(column)
            while (cursor.moveToNext()) {
                val id = cursor.getString(index)?.trim().orEmpty()
                if (id.isNotEmpty()) destination += id
            }
        }
    }

    private fun collectUnits(
        db: SupportSQLiteDatabase,
        destination: MutableMap<String, MutableSet<String>>
    ) {
        val tables = listOf(
            "recipe_ingredients",
            "pantry_items",
            "price_observations",
            "shopping_items",
            "cooked_meal_deductions",
            "plan_price_snapshots"
        )
        tables.forEach { table ->
            db.query(
                "SELECT ingredientId, unit FROM $table " +
                    "WHERE ingredientId IS NOT NULL AND unit IS NOT NULL"
            ).use { cursor ->
                val ingredientIndex = cursor.getColumnIndexOrThrow("ingredientId")
                val unitIndex = cursor.getColumnIndexOrThrow("unit")
                while (cursor.moveToNext()) {
                    val ingredientId = cursor.getString(ingredientIndex)?.trim().orEmpty()
                    val unit = cursor.getString(unitIndex)?.trim().orEmpty()
                    if (ingredientId.isNotEmpty() && unit.isNotEmpty()) {
                        destination.getOrPut(ingredientId) { linkedSetOf() }.add(unit)
                    }
                }
            }
        }
    }

    private fun updateIngredientReferences(
        db: SupportSQLiteDatabase,
        table: String,
        canonicalByLegacyId: Map<String, String>
    ) {
        val statement = db.compileStatement(
            "UPDATE $table SET ingredientId = ? WHERE ingredientId = ?"
        )
        canonicalByLegacyId.forEach { (legacyId, canonicalId) ->
            if (legacyId != canonicalId) {
                statement.clearBindings()
                statement.bindString(1, canonicalId)
                statement.bindString(2, legacyId)
                statement.executeUpdateDelete()
            }
        }
    }

    private fun rebuildRecipeIngredients(
        db: SupportSQLiteDatabase,
        canonicalByLegacyId: Map<String, String>
    ) {
        val rows = mutableListOf<RecipeIngredientRow>()
        db.query(
            "SELECT recipeId, ingredientId, quantityBase, unit, prepNote, optional, scalingRule " +
                "FROM recipe_ingredients ORDER BY recipeId, ingredientId, unit"
        ).use { cursor ->
            val recipeIndex = cursor.getColumnIndexOrThrow("recipeId")
            val ingredientIndex = cursor.getColumnIndexOrThrow("ingredientId")
            val quantityIndex = cursor.getColumnIndexOrThrow("quantityBase")
            val unitIndex = cursor.getColumnIndexOrThrow("unit")
            val noteIndex = cursor.getColumnIndexOrThrow("prepNote")
            val optionalIndex = cursor.getColumnIndexOrThrow("optional")
            val scalingIndex = cursor.getColumnIndexOrThrow("scalingRule")
            while (cursor.moveToNext()) {
                val oldIngredientId = cursor.getString(ingredientIndex)
                rows += RecipeIngredientRow(
                    recipeId = cursor.getString(recipeIndex),
                    ingredientId = canonicalByLegacyId[oldIngredientId]
                        ?: identityResolver.canonicalId(oldIngredientId),
                    rowKey = oldIngredientId,
                    quantityBase = cursor.getDouble(quantityIndex),
                    unit = cursor.getString(unitIndex),
                    prepNote = cursor.getString(noteIndex),
                    optional = cursor.getInt(optionalIndex) != 0,
                    scalingRule = cursor.getString(scalingIndex)
                )
            }
        }

        db.execSQL("DROP INDEX IF EXISTS index_recipe_ingredients_ingredientId")
        db.execSQL("ALTER TABLE recipe_ingredients RENAME TO recipe_ingredients_before_food_uuid")
        db.execSQL(
            "CREATE TABLE recipe_ingredients (" +
                "recipeId TEXT NOT NULL, " +
                "ingredientId TEXT NOT NULL, " +
                "quantityBase REAL NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "prepNote TEXT NOT NULL, " +
                "optional INTEGER NOT NULL, " +
                "scalingRule TEXT NOT NULL DEFAULT 'linear', " +
                "rowKey TEXT NOT NULL, " +
                "PRIMARY KEY(recipeId, ingredientId, unit, rowKey))"
        )

        val insert = db.compileStatement(
            "INSERT INTO recipe_ingredients " +
                "(recipeId, ingredientId, quantityBase, unit, prepNote, optional, scalingRule, rowKey) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )
        rows.forEach { row ->
            insert.clearBindings()
            insert.bindString(1, row.recipeId)
            insert.bindString(2, row.ingredientId)
            insert.bindDouble(3, row.quantityBase)
            insert.bindString(4, row.unit)
            insert.bindString(5, row.prepNote)
            insert.bindLong(6, if (row.optional) 1L else 0L)
            insert.bindString(7, row.scalingRule)
            insert.bindString(8, row.rowKey)
            insert.executeInsert()
        }

        db.execSQL("DROP TABLE recipe_ingredients_before_food_uuid")
        db.execSQL("CREATE INDEX index_recipe_ingredients_ingredientId ON recipe_ingredients(ingredientId)")
    }

    private fun rebuildCookedMealDeductions(
        db: SupportSQLiteDatabase,
        canonicalByLegacyId: Map<String, String>
    ) {
        val rows = mutableListOf<CookedMealDeductionRow>()
        db.query(
            "SELECT mealId, ingredientId, unit, quantityBase " +
                "FROM cooked_meal_deductions ORDER BY mealId, ingredientId"
        ).use { cursor ->
            val mealIndex = cursor.getColumnIndexOrThrow("mealId")
            val ingredientIndex = cursor.getColumnIndexOrThrow("ingredientId")
            val unitIndex = cursor.getColumnIndexOrThrow("unit")
            val quantityIndex = cursor.getColumnIndexOrThrow("quantityBase")
            while (cursor.moveToNext()) {
                val oldIngredientId = cursor.getString(ingredientIndex)
                rows += CookedMealDeductionRow(
                    mealId = cursor.getString(mealIndex),
                    ingredientId = canonicalByLegacyId[oldIngredientId]
                        ?: identityResolver.canonicalId(oldIngredientId),
                    unit = cursor.getString(unitIndex),
                    quantityBase = cursor.getDouble(quantityIndex),
                    rowKey = oldIngredientId
                )
            }
        }

        db.execSQL("ALTER TABLE cooked_meal_deductions RENAME TO cooked_meal_deductions_before_food_uuid")
        db.execSQL(
            "CREATE TABLE cooked_meal_deductions (" +
                "mealId TEXT NOT NULL, " +
                "ingredientId TEXT NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "quantityBase REAL NOT NULL, " +
                "rowKey TEXT NOT NULL, " +
                "pantryItemId TEXT, " +
                "PRIMARY KEY(mealId, ingredientId, unit, rowKey))"
        )

        val insert = db.compileStatement(
            "INSERT INTO cooked_meal_deductions " +
                "(mealId, ingredientId, unit, quantityBase, rowKey, pantryItemId) " +
                "VALUES (?, ?, ?, ?, ?, NULL)"
        )
        rows.forEach { row ->
            insert.clearBindings()
            insert.bindString(1, row.mealId)
            insert.bindString(2, row.ingredientId)
            insert.bindString(3, row.unit)
            insert.bindDouble(4, row.quantityBase)
            insert.bindString(5, row.rowKey)
            insert.executeInsert()
        }

        db.execSQL("DROP TABLE cooked_meal_deductions_before_food_uuid")
    }

    private fun rebuildIngredients(
        db: SupportSQLiteDatabase,
        canonicalByLegacyId: Map<String, String>,
        unitsByLegacyId: Map<String, MutableSet<String>>
    ) {
        val rows = mutableListOf<IngredientRow>()
        db.query(
            "SELECT id, nameAr, nameFr, category, baseUnit, allergensCsv, aliasesCsv " +
                "FROM ingredients ORDER BY id"
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val nameArIndex = cursor.getColumnIndexOrThrow("nameAr")
            val nameFrIndex = cursor.getColumnIndexOrThrow("nameFr")
            val categoryIndex = cursor.getColumnIndexOrThrow("category")
            val unitIndex = cursor.getColumnIndexOrThrow("baseUnit")
            val allergensIndex = cursor.getColumnIndexOrThrow("allergensCsv")
            val aliasesIndex = cursor.getColumnIndexOrThrow("aliasesCsv")
            while (cursor.moveToNext()) {
                val legacyId = cursor.getString(idIndex)
                rows += IngredientRow(
                    legacyId = legacyId,
                    canonicalId = canonicalByLegacyId[legacyId] ?: identityResolver.canonicalId(legacyId),
                    nameAr = cursor.getString(nameArIndex),
                    nameFr = cursor.getString(nameFrIndex),
                    category = cursor.getString(categoryIndex),
                    baseUnit = cursor.getString(unitIndex),
                    allergensCsv = cursor.getString(allergensIndex),
                    aliasesCsv = cursor.getString(aliasesIndex)
                )
            }
        }

        val existingLegacyIds = rows.mapTo(hashSetOf()) { it.legacyId }
        canonicalByLegacyId.forEach { (legacyId, canonicalId) ->
            if (legacyId !in existingLegacyIds) {
                val fallbackUnit = unitsByLegacyId[legacyId]?.firstOrNull().orEmpty().ifBlank { "piece" }
                rows += IngredientRow(
                    legacyId = legacyId,
                    canonicalId = canonicalId,
                    nameAr = legacyId,
                    nameFr = legacyId,
                    category = GENERIC_INGREDIENT_CATEGORY,
                    baseUnit = fallbackUnit,
                    allergensCsv = "",
                    aliasesCsv = ""
                )
            }
        }

        val grouped = rows.groupBy { it.canonicalId }
        db.execSQL("ALTER TABLE ingredients RENAME TO ingredients_before_food_uuid")
        db.execSQL(
            "CREATE TABLE ingredients (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "nameAr TEXT NOT NULL, " +
                "nameFr TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "baseUnit TEXT NOT NULL, " +
                "allergensCsv TEXT NOT NULL, " +
                "aliasesCsv TEXT NOT NULL)"
        )

        val insert = db.compileStatement(
            "INSERT INTO ingredients (id, nameAr, nameFr, category, baseUnit, allergensCsv, aliasesCsv) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)"
        )
        grouped.toSortedMap().forEach { (canonicalId, variants) ->
            val merged = mergeIngredientVariants(canonicalId, variants)
            insert.clearBindings()
            insert.bindString(1, merged.canonicalId)
            insert.bindString(2, merged.nameAr)
            insert.bindString(3, merged.nameFr)
            insert.bindString(4, merged.category)
            insert.bindString(5, merged.baseUnit)
            insert.bindString(6, merged.allergensCsv)
            insert.bindString(7, merged.aliasesCsv)
            insert.executeInsert()
        }

        db.execSQL("DROP TABLE ingredients_before_food_uuid")
    }

    private fun mergeIngredientVariants(
        canonicalId: String,
        variants: List<IngredientRow>
    ): IngredientRow {
        val ordered = variants.sortedBy { it.legacyId }
        val nameAr = ordered.firstOrNull { it.nameAr.isNotBlank() }?.nameAr ?: canonicalId
        val nameFr = ordered.firstOrNull { it.nameFr.isNotBlank() }?.nameFr ?: nameAr
        val category = ordered.firstOrNull {
            it.category.isNotBlank() && it.category != GENERIC_INGREDIENT_CATEGORY
        }?.category ?: ordered.firstOrNull { it.category.isNotBlank() }?.category.orEmpty()
        val baseUnit = ordered.groupingBy { it.baseUnit.ifBlank { "piece" } }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key ?: "piece"
        val allergens = ordered.flatMap { it.allergensCsv.split(',') }
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        val aliases = buildList {
            ordered.flatMap { it.aliasesCsv.split('|') }.forEach(::add)
            ordered.map { it.nameAr }.filter { it.isNotBlank() && it != nameAr }.forEach(::add)
            ordered.map { it.nameFr }.filter { it.isNotBlank() && it != nameFr }.forEach(::add)
        }.map(String::trim).filter(String::isNotEmpty).distinct()

        return IngredientRow(
            legacyId = ordered.firstOrNull()?.legacyId.orEmpty(),
            canonicalId = canonicalId,
            nameAr = nameAr,
            nameFr = nameFr,
            category = category,
            baseUnit = baseUnit,
            allergensCsv = allergens.joinToString(","),
            aliasesCsv = aliases.joinToString("|")
        )
    }

    private data class RecipeIngredientRow(
        val recipeId: String,
        val ingredientId: String,
        val rowKey: String,
        val quantityBase: Double,
        val unit: String,
        val prepNote: String,
        val optional: Boolean,
        val scalingRule: String
    )

    private data class CookedMealDeductionRow(
        val mealId: String,
        val ingredientId: String,
        val unit: String,
        val quantityBase: Double,
        val rowKey: String
    )

    private data class IngredientRow(
        val legacyId: String,
        val canonicalId: String,
        val nameAr: String,
        val nameFr: String,
        val category: String,
        val baseUnit: String,
        val allergensCsv: String,
        val aliasesCsv: String
    )

    private companion object {
        const val GENERIC_INGREDIENT_CATEGORY = "مكوّن"
    }
}
