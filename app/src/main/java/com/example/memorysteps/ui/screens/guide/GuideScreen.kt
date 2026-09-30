package com.example.memorysteps.ui.screens.guide

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.memorysteps.R
import com.example.memorysteps.ui.components.ActionButton
import com.example.memorysteps.ui.components.Page
import com.example.memorysteps.ui.components.PageTitle

@Composable
fun GuideScreen(onReturnHome: () -> Unit, onLicenses: () -> Unit) {
    Page {
        PageTitle(stringResource(R.string.guide_title))
        Text(stringResource(R.string.guide_description), style = MaterialTheme.typography.bodyLarge)
        listOf(
            R.string.remember_title to R.string.remember_description,
            R.string.wait_title to R.string.wait_description,
            R.string.solve_title to R.string.solve_description,
        ).forEachIndexed { index, (title, description) ->
            HorizontalDivider()
            Text("${index + 1}. ${stringResource(title)}", style = MaterialTheme.typography.titleLarge)
            Text(stringResource(description), style = MaterialTheme.typography.bodyLarge)
        }
        ActionButton(stringResource(R.string.home_button), onReturnHome)
        ActionButton(stringResource(R.string.licenses_title), onLicenses, primary = false)
    }
}
