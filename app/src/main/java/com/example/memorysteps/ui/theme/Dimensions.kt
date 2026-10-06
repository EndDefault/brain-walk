package com.example.memorysteps.ui.theme

import androidx.compose.ui.unit.dp

/** Shared spacing, control sizes and page bounds, analogous to CSS design variables. */
internal object AppDimensions {
    val tinyGap = 4.dp
    val smallGap = 8.dp
    val mediumGap = 12.dp
    val itemGap = 16.dp
    val sectionGap = 20.dp
    val pagePadding = 24.dp
    val wideGap = 40.dp
    val borderWidth = 2.dp
    val titleDividerWidth = 3.dp
    val controlCorner = 8.dp
    val dialogCorner = 16.dp

    val buttonMinHeight = 64.dp
    val largeButtonMinHeight = 88.dp
    val pageMaxWidth = 1000.dp
    val menuMaxWidth = 680.dp
    val gameMaxWidth = 1100.dp
    val gameActionMaxWidth = 800.dp
    val dialogMaxWidth = 520.dp
    val wideLayoutMinWidth = 600.dp
    const val wideLayoutMaxFontScale = 1.3f

    val targetMinSize = 220.dp
    val targetMaxSize = 340.dp
    val optionMinSize = 96.dp
    val optionMaxHeight = 184.dp
    val labeledOptionMinHeight = 164.dp
    val labeledOptionImageHeight = 124.dp
    const val disabledOptionAlpha = 0.4f
}
