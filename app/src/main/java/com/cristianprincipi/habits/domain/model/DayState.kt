package com.cristianprincipi.habits.domain.model

import java.time.LocalDate

/**
 * What one day means for one habit.
 *
 * The domain twin of the design system's `DayStatus`, and deliberately a separate type: `DayStatus`
 * lives in `core/designsystem/component` and its own KDoc says it stays out of `domain/` so that
 * every component can be previewed and tested alone. The dependency runs one way — the feature layer
 * maps this onto that, never the reverse.
 *
 * **Four states, not five.** `DayStatus.PADDING` has no counterpart here because padding is not a
 * day; it is a hole in a grid. [PeriodCell] models that as its own case, so a [PeriodCell.Day]
 * always has a real [LocalDate] and this enum never has to describe the absence of one.
 */
enum class DayState {
    /** Checked off. */
    COMPLETED,

    /** A day that has been and gone without being checked off. Every one of these breaks a streak. */
    INCOMPLETE,

    /** Still to come. Not a miss — see [PeriodCell] for why the distinction has to survive. */
    FUTURE,

    /**
     * Before this habit is known to have existed.
     *
     * Not a miss either. `Habit.startDate` is inferred from the earliest day synced from Notion, so
     * this is "we have no record", not "the user failed".
     */
    BEFORE_START,
}

/**
 * One cell of a Week, Month or Year grid.
 *
 * A sealed type rather than a nullable date beside a five-valued enum, because the two illegal
 * combinations — a padding cell with a date, a real day without one — then cannot be constructed at
 * all. The grids are built by index arithmetic (`core/datetime/CalendarWindows.kt`), which is
 * exactly the kind of code that produces those pairings when it is one off.
 */
sealed interface PeriodCell {
    /** A blank cell that aligns a grid to the week start, or completes its final column. */
    data object Padding : PeriodCell

    data class Day(
        val date: LocalDate,
        val state: DayState,
    ) : PeriodCell
}
