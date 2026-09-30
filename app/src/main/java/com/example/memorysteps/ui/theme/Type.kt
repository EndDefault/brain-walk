package com.example.memorysteps.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.memorysteps.R

// Original Pretendard 1.3.9 files, bundled for offline use. See licenses/Pretendard-OFL-1.1.txt.
private val MemoryFontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_black, FontWeight.Black),
)

private fun textStyle(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = MemoryFontFamily,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
)

internal val MemoryTypography = Typography(
    displayLarge = textStyle(64, 84, FontWeight.Black),
    displayMedium = textStyle(56, 76, FontWeight.Bold),
    displaySmall = textStyle(40, 52, FontWeight.Bold),
    headlineLarge = textStyle(38, 52, FontWeight.Bold),
    headlineMedium = textStyle(30, 42, FontWeight.Bold),
    headlineSmall = textStyle(28, 40, FontWeight.Bold),
    titleLarge = textStyle(24, 34, FontWeight.Bold),
    titleMedium = textStyle(22, 32, FontWeight.Bold),
    titleSmall = textStyle(20, 30, FontWeight.Bold),
    bodyLarge = textStyle(22, 34),
    bodyMedium = textStyle(20, 30),
    bodySmall = textStyle(20, 30),
    labelLarge = textStyle(22, 32, FontWeight.Bold),
    labelMedium = textStyle(20, 30, FontWeight.Bold),
    labelSmall = textStyle(20, 30, FontWeight.Bold),
)

/** Special game sizes share the same font as MaterialTheme.typography. */
internal object GameTextStyles {
    val countdown = textStyle(64, 80, FontWeight.Black)
    val waitCountdown = textStyle(160, 190, FontWeight.Black)
    val targetNumber = textStyle(96, 112, FontWeight.Bold)
    val optionNumber = textStyle(40, 48, FontWeight.Bold)
}
