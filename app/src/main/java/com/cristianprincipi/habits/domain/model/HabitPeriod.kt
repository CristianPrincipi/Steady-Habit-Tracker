package com.cristianprincipi.habits.domain.model

/**
 * One habit over one stretch of time, ready to draw.
 *
 * The shape the Week, Month and Year views all consume: the habit, and one cell per grid position.
 * Which stretch — seven days, a calendar month, the trailing 365 — is the use case's business, and
 * nothing here records it, because no screen asks a period what it is.
 *
 * @param cells in grid order, padding included. The Week has exactly seven and no padding; the Month
 *   leads with blanks and ends ragged; the Year is column-major and padded at both ends.
 * @param todayIndex where today sits in [cells], or `-1` if this period does not contain it. Carried
 *   here so the UI never has to ask a clock what day it is — the Week pills need it to draw the ring
 *   (`docs/design-spec.md` §3), and it is the same value for every habit in a list.
 */
data class HabitPeriod(
    val habit: Habit,
    val cells: List<PeriodCell>,
    val todayIndex: Int,
) {
    /**
     * How many of [cells] are checked off — the "N" in the card's *"N of 7 this week"* line.
     *
     * ⚠ **Moved here from `WeekContent` in M15.6**, where it was a private extension, because the
     * home-screen widget prints the same sentence (`Plan.md` M15.6, the user's own answer for the
     * widget subtitle). Two counts of the same thing is two things that can disagree, and the pair
     * that disagreed would be a screen and a home screen showing different numbers for one habit
     * on one day — visible, alarming, and with nothing broken to find.
     */
    val completedCount: Int
        get() = cells.count { cell -> cell is PeriodCell.Day && cell.state == DayState.COMPLETED }

    companion object {
        /** [todayIndex] for a period that does not contain today. */
        const val TODAY_ABSENT = -1
    }
}
