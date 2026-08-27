package com.cristianprincipi.habits.feature.week

import com.cristianprincipi.habits.domain.model.HabitId
import java.time.LocalDate

/**
 * Everything the user can do on the Week screen.
 *
 * One sealed interface and one `onEvent`, per `CLAUDE.md` §6 — rather than a lambda per control
 * piled onto the ViewModel. Four cases, because four things happen here: you check a day off, and
 * you move between weeks three ways.
 *
 * ### What is deliberately not here yet
 *
 * **Undo** ([Plan.md] M6.6) and the **future-day refusal** ([Plan.md] M6.7) both belong on this
 * interface eventually, and neither is declared now. An event case that no `when` branch handles is
 * a promise the code does not keep, and `CLAUDE.md` §6 wants the deferred work in `Plan.md` — where
 * both already are — not stubbed into the type.
 *
 * Note that the refusal is not a *user* action anyway: `ToggleCompletion` returns `false` for a
 * future date, so it is a one-shot message back to the screen, which §6 routes through a `Channel`
 * rather than through state or events.
 */
sealed interface WeekEvent {
    /**
     * A pill was tapped: set [habitId] on [date] to [completed].
     *
     * ⚠ **Carries the wanted value, not "flip it".** `ToggleCompletion`'s KDoc is explicit about
     * why — *"a toggle replayed by a sync killed mid-run is a toggle undone"* — and the UI already
     * knows whether the pill is filled, so there is nothing to be gained by making the domain look
     * it up again. "Toggled" names the gesture; the payload names the outcome.
     *
     * The write is local and immediate, never network-gated (`CLAUDE.md` §1), so there is no
     * in-flight state to model and no spinner to show. That is the architecture, not an omission.
     */
    data class DayToggled(
        val habitId: HabitId,
        val date: LocalDate,
        val completed: Boolean,
    ) : WeekEvent

    /** `‹` — step back seven days. */
    data object PreviousWeekPressed : WeekEvent

    /** `›` — step forward seven days. */
    data object NextWeekPressed : WeekEvent

    /**
     * Jump back to the week containing today.
     *
     * ⭐ **Not in the prototype, and added on purpose.** `docs/design-spec.md` §3 gives the sub-nav
     * only `‹` and `›`, which means a user who pages away has to count their way back — and on the
     * landing screen of a daily app, "how do I get to today" should never be a puzzle. `Plan.md`
     * M6.4 calls for the affordance; `WeekUiState.Content.isCurrentWeek` says when to show it.
     */
    data object ThisWeekPressed : WeekEvent
}
