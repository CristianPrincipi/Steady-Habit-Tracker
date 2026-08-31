package com.cristianprincipi.habits.feature.history

/**
 * Everything the Month screen can be asked to do — `docs/design-spec.md` §2.
 *
 * ⚠ **Two events, and there is no third.** The dot grids are read-only: at a typical width a cell is
 * about 14dp, far under a usable touch target, so check-off lives on the Week pills and nowhere else
 * (ADR-009, `CLAUDE.md` §1). A `DayToggled` here would be a tap the user cannot aim.
 *
 * There is also no "this month" — the design gives the Month sub-nav no jump-to-today affordance,
 * unlike the Week header's `This week` button, which exists because Week is the landing tab and
 * ADR-009 makes reaching today in one tap its whole premise.
 */
sealed interface MonthEvent {
    /** Step back one calendar month, rolling the year at January. */
    data object PreviousMonthPressed : MonthEvent

    /** Step forward one calendar month, rolling the year at December. */
    data object NextMonthPressed : MonthEvent
}
