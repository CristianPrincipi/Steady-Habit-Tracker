package com.cristianprincipi.habits.feature.week

import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.MeasureId
import java.time.LocalDate

/**
 * Everything the user can do on the Week screen.
 *
 * One sealed interface and one `onEvent`, per `CLAUDE.md` §6 — rather than a lambda per control
 * piled onto the ViewModel. You check a day off, you take that back from the snackbar, you move
 * between weeks three ways — and since M16.2b you type a number into a day, through a small dialog
 * whose state the ViewModel holds (the `Reading…` cases at the bottom). The first two share the
 * [Write] supertype, because they are the two habit events that touch storage and therefore can
 * fail (M13.12); a number's write fails through [ReadingRetried] instead.
 *
 * ### What is deliberately not here yet
 *
 * The **future-day refusal** ([Plan.md] M6.7) is not declared, and it is not going to be an event
 * when it arrives: `ToggleCompletion` returns `false` for a future date, so the refusal is a
 * one-shot message *back to the screen* rather than an action taken by the user. §6 routes that
 * through a `Channel` — the one [WeekMessage] already travels on — not through this interface.
 */
sealed interface WeekEvent {
    /**
     * The two events that **write**, and the only two that can fail.
     *
     * Added in M13.12 so [WeekMessage.SaveFailed] can carry the thing to try again without widening
     * to a bare [WeekEvent] — paging is not retryable and must not be offered as though it were.
     *
     * The two stay distinct below rather than collapsing into one case with a flag: what separates
     * them is whether success *announces*, and [UndoPressed] explains why that difference is a loop
     * rather than a preference. Retrying a failed undo must stay silent for the same reason the
     * undo itself does.
     */
    sealed interface Write : WeekEvent {
        val habitId: HabitId
        val date: LocalDate

        /** The value to write — see [DayToggled], which explains why it is a value and not a flip. */
        val completed: Boolean
    }

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
        override val habitId: HabitId,
        override val date: LocalDate,
        override val completed: Boolean,
    ) : Write

    /**
     * The snackbar's **Undo** was pressed: restore [habitId] on [date] to [completed].
     *
     * ⚠ **A separate case from [DayToggled], and the reason is a loop rather than tidiness.** The
     * ViewModel answers a `DayToggled` with a [WeekMessage] offering an undo. If undo re-entered
     * through `DayToggled`, it would earn a second message offering to undo the undo, and a third
     * after that, for as long as the user kept pressing. This case writes and stays silent.
     *
     * Silence is also correct on its own terms. The pill flips back where the user is already
     * looking, so a confirmation would say what the screen has just shown — and the honest risk
     * with this whole feature is saying too much, not too little (see [WeekMessage]).
     *
     * It carries the value to restore, like [DayToggled] carries the value to write, so a stale
     * message cannot be replayed into a flip of whatever the pill happens to hold now.
     */
    data class UndoPressed(
        override val habitId: HabitId,
        override val date: LocalDate,
        override val completed: Boolean,
    ) : Write

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

    // ---- M16.2b: a number's cell and its dialog (ADR-156 §2) -----------------------------------

    /** The six number events, grouped so the ViewModel handles them in one place of their own. */
    sealed interface Reading : WeekEvent

    /**
     * A number's cell was tapped — open the dialog for [measureId] on [date].
     *
     * @param prefill the day's value as the field should start, **formatted by the screen** in the
     *   reader's own decimal separator, or empty for a day without one. The screen formats because
     *   only it knows the locale (`CLAUDE.md` §6 keeps `Context` out of a ViewModel); the ViewModel
     *   only ever *parses*, and parsing accepts either separator (`ReadingInput`).
     */
    data class ReadingCellTapped(
        val measureId: MeasureId,
        val date: LocalDate,
        val prefill: String,
    ) : Reading

    /** The dialog's field changed. */
    data class ReadingTextChanged(
        val text: String,
    ) : Reading

    /** Save: write what the field holds. Ignored while it does not hold a number. */
    data object ReadingSaved : Reading

    /** Clear: the day had a value and now has none — sent to Notion as empty, never as zero. */
    data object ReadingCleared : Reading

    /** Cancel, the scrim, or back: nothing is written. */
    data object ReadingDismissed : Reading

    /**
     * The failure snackbar's Retry, for a number (M13.12's rule: a local write that fails says so).
     *
     * Carries the value rather than reopening the dialog: the user already said what they wanted,
     * and what failed was storing it.
     */
    data class ReadingRetried(
        val measureId: MeasureId,
        val date: LocalDate,
        val value: Double?,
    ) : Reading
}
