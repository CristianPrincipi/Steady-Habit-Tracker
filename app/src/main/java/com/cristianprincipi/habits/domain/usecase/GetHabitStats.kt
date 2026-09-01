package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.CompletionRate
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.Streak
import com.cristianprincipi.habits.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * The four numbers the Stats screen prints for one habit (`docs/design-spec.md` §5.3).
 *
 * Four numbers, two values: the "completed days" and "completion rate" tiles are the two halves of
 * [rate], and the "current" and "best" streak tiles are the two halves of [streak]. They are carried
 * together for [HabitYear]'s reason — one range query answers both, and a screen that computed them
 * from two queries could show a rate and a streak that disagree about the same day.
 */
data class HabitStats(
    val habit: Habit,
    val streak: Streak,
    val rate: CompletionRate,
)

/**
 * One habit's whole history, reduced to a [Streak] and a [CompletionRate].
 *
 * Takes a [Habit] rather than a `HabitId` because every caller already has one: the Stats screen
 * holds the list to draw its selector pills, and passing the id would mean a second query for the
 * row it is already holding.
 *
 * ### Why the window starts at the habit's start date and not a year ago
 *
 * [CalculateStreak] measures both figures over **everything**, deliberately — bounding the best
 * streak to a year while letting the current one run past it produces `current > best`, which
 * [Streak] rejects by invariant. So this asks for every completion the habit has.
 *
 * `startDate` is the earliest day the habit is known to have existed (`Habit.startDate`), inferred
 * from the earliest day synced, so nothing precedes it and the query loses nothing by starting
 * there. [GetCompletionRate] then narrows to its own trailing-365 window internally, which is why
 * the rate agrees with the Year card's percentage while the streak does not have to.
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
            // A habit whose inferred start is in the future would otherwise ask for a backwards
            // range. It has no completions either way; this keeps the query well-formed rather
            // than relying on every implementation of it to shrug at from > to.
            val from = minOf(habit.startDate, today)
            return habits
                .observeCompletions(from = from, to = today)
                .map { completions ->
                    // One range query answers for every habit — the repository has no per-habit
                    // overload, and an unused query is an untested one (HabitRepository KDoc).
                    val dates = completions.filter { it.habitId == habit.id }.map { it.date }
                    HabitStats(
                        habit = habit,
                        streak = calculateStreak(dates),
                        rate = completionRate(completedDates = dates, startDate = habit.startDate),
                    )
                }
        }
    }
