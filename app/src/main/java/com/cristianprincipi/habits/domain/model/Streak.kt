package com.cristianprincipi.habits.domain.model

/**
 * How many days in a row, now and at best.
 *
 * Both stat tiles in the design read from this (`docs/design-spec.md \u00a75`). It is a value, not a
 * calculation: `CalculateStreak` in `domain/usecase` produces it (M3.9) and is where the rules live.
 *
 * **Any missed day breaks a streak.** Schedules are deferred post-v1, so every habit is daily and
 * there is no "gap on a non-scheduled day" case (`CLAUDE.md \u00a71`). Do not add one back without the
 * schedule model that would justify it.
 */
data class Streak(
    val current: Int,
    val best: Int,
) {
    init {
        require(current >= 0) { "A streak cannot be negative: current=$current" }
        // Not a style check. If this fires, the streak calculation has a bug that would otherwise
        // reach the UI as a plausible-looking pair of numbers and never be noticed.
        require(best >= current) { "Best streak $best is shorter than the current streak $current" }
    }

    companion object {
        /** A habit with no completions at all \u2014 the first-run and never-started case. */
        val None = Streak(current = 0, best = 0)
    }
}
