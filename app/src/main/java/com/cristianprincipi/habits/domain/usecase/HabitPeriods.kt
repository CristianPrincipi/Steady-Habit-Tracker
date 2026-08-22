package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.datetime.CalendarWindow
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.PeriodCell
import java.time.LocalDate

/**
 * Turns a calendar shape plus a set of completions into cells the UI can draw.
 *
 * Shared by the Week, Month and Year use cases, which differ only in the window they pass. Keeping
 * one implementation is the point: three copies of this would drift, and the way they would drift is
 * in the precedence below, which is the only part with a decision in it.
 */
internal fun buildPeriod(
    habit: Habit,
    window: CalendarWindow,
    completedDates: Set<LocalDate>,
    today: LocalDate,
): HabitPeriod =
    HabitPeriod(
        habit = habit,
        cells =
            window.map { date ->
                if (date ==
                    null
                ) {
                    PeriodCell.Padding
                } else {
                    PeriodCell.Day(date, stateOf(date, habit, completedDates, today))
                }
            },
        todayIndex = window.indexOf(today).takeIf { it >= 0 } ?: HabitPeriod.TODAY_ABSENT,
    )

/**
 * **A completion outranks everything.** A recorded check-off is a fact; "before the habit started"
 * is an inference from the earliest day we happen to have synced, and "in the future" can only
 * happen if a clock or a sync disagrees with us. In any of those collisions the honest thing to draw
 * is the tick the user earned — which is also what the prototype does (`docs/design-spec.md` §2).
 */
private fun stateOf(
    date: LocalDate,
    habit: Habit,
    completedDates: Set<LocalDate>,
    today: LocalDate,
): DayState =
    when {
        date in completedDates -> DayState.COMPLETED
        date > today -> DayState.FUTURE
        date < habit.startDate -> DayState.BEFORE_START
        else -> DayState.INCOMPLETE
    }

/** Completions arrive for every habit at once — one range query serves the whole screen. */
internal fun List<Completion>.byHabit(): Map<HabitId, Set<LocalDate>> =
    groupBy { it.habitId }.mapValues { (_, list) ->
        list.map { it.date }.toSet()
    }

/** The span a [CalendarWindow] covers, ignoring its padding, or null if it holds no real days. */
internal fun CalendarWindow.dateSpan(): ClosedRange<LocalDate>? {
    val days = filterNotNull()
    return if (days.isEmpty()) null else days.first()..days.last()
}
