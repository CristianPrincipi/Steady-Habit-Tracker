package com.cristianprincipi.habits.domain.model

import kotlin.math.roundToInt

/**
 * How much of the eligible window a habit was actually kept.
 *
 * Both numbers are carried, not just the percentage, because the design shows both: the Year card's
 * headline `COMPLETION` figure is [percent], and the Stats screen's "Completed days" tile is
 * [completedDays] (`docs/design-spec.md` §4, §5.3).
 *
 * @param eligibleDays days in the window that are on or after the habit's start date. A habit that
 *   started today has one eligible day, not zero — the window is inclusive at both ends.
 */
data class CompletionRate(
    val completedDays: Int,
    val eligibleDays: Int,
) {
    init {
        require(eligibleDays >= 0) { "A window cannot have $eligibleDays days" }
        require(completedDays in 0..eligibleDays) {
            "$completedDays completions in a $eligibleDays-day window"
        }
    }

    /**
     * The rate as a whole percent, rounded.
     *
     * **Zero when there are no eligible days at all**, which is the divide-by-zero the design spec
     * asks to be guarded (`§4`). It happens for a habit whose start date is in the future — nothing
     * has been missed, so a rate of 0% is the honest answer and 100% would be a lie.
     */
    val percent: Int
        get() = if (eligibleDays == 0) 0 else (completedDays * PERCENT / eligibleDays.toDouble()).roundToInt()

    private companion object {
        const val PERCENT = 100
    }
}
