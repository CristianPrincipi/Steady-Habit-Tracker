package com.cristianprincipi.habits.core.designsystem.component

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap

/**
 * How big a sparkline is drawn, and how heavy its line is — all in pixels, all the caller's.
 *
 * [DotGridMetrics]' twin, and separate from the points for its reason: the geometry is arithmetic
 * that can be wrong, and it is worth being able to test it without a `Canvas`.
 */
internal data class SparklineMetrics(
    val widthPx: Float,
    val heightPx: Float,
    val strokePx: Float,
) {
    init {
        require(widthPx > 0f && heightPx > 0f) {
            "A sparkline needs a positive box, got ${widthPx}x$heightPx"
        }
        require(strokePx > 0f) { "A sparkline needs a positive stroke, got $strokePx" }
    }

    /**
     * The band the curve may occupy, inset by half a stroke top and bottom.
     *
     * Without it a reading at the maximum is drawn with half its line outside the bitmap and
     * silently shaved off — the same inset [MonthlyChart] takes at its top edge.
     */
    val plotTopPx: Float get() = strokePx / 2f

    /** The foot of that band; see [plotTopPx]. */
    val plotBottomPx: Float get() = heightPx - strokePx / 2f
}

/** A sparkline's three colours, already resolved to ARGB ints. */
internal data class SparklineColors(
    val line: Int,
    val fillTop: Int,
    val fillBottom: Int,
)

/**
 * Places [points] inside [metrics] — x is taken as given, y is scaled to the data's own range.
 *
 * ### ⭐⭐ The two axes are normalised by different rules, and that is the whole design
 *
 * **x arrives already normalised**, `0f` at the start of the window and `1f` at its end, because
 * only the caller knows the dates. That is what keeps the gaps honest: a user who weighs in weekly
 * gets four points spread across the box at the spacing their weigh-ins actually had, not four
 * evenly spaced ones describing a month of steady change (`MeasureSeries`).
 *
 * **y is scaled to min–max of the values themselves, not from zero.** A body weight moving 84.1 to
 * 84.6 over a month is drawn against a zero baseline as an exactly flat line, which is a true
 * statement about the numbers and a useless picture. A sparkline's subject is the *shape* of the
 * change; the figure printed beside it is what carries the magnitude.
 *
 * ⚠ **A flat series draws down the middle**, because min == max leaves nothing to divide by. It is
 * the honest picture too: a weight that has not moved has no trend to show, and pinning it to the
 * top or the bottom of the box would invent one.
 */
internal fun sparklinePoints(
    points: List<Offset>,
    metrics: SparklineMetrics,
): List<Offset> {
    if (points.isEmpty()) return emptyList()
    val values = points.map { it.y }
    val low = values.min()
    val high = values.max()
    val band = metrics.plotBottomPx - metrics.plotTopPx
    val middle = metrics.plotTopPx + band / 2f
    return points.map { point ->
        Offset(
            x = point.x * metrics.widthPx,
            // Screen y grows downward, so the highest value takes the smallest y.
            y =
                if (high == low) {
                    middle
                } else {
                    metrics.plotBottomPx - band * ((point.y - low) / (high - low))
                },
        )
    }
}

/**
 * Draws a sparkline onto a **plain [Canvas]**: a monotone curve over a fade to nothing beneath it.
 *
 * ⭐⭐ **This is the app's existing chart with its axes taken off, not a new mark.** The curve is
 * [monotoneCurve] — the same Fritsch–Carlson the Stats chart draws through, so a peak is a peak
 * rather than something the curve leans towards — and the fill is [MonthlyChart]'s gradient. The
 * only things dropped are the two axes and their labels, which a widget this size has no room for
 * and a sparkline by definition does without.
 *
 * ⚠ **A plain `Canvas` for [drawDotGrid]'s reason**: Glance compiles to `RemoteViews`, which has no
 * `Canvas` of any kind, so the widget's picture has to be a `Bitmap`. Unlike the dot grid this has
 * no on-screen sibling yet — Stats prints measures as numbers — so nothing shares it today. It is
 * written here anyway, beside the drawing it borrows from, because the alternative is a second
 * chart language living in `feature/widget/` where the design system cannot see it.
 *
 * ⭐ `MINIMUM_CHART_POINTS` and `SEGMENT_POINTS` are [MonthlyChart]'s own, shared rather than
 * restated: they describe `monotoneCurve`'s output, which both drawings consume identically.
 *
 * @param points already placed by [sparklinePoints], in pixels.
 * @param paint reused for the curve and mutated as it goes, as [drawDotGrid]'s is. The fill
 *   allocates its own, because a shader lives on a paint and one left behind would tint whatever
 *   is drawn next.
 */
internal fun drawSparkline(
    canvas: Canvas,
    points: List<Offset>,
    metrics: SparklineMetrics,
    colors: SparklineColors,
    paint: Paint,
) {
    when {
        points.isEmpty() -> {
            return
        }

        // ⚠ One reading is a dot, not a line. A single point has no segment to describe, and
        // `monotoneCurve` returns nothing for it — drawing nothing at all would leave a widget that
        // has a reading looking exactly like one that has none.
        points.size < MINIMUM_CHART_POINTS -> {
            paint.reset()
            paint.isAntiAlias = true
            paint.color = colors.line
            canvas.drawCircle(points.first().x, points.first().y, metrics.strokePx, paint)
        }

        else -> {
            val line = curvePath(points)
            fillUnder(canvas, line, points, metrics, colors)
            paint.reset()
            paint.isAntiAlias = true
            paint.color = colors.line
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = metrics.strokePx
            // Round joins and caps for the reason the Compose chart uses them: at this weight a
            // mitre on a sharp turn draws a spike the data does not have.
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(line, paint)
        }
    }
}

/** The curve itself, as cubics through every point rather than near them. */
private fun curvePath(points: List<Offset>): Path =
    Path().apply {
        moveTo(points.first().x, points.first().y)
        monotoneCurve(points).chunked(SEGMENT_POINTS).forEach { segment ->
            val (first, second, end) = segment
            cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
        }
    }

/**
 * The gradient beneath the curve, closed down to the foot of the box.
 *
 * ⚠ **The gradient spans the drawn shape rather than the box**, exactly as the Stats chart's does:
 * it starts at the curve's highest point, so a line that stays low is shaded as fully as one that
 * climbs. Spanning the box instead would make a flat month look faded and a peaky one solid, which
 * is a difference in the data that is not there.
 */
private fun fillUnder(
    canvas: Canvas,
    line: Path,
    points: List<Offset>,
    metrics: SparklineMetrics,
    colors: SparklineColors,
) {
    val peak = points.minOf { it.y }
    // A curve sitting on the floor of the box has no height to shade, and a gradient between two
    // identical stops is undefined. `sparklinePoints` never produces one; a caller could.
    if (peak >= metrics.heightPx) return
    val area =
        Path(line).apply {
            lineTo(points.last().x, metrics.heightPx)
            lineTo(points.first().x, metrics.heightPx)
            close()
        }
    // ⚠ Its own `Paint`, where the curve reuses the caller's. A shader has to be hung on a paint
    // and taken off again afterwards, and a shared one that kept its gradient would tint the next
    // thing drawn with it — a bug that only shows up once something else is added to this canvas.
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.style = Paint.Style.FILL
    fill.shader =
        LinearGradient(
            0f,
            peak,
            0f,
            metrics.heightPx,
            colors.fillTop,
            colors.fillBottom,
            Shader.TileMode.CLAMP,
        )
    canvas.drawPath(area, fill)
}

/**
 * The same sparkline, as a [android.graphics.Bitmap] the size of [metrics].
 *
 * [dotGridBitmap]'s twin, and `ARGB_8888` for its reason: the box is mostly empty and the widget's
 * card — ultimately the user's wallpaper — has to show through everything the curve does not cover.
 */
internal fun sparklineBitmap(
    points: List<Offset>,
    metrics: SparklineMetrics,
    colors: SparklineColors,
): android.graphics.Bitmap {
    val bitmap =
        createBitmap(
            kotlin.math
                .ceil(metrics.widthPx)
                .toInt()
                .coerceAtLeast(1),
            kotlin.math
                .ceil(metrics.heightPx)
                .toInt()
                .coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888,
        )
    drawSparkline(
        canvas = Canvas(bitmap),
        points = sparklinePoints(points, metrics),
        metrics = metrics,
        colors = colors,
        paint = Paint(Paint.ANTI_ALIAS_FLAG),
    )
    return bitmap
}
