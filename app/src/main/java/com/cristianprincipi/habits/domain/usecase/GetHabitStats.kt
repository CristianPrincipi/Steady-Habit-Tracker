package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.datetime.trailingYearWindow
import com.cristianprincipi.habits.domain.model.CompletionRate
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.MonthlyCompletions
import com.cristianprincipi.habits.domain.model.PeriodCell
import com.cristianprincipi.habits.domain.model.Streak
import com.cristianprincipi.habits.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Everything the Stats screen draws for one habit (`docs/design-spec.md` §5).
 *
 * Four numbers, a grid and a chart, from four values: the "completed days" and "completion rate"
 * tiles are the two halves of [rate], the "current" and "best" streak tiles are the two halves of
 * [streak], the year overview card is [period], and the monthly chart is [months]. They are carried
 * together for [HabitYear]'s reason — one range query answers all of them, and a screen that
 * computed them from separate queries could show a grid and a percentage that disagree about the
 * same day.
 *
 * [habit] is [period]'s rather than a field of its own: `HabitPeriod` already carries the habit it
 * describes, and two copies of it in one object is two things to keep in step.
 *
 * @param months one bucket per calendar month the habit could have been kept, oldest first — see
 *   [monthlyCompletions] for which months those are. Computed once here rather than derived on
 *   demand: it is a fold over 371 cells, and a `get()` would run it on every recomposition, which
 *   is the waste `Plan.md` M9.7 exists to rule out.
 * @param lastMonthIsUnfinished whether the newest bucket covers a month that has not ended — true
 *   on every day but the last of the month. **M13.10, the user's ruling.** The window ends *today*,
 *   so that bucket counts a handful of days against neighbours counting thirty, and the chart drew
 *   a near-vertical drop that reads as a collapse on a habit sitting at 87%. The number is honest;
 *   the slope was not. `MonthlyChart` draws that final span dashed and leaves it unshaded.
 *
 *   ⚠ Carried here rather than derived in the composable because it is a question about **today**,
 *   and this use case is the only thing in the chain holding a clock (`CLAUDE.md` §6).
 */
data class HabitStats(
    val period: HabitPeriod,
    val streak: Streak,
    val rate: CompletionRate,
    val months: List<MonthlyCompletions>,
    val lastMonthIsUnfinished: Boolean,
) {
    /** The habit these figures are about. */
    val habit: Habit get() = period.habit
}

/**
 * The monthly chart's buckets — `docs/design-spec.md` §5.4.
 *
 * ### ⭐ It reads [HabitPeriod.cells], not the completions the period was built from
 *
 * The Year overview card on this same screen draws those exact cells. Bucketing anything else would
 * mean two renderings of one habit deciding independently what a given day was, which is the bug
 * M9.3 widened the query to prevent (see this file's other KDoc). Here it also comes for free:
 * `buildPeriod` has already applied the precedence rule, so a completion recorded *before* the
 * inferred start date lands in this chart as a completion, exactly as it lands in the grid as a
 * filled dot.
 *
 * ### ⚠ Months before the habit started are omitted, and that is a deliberate divergence
 *
 * The prototype gives **every** month in the window a bucket and increments only the eligible ones,
 * so a habit that started in March renders a legitimate-looking `0` for January and February —
 * indistinguishable from a month the user missed entirely. `Plan.md` M9.5 calls that out as a bug
 * not to reproduce, and `DayState.BEFORE_START` already carries the distinction, so no composable
 * has to be told a start date to get this right.
 *
 * The leading days are dropped rather than the leading *months*, which keeps the result a
 * contiguous run: the first surviving bucket is the habit's own first month, partial and honestly
 * counted, and there is never a hole in the middle for the axis to draw across.
 */
internal fun monthlyCompletions(period: HabitPeriod): List<MonthlyCompletions> =
    period.cells
        .filterIsInstance<PeriodCell.Day>()
        .dropWhile { day -> day.state == DayState.BEFORE_START }
        .groupBy { day -> YearMonth.from(day.date) }
        .map { (month, days) ->
            MonthlyCompletions(month, days.count { it.state == DayState.COMPLETED })
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
                    val period = buildPeriod(habit, window, dates.toSet(), today)
                    HabitStats(
                        period = period,
                        streak = calculateStreak(dates),
                        rate = completionRate(completedDates = dates, startDate = habit.startDate),
                        months = monthlyCompletions(period),
                        // The window ends today, so the newest bucket is today's month — finished
                        // only on the last day of it.
                        lastMonthIsUnfinished = today != YearMonth.from(today).atEndOfMonth(),
                    )
                }
        }
    }
