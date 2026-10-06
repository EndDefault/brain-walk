package com.example.memorysteps.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.example.memorysteps.ui.theme.AppDimensions

@Composable
internal fun Page(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = AppDimensions.pageMaxWidth).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(AppDimensions.pagePadding),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.sectionGap),
                content = content,
            )
        }
    }
}

@Composable
internal fun PageTitle(text: String, centered: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}
