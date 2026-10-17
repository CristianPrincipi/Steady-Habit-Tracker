package com.cristianprincipi.habits.feature.week

import com.cristianprincipi.habits.domain.model.HabitId
import java.time.LocalDate

/**
 * What the Week screen has to say about one write — the payload behind its snackbar (`Plan.md` M6.6).
 *
 * **Two cases since M13.12**: [Toggled], a write that landed and can be taken back, and
 * [SaveFailed], a write that did not land and can be tried again. Everything below was written
 * about the first and still describes it; the second deliberately breaks most of it, and says why.
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
sealed interface WeekMessage {
    /**
     * The day the write was about — carried by both cases, because the screen dates every sentence
     * it can and needs the label before it knows which case it is holding.
     */
    val date: LocalDate

    /**
     * A write that landed, offered back.
     *
     * Everything above describes this case: it is what the type was before M13.12 made room for
     * [SaveFailed] beside it.
     */
    data class Toggled(
        val habitId: HabitId,
        val habitName: String,
        override val date: LocalDate,
        val completed: Boolean,
    ) : WeekMessage {
        /**
         * The event that puts it back.
         *
         * ⚠ **[WeekEvent.UndoPressed], not another [WeekEvent.DayToggled]** — the reason is a loop,
         * not a preference. Undoing through `DayToggled` would send a second [Toggled], which offers
         * to undo the undo, which sends a third, for as long as the user keeps tapping.
         * `UndoPressed` writes and says nothing, which is also the right behaviour on its own terms:
         * the pill flips back in front of the user, and announcing a reversal they just asked for is
         * the nagging the snackbar is already at risk of.
         */
        val undo: WeekEvent.UndoPressed
            get() = WeekEvent.UndoPressed(habitId = habitId, date = date, completed = !completed)
    }

    /**
     * ⚠⚠ **A write that did not land — M13.12, and the one message this screen must never skip.**
     *
     * Every rule above is about saying *less*: today is silent, an undo is silent, a refused future
     * tap is silent. **This case inverts all of them, and the inversion is the point.** Those
     * silences are all bought with the same currency — the pill has already shown the user what
     * happened, so a sentence would only repeat it. When the write fails, the pill shows **nothing**:
     * the state is observed from Room with no optimistic overlay, so a failed check-off looks
     * exactly like a finger that missed.
     *
     * ⭐ **So today is NOT silent here.** M6.11's rule turns on a mis-aim being implausible on the
     * column the ring points at; it says nothing about a write that did not happen, which is
     * invisible on every column alike. A failed check-off on today is the most important one to
     * report, not the least.
     *
     * @param habitName `null` when the tap came from a card that is not on screen — impossible from
     *   a gesture, and kept honest rather than papered over with a placeholder, exactly as
     *   `WeekViewModel.habitToAnnounce` argues. The screen has a sentence for it that names no
     *   habit, because the one thing this message may never do is say nothing at all.
     * @param retry the same write, to attempt again. Typed [WeekEvent.Write] so that paging cannot
     *   be offered as a retry, and carried rather than rebuilt so that a failed **undo** retries as
     *   an undo — silent on success, for the loop reason [Toggled.undo] gives.
     */
    data class SaveFailed(
        val habitName: String?,
        override val date: LocalDate,
        val completed: Boolean,
        val retry: WeekEvent.Write,
    ) : WeekMessage

    /**
     * A number did not land in Room (M16.2b) — [SaveFailed]'s twin, for the same M13.12 rule.
     *
     * There is no success counterpart: the dialog closing over a cell that now shows the value is
     * the confirmation, and a sentence would repeat it.
     *
     * @param measureName the number as this screen draws it, or `null` if it is no longer on screen.
     */
    data class ReadingSaveFailed(
        val measureName: String?,
        override val date: LocalDate,
        val retry: WeekEvent.ReadingRetried,
    ) : WeekMessage
}
