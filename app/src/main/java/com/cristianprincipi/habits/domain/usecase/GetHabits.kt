package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The habit set, plain — no window, no completions.
 *
 * The one caller is M7.5's manage-habits screen, which lists habits and changes a colour. Every
 * other screen wants habits *over a period* and asks [GetHabitsForWeek], [GetHabitsForMonth] or
 * [GetHabitsForYear] instead.
 *
 * ⚠ **It was `GetAllHabits`, and "all" is exactly what it no longer means** (ADR-081). A habit whose
 * property has left the mapped set used to be listed here, with a note, on the argument that its
 * history might be the only copy left. The user reversed that on 2026-08-31: a habit that is gone is
 * **hidden**, everywhere, and comes back whole if its property does. So the read that returned the
 * hidden ones is gone with it — not left unread, which is how a later screen quietly resurrects a
 * decision.
 *
 * A pass-through, like [SetHabitColor], and for the same reason: it keeps the screen from holding a
 * repository that can also write completions.
 */
class GetHabits
    @Inject
    constructor(
        private val habits: HabitRepository,
    ) {
        operator fun invoke(): Flow<List<Habit>> = habits.observeHabits()
    }
