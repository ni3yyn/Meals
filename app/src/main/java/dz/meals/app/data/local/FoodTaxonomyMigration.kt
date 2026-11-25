package dz.meals.app.data.local

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.io.IOException

/**
 * Normalizes the v15 food catalogue into taxonomy tables.
 * Reads the canonical bundled taxonomy so upgrades work fully offline.
 * Any bad or missing reference aborts Room's migration transaction without data loss.
 */
internal class FoodTaxonomyMigration(context: Context) : Migration(15, 16) {
    private val appContext = context.applicationContext

    override fun migrate(db: SupportSQLiteDatabase) {
        val oldFoodCount = count(db, "SELECT COUNT(*) FROM food_items")
        val taxonomy = readTaxonomy()
        createTaxonomyTables(db)
        insertTaxonomy(db, taxonomy)
        validateExistingFoodReferences(db)

        db.execSQL("ALTER TABLE food_items RENAME TO food_items_before_taxonomy")
        createNormalizedFoodItemsTable(db)
        db.execSQL(
            """
            INSERT INTO food_items (
                id, nameAr, nameEn, nameFr, aliasesArCsv, aliasesEnCsv, aliasesFrCsv,
                categoryId, subcategoryId, acceptedUnitsCsv, imageUrl, quantity,
                countryOfOrigin, nutritionJson, sourceMappingsJson, recipeIngredientIdsCsv, sourceJson
            )
            SELECT
                id, nameAr, nameEn, nameFr, aliasesArCsv, aliasesEnCsv, aliasesFrCsv,
                categoryId, subcategoryId, acceptedUnitsCsv, imageUrl, quantity,
                countryOfOrigin, nutritionJson, sourceMappingsJson, recipeIngredientIdsCsv, sourceJson
            FROM food_items_before_taxonomy
            """.trimIndent()
        )

        val newCount = count(db, "SELECT COUNT(*) FROM food_items")
        if (newCount != oldFoodCount) {
            throw IllegalStateException("Food taxonomy migration copied $newCount of $oldFoodCount food rows.")
        }
        val missingIds = count(
            db,
            """
            SELECT COUNT(*) FROM food_items_before_taxonomy old
            LEFT JOIN food_items current ON current.id = old.id
            WHERE current.id IS NULL
            """.trimIndent()
        )
        if (missingIds != 0) throw IllegalStateException("Food taxonomy migration lost $missingIds food identities.")

        db.execSQL("DROP TABLE food_items_before_taxonomy")
        db.execSQL(
            "CREATE INDEX index_food_items_categoryId_subcategoryId ON food_items(categoryId, subcategoryId)"
        )

        val foreignKeyErrors = db.query("PRAGMA foreign_key_check").use { cursor ->
            var count = 0
            while (cursor.moveToNext()) count++
            count
        }
        if (foreignKeyErrors != 0) {
            throw IllegalStateException("Food taxonomy migration created $foreignKeyErrors foreign-key violations.")
        }
        if (count(db, "SELECT COUNT(*) FROM food_categories") != EXPECTED_CATEGORIES ||
            count(db, "SELECT COUNT(*) FROM food_subcategories") != EXPECTED_SUBCATEGORIES
        ) throw IllegalStateException("Canonical food taxonomy was not fully installed.")
    }

    private fun createTaxonomyTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE food_categories (
                id TEXT NOT NULL, nameAr TEXT NOT NULL, nameEn TEXT NOT NULL,
                nameFr TEXT NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE food_subcategories (
                id TEXT NOT NULL, categoryId TEXT NOT NULL, nameAr TEXT NOT NULL,
                nameEn TEXT NOT NULL, nameFr TEXT NOT NULL, sortOrder INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(categoryId) REFERENCES food_categories(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_food_subcategories_categoryId ON food_subcategories(categoryId)")
        db.execSQL("CREATE UNIQUE INDEX index_food_subcategories_categoryId_id ON food_subcategories(categoryId, id)")
        db.execSQL("CREATE INDEX index_food_categories_sortOrder ON food_categories(sortOrder)")
        db.execSQL("CREATE INDEX index_food_subcategories_sortOrder ON food_subcategories(sortOrder)")
    }

    private fun insertTaxonomy(db: SupportSQLiteDatabase, taxonomy: Taxonomy) {
        val categoryInsert = db.compileStatement(
            "INSERT INTO food_categories(id, nameAr, nameEn, nameFr, sortOrder) VALUES (?, ?, ?, ?, ?)"
        )
        taxonomy.categories.forEach { row ->
            categoryInsert.clearBindings()
            categoryInsert.bindString(1, row.id)
            categoryInsert.bindString(2, row.nameAr)
            categoryInsert.bindString(3, row.nameEn)
            categoryInsert.bindString(4, row.nameFr)
            categoryInsert.bindLong(5, row.sortOrder.toLong())
            categoryInsert.executeInsert()
        }
        val subcategoryInsert = db.compileStatement(
            "INSERT INTO food_subcategories(id, categoryId, nameAr, nameEn, nameFr, sortOrder) VALUES (?, ?, ?, ?, ?, ?)"
        )
        taxonomy.subcategories.forEach { row ->
            subcategoryInsert.clearBindings()
            subcategoryInsert.bindString(1, row.id)
            subcategoryInsert.bindString(2, row.categoryId)
            subcategoryInsert.bindString(3, row.nameAr)
            subcategoryInsert.bindString(4, row.nameEn)
            subcategoryInsert.bindString(5, row.nameFr)
            subcategoryInsert.bindLong(6, row.sortOrder.toLong())
            subcategoryInsert.executeInsert()
        }
    }

    private fun createNormalizedFoodItemsTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE food_items (
                id TEXT NOT NULL, nameAr TEXT NOT NULL, nameEn TEXT NOT NULL, nameFr TEXT NOT NULL,
                aliasesArCsv TEXT NOT NULL, aliasesEnCsv TEXT NOT NULL, aliasesFrCsv TEXT NOT NULL,
                categoryId TEXT NOT NULL, subcategoryId TEXT NOT NULL,
                acceptedUnitsCsv TEXT NOT NULL, imageUrl TEXT NOT NULL, quantity REAL,
                countryOfOrigin TEXT NOT NULL, nutritionJson TEXT NOT NULL,
                sourceMappingsJson TEXT NOT NULL, recipeIngredientIdsCsv TEXT NOT NULL,
                sourceJson TEXT NOT NULL, PRIMARY KEY(id),
                FOREIGN KEY(categoryId, subcategoryId)
                    REFERENCES food_subcategories(categoryId, id)
                    ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
    }

    private fun validateExistingFoodReferences(db: SupportSQLiteDatabase) {
        val invalid = count(
            db,
            """
            SELECT COUNT(*) FROM food_items AS food
            WHERE NOT EXISTS (SELECT 1 FROM food_categories category WHERE category.id = food.categoryId)
               OR NOT EXISTS (
                    SELECT 1 FROM food_subcategories subcategory
                    WHERE subcategory.id = food.subcategoryId AND subcategory.categoryId = food.categoryId
               )
            """.trimIndent()
        )
        if (invalid != 0) {
            throw IllegalStateException("$invalid existing food records reference an invalid taxonomy entry.")
        }
    }

    private fun readTaxonomy(): Taxonomy {
        val root = try {
            appContext.assets.open(CATEGORIES_ASSET).bufferedReader(Charsets.UTF_8).use {
                JSONObject(it.readText())
            }
        } catch (error: Exception) {
            throw IOException("Canonical food taxonomy asset is missing or invalid; migration cannot continue.", error)
        }
        val array = root.optJSONArray("categories")
            ?: throw IOException("Canonical taxonomy has no categories array.")
        if (array.length() != EXPECTED_CATEGORIES) {
            throw IOException("Canonical taxonomy has ${array.length()} categories, expected $EXPECTED_CATEGORIES.")
        }
        val categories = ArrayList<FoodCategoryRow>(EXPECTED_CATEGORIES)
        val subcategories = ArrayList<FoodSubcategoryRow>(EXPECTED_SUBCATEGORIES)
        val categoryIds = HashSet<String>()
        val subcategoryIds = HashSet<String>()
        for (categoryIndex in 0 until array.length()) {
            val category = array.optJSONObject(categoryIndex) ?: throw IOException("Invalid category at $categoryIndex.")
            val id = requiredString(category, "id", "category[$categoryIndex]")
            if (!categoryIds.add(id)) throw IOException("Duplicate category ID: $id.")
            val names = category.optJSONObject("names") ?: throw IOException("Missing names for category $id.")
            categories += FoodCategoryRow(
                id, requiredString(names, "ar", id), requiredString(names, "en", id),
                requiredString(names, "fr", id), categoryIndex
            )
            val children = category.optJSONArray("subcategories")
                ?: throw IOException("Missing subcategories for category $id.")
            for (childIndex in 0 until children.length()) {
                val child = children.optJSONObject(childIndex) ?: throw IOException("Invalid child for $id.")
                val childId = requiredString(child, "id", "$id.subcategory[$childIndex]")
                if (!subcategoryIds.add(childId)) throw IOException("Duplicate subcategory ID: $childId.")
                val childNames = child.optJSONObject("names") ?: throw IOException("Missing names for $childId.")
                subcategories += FoodSubcategoryRow(
                    childId, id, requiredString(childNames, "ar", childId),
                    requiredString(childNames, "en", childId), requiredString(childNames, "fr", childId), childIndex
                )
            }
        }
        if (subcategories.size != EXPECTED_SUBCATEGORIES) {
            throw IOException("Canonical taxonomy has ${subcategories.size} subcategories, expected $EXPECTED_SUBCATEGORIES.")
        }
        return Taxonomy(categories, subcategories)
    }

    private fun requiredString(json: JSONObject, key: String, owner: String): String =
        json.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
            ?: throw IOException("$owner is missing a non-empty $key label.")

    private fun count(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private data class FoodCategoryRow(val id: String, val nameAr: String, val nameEn: String, val nameFr: String, val sortOrder: Int)
    private data class FoodSubcategoryRow(val id: String, val categoryId: String, val nameAr: String, val nameEn: String, val nameFr: String, val sortOrder: Int)
    private data class Taxonomy(val categories: List<FoodCategoryRow>, val subcategories: List<FoodSubcategoryRow>)

    private companion object {
        const val EXPECTED_CATEGORIES = 22
        const val EXPECTED_SUBCATEGORIES = 82
        const val CATEGORIES_ASSET = "food/categories.json"
    }
}
