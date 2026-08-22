package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.repository.HabitRepository
import javax.inject.Inject

/**
 * Set a habit's colour — **the only edit v1 can make to a habit** (ADR-010).
 *
 * Creating, renaming and deleting habits are all Notion *schema* changes, and this app holds no code
 * path that can write a schema. Colour is the exception because it is not in the schema at all: the
 * user's database has no colour property, so this is stored locally and never pushed
 * (`docs/notion-schema.md` §3).
 *
 * A pass-through today, and worth having anyway: it is what M7.5's manage-habits screen calls, and
 * it keeps that screen from holding a repository that can also write completions.
 */
class SetHabitColor
    @Inject
    constructor(
        private val habits: HabitRepository,
    ) {
        suspend operator fun invoke(
            habitId: HabitId,
            color: HabitColor,
        ) {
            habits.setColor(habitId = habitId, color = color)
        }
    }
