package com.example.memorysteps.ui.screens.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.memorysteps.R
import com.example.memorysteps.ui.components.ActionButton
import com.example.memorysteps.ui.theme.AppDimensions

@Composable
internal fun PauseMenu(restart: Boolean, enabled: Boolean, onContinue: () -> Unit, onBack: () -> Unit, onHome: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(AppDimensions.pagePadding).widthIn(max = AppDimensions.dialogMaxWidth).fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(AppDimensions.pagePadding),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppDimensions.sectionGap)) {
                StageTitle(stringResource(R.string.pause_title))
                if (restart) Text(stringResource(R.string.pause_notice), textAlign = TextAlign.Center)
                ActionButton(stringResource(if (restart) R.string.resume_question else R.string.pause_continue), onContinue, enabled = enabled)
                ActionButton(stringResource(R.string.pause_back), onBack, primary = false, enabled = enabled)
                ActionButton(stringResource(R.string.home_button), onHome, primary = false, enabled = enabled)
            }
        }
    }
}
