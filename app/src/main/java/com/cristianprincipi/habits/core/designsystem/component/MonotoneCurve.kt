package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

/**
 * The cubic Bézier control points of a **monotone** curve through [points] — three per segment: the
 * two controls, then the segment's own end point.
 *
 * ### ⚠ Not the `quadraticBezierTo` the design note suggests
 *
 * `docs/design-spec.md` §5.4 proposes a quadratic through the midpoints, which is the usual quick
 * way to smooth a polyline. It does not pass through the data: every interior point becomes a
 * *control* point that the curve merely leans towards, so a month that stands out gets drawn
 * flatter than it was. On a chart whose whole job is to show which months were good, that is the
 * one distortion not worth taking.
 *
 * ### What "monotone" buys, and it is not only the look
 *
 * This is Fritsch–Carlson: tangents are averaged between neighbouring slopes, flattened to zero at
 * a local peak or trough, and clamped inside a circle of radius three. The guarantee that falls out
 * is that **each segment stays between its two end values** — so a quiet month between two busy ones
 * cannot make the curve dip below zero, which on this chart would draw a gradient fill hanging off
 * the wrong side of the axis. `recharts`' own `type="monotone"` is the same algorithm.
 *
 * @param points at least two, ordered left to right with strictly increasing `x`. Fewer than two has
 *   no segment to describe and returns empty; [MonthlyChart] draws that case as a dot.
 */
internal fun monotoneCurve(points: List<Offset>): List<Offset> {
    if (points.size < SEGMENT_MINIMUM) return emptyList()
    require(points.zipWithNext().all { (from, to) -> to.x > from.x }) {
        "A curve needs strictly increasing x, and got ${points.map { it.x }}"
    }
    val tangents = monotoneTangents(points)
    return points
        .zipWithNext()
        .mapIndexed { index, (from, to) ->
            val reach = (to.x - from.x) / TANGENT_REACH
            listOf(
                Offset(from.x + reach, from.y + tangents[index] * reach),
                Offset(to.x - reach, to.y - tangents[index + 1] * reach),
                to,
            )
        }.flatten()
}

/** One tangent per point, in the units of the caller's space — `dy` per `dx`. */
private fun monotoneTangents(points: List<Offset>): FloatArray {
    val last = points.lastIndex
    val slopes = FloatArray(last) { i -> (points[i + 1].y - points[i].y) / (points[i + 1].x - points[i].x) }
    val tangents = FloatArray(points.size)
    tangents[0] = slopes.first()
    tangents[last] = slopes.last()
    for (index in 1 until last) {
        // A sign change means the point is a peak or a trough. A flat tangent there is what keeps
        // the curve from sailing past it and back.
        val turning = slopes[index - 1] * slopes[index] <= 0f
        tangents[index] = if (turning) 0f else (slopes[index - 1] + slopes[index]) / 2f
    }
    for (index in 0 until last) {
        val slope = slopes[index]
        if (slope == 0f) {
            // A flat segment must be drawn flat, or the curve bulges through a month either side.
            tangents[index] = 0f
            tangents[index + 1] = 0f
            continue
        }
        val from = tangents[index] / slope
        val to = tangents[index + 1] / slope
        val radius = from * from + to * to
        if (radius > MONOTONE_LIMIT * MONOTONE_LIMIT) {
            val scale = MONOTONE_LIMIT / sqrt(radius)
            tangents[index] = scale * from * slope
            tangents[index + 1] = scale * to * slope
        }
    }
    return tangents
}

/** How many control points [monotoneCurve] emits per segment: two controls and an end point. */
internal const val SEGMENT_POINTS = 3

/**
 * Where a control point sits along its segment.
 *
 * A third of the way, which is what converts a Hermite tangent into the equivalent cubic Bézier —
 * the two forms describe the same curve, and `Path` speaks only the second.
 */
private const val TANGENT_REACH = 3f

private const val SEGMENT_MINIMUM = 2

/** Fritsch-Carlson's own constant: the radius of the circle the two tangent ratios must sit in. */
private const val MONOTONE_LIMIT = 3f
