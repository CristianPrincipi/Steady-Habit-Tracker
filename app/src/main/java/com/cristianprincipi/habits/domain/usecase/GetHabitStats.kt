package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.datetime.trailingYearWindow
import com.cristianprincipi.habits.domain.model.CompletionRate
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.Streak
import com.cristianprincipi.habits.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Everything the Stats screen draws for one habit (`docs/design-spec.md` §5).
 *
 * Four numbers and a grid, from three values: the "completed days" and "completion rate" tiles are
 * the two halves of [rate], the "current" and "best" streak tiles are the two halves of [streak],
 * and the year overview card is [period]. They are carried together for [HabitYear]'s reason — one
 * range query answers all of them, and a screen that computed them from separate queries could show
 * a grid and a percentage that disagree about the same day.
 *
 * [habit] is [period]'s rather than a field of its own: `HabitPeriod` already carries the habit it
 * describes, and two copies of it in one object is two things to keep in step.
 */
data class HabitStats(
    val period: HabitPeriod,
    val streak: Streak,
    val rate: CompletionRate,
) {
    /** The habit these figures are about. */
    val habit: Habit get() = period.habit
}

/**
 * One habit's whole history, reduced to a [Streak], a [CompletionRate] and a year of cells.
 *
 * Takes a [Habit] rather than a `HabitId` because every caller already has one: the Stats screen
 * holds the list to draw its selector pills, and passing the id would mean a second query for the
 * row it is already holding.
 *
 * ### Why the window starts before the habit's start date
 *
 * [CalculateStreak] measures both its figures over **everything**, deliberately — bounding the best
 * streak to a year while letting the current one run past it produces `current > best`, which
 * [Streak] rejects by invariant. So this asks for every completion the habit has.
 *
 * ⚠ **And it asks for a little more than that: the window also reaches back to the start of the
 * trailing year, even when the habit is younger.** `startDate` is *inferred* from the earliest day
 * synced, not read from Notion, so a completion older than it is possible — and one missed by the
 * query would be drawn `BEFORE_START` here while the Year tab, whose use case queries the whole
 * window, draws the same day `COMPLETED`. One grid, two screens, two answers. Widening the window
 * costs nothing: a day that is not there is not there.
 */
class GetHabitStats
    @Inject
    constructor(
        private val habits: HabitRepository,
        private val calculateStreak: CalculateStreak,
        private val completionRate: GetCompletionRate,
        private val clock: Clock,
    ) {
        operator fun invoke(habit: Habit): Flow<HabitStats> {
            val today = LocalDate.now(clock)
            val window = trailingYearWindow(today)
            val span = requireNotNull(window.dateSpan()) { "The year window always has days" }
            // `minOf` and not just the start date: see the KDoc. A habit whose inferred start is in
            // the future would otherwise ask for a backwards range, and one younger than a year
            // would hide a completion older than itself.
            val from = minOf(habit.startDate, span.start, today)
            return habits
                .observeCompletions(from = from, to = today)
                .map { completions ->
                    // One range query answers for every habit — the repository has no per-habit
                    // overload, and an unused query is an untested one (HabitRepository KDoc).
                    val dates = completions.filter { it.habitId == habit.id }.map { it.date }
                    HabitStats(
                        period = buildPeriod(habit, window, dates.toSet(), today),
                        streak = calculateStreak(dates),
                        rate = completionRate(completedDates = dates, startDate = habit.startDate),
                    )
                }
        }
    }
