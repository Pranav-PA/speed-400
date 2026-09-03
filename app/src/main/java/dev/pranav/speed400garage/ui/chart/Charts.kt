package dev.pranav.speed400garage.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Chart primitives, drawn on Canvas.
 *
 * Conventions, applied uniformly:
 *  - thin marks, 4dp rounded data-ends anchored to the baseline
 *  - a 2dp surface gap between adjacent bars
 *  - 2dp lines, 8dp markers
 *  - recessive grid and axes
 *  - values direct-labelled rather than hidden behind a tooltip. On a tablet there is
 *    no hover, and a value you have to tap to see is a value you will not look at.
 */

/** Every chart states its window and carries a plain-language takeaway (§11). */
@Composable
fun ChartFrame(
    question: String,
    window: String,
    takeaway: String?,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(question, style = MaterialTheme.typography.titleLarge)
        Text(
            window,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        content()
        takeaway?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Column chart for magnitude over time. One hue — the bars are not different things. */
@Composable
fun ColumnChart(
    values: List<Double>,
    labels: List<String>,
    valueLabel: (Double) -> String,
    height: Int = 180,
) {
    if (values.isEmpty()) {
        EmptyChart()
        return
    }
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val grid = ChartTokens.grid()

    Column {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val gap = 2.dp.toPx()
            val slot = size.width / values.size
            val barWidth = (slot - gap).coerceAtLeast(1f)
            // A recessive baseline only. With this few bars, gridlines behind them
            // would be chartjunk competing with the marks.
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { i, v ->
                val h = (v / max * (size.height - 4)).toFloat()
                if (h <= 0f) return@forEachIndexed
                drawRoundRect(
                    color = ChartTokens.data,
                    topLeft = Offset(i * slot + gap / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (values.getOrElse(i) { 0.0 } > 0) valueLabel(values[i]) else "",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Raw points with a trend line over them — emphasis rather than two categorical
 * series. The trend is the point; the raw values are context, so they recede.
 */
@Composable
fun TrendChart(
    raw: List<Double>,
    trend: List<Double>,
    height: Int = 200,
) {
    if (raw.size < 2) {
        EmptyChart("Needs at least two measured tanks before there is a trend.")
        return
    }
    val all = raw + trend
    val min = (all.minOrNull() ?: 0.0) * 0.9
    val max = (all.maxOrNull() ?: 1.0) * 1.05
    val span = (max - min).takeIf { it > 0 } ?: 1.0
    val muted = ChartTokens.muted()
    val grid = ChartTokens.grid()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            fun x(i: Int) = size.width * i / (raw.size - 1).coerceAtLeast(1)
            fun y(v: Double) = (size.height - ((v - min) / span * size.height)).toFloat()

            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())

            raw.forEachIndexed { i, v ->
                drawCircle(muted, radius = 4.dp.toPx(), center = Offset(x(i), y(v)))
            }
            if (trend.size >= 2) {
                val path = Path().apply {
                    moveTo(x(0), y(trend[0]))
                    trend.drop(1).forEachIndexed { i, v -> lineTo(x(i + 1), y(v)) }
                }
                drawPath(path, ChartTokens.data, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LegendDot(ChartTokens.data, "Rolling 5 tanks")
            LegendDot(muted, "Each tank")
        }
    }
}

/**
 * Ranked horizontal bars for part-to-whole across many categories.
 *
 * Deliberately not a donut, which is what the plan sketched: thirteen spend
 * categories is far more slices than anyone can tell apart, and the question —
 * "where does the money actually go?" — is a ranking question. A ranked list answers
 * it directly, stays readable in both themes, and needs one hue rather than thirteen.
 */
@Composable
fun RankedBars(
    rows: List<Triple<String, Double, String>>,
    barHeight: Int = 22,
) {
    if (rows.isEmpty()) {
        EmptyChart()
        return
    }
    val max = rows.maxOf { it.second }.takeIf { it > 0 } ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (label, value, display) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(0.30f), style = MaterialTheme.typography.bodyMedium)
                Canvas(Modifier.weight(0.50f).height(barHeight.dp)) {
                    val w = (value / max * size.width).toFloat()
                    drawRoundRect(
                        color = ChartTokens.data,
                        topLeft = Offset(0f, size.height * 0.2f),
                        size = Size(w.coerceAtLeast(2f), size.height * 0.6f),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
                Text(
                    display,
                    Modifier.weight(0.20f).padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp)) {
            Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                drawCircle(color, radius = 4.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyChart(message: String = "Not enough logged yet.") {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}
