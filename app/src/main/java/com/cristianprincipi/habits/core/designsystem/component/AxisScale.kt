package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.runtime.Immutable

/*
 * Where the monthly chart's Y axis tops out, and what it labels on the way.
 *
 * Separated from the curve it stands beside because it is the half with an answer that can be
 * arithmetically wrong rather than merely ugly, and it never touches a `DrawScope` — see
 * `ChartMathTest`.
 */

/** A value axis: where it tops out, and how far apart its labelled ticks are. */
@Immutable
internal data class AxisScale(
    val ceiling: Int,
    val step: Int,
) {
    init {
        require(step > 0) { "An axis step must be positive, not $step" }
        require(ceiling >= step) { "An axis topping out at $ceiling cannot step by $step" }
    }

    /** Every labelled value from zero up, [ceiling] included. */
    val tickValues: List<Int> = (0..ceiling step step).toList()
}

/**
 * The smallest axis covering [maxValue] whose ticks land on round numbers.
 *
 * `recharts` does this for the prototype and it is the only reason its Y axis reads `0 10 20 30`
 * rather than `0 7.75 15.5 23.25`. Steps come from the 1-2-5 ladder every charting library uses:
 * those are the multiples a reader adds up without thinking, and each decade of them is a tenfold
 * step, so one loop covers every magnitude.
 *
 * **The ceiling is rounded up past the data, never down through it** — a bar the axis does not reach
 * would be drawn outside the plot. And [maxValue] `0` still yields an axis of height one: a habit
 * with no completions in any month draws a flat line along the baseline, where an axis topping out
 * at zero would divide by it.
 *
 * @param maxIntervals how many gaps between ticks to allow at most. Four is the chart's own figure —
 *   five labels down 130dp of card.
 */
internal fun niceScale(
    maxValue: Int,
    maxIntervals: Int = MAX_AXIS_INTERVALS,
): AxisScale {
    require(maxValue >= 0) { "An axis cannot cover $maxValue" }
    require(maxIntervals > 0) { "An axis needs at least one interval, not $maxIntervals" }
    val target = maxValue.coerceAtLeast(1)
    // The fallback cannot be reached by any value a month can hold; it is here so that the function
    // is total for any Int rather than throwing on one nobody will ever pass.
    val step = niceSteps().firstOrNull { candidate -> ceilDiv(target, candidate) <= maxIntervals } ?: target
    return AxisScale(ceiling = ceilDiv(target, step) * step, step = step)
}

/** 1, 2, 5, 10, 20, 50, … as far as an [Int] ceiling can go without overflowing. */
private fun niceSteps(): Sequence<Int> =
    generateSequence(1) { decade -> decade * DECADE }
        .takeWhile { decade -> decade <= LARGEST_DECADE }
        .flatMap { decade -> NICE_STEPS.asSequence().map { multiple -> multiple * decade } }

/** `(value + divisor - 1) / divisor` written so it cannot overflow on the way. */
private fun ceilDiv(
    value: Int,
    divisor: Int,
): Int = 1 + (value - 1) / divisor

/** The 1-2-5 ladder every charting library rounds to. Those three digits *are* the concept. */
@Suppress("MagicNumber")
private val NICE_STEPS = listOf(1, 2, 5)

private const val DECADE = 10
private const val LARGEST_DECADE = 100_000_000
private const val MAX_AXIS_INTERVALS = 4
