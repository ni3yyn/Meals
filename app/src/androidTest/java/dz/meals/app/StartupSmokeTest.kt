package dz.meals.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupSmokeTest {

    @Test
    fun launcherActivityStartsAndStartupDiagnosticsAreWritten() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val diagnosticsDirectory = java.io.File(targetContext.filesDir, "diagnostics")
        val updateIconPath =
            "composeResources/dz.meals.app.shared.generated.resources/drawable/meals_icon_update.xml"

        // Check the exact asset path reported by the user's production crash.
        targetContext.assets.open(updateIconPath).use { stream ->
            assertTrue("Packaged Compose resource is empty: $updateIconPath", stream.read() >= 0)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("MainActivity unexpectedly finished during launch", activity.isFinishing)
            }
        }

        val reports = diagnosticsDirectory.listFiles()
            ?.filter { it.isFile && it.name.startsWith("Meals-startup-") }
            .orEmpty()

        assertTrue(
            "MealsApplication did not create a startup diagnostics file",
            reports.any { report ->
                runCatching { report.readText().contains("Diagnostics initialized") }.getOrDefault(false)
            }
        )
    }
}
