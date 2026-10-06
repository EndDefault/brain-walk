package com.example.memorysteps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.memorysteps.R
import com.example.memorysteps.ui.theme.AppDimensions
import com.example.memorysteps.ui.theme.MenuStyle

internal enum class MenuSymbol { PLAY, RECORDS, GUIDE, COLOR, PICTURE, NUMBER }

@Composable
internal fun MenuPage(content: @Composable ColumnScope.() -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primary = MenuStyle.teal, onPrimary = Color.White,
        background = MenuStyle.background, onBackground = MenuStyle.ink,
        surface = Color.White, onSurface = MenuStyle.ink, onSurfaceVariant = MenuStyle.muted,
    )) {
        Surface(Modifier.fillMaxSize(), color = MenuStyle.background) {
            Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = MenuStyle.contentWidth).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(AppDimensions.pagePadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimensions.pagePadding), content = content)
            }
        }
    }
}

@Composable
internal fun MenuHeader(title: String, subtitle: String, onHome: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= AppDimensions.wideLayoutMinWidth &&
            LocalDensity.current.fontScale <= AppDimensions.wideLayoutMaxFontScale
        if (wide) Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.sectionGap)) {
            Column(Modifier.weight(1f)) {
                PageTitle(title)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
            }
            ActionButton(stringResource(R.string.home_button), onHome, primary = false, fillWidth = false)
        } else Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.mediumGap)) {
            ActionButton(stringResource(R.string.home_button), onHome, primary = false)
            PageTitle(title)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
        }
    }
}

@Composable
internal fun MenuPanel(modifier: Modifier = Modifier, dark: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(MenuStyle.panelCorner),
        color = if (dark) MenuStyle.ink else Color.White,
        contentColor = if (dark) Color.White else MenuStyle.ink) {
        Column(Modifier.padding(AppDimensions.pagePadding),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.itemGap), content = content)
    }
}

@Composable
internal fun MenuAction(title: String, symbol: MenuSymbol, onClick: () -> Unit,
    modifier: Modifier = Modifier, subtitle: String? = null, primary: Boolean = false,
    enabled: Boolean = true, accent: Color = MenuStyle.teal, tint: Color = MenuStyle.tealTint) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = AppDimensions.largeButtonMinHeight),
        enabled = enabled, shape = RoundedCornerShape(MenuStyle.controlCorner),
        color = if (primary) MenuStyle.teal else Color.White,
        contentColor = if (primary) Color.White else MenuStyle.ink,
        border = if (primary) null else BorderStroke(1.dp, MenuStyle.line)) {
        Row(Modifier.padding(AppDimensions.sectionGap), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.itemGap)) {
            if (LocalDensity.current.fontScale <= AppDimensions.wideLayoutMaxFontScale) Box(Modifier.size(52.dp).background(if (primary) Color.White.copy(alpha = .15f) else tint,
                RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                MenuGlyph(symbol, if (primary) Color.White else accent, Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium,
                    color = if (enabled) Color.Unspecified else MenuStyle.muted)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = if (primary) Color.White else MenuStyle.muted)
            }
            Canvas(Modifier.size(16.dp).clearAndSetSemantics {}) {
                val color = if (primary) Color.White else MenuStyle.muted
                drawLine(color, Offset(0f, 0f), Offset(size.width / 2, size.height / 2), 3.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(size.width / 2, size.height / 2), Offset(0f, size.height), 3.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun MenuGlyph(symbol: MenuSymbol, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round)
        when (symbol) {
            MenuSymbol.PLAY -> drawPath(Path().apply {
                moveTo(w * .24f, h * .08f); lineTo(w * .88f, h * .5f)
                lineTo(w * .24f, h * .92f); close()
            }, color)
            MenuSymbol.RECORDS -> listOf(.45f, .7f, 1f).forEachIndexed { i, fraction ->
                drawRoundRect(color, Offset(w * (i * .34f), h * (1 - fraction)),
                    Size(w * .22f, h * fraction), CornerRadius(2.dp.toPx()))
            }
            MenuSymbol.GUIDE -> {
                drawRoundRect(color, Offset(w * .1f, 0f), Size(w * .8f, h), CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(color, Offset(w * .32f, h * .1f), Offset(w * .32f, h * .9f), stroke.width)
                drawLine(color, Offset(w * .48f, h * .32f), Offset(w * .74f, h * .32f), stroke.width)
                drawLine(color, Offset(w * .48f, h * .55f), Offset(w * .74f, h * .55f), stroke.width)
            }
            MenuSymbol.COLOR -> {
                drawCircle(color, w * .28f, Offset(w * .3f, h * .3f))
                drawRoundRect(color.copy(alpha = .6f), Offset(w * .42f, h * .42f), Size(w * .55f, h * .55f), CornerRadius(4.dp.toPx()))
            }
            MenuSymbol.PICTURE -> {
                drawRoundRect(color, Offset.Zero, size, CornerRadius(3.dp.toPx()), style = stroke)
                drawCircle(color, w * .1f, Offset(w * .7f, h * .28f))
                drawPath(Path().apply {
                    moveTo(w * .12f, h * .78f); lineTo(w * .38f, h * .4f)
                    lineTo(w * .58f, h * .7f); lineTo(w * .73f, h * .53f); lineTo(w * .9f, h * .8f)
                }, color, style = stroke)
            }
            MenuSymbol.NUMBER -> {
                drawPath(Path().apply {
                    moveTo(w * .05f, h * .24f); lineTo(w * .23f, h * .08f); lineTo(w * .23f, h * .88f)
                    moveTo(w * .06f, h * .9f); lineTo(w * .4f, h * .9f)
                    moveTo(w * .55f, h * .22f); quadraticTo(w * .84f, -h * .1f, w * .96f, h * .24f)
                    quadraticTo(w, h * .42f, w * .56f, h * .9f); lineTo(w, h * .9f)
                }, color, style = stroke)
            }
        }
    }
}

/** Stacks instead of squeezing text on phones or with enlarged system fonts. */
@Composable
internal fun AdaptiveMenuRow(items: List<@Composable () -> Unit>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= AppDimensions.wideLayoutMinWidth &&
            LocalDensity.current.fontScale <= AppDimensions.wideLayoutMaxFontScale) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimensions.itemGap)) {
                items.forEach { item -> Box(Modifier.weight(1f)) { item() } }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.itemGap)) {
            items.forEach { it() }
        }
    }
}

@Composable
internal fun SectionAccent(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppDimensions.mediumGap)) {
        Box(Modifier.size(8.dp, 28.dp).background(MenuStyle.teal, CircleShape))
        SectionTitle(title)
    }
}
