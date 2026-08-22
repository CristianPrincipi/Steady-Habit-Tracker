package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.repository.HabitRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Check a habit off on a day, or take it back.
 *
 * **States rather than toggles, despite the name.** The caller passes the value it wants, not a
 * flip: the UI already knows whether the pill is filled, and a toggle replayed by a sync killed
 * mid-run is a toggle undone (`CLAUDE.md` §3.5). "Toggle" here names the gesture, not the operation.
 *
 * The write lands in Room and returns. Notion finds out when the sync engine next runs, and this
 * succeeds whether or not the device has a network — that is the whole architecture in one method
 * (`CLAUDE.md` §1).
 */
class ToggleCompletion
    @Inject
    constructor(
        private val habits: HabitRepository,
        private val clock: Clock,
    ) {
        /**
         * @return false if nothing was written because [date] has not happened yet.
         *
         * The Week pills report a tap on any of their seven days, including the future ones a
         * mid-week Wednesday still shows, and the design gives them no disabled appearance
         * (`docs/design-spec.md` §3). Refusing here rather than in the composable keeps the rule
         * where the rest of the domain rules live, and gives M6 something to react to instead of a
         * tap that silently does nothing.
         */
        suspend operator fun invoke(
            habitId: HabitId,
            date: LocalDate,
            completed: Boolean,
        ): Boolean {
            if (date > LocalDate.now(clock)) return false
            habits.setCompleted(habitId = habitId, date = date, completed = completed)
            return true
        }
    }
