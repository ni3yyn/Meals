package dz.meals.app

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dz.meals.app.ui.MealsApp
import dz.meals.app.ui.theme.MidaTheme

private val TajawalAndroidFontFamily = FontFamily(
    Font(R.font.tajawal_regular, weight = FontWeight.Normal),
    Font(R.font.tajawal_medium, weight = FontWeight.Medium),
    Font(R.font.tajawal_bold, weight = FontWeight.Bold),
    Font(R.font.tajawal_extra_bold, weight = FontWeight.ExtraBold)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        setContent {
            MidaTheme(fontFamily = TajawalAndroidFontFamily) {
                MealsApp()
            }
        }
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
