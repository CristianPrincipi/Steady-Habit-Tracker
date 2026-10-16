package com.cristianprincipi.habits.domain.model

import java.time.LocalDate

/**
 * One measure over one week, ready to draw — the Week screen's number rows (M16.1).
 *
 * [HabitPeriod]'s twin for a number, and deliberately not a [HabitPeriod]: a cell here holds a value
 * or nothing, where a habit's cell holds one of five states, and the two must not share a list for
 * [Measure]'s reason — anything that iterates habits would then draw a body weight as a pill.
 *
 * @param days exactly seven, Monday first, matching the pill columns above them.
 * @param todayIndex where today sits in [days], or [HabitPeriod.TODAY_ABSENT] — the same value the
 *   habit cards on the same screen carry, so the rings line up.
 */
data class MeasureWeek(
    val measure: Measure,
    val days: List<MeasureDay>,
    val todayIndex: Int,
) {
    /**
     * The mean over the days of this week that had a reading — never over seven.
     *
     * Through [MeasureAverage.of], the rule Stats and the widget already share, so a week with no
     * readings has no average rather than an average of zero.
     */
    val average: MeasureAverage
        get() = MeasureAverage.of(measure, days.mapNotNull { day -> day.value })
}

/**
 * One day of a [MeasureWeek].
 *
 * ⚠⚠ **[value] is null for a day with no reading, and that is not zero** — [MeasureReading]'s rule
 * carried up to the screen. The UI draws it as a dimmed dash: the user's ruling (2026-10-16) was a
 * dash rather than an "X", because a skipped weigh-in is not a missed habit.
 *
 * @param isFuture whether the day has not happened yet, so the UI can leave it blank instead of
 *   calling it missing. ⚠ **A reading outranks it**, as a completion outranks `DayState.FUTURE`: a
 *   value that exists is a fact, and "in the future" can only be a clock or a sync disagreeing with
 *   us. So a future day with a value draws the value.
 */
data class MeasureDay(
    val date: LocalDate,
    val value: Double?,
    val isFuture: Boolean,
)
