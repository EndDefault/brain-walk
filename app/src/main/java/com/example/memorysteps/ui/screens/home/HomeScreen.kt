package com.example.memorysteps.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.example.memorysteps.R
import com.example.memorysteps.ui.components.ActionButton
import com.example.memorysteps.ui.components.ActionButtonSize
import com.example.memorysteps.ui.components.Page
import com.example.memorysteps.ui.theme.AppDimensions

@Composable
fun HomeScreen(
    onLearn: () -> Unit,
    onRecords: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    Page {
        Column(Modifier.widthIn(max = AppDimensions.menuMaxWidth).fillMaxWidth().align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppDimensions.sectionGap)) {
            Spacer(Modifier.height(AppDimensions.sectionGap))
            Text(stringResource(R.string.game_caption), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.brand_name), Modifier.fillMaxWidth().semantics { heading() },
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            HorizontalDivider(thickness = AppDimensions.titleDividerWidth, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(AppDimensions.mediumGap))
            listOf(R.string.learn_menu to onLearn, R.string.home_title to onRecords, R.string.guide_button to onOpenGuide).forEach { (label, click) ->
                ActionButton(stringResource(label), click, size = ActionButtonSize.Large)
            }
        }
    }
}
