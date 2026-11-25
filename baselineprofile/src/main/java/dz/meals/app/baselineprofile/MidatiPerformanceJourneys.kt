package dz.meals.app.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "dz.meals.app"
private const val UI_TIMEOUT_MS = 30_000L
private const val ADD_ROW_COUNT = 9

private fun benchmarkUiDevice(): UiDevice =
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

/**
 * First-run-safe setup shared by the profile generator and category benchmark.
 * The app keeps its local data between iterations, so adding several rows once
 * gives the category sheet enough content to exercise actual lazy scrolling.
 */
private fun MacrobenchmarkScope.openAppAndPrepareShopping(device: UiDevice) {
    pressHome()
    startActivityAndWait()

    // New installs start in the quick onboarding flow. Existing installs skip it.
    device.wait(Until.findObject(By.text("ابدئي الآن")), 5_000L)?.click()

    val shoppingTab = device.wait(
        Until.findObject(By.res("nav.tab.2")),
        UI_TIMEOUT_MS
    ) ?: error("Shopping navigation tab did not become available")
    shoppingTab.click()

    repeat(ADD_ROW_COUNT) { index ->
        val search = device.wait(
            Until.findObject(By.res("shopping.search")),
            UI_TIMEOUT_MS
        ) ?: error("Shopping search field did not become available")
        search.click()
        search.setText("zzmidatibenchmarkerow$index")
        device.pressBack() // Hide the keyboard so the empty-state action is tappable.

        val addCustom = device.wait(
            Until.findObject(By.res("shopping.addCustomItem")),
            UI_TIMEOUT_MS
        ) ?: error("The custom-item action did not appear for an unmatched search")
        addCustom.click()

        // Modal sheets live in their own Android window, so use the
        // focused text input and visible action label rather than Activity IDs.
        val name = device.wait(
            Until.findObject(By.clazz("android.widget.EditText")),
            UI_TIMEOUT_MS
        ) ?: error("Custom-item name field did not appear")
        name.click()
        name.setText("Benchmark item $index")
        device.pressBack()

        val confirm = device.wait(
            Until.findObject(By.text("إضافة")),
            UI_TIMEOUT_MS
        ) ?: error("Custom-item save action did not appear")
        confirm.click()

        // The sheet dismisses before the async repository refresh publishes the row.
        device.wait(Until.gone(By.text("إضافة عنصر يدوي")), UI_TIMEOUT_MS)

        val clearedSearch = device.wait(
            Until.findObject(By.res("shopping.search")),
            UI_TIMEOUT_MS
        ) ?: error("Shopping search field disappeared after adding an item")
        clearedSearch.click()
        clearedSearch.setText("")
        device.pressBack()

        device.wait(
            Until.hasObject(By.res("shopping.category.row")),
            UI_TIMEOUT_MS
        )
    }
}

/** Captures startup separately so setup and sheet navigation do not pollute Startup Profile rules. */
@RunWith(AndroidJUnit4::class)
class MidatiBaselineProfile {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun coldStartup() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()
            val currentDevice = benchmarkUiDevice()
            val quickStart = currentDevice.wait(
                Until.findObject(By.text("ابدئي الآن")),
                10_000L
            )
            if (quickStart == null) {
                currentDevice.wait(
                    Until.hasObject(By.res("nav.tab.2")),
                    UI_TIMEOUT_MS
                )
            }
        }
    }

    @Test
    fun shoppingCategoryNavigationAndScroll() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false
        ) {
            val currentDevice = benchmarkUiDevice()
            openAppAndPrepareShopping(currentDevice)

            val category = currentDevice.wait(
                Until.findObject(By.res("shopping.category.row")),
                UI_TIMEOUT_MS
            ) ?: error("No shopping category row was available")
            category.click()

            // The modal close action is a unique accessibility anchor for this sheet.
            currentDevice.wait(
                Until.hasObject(By.desc("إغلاق")),
                UI_TIMEOUT_MS
            )

            // Exercise real scrolling through the sheet's lazy content.
            repeat(3) {
                currentDevice.swipe(
                    currentDevice.displayWidth / 2,
                    (currentDevice.displayHeight * 0.76f).toInt(),
                    currentDevice.displayWidth / 2,
                    (currentDevice.displayHeight * 0.42f).toInt(),
                    18
                )
            }
            pressHome()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class MidatiMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupTiming() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() }
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun shoppingCategorySheetFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = {
                openAppAndPrepareShopping(benchmarkUiDevice())
                pressHome()
            }
        ) {
            startActivityAndWait()
            val currentDevice = benchmarkUiDevice()

            val shoppingTab = currentDevice.wait(
                Until.findObject(By.res("nav.tab.2")),
                UI_TIMEOUT_MS
            ) ?: error("Shopping navigation tab did not become available")
            shoppingTab.click()

            val category = currentDevice.wait(
                Until.findObject(By.res("shopping.category.row")),
                UI_TIMEOUT_MS
            ) ?: error("No shopping category row was available")
            category.click()

            currentDevice.wait(
                Until.hasObject(By.desc("إغلاق")),
                UI_TIMEOUT_MS
            )

            repeat(3) {
                currentDevice.swipe(
                    currentDevice.displayWidth / 2,
                    (currentDevice.displayHeight * 0.76f).toInt(),
                    currentDevice.displayWidth / 2,
                    (currentDevice.displayHeight * 0.42f).toInt(),
                    18
                )
            }
            currentDevice.pressBack()
        }
    }
}
