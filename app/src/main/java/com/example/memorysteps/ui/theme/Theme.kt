package com.example.memorysteps.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MemoryColors = lightColorScheme(
    primary = Color(0xFF245F54),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EDE5),
    onPrimaryContainer = Color(0xFF193F37),
    secondary = Color(0xFF79552C),
    background = Color(0xFFF6F5EF),
    onBackground = Color(0xFF203B36),
    surface = Color(0xFFFFFEFA),
    onSurface = Color(0xFF203B36),
    onSurfaceVariant = Color(0xFF52635B),
    outlineVariant = Color(0xFFD5DED5),
)

private val MemoryTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 52.sp,
    ),
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 34.sp),
    bodyMedium = TextStyle(fontSize = 20.sp, lineHeight = 30.sp),
    labelLarge = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun MemoryStepsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MemoryColors, typography = MemoryTypography, content = content)
}
