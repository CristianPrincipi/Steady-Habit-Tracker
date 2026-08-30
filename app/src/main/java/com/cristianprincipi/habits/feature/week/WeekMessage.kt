package com.cristianprincipi.habits.feature.week

import com.cristianprincipi.habits.domain.model.HabitId
import java.time.LocalDate

/**
 * One check-off, offered back — the payload behind the undo snackbar (`Plan.md` M6.6).
 *
 * A one-shot, so it travels on a `Channel` rather than in [WeekUiState] (`CLAUDE.md` §6). The
 * difference is not bookkeeping: a message in state is shown again after every rotation, every tab
 * return and every process restore, and "Reading cleared for Wed, Aug 19" is a sentence that must
 * be said once or not at all.
 *
 * ### ⚠ What this is for, and what it is not for
 *
 * **It is not for reversing the tap.** Tapping the same pill again already does that, exactly, and
 * it is the shorter gesture — a snackbar whose only value was a second way to undo would be a
 * control the user did not need, offered every time they used the app.
 *
 * **It is for naming what changed.** Seven pills sit 44dp apart across a card with five cards
 * stacked above one another, so the two mistakes this screen invites are the neighbouring *column*
 * and the neighbouring *row*. A filled pill tells you something happened; it cannot tell you it was
 * Thursday when you meant Wednesday, or Reading when you meant Running. That is why every field
 * below exists: the sentence names the habit and the day, and [undo] is what makes the naming
 * actionable instead of merely informative.
 *
 * ### ⚠ One of these never describes today — M6.11
 *
 * ADR-070 built the snackbar for **every** check-off and put the frequency to the user, because a
 * sentence on the commonest action in the app runs against their stated taste for persistent state
 * over toasts. Their answer was to keep the naming and drop the nag: today's column is the one the
 * ring points at and the one the landing tab exists to make trivial, so it says nothing, while a
 * back-fill onto any other column still gets named. `WeekViewModel.habitToAnnounce` is where that
 * is decided and why.
 *
 * The consequence here is that [date] is **always** worth printing — there is no longer a case
 * where the honest word for it is "today".
 *
 * ⚠ **Undesigned — the prototype has no snackbar because it has no click handlers at all**
 * (`docs/design-spec.md` §0). Built on M2's design system. See ADR-070 and ADR-075.
 *
 * @param habitName carried rather than looked up later, so the sentence names the habit **as it was
 *   when the tap happened**. A message is shown once, immediately; re-resolving the name from a
 *   state that has since re-emitted would only introduce a way for the two to disagree.
 * @param completed what the tap *did*, not what to do about it — `true` for a check-off, `false`
 *   for a clear. [undo] is the inverse and is derived, so the two cannot drift apart.
 */
data class WeekMessage(
    val habitId: HabitId,
    val habitName: String,
    val date: LocalDate,
    val completed: Boolean,
) {
    /**
     * The event that puts it back.
     *
     * ⚠ **[WeekEvent.UndoPressed], not another [WeekEvent.DayToggled]** — the reason is a loop, not
     * a preference. Undoing through `DayToggled` would send a second [WeekMessage], which offers to
     * undo the undo, which sends a third, for as long as the user keeps tapping. `UndoPressed`
     * writes and says nothing, which is also the right behaviour on its own terms: the pill flips
     * back in front of the user, and announcing a reversal they just asked for is the nagging the
     * snackbar is already at risk of.
     */
    val undo: WeekEvent.UndoPressed
        get() = WeekEvent.UndoPressed(habitId = habitId, date = date, completed = !completed)
}
