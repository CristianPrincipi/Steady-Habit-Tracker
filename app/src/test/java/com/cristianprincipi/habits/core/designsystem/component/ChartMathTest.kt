package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two calculations behind the monthly chart — `Plan.md` M9.5.
 *
 * ### Why a curve gets a unit test at all
 *
 * Because "monotone" is a **claim**, not a look. `docs/design-spec.md` §5.4 suggests smoothing the
 * polyline with `quadraticBezierTo`, which is easier, renders something perfectly plausible, and is
 * wrong in two ways a screenshot will not show: it misses the data points, and it can carry the fill
 * past a value it was drawn from. Both are asserted here rather than eyeballed.
 *
 * The axis is here for the plainer reason that a chart whose ceiling cuts through its own data draws
 * outside the plot, and the arithmetic that prevents it is off-by-one country.
 */
class ChartMathTest {
    // --- the axis ------------------------------------------------------------------------------

    @Test
    fun `an axis tops out at a round number above the data`() {
        assertEquals(AxisScale(ceiling = 40, step = 10), niceScale(31))
        assertEquals(AxisScale(ceiling = 15, step = 5), niceScale(12))
        assertEquals(AxisScale(ceiling = 6, step = 2), niceScale(5))
        assertEquals(AxisScale(ceiling = 3, step = 1), niceScale(3))
    }

    @Test
    fun `a habit with no completions still gets an axis one unit tall`() {
        // The flat-line case, and the one that would otherwise divide by zero when the drawing code
        // asks what fraction of the ceiling a value is.
        val axis = niceScale(0)

        assertTrue("An axis of ${axis.ceiling} has no height", axis.ceiling > 0)
        assertEquals(listOf(0, 1), axis.tickValues)
    }

    @Test
    fun `the ceiling clears the data and is the tightest one that does`() {
        (0..400).forEach { max ->
            val axis = niceScale(max)

            assertTrue("$max does not fit under ${axis.ceiling}", axis.ceiling >= max)
            assertTrue(
                "${axis.ceiling} is a step too generous for $max",
                axis.ceiling - axis.step < max.coerceAtLeast(1),
            )
        }
    }

    @Test
    fun `no axis asks for more than four gaps, or a step off the one-two-five ladder`() {
        // Five labels down 130dp of card is the budget; more would collide, fewer would leave the
        // reader interpolating.
        (0..400).forEach { max ->
            val axis = niceScale(max)

            assertTrue("${axis.ceiling} by ${axis.step} is too many ticks", axis.ceiling / axis.step <= 4)
            assertTrue("${axis.step} is not a round step", axis.step.isOneTwoOrFive())
        }
    }

    @Test
    fun `the ticks run from zero to the ceiling inclusive`() {
        assertEquals(listOf(0, 10, 20, 30, 40), niceScale(31).tickValues)
    }

    private fun Int.isOneTwoOrFive(): Boolean {
        var remaining = this
        while (remaining % 10 == 0 && remaining > 5) remaining /= 10
        return remaining == 1 || remaining == 2 || remaining == 5
    }

    // --- the curve -----------------------------------------------------------------------------

    @Test
    fun `the curve passes through every data point`() {
        // ⭐ The half the design note's `quadraticBezierTo` would fail. A midpoint quadratic makes
        // each interior point a *control* point the curve merely leans towards, so the best month of
        // the year gets drawn lower than it was.
        val points = series(listOf(4, 19, 7, 26, 11))

        val controls = monotoneCurve(points)

        points.drop(1).forEachIndexed { segment, point ->
            assertEquals(point, controls[segment * SEGMENT_POINTS + END_OF_SEGMENT])
        }
    }

    @Test
    fun `a quiet month between two busy ones never carries the fill below the axis`() {
        // ⚠ The failure this rules out is visible and absurd: a gradient hanging off the underside
        // of the axis, because an unconstrained spline undershot a zero.
        val points = series(listOf(30, 0, 30))

        sample(points).forEach { (_, y) ->
            assertTrue("The curve reached $y, below the axis at $BASELINE", y <= BASELINE + TOLERANCE)
        }
    }

    @Test
    fun `no segment leaves the range of its own two end values`() {
        // The general form of the case above, and the definition of "monotone": between two months
        // the curve is only ever on its way from one to the other.
        listOf(
            listOf(30, 0, 30),
            listOf(0, 31, 0, 31, 0),
            listOf(1, 5, 2, 8, 3, 9, 4),
            listOf(18, 22, 26, 19, 9, 4, 11, 20, 24, 28, 25, 21, 14),
            listOf(1, 2, 3, 4, 5),
            listOf(9, 9, 9, 9),
        ).forEach { values ->
            val points = series(values)
            val controls = monotoneCurve(points)

            points.zipWithNext().forEachIndexed { segment, (from, to) ->
                val low = minOf(from.y, to.y) - TOLERANCE
                val high = maxOf(from.y, to.y) + TOLERANCE
                sampleSegment(from, controls, segment, to).forEach { point ->
                    assertTrue(
                        "$values overshot to ${point.y} between ${from.y} and ${to.y}",
                        point.y in low..high,
                    )
                }
            }
        }
    }

    @Test
    fun `a flat run is drawn flat`() {
        // Not cosmetic: a spline that bulges through a pair of equal months invents a peak the data
        // does not have, which on this chart reads as a month that went better than its neighbours.
        val points = series(listOf(9, 9, 9, 9))

        sample(points).forEach { point ->
            assertEquals(BASELINE - 9f, point.y, TOLERANCE)
        }
    }

    @Test
    fun `a single point has no segment to describe`() {
        assertEquals(emptyList<Offset>(), monotoneCurve(series(listOf(7))))
        assertEquals(emptyList<Offset>(), monotoneCurve(emptyList()))
    }

    @Test
    fun `x has to increase, because a slope through a repeated x is infinite`() {
        val stacked = listOf(Offset(0f, 0f), Offset(0f, 10f))

        assertThrows(IllegalArgumentException::class.java) { monotoneCurve(stacked) }
    }

    // --- helpers -------------------------------------------------------------------------------

    /** Values as evenly spaced screen points, where a bigger value sits *higher* — a smaller `y`. */
    private fun series(values: List<Int>): List<Offset> =
        values.mapIndexed { index, value -> Offset(index * SPACING, BASELINE - value) }

    private fun sample(points: List<Offset>): List<Offset> {
        val controls = monotoneCurve(points)
        return points.zipWithNext().flatMapIndexed { segment, (from, to) ->
            sampleSegment(from, controls, segment, to)
        }
    }

    private fun sampleSegment(
        from: Offset,
        controls: List<Offset>,
        segment: Int,
        to: Offset,
    ): List<Offset> {
        val first = controls[segment * SEGMENT_POINTS]
        val second = controls[segment * SEGMENT_POINTS + 1]
        return (0..SAMPLES).map { step -> cubic(from, first, second, to, step / SAMPLES.toFloat()) }
    }

    /** The same cubic `Path.cubicTo` draws, evaluated so the test can look inside it. */
    private fun cubic(
        from: Offset,
        first: Offset,
        second: Offset,
        to: Offset,
        t: Float,
    ): Offset {
        val u = 1f - t

        fun on(
            a: Float,
            b: Float,
            c: Float,
            d: Float,
        ) = u * u * u * a + 3f * u * u * t * b + 3f * u * t * t * c + t * t * t * d
        return Offset(
            x = on(from.x, first.x, second.x, to.x),
            y = on(from.y, first.y, second.y, to.y),
        )
    }

    private companion object {
        const val BASELINE = 100f
        const val SPACING = 20f
        const val SAMPLES = 40
        const val TOLERANCE = 0.01f
        const val END_OF_SEGMENT = 2
    }
}
