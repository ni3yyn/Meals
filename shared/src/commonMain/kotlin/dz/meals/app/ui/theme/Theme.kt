package dz.meals.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dz.meals.app.shared.generated.resources.Res
import dz.meals.app.shared.generated.resources.baloo_bhaijaan_2_variable
import org.jetbrains.compose.resources.Font

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
    onSurfaceVariant = BodyInk,
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

/**
 * Bundled with the shared UI so Arabic shaping and Latin numerals are identical
 * on Android and iOS rather than depending on the host device's font fallback.
 */
@Composable
private fun MidaTypography(hostFontFamily: FontFamily? = null): Typography {
    // Use the real variable-font axis, not synthetic bold. The normal text
    // baseline starts at 500 so the app keeps Baloo's fuller, softer character;
    // headings and emphasis can reach the font's true ExtraBold 800 weight.
    val balooBhaijaan2 = hostFontFamily ?: FontFamily(
        Font(
            Res.font.baloo_bhaijaan_2_variable,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))
        ),
        Font(
            Res.font.baloo_bhaijaan_2_variable,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(600))
        ),
        Font(
            Res.font.baloo_bhaijaan_2_variable,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(650))
        ),
        Font(
            Res.font.baloo_bhaijaan_2_variable,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700))
        ),
        Font(
            Res.font.baloo_bhaijaan_2_variable,
            weight = FontWeight.ExtraBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(800))
        )
    )

    return Typography(
        displayLarge = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 44.sp),
        displayMedium = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 39.sp),
        displaySmall = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 36.sp),
        headlineLarge = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 34.sp),
        headlineMedium = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
        headlineSmall = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 27.sp),
        titleMedium = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 25.sp),
        titleSmall = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp),
        labelMedium = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
        labelSmall = TextStyle(fontFamily = balooBhaijaan2, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
    )
}

@Composable
fun MidaTheme(
    fontFamily: FontFamily? = null,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MidaColors,
        shapes = MidaShapes,
        typography = MidaTypography(fontFamily),
        content = content
    )
}
