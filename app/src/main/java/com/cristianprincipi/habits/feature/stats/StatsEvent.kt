package com.cristianprincipi.habits.feature.stats

import com.cristianprincipi.habits.domain.model.HabitId

/**
 * Everything the Stats screen can be asked to do — `docs/design-spec.md` §5.
 *
 * ⚠ **One event, and there is no second.** This screen is a read-only deep dive: its year grid is
 * the same read-only canvas the Year tab draws (ADR-009), its tiles are numbers, and its chart has
 * no Android equivalent of the prototype's hover tooltip. Check-off lives on the Week pills and
 * nowhere else (`CLAUDE.md` §1).
 */
sealed interface StatsEvent {
    /**
     * Show a different habit's figures.
     *
     * @param habitId the pill that was tapped. An id rather than a [com.cristianprincipi.habits.domain.model.Habit],
     *   because the ViewModel re-resolves it against the current set on every emission — a habit
     *   that leaves the mapped set (ADR-081) must not keep a stale copy of itself on screen.
     */
    data class HabitSelected(
        val habitId: HabitId,
    ) : StatsEvent
}
