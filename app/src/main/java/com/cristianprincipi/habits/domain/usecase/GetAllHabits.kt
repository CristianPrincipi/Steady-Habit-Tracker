package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Every habit, **including the ones whose Notion column is gone**.
 *
 * The one caller is M7.5's manage-habits screen, and the `includeInactive = true` is the whole
 * reason it exists as a use case rather than a repository call: every other screen wants the active
 * set, and `HabitRepository.observeHabits` documents this screen as the exception. Naming the
 * exception once beats each new screen deciding what a boolean means.
 *
 * ⚠ **An inactive habit is not an archived one.** Nobody archives anything here (ADR-010 removed
 * every write path to the schema): a habit goes inactive because its checkbox property disappeared
 * from Notion, and its history stays exactly where it is. A deleted property destroys the column
 * upstream, so the local copy may be the only record left — which is precisely why the screen that
 * lists habits has to show it rather than quietly dropping it.
 *
 * A pass-through, like [SetHabitColor], and for the same reason: it keeps the screen from holding a
 * repository that can also write completions.
 */
class GetAllHabits
    @Inject
    constructor(
        private val habits: HabitRepository,
    ) {
        operator fun invoke(): Flow<List<Habit>> = habits.observeHabits(includeInactive = true)
    }
