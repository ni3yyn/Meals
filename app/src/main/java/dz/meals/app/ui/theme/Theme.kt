package dz.meals.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MidaColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    secondary = Tangerine,
    onSecondary = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SoftGreen,
    onSurfaceVariant = MutedInk,
    outline = Border
)

@Composable
fun MidaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MidaColors,
        typography = MaterialTheme.typography.copy(
            headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 31.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 21.sp)
        ),
        content = content
    )
}
