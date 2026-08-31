package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.datetime.trailingYearWindow
import com.cristianprincipi.habits.domain.model.CompletionRate
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * A habit's trailing year, with the completion figure the card prints beside it.
 *
 * The two travel together because they are measured over the same window and the card shows both
 * (`docs/design-spec.md` §4). Computing them apart would mean querying the same 365 days twice and
 * risking a grid and a percentage that disagree.
 */
data class HabitYear(
    val period: HabitPeriod,
    val rate: CompletionRate,
)

/**
 * Every habit over the trailing 365 days ending today (`docs/design-spec.md` §4).
 *
 * Takes no date parameter, unlike the Week and Month use cases: the Year view has no sub-navigation
 * in the design, and the window is always the one ending today.
 */
class GetHabitsForYear
    @Inject
    constructor(
        private val habits: HabitRepository,
        private val completionRate: GetCompletionRate,
        private val clock: Clock,
    ) {
        operator fun invoke(): Flow<List<HabitYear>> {
            val today = LocalDate.now(clock)
            val window = trailingYearWindow(today)
            val span = requireNotNull(window.dateSpan()) { "The year window always has days" }
            return combine(
                habits.observeHabits(),
                habits.observeCompletions(from = span.start, to = span.endInclusive),
            ) { habitList, completions ->
                val byHabit = completions.byHabit()
                habitList.map { habit ->
                    val dates = byHabit[habit.id].orEmpty()
                    HabitYear(
                        period = buildPeriod(habit, window, dates, today),
                        rate = completionRate(completedDates = dates, startDate = habit.startDate),
                    )
                }
            }
        }
    }
