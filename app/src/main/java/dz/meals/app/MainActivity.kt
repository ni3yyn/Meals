package dz.meals.app

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import dz.meals.app.ui.MealsApp
import dz.meals.app.ui.theme.MidaTheme

class MainActivity : ComponentActivity() {
    private var isInitialContentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        MealsStartupLogger.mark("MainActivity.onCreate entered")
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !isInitialContentReady }
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .translationY(-12f)
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(210L)
                .withEndAction { provider.remove() }
                .start()
        }
        MealsStartupLogger.mark("MainActivity.super.onCreate completed")

        configureSystemBars()
        MealsStartupLogger.mark("System bars configured")

        setContent {
            MealsStartupLogger.markOnce("First Compose content pass entered")
            MidaTheme {
                MealsStartupLogger.markOnce("MidaTheme composition entered")
                MealsApp(
                    onInitialContentReady = { isInitialContentReady = true },
                    onExit = { finish() }
                )
            }
        }
        MealsStartupLogger.mark("MainActivity.setContent returned")
    }

    override fun onStart() {
        super.onStart()
        MealsStartupLogger.mark("MainActivity.onStart")
    }

    override fun onResume() {
        super.onResume()
        MealsStartupLogger.mark("MainActivity.onResume")
    }

    private fun configureSystemBars() {
        // Draw the pastel backdrop continuously behind both system bars.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
            window.navigationBarDividerColor = AndroidColor.TRANSPARENT
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            // Meals currently uses a light porcelain surface throughout.
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }
}
