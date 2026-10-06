package com.example.memorysteps.ui.screens.learn

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.memorysteps.R
import com.example.memorysteps.data.CycleHistory
import com.example.memorysteps.game.TrainingMode
import com.example.memorysteps.ui.components.*
import com.example.memorysteps.ui.theme.MenuStyle

@Composable
fun LearnScreen(active: CycleHistory?, enabled: Boolean, onStart: () -> Unit, onPractice: (TrainingMode) -> Unit,
    onStop: () -> Unit, onHome: () -> Unit) {
    MenuPage {
        MenuHeader(stringResource(R.string.learn_menu), stringResource(R.string.learn_subtitle), onHome)
        MenuPanel(dark = true) {
            SectionTitle(stringResource(R.string.challenge_heading))
            Text(stringResource(R.string.mixed_description), style = MaterialTheme.typography.bodyLarge)
            MenuAction(stringResource(if (active == null) R.string.mixed_title else R.string.continue_training),
                MenuSymbol.PLAY, onStart, primary = true, enabled = enabled)
            if (active != null) {
                Text(stringResource(R.string.saved_progress, active.problems))
            }
            Text(stringResource(R.string.challenge_notice), style = MaterialTheme.typography.bodyMedium)
        }
        if (active != null) ActionButton(stringResource(R.string.end_training), onStop, primary = false, enabled = enabled)
        SectionAccent(stringResource(R.string.practice_heading))
        Text(stringResource(R.string.practice_notice), style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
        AdaptiveMenuRow(listOf(
            { MenuAction(stringResource(R.string.type_color), MenuSymbol.COLOR,
                { onPractice(TrainingMode.COLOR) }, enabled = enabled) },
            { MenuAction(stringResource(R.string.type_picture), MenuSymbol.PICTURE,
                { onPractice(TrainingMode.PICTURE) }, enabled = enabled, accent = MenuStyle.blue, tint = MenuStyle.blueTint) },
            { MenuAction(stringResource(R.string.type_number), MenuSymbol.NUMBER,
                { onPractice(TrainingMode.NUMBER) }, enabled = enabled, accent = MenuStyle.plum, tint = MenuStyle.plumTint) },
        ))
        MenuPanel {
            Text(stringResource(R.string.session_notice), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
