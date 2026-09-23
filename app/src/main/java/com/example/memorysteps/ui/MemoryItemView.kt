package com.example.memorysteps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.memorysteps.R
import com.example.memorysteps.game.ColorPair
import com.example.memorysteps.game.MemoryItem
import com.example.memorysteps.game.NumberItem
import com.example.memorysteps.game.PictureItem
import com.example.memorysteps.game.PictureSymbol
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun itemDescription(item: MemoryItem): String = when (item) {
    is NumberItem -> stringResource(R.string.number_item, item.value)
    is PictureItem -> stringArrayResource(R.array.picture_names)[item.symbol.ordinal]
    is ColorPair -> stringResource(R.string.color_pair,
        stringArrayResource(R.array.color_names)[item.left.ordinal],
        stringArrayResource(R.array.color_names)[item.right.ordinal])
}

/** Original vector drawings; no font glyphs or network/downloaded image dependencies. */
@Composable
internal fun MemoryItemView(item: MemoryItem, modifier: Modifier = Modifier, large: Boolean = false) {
    val description = itemDescription(item)
    val ink = MaterialTheme.colorScheme.onSurface
    Box(modifier.clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        if (item is NumberItem) {
            Text(item.value.toString(), fontSize = if (large) 64.sp else 40.sp, fontWeight = FontWeight.Bold)
        } else Canvas(Modifier.fillMaxSize()) {
            val side = minOf(size.width, size.height)
            withTransform({
                translate((size.width - side) / 2, (size.height - side) / 2)
                scale(side / 100f, side / 100f, pivot = Offset.Zero)
            }) {
                when (item) {
                    is ColorPair -> {
                        drawRect(Color(item.left.argb.toInt()), Offset(5f, 12f), Size(45f, 76f))
                        drawRect(Color(item.right.argb.toInt()), Offset(50f, 12f), Size(45f, 76f))
                        drawRect(ink, Offset(5f, 12f), Size(90f, 76f), style = Stroke(2f))
                        drawLine(Color.White, Offset(50f, 13f), Offset(50f, 87f), 3f)
                    }
                    is PictureItem -> drawSymbol(item.symbol, ink)
                    else -> Unit
                }
            }
        }
    }
}

private fun DrawScope.drawSymbol(symbol: PictureSymbol, ink: Color) {
    fun polygon(vararg points: Float) = Path().apply {
        moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
        close()
    }
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 5f) =
        drawLine(ink, Offset(x1, y1), Offset(x2, y2), width)
    when (symbol) {
        PictureSymbol.CIRCLE -> drawCircle(ink, 34f, Offset(50f, 50f))
        PictureSymbol.SQUARE -> drawRect(ink, Offset(18f, 18f), Size(64f, 64f))
        PictureSymbol.TRIANGLE -> drawPath(polygon(50f, 10f, 90f, 86f, 10f, 86f), ink)
        PictureSymbol.STAR -> {
            val star = Path()
            repeat(10) { i ->
                val angle = (i * Math.PI / 5 - Math.PI / 2)
                val radius = if (i % 2 == 0) 42 else 18
                val x = 50 + cos(angle).toFloat() * radius
                val y = 50 + sin(angle).toFloat() * radius
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close(); drawPath(star, ink)
        }
        PictureSymbol.HEART -> drawPath(Path().apply {
            moveTo(50f, 87f); cubicTo(0f, 53f, 8f, 7f, 35f, 16f)
            cubicTo(44f, 18f, 48f, 23f, 50f, 29f)
            cubicTo(68f, -6f, 111f, 23f, 77f, 63f); close()
        }, ink)
        PictureSymbol.MOON -> drawPath(Path().apply {
            moveTo(72f, 12f); cubicTo(3f, -2f, 1f, 97f, 72f, 87f)
            cubicTo(31f, 65f, 31f, 34f, 72f, 12f); close()
        }, ink)
        PictureSymbol.FLOWER -> {
            repeat(6) { i ->
                val angle = i * Math.PI / 3
                drawCircle(ink, 16f, Offset(50 + cos(angle).toFloat() * 25, 48 + sin(angle).toFloat() * 25))
            }
            drawCircle(Color.White, 10f, Offset(50f, 48f))
        }
        PictureSymbol.HOUSE -> {
            drawPath(polygon(8f, 44f, 50f, 10f, 92f, 44f), ink)
            drawRect(ink, Offset(22f, 42f), Size(56f, 46f))
            drawRect(Color.White, Offset(43f, 61f), Size(14f, 27f))
        }
        PictureSymbol.TREE -> {
            drawRect(ink, Offset(45f, 57f), Size(10f, 34f))
            drawCircle(ink, 28f, Offset(50f, 36f)); drawCircle(ink, 20f, Offset(29f, 51f)); drawCircle(ink, 20f, Offset(71f, 51f))
        }
        PictureSymbol.FISH -> {
            drawOval(ink, Offset(12f, 27f), Size(60f, 45f))
            drawPath(polygon(65f, 50f, 93f, 25f, 93f, 75f), ink)
            drawCircle(Color.White, 4f, Offset(27f, 43f))
        }
        PictureSymbol.APPLE -> {
            drawOval(ink, Offset(18f, 29f), Size(42f, 59f)); drawOval(ink, Offset(40f, 29f), Size(42f, 59f))
            line(50f, 37f, 52f, 13f)
            drawOval(ink, Offset(54f, 13f), Size(23f, 12f))
        }
        PictureSymbol.CUP -> {
            drawCircle(ink, 17f, Offset(72f, 45f), style = Stroke(6f))
            drawRect(ink, Offset(18f, 25f), Size(48f, 50f))
            line(12f, 85f, 83f, 85f)
        }
        PictureSymbol.KEY -> {
            drawCircle(ink, 19f, Offset(30f, 30f), style = Stroke(8f))
            line(43f, 43f, 84f, 84f, 9f); line(63f, 64f, 74f, 53f, 8f); line(75f, 76f, 87f, 64f, 8f)
        }
        PictureSymbol.UMBRELLA -> {
            drawPath(Path().apply { moveTo(9f, 48f); cubicTo(13f, -4f, 87f, -4f, 91f, 48f); close() }, ink)
            line(50f, 45f, 50f, 80f)
            drawArc(ink, 0f, 180f, false, Offset(30f, 70f), Size(20f, 20f), style = Stroke(5f))
        }
        PictureSymbol.BELL -> {
            drawPath(Path().apply {
                moveTo(15f, 76f); lineTo(25f, 61f); lineTo(25f, 36f)
                cubicTo(25f, 3f, 75f, 3f, 75f, 36f); lineTo(75f, 61f); lineTo(85f, 76f); close()
            }, ink)
            drawCircle(ink, 8f, Offset(50f, 86f))
        }
        PictureSymbol.CAR -> {
            drawPath(polygon(10f, 62f, 16f, 44f, 27f, 40f, 36f, 22f, 68f, 22f, 81f, 43f, 91f, 49f, 91f, 70f, 10f, 70f), ink)
            drawPath(polygon(35f, 41f, 41f, 28f, 63f, 28f, 71f, 41f), Color.White)
            drawCircle(ink, 11f, Offset(28f, 74f)); drawCircle(ink, 11f, Offset(75f, 74f))
        }
    }
}
