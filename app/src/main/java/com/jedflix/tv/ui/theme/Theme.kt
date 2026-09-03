package com.jedflix.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

private val JedflixColorScheme = darkColorScheme(
    primary = JedflixRed,
    onPrimary = WarmWhite,
    primaryContainer = JedflixRedDark,
    onPrimaryContainer = WarmWhite,
    secondary = Zinc300,
    onSecondary = Zinc950,
    background = Zinc950,
    onBackground = WarmWhite,
    surface = Zinc950,
    onSurface = WarmWhite,
    surfaceVariant = Zinc900,
    onSurfaceVariant = Zinc400,
    inverseSurface = WarmWhite,
    inverseOnSurface = Zinc950,
    border = Zinc700,
    borderVariant = Zinc800,
    error = JedflixRed,
    onError = WarmWhite,
)

private val JedflixTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, fontSize = 48.sp, lineHeight = 52.sp),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 44.sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
    )
}

@Composable
fun JedflixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JedflixColorScheme,
        typography = JedflixTypography,
        content = content,
    )
}
