package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.core.datetime.monthWindow
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Every habit over one calendar month, for the Month grid (`docs/design-spec.md` §2).
 *
 * Read-only by design: the cells are ~14dp at a typical width, far under a usable touch target,
 * which is why check-off lives on the Week pills instead (ADR-009).
 */
class GetHabitsForMonth
    @Inject
    constructor(
        private val habits: HabitRepository,
        private val clock: Clock,
    ) {
        operator fun invoke(month: YearMonth): Flow<List<HabitPeriod>> {
            val window = monthWindow(month)
            val span = requireNotNull(window.dateSpan()) { "A month always has days" }
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
