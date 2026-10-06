package com.example.memorysteps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.memorysteps.ui.theme.AppDimensions

internal enum class ActionButtonSize { Regular, Large }

/** All page, game and pause actions use these two sizes and the theme's typography. */
@Composable
internal fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    size: ActionButtonSize = ActionButtonSize.Regular,
    fillWidth: Boolean = true,
) {
    val large = size == ActionButtonSize.Large
    val height = if (large) AppDimensions.largeButtonMinHeight else AppDimensions.buttonMinHeight
    val bounds = (if (fillWidth) modifier.fillMaxWidth() else modifier).heightIn(min = height)
    val padding = if (large) PaddingValues(AppDimensions.pagePadding) else PaddingValues(
        horizontal = AppDimensions.sectionGap,
        vertical = AppDimensions.itemGap,
    )
    val label: @Composable () -> Unit = {
        Text(
            text,
            style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
    if (primary) {
        Button(
            onClick = onClick, modifier = bounds, enabled = enabled,
            shape = MaterialTheme.shapes.small, contentPadding = padding,
        ) { label() }
    } else {
        OutlinedButton(
            onClick = onClick, modifier = bounds, enabled = enabled,
            shape = MaterialTheme.shapes.small, contentPadding = padding,
            border = BorderStroke(AppDimensions.borderWidth, MaterialTheme.colorScheme.primary),
        ) { label() }
    }
}

@Composable
internal fun DialogActionButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = AppDimensions.buttonMinHeight),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(AppDimensions.itemGap),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}
