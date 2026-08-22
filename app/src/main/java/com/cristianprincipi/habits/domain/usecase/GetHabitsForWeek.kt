package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.datetime.weekWindow
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Every habit over one week — **the landing screen's data** (ADR-009: Week is the landing tab, and
 * the only place a habit gets checked off).
 *
 * Replaces the `GetTodayHabits` the plan originally listed. There is no Today screen in v1; the Week
 * strip with today's column ringed is what serves that purpose.
 *
 * @param dateInWeek any day of the wanted week — the use case normalises to the week start itself,
 *   so a caller stepping ±7 days from an arbitrary anchor never drifts off the boundary.
 */
class GetHabitsForWeek
    @Inject
    constructor(
        private val habits: HabitRepository,
        private val clock: Clock,
    ) {
        operator fun invoke(dateInWeek: LocalDate): Flow<List<HabitPeriod>> {
            val window = weekWindow(dateInWeek)
            val span = requireNotNull(window.dateSpan()) { "A week always has seven days" }
            return combine(
                habits.observeHabits(includeInactive = false),
                habits.observeCompletions(from = span.start, to = span.endInclusive),
            ) { habitList, completions ->
                val today = LocalDate.now(clock)
                val byHabit = completions.byHabit()
                habitList.map { habit ->
                    buildPeriod(habit, window, byHabit[habit.id].orEmpty(), today)
                }
            }
        }
    }
