package com.cristianprincipi.habits.core.designsystem.component

/**
 * What a single day looks like in a grid or a pill row.
 *
 * Presentation state, not domain state: the design system knows a day is dimmed, not *why*. The
 * distinction between "before this habit started" and "hasn't happened yet" collapses here because
 * the design draws them the same way in the Month grid, and only the Year grid tells them apart.
 *
 * Deliberately not a `domain/model` type. `domain/` does not exist until M3, and keeping the design
 * system free of it is what lets every component be previewed and tested on its own.
 */
enum class DayStatus {
    /** Checked off. Drawn in the habit's own colour at full strength. */
    COMPLETED,

    /** A day that has happened and was not checked off. A faint tint of the habit colour. */
    INCOMPLETE,

    /** Still to come. Drawn like [INCOMPLETE] but dimmer, so the month reads as unfinished. */
    FUTURE,

    /** Before the habit existed. The faintest mark the design draws — present, but not a gap. */
    BEFORE_START,

    /** No day at all: the leading and trailing padding cells that align a grid to Monday. */
    PADDING,
}
