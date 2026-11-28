package dz.meals.app.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dz.meals.app.data.local.MealsDatabase
import kotlinx.coroutines.runBlocking
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarketProductCatalogueInstallerTest {

    @Test
    fun installsUniqueProductsSourcesAndPriceSnapshotsIdempotently() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "market-products-${UUID.randomUUID()}.db"
        val preferences = context.getSharedPreferences(
            "market-products-test-$databaseName",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val database = MealsDatabase.createForTesting(context, databaseName)

        try {
            val installer = MarketProductCatalogueInstaller(context, database, preferences)
            assertEquals(1_803, installer.installIfNeeded())

            val dao = database.mealsDao()
            assertEquals(1_803, dao.countMarketProducts())
            assertEquals(1_803, dao.countMarketProductSources())
            assertEquals(1_803, dao.countMarketPriceObservations())

            val products = dao.getMarketProducts(2_000)
            assertEquals(1_803, products.size)
            assertEquals(1_803, products.map { it.id }.toSet().size)
            assertEquals(1_803, products.map { it.identityKey }.toSet().size)
            assertEquals(1_958, products.sumOf { it.sourceRowCount })
            assertEquals(49, products.count { it.gtin != null })
            assertEquals(3, products.count { it.brand != null })
            assertTrue(products.none { it.brand?.startsWith("Poids", ignoreCase = true) == true })
            assertTrue(products.all { it.imageUrl.startsWith("https://") })

            val prices = dao.getAllMarketPriceObservations()
            assertEquals(1_803, prices.size)
            assertEquals(932, prices.count { it.availabilityRaw.contains("OutOfStock", ignoreCase = true) })
            assertEquals(38, prices.count {
                it.priceDzd == null && it.priceMinDzd != null && it.priceMaxDzd != null
            })

            val brandedProducts = dao.searchMarketProducts("FERRERO", 10)
            assertEquals(2, brandedProducts.size)
            val selected = brandedProducts.first()
            val source = dao.getMarketProductSources(selected.id).single()
            assertTrue(source.storeName.isNotBlank())
            assertEquals(1, dao.getMarketPriceObservations(selected.id).size)

            assertEquals(0, installer.installIfNeeded())
            assertEquals(1_803, dao.countMarketProducts())
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            preferences.edit().clear().commit()
        }
    }
}
