package com.example.memorysteps.ui.screens.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.memorysteps.R
import com.example.memorysteps.ui.components.*
import com.example.memorysteps.ui.theme.AppDimensions
import com.example.memorysteps.ui.theme.MenuStyle

@Composable
fun GuideScreen(onReturnHome: () -> Unit) {
    MenuPage {
        MenuHeader(stringResource(R.string.guide_title), stringResource(R.string.guide_description), onReturnHome)
        listOf(R.string.remember_title to R.string.remember_description,
            R.string.wait_title to R.string.wait_description,
            R.string.solve_title to R.string.solve_description).forEachIndexed { index, (title, description) ->
            MenuPanel {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.itemGap)) {
                    Box(Modifier.background(MenuStyle.tealTint, CircleShape).padding(horizontal = 18.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", style = MaterialTheme.typography.headlineMedium, color = MenuStyle.teal)
                    }
                    Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                }
                Text(stringResource(description), style = MaterialTheme.typography.bodyLarge)
            }
        }
        MenuPanel(dark = true) {
            Text(stringResource(R.string.guide_progress), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
