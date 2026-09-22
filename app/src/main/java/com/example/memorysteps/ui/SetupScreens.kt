package com.example.memorysteps.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.memorysteps.R
import com.example.memorysteps.ui.theme.MemoryStepsTheme

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 1320.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(
            painter = painterResource(R.drawable.ic_memory_steps),
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
        )
        Column {
            Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.brand_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun HomeScreen(onOpenGuide: () -> Unit) {
    Page {
        Brand()
        BoxWithConstraints {
            // Large system text uses the stacked layout even on tablets.
            val useColumns = maxWidth >= 900.dp && LocalDensity.current.fontScale <= 1.3f
            if (useColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    Welcome(onOpenGuide, Modifier.weight(1f))
                    TrainingOverview(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Welcome(onOpenGuide)
                    TrainingOverview()
                }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(R.string.record_heading)
                Text(stringResource(R.string.empty_record), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(
            stringResource(R.string.setup_status),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Welcome(onOpenGuide: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.home_description), style = MaterialTheme.typography.bodyLarge)
        LargeButton(R.string.guide_button, onOpenGuide)
    }
}

@Composable
private fun TrainingOverview(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(R.string.training_heading)
        TrainingCard("●", R.string.color_title, R.string.color_description, Color(0xFFE7EFE2))
        TrainingCard("▲", R.string.picture_title, R.string.picture_description, Color(0xFFF6E8D6))
        TrainingCard("7", R.string.number_title, R.string.number_description, Color(0xFFE6ECF5))
    }
}

@Composable
private fun TrainingCard(symbol: String, @StringRes title: Int, @StringRes description: Int, tint: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tint),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(symbol, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun GuideScreen(onReturnHome: () -> Unit) {
    Page {
        Brand()
        Text(
            stringResource(R.string.guide_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.guide_description), style = MaterialTheme.typography.bodyLarge)
        GuideStep(1, R.string.remember_title, R.string.remember_description)
        GuideStep(2, R.string.wait_title, R.string.wait_description)
        GuideStep(3, R.string.solve_title, R.string.solve_description)
        Text(stringResource(R.string.encouragement), style = MaterialTheme.typography.bodyLarge)
        LargeButton(R.string.home_button, onReturnHome)
    }
}

@Composable
private fun GuideStep(number: Int, @StringRes title: Int, @StringRes description: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.step_number, number), style = MaterialTheme.typography.titleLarge)
                }
                SectionTitle(title)
            }
            Text(stringResource(description), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun LargeButton(@StringRes text: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(stringResource(text))
    }
}

@Preview(name = "Tablet landscape", widthDp = 1280, heightDp = 800)
@Preview(name = "Phone large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun HomePreview() {
    MemoryStepsTheme { HomeScreen(onOpenGuide = {}) }
}
