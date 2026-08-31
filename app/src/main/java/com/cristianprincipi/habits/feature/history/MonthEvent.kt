package com.cristianprincipi.habits.feature.history

/**
 * Everything the Month screen can be asked to do — `docs/design-spec.md` §2.
 *
 * ⚠ **Three events, and there is no fourth.** The dot grids are read-only: at a typical width a cell
 * is about 14dp, far under a usable touch target, so check-off lives on the Week pills and nowhere
 * else (ADR-009, `CLAUDE.md` §1). A `DayToggled` here would be a tap the user cannot aim.
 *
 * ⭐ **[ThisMonthPressed] is the user's call at M8.7 and is taken against the design** (ADR-091),
 * which gives this sub-nav no jump-to-today affordance at all. Six taps back deserves one tap
 * forward, and Week has had the same control since M6.4.
 */
sealed interface MonthEvent {
    /** Step back one calendar month, rolling the year at January. */
    data object PreviousMonthPressed : MonthEvent

    /** Step forward one calendar month, rolling the year at December. */
    data object NextMonthPressed : MonthEvent

    /**
     * Back to the month containing today.
     *
     * Clears the anchor rather than setting it to the current month, for `WeekViewModel`'s reason:
     * the control's promise is "put me back on the current month", not "on this month" — and a
     * screen left open across a month boundary must then follow, exactly as an untouched one does.
     */
    data object ThisMonthPressed : MonthEvent
}
