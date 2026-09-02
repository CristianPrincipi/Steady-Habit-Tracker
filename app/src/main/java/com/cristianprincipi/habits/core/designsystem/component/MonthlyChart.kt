package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.theme.ChartOpacity
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * The "Monthly completions" area chart — `docs/design-spec.md` §5.4.
 *
 * A `Canvas` per `CLAUDE.md` §4: a monotone curve at [Sizing.chartStroke] in the habit's colour,
 * over a fill that fades from [ChartOpacity.FILL_TOP] to nothing at the axis. Month names run along
 * the bottom, round numbers up the left.
 *
 * ### ⚠ The tooltip is dropped rather than converted
 *
 * The prototype's chart answers "how many in March?" on hover, which Android has no equivalent for;
 * `docs/design-spec.md` §5.4 offers dropping it or making it a tap-to-select. It is dropped, and the
 * **Y axis is what replaces it** — the same axis the prototype draws and then barely needs, because
 * its tooltip has already said the number. Reading a value off a labelled axis costs no gesture and
 * works for a reader who cannot make one.
 *
 * ### ⚠ This one speaks, where [YearDotGrid] deliberately does not
 *
 * ADR-089 clears the year grid's semantics because 365 coloured squares cannot usefully be read out
 * and the stat tiles beside it say the same thing in words. Neither half of that holds here: a year
 * is thirteen buckets, not 365, and **nothing else on the screen prints a monthly figure**. So the
 * canvas still clears the semantics of its own drawing, but takes a [contentDescription] to put back
 * in words what a sighted reader gets from the shape.
 *
 * ### Labels thin out from the left, and the newest one always survives
 *
 * Thirteen month names across a phone fit at the default font scale and stop fitting well before the
 * largest one. When they collide, the ones dropped are chosen left to right with **the newest month
 * reserved first** — it is the one the reader came for, and it is `recharts`' own `preserveEnd`
 * behaviour.
 *
 * @param points oldest first. Empty draws nothing at all, height included — a habit whose inferred
 *   start date is in the future has no month it could have been kept in, and `Plan.md` M9.6 is where
 *   that case gets something to say for itself.
 * @param formatValue renders an axis number in the reader's own digits. Pass a `remember`ed lambda;
 *   the measured text is cached against it.
 */
@Composable
fun MonthlyChart(
    points: List<ChartPoint>,
    habitColor: Color,
    modifier: Modifier = Modifier,
    formatValue: (Int) -> String = { value -> value.toString() },
    contentDescription: String? = null,
) {
    if (points.isEmpty()) return

    val measurer = rememberTextMeasurer()
    val labelStyle = HabitsTheme.textStyles.axisLabel.copy(color = HabitsTheme.colors.text.helper)
    val axis = remember(points) { niceScale(points.maxOf { it.value }) }
    val valueLabels =
        remember(measurer, labelStyle, axis, formatValue) {
            axis.tickValues.map { value -> measurer.measure(formatValue(value), labelStyle) }
        }
    val monthLabels =
        remember(measurer, labelStyle, points) {
            points.map { point -> measurer.measure(point.label, labelStyle) }
        }

    val density = LocalDensity.current
    val strokePx = with(density) { Sizing.chartStroke.toPx() }
    val dotPx = with(density) { Sizing.chartDot.toPx() }
    val gapPx = with(density) { Spacing.chartAxisGap.toPx() }
    val description = contentDescription

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Sizing.chartHeight)
                .clearAndSetSemantics {
                    if (description != null) this.contentDescription = description
                },
    ) {
        val valueColumn = valueLabels.maxOf { it.size.width }.toFloat()
        val monthRow = monthLabels.maxOf { it.size.height }.toFloat()
        // Half a stroke of inset at the top and right, so a curve touching the ceiling is not shaved
        // off by the canvas edge. The other two edges are held by the axis labels instead.
        val plot =
            Rect(
                left = valueColumn + gapPx,
                top = strokePx / 2f,
                right = size.width - strokePx / 2f,
                bottom = size.height - monthRow - gapPx,
            )
        if (plot.width <= 0f || plot.height <= 0f) return@Canvas

        val curve =
            points.mapIndexed { index, point ->
                val x =
                    if (points.size == 1) {
                        plot.center.x
                    } else {
                        plot.left + plot.width * index / (points.size - 1)
                    }
                Offset(x, plot.bottom - plot.height * point.value / axis.ceiling.toFloat())
            }

        drawArea(curve, plot, habitColor, strokePx, dotPx)
        drawValueAxis(valueLabels, axis, plot, valueColumn)
        drawMonthAxis(monthLabels, curve, gapPx)
    }
}

/** The fill, then the curve on top of it — or a single dot, where there is no segment to draw. */
private fun DrawScope.drawArea(
    curve: List<Offset>,
    plot: Rect,
    habitColor: Color,
    strokePx: Float,
    dotPx: Float,
) {
    if (curve.size == 1) {
        drawCircle(color = habitColor, radius = dotPx, center = curve.single())
        return
    }

    val line =
        Path().apply {
            moveTo(curve.first().x, curve.first().y)
            monotoneCurve(curve).chunked(SEGMENT_POINTS).forEach { (first, second, end) ->
                cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
            }
        }

    val peak = curve.minOf { it.y }
    // A habit with no completions in any month draws its curve flat along the axis, where the fill
    // has no height and a gradient between two identical stops is undefined. Nothing to shade.
    if (peak < plot.bottom) {
        val area =
            Path().apply {
                addPath(line)
                lineTo(curve.last().x, plot.bottom)
                lineTo(curve.first().x, plot.bottom)
                close()
            }
        drawPath(
            path = area,
            brush =
                Brush.verticalGradient(
                    colors =
                        listOf(
                            habitColor.copy(alpha = ChartOpacity.FILL_TOP),
                            habitColor.copy(alpha = ChartOpacity.FILL_BOTTOM),
                        ),
                    // The prototype's gradient spans the filled shape rather than the plot, so it
                    // starts at the highest point of the curve: a quiet year is shaded as fully as
                    // a busy one instead of fading out just above the floor.
                    startY = peak,
                    endY = plot.bottom,
                ),
        )
    }
    drawPath(path = line, color = habitColor, style = Stroke(width = strokePx, cap = StrokeCap.Round))
}

/** The round numbers up the left, each centred on the height it stands for. */
private fun DrawScope.drawValueAxis(
    labels: List<TextLayoutResult>,
    axis: AxisScale,
    plot: Rect,
    columnWidth: Float,
) {
    labels.forEachIndexed { index, label ->
        val centre = plot.bottom - plot.height * axis.tickValues[index] / axis.ceiling.toFloat()
        val top = centre - label.size.height / 2f
        drawText(
            textLayoutResult = label,
            topLeft =
                Offset(
                    x = columnWidth - label.size.width,
                    y = top.coerceIn(0f, (size.height - label.size.height).coerceAtLeast(0f)),
                ),
        )
    }
}

/**
 * The month names along the bottom, thinned left to right with the newest reserved.
 *
 * Each label is centred under its own point and then pushed back inside the canvas, so the first and
 * last never hang off an edge — which is the one thing `recharts` lets happen, into its own margin.
 */
private fun DrawScope.drawMonthAxis(
    labels: List<TextLayoutResult>,
    curve: List<Offset>,
    gapPx: Float,
) {
    fun leftOf(index: Int): Float {
        val width = labels[index].size.width
        return (curve[index].x - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
    }

    fun draw(index: Int) {
        val label = labels[index]
        drawText(textLayoutResult = label, topLeft = Offset(leftOf(index), size.height - label.size.height))
    }

    val newest = labels.lastIndex
    draw(newest)

    val reserved = leftOf(newest)
    var drawnTo = Float.NEGATIVE_INFINITY
    for (index in 0 until newest) {
        val left = leftOf(index)
        val right = left + labels[index].size.width
        if (left < drawnTo + gapPx || right + gapPx > reserved) continue
        draw(index)
        drawnTo = right
    }
}

@Preview(name = "Monthly chart", backgroundColor = 0xFF1C1C1F, showBackground = true, widthDp = 328)
@Composable
private fun MonthlyChartPreview() {
    HabitsTheme {
        MonthlyChart(
            points = previewMonths(),
            habitColor = HabitsTheme.colors.habit.green,
            modifier = Modifier.padding(Spacing.cardPadding),
        )
    }
}

@Preview(name = "Monthly chart — one month", backgroundColor = 0xFF1C1C1F, showBackground = true, widthDp = 328)
@Composable
private fun MonthlyChartSingleMonthPreview() {
    HabitsTheme {
        MonthlyChart(
            points = previewMonths().takeLast(1),
            habitColor = HabitsTheme.colors.habit.orange,
            modifier = Modifier.padding(Spacing.cardPadding),
        )
    }
}

/**
 * A trailing year with a slump in the middle, so the curve has a trough not to overshoot.
 *
 * `@Suppress` for the reason `config/detekt/detekt.yml` excludes `core/ui/preview` wholesale:
 * in sample data the numbers **are** the content, and naming thirteen of them would say less than
 * the list does.
 */
@Suppress("MagicNumber")
internal fun previewMonths(): List<ChartPoint> =
    listOf(18, 22, 26, 19, 9, 4, 11, 20, 24, 28, 25, 21, 14)
        .mapIndexed { index, value -> ChartPoint(label = PREVIEW_MONTHS[index], value = value) }

private val PREVIEW_MONTHS =
    listOf("Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep")
