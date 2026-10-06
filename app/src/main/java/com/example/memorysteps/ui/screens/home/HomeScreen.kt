package com.example.memorysteps.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.memorysteps.R
import com.example.memorysteps.ui.components.*
import com.example.memorysteps.ui.theme.AppDimensions
import com.example.memorysteps.ui.theme.MenuStyle

@Composable
fun HomeScreen(onLearn: () -> Unit, onRecords: () -> Unit, onOpenGuide: () -> Unit) {
    MenuPage {
        Column(Modifier.widthIn(max = MenuStyle.homeWidth).fillMaxWidth().align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.itemGap)) {
            MenuPanel(dark = true) {
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    listOf(MenuSymbol.COLOR, MenuSymbol.PICTURE, MenuSymbol.NUMBER).forEach { symbol ->
                        MenuGlyph(symbol, MenuStyle.gold, Modifier.size(30.dp))
                    }
                }
                Text(stringResource(R.string.brand_name), Modifier.fillMaxWidth().semantics { heading() },
                    style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
                Box(Modifier.size(56.dp, 4.dp).background(MenuStyle.gold, CircleShape).align(Alignment.CenterHorizontally))
                Text(stringResource(R.string.home_tagline), Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center)
            }
            MenuAction(stringResource(R.string.learn_menu), MenuSymbol.PLAY, onLearn,
                subtitle = stringResource(R.string.home_learn_hint), primary = true)
            MenuAction(stringResource(R.string.home_title), MenuSymbol.RECORDS, onRecords,
                subtitle = stringResource(R.string.home_records_hint), accent = MenuStyle.blue, tint = MenuStyle.blueTint)
            MenuAction(stringResource(R.string.guide_button), MenuSymbol.GUIDE, onOpenGuide,
                subtitle = stringResource(R.string.home_guide_hint), accent = MenuStyle.plum, tint = MenuStyle.plumTint)
        }
    }
}
