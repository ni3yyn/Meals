package dz.meals.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dz.meals.app.ui.MealsApp
import dz.meals.app.ui.theme.MidaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MidaTheme { MealsApp() } }
    }
}
