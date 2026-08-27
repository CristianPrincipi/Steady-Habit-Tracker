package com.cristianprincipi.habits.feature.week

import com.cristianprincipi.habits.core.datetime.DAYS_PER_WEEK
import com.cristianprincipi.habits.domain.model.HabitPeriod
import java.time.LocalDate

/**
 * What the Week screen renders — **the landing screen, and the only place a habit is checked off**
 * (ADR-009, `docs/design-spec.md` §3).
 *
 * A sealed interface rather than one data class with nullable fields, per `CLAUDE.md` §6: the three
 * cases below share almost nothing, and "a list of habits that is empty" and "no habits have loaded
 * yet" are different sentences on screen even though both are an empty list in a data class.
 *
 * ### ⚠ There is no `Error` case, and that is deliberate
 *
 * `Plan.md` M6.1 asked for Loading / Empty / Content / **Error**. The fourth has no producer, and
 * this was checked rather than assumed:
 *
 * - **Nothing on this path touches the network.** `CLAUDE.md` §1 is the whole architecture: Room is
 *   the source of truth for the UI and no ViewModel awaits a network call in order to render. A
 *   broken sync is a fact about *sync*, and `SyncStatusScreen` is where it is surfaced (§3.5) —
 *   putting it here would also put it in front of the check-off loop, which §3.5 forbids.
 * - **The one mapper field that could throw refuses to.** An unknown stored `HabitColor` falls back
 *   to blue instead of raising, and `HabitMapper`'s own KDoc gives the reason: *"Crashing every
 *   screen over a colour would be a poor trade for a field that is decoration."* Every other field
 *   is a direct read of a value this app wrote.
 *
 * So an `Error` case would be an unreachable branch, a `@Preview` of something no user can see, and
 * a test that has to fake a failure the code cannot produce. `ADR-027` dropped `Habit.description`
 * on exactly this reasoning — do not model what cannot happen.
 *
 * **What would bring it back:** a read on this screen that can genuinely fail. If Week ever shows
 * something fetched rather than observed, or the repository starts surfacing a Room failure instead
 * of letting it crash, add the case then — and give it a producer in the same commit.
 */
sealed interface WeekUiState {
    /**
     * Room has not emitted yet.
     *
     * Local and brief, but real: `stateIn` needs an initial value and the honest one is "not known
     * yet" rather than an empty week, which would flash the empty state on every cold start.
     */
    data object Loading : WeekUiState

    /**
     * There are no active habits **at all**.
     *
     * ⚠ **Not "nothing this week".** Habits are global rather than per-week — `GetHabitsForWeek`
     * returns every habit for any week you ask about, with days before the habit existed coming
     * back as `DayState.BEFORE_START`. So if a single habit exists, every week has content, and
     * this state is week-independent. That is why it carries no week to page from: paging an empty
     * screen would move a label and change nothing else.
     *
     * ⚠ **And it is not "add your first habit".** ADR-010 removed habit creation — a habit *is* a
     * checkbox property in the user's Notion database, so there is nothing this app can create.
     * Reaching this state means the mapping selected no habit columns, or every mapped column has
     * since disappeared upstream. **M6.8 owns what it says**; it is undesigned, and the wording has
     * to point at Notion rather than at a `+` button this app will never have.
     */
    data object Empty : WeekUiState

    /**
     * Habits to draw, for one week.
     *
     * @param weekStart the [com.cristianprincipi.habits.core.datetime.WeekStart] on or before the
     *   day being viewed — **Monday**, per `docs/design-spec.md` §3. Everything about *which* week
     *   this is derives from it, which is why the seven dates are not repeated here: [habits]
     *   already carries them, cell by cell, and a second copy is a second thing to get out of step.
     * @param habits one entry per active habit, each already holding its seven cells and where
     *   today sits among them. The heavy lifting is `GetHabitsForWeek`'s; this state is a courier.
     * @param isCurrentWeek whether [weekStart] is the week containing today. **The one field here
     *   that needs a clock**, which is precisely why it is a field: `CLAUDE.md` §6 keeps `now` out
     *   of composables, so the ViewModel answers the question and the UI reads a boolean. M6.4's
     *   "jump to this week" affordance is `!isCurrentWeek` and nothing more.
     */
    data class Content(
        val weekStart: LocalDate,
        val habits: List<HabitPeriod>,
        val isCurrentWeek: Boolean,
    ) : WeekUiState {
        /**
         * The last day of the shown week, for the `‹ Aug 17 – Aug 23 ›` label (M6.4).
         *
         * Derived rather than stored, like `SyncStatusUiState.canRetry`: it cannot drift from
         * [weekStart], and there is no arrangement of fields that makes the two disagree.
         */
        val weekEndInclusive: LocalDate get() = weekStart.plusDays((DAYS_PER_WEEK - 1).toLong())
    }
}
