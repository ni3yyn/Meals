package dz.meals.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MidaColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = SoftGreen,
    onPrimaryContainer = ForestDark,
    secondary = Tangerine,
    onSecondary = Ink,
    tertiary = SoftPink,
    onTertiary = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SoftGreen,
    onSurfaceVariant = MutedInk,
    outline = Border,
    outlineVariant = Border.copy(alpha = 0.65f),
    error = Color(0xFFB64A45),
    onError = Color.White
)

private val MidaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(21.dp),
    large = RoundedCornerShape(27.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MidaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MidaColors,
        shapes = MidaShapes,
        typography = MaterialTheme.typography.copy(
            headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 29.sp, lineHeight = 38.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 27.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 19.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp)
        ),
        content = content
    )
}
