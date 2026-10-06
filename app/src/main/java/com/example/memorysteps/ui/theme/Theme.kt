package com.example.memorysteps.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable

private val MemoryShapes = Shapes(
    extraSmall = RoundedCornerShape(AppDimensions.controlCorner),
    small = RoundedCornerShape(AppDimensions.controlCorner),
    medium = RoundedCornerShape(AppDimensions.dialogCorner),
    large = RoundedCornerShape(AppDimensions.dialogCorner),
    extraLarge = RoundedCornerShape(AppDimensions.dialogCorner),
)

@Composable
fun MemoryStepsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MemoryColors,
        typography = MemoryTypography,
        shapes = MemoryShapes,
        content = content,
    )
}
