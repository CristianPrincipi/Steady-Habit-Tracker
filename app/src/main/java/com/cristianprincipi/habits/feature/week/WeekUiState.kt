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
     *
     * ### ⭐ What actually reaches this state, and it is not what M6.8 was written against
     *
     * `Plan.md` named two causes and **neither can occur** — ADR-072 has the evidence:
     *
     * - *"the mapping selected no habit columns"* — `NotionSetupUiState.canSaveMapping` requires
     *   `habitPropertyIds.isNotEmpty()`, and `NotionSetupViewModel.submitMapping` guards it again
     *   before saving. No saved mapping has an empty habit set.
     * - *"every mapped column disappeared upstream"* — that is `NotionMappingCheck.Broken`, and
     *   `SyncEngine.sync` returns on it **before** `HabitImporter.import`, so `deactivateAllExcept`
     *   never runs. Those habits stay active and keep rendering, which is the right answer:
     *   deleting a Notion property destroys its column, so the local rows may be the only surviving
     *   copy (`docs/notion-schema.md` §4.1).
     *
     * What is left is one thing: **no successful import has put habits in Room yet.** Every
     * first-run user passes through it, because `submitMapping` saves the mapping and *then* awaits
     * `importNow()`, while `HabitsShell`'s gate is `mapping != null` — so the shell replaces the
     * wizard mid-import and this screen is what the user lands on.
     *
     * @param reason which sentence to say. It exists because that window has two ends: the import
     *   is still on its way, or it failed and nobody has been told — the wizard that would have
     *   shown the error is no longer on screen by the time the error arrives.
     */
    data class Empty(
        val reason: WeekEmptyReason,
    ) : WeekUiState

    /**
     * Habits to draw, for one week.
     *
     * @param weekStart the [com.cristianprincipi.habits.core.datetime.WeekStart] on or before the
     *   day being viewed — **Monday**, per `docs/design-spec.md` §3. Everything about *which* week
     *   this is derives from it, which is why the seven dates are not repeated here: [habits]
     *   already carries them, cell by cell, and a second copy is a second thing to get out of step.
     * @param habits one entry per active habit, each already holding its seven cells and where
     *   today sits among them. The heavy lifting is `GetHabitsForWeek`'s; this state is a courier.
     * @param isCurrentWeek whether [weekStart] is the week containing today. One of the two fields
     *   here that need a clock, which is precisely why they are fields: `CLAUDE.md` §6 keeps `now`
     *   out of composables, so the ViewModel answers the question and the UI reads a value. M6.4's
     *   "jump to this week" affordance is `!isCurrentWeek` and nothing more.
     * @param currentYear the year today falls in, for the sub-nav label — `weekRangeLabel` prints a
     *   year only for an endpoint that is not in it (M6.11). Read from the **same snapshot** as
     *   [isCurrentWeek], so the label and the today ring can never describe different days.
     */
    data class Content(
        val weekStart: LocalDate,
        val habits: List<HabitPeriod>,
        val isCurrentWeek: Boolean,
        val currentYear: Int,
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

/**
 * Why [WeekUiState.Empty] is on screen, in the only two shapes it comes in.
 *
 * Both mean the same fact — no successful import has put habits in Room — and differ only in what
 * the user should do about it, which is the split `SyncFailure` already uses one layer down: cases
 * that need the same sentence and the same button are one case.
 *
 * **Derived from `SyncStatus`, not stored.** `WeekViewModel` collapses that four-case status into
 * these two, which is also what stops a sync starting and finishing from re-emitting the whole
 * screen every time.
 */
enum class WeekEmptyReason {
    /**
     * The habits are on their way, and nothing is owed by the user.
     *
     * `SyncStatus.Idle`, `Syncing` **and `WaitingForNetwork`** all land here. Folding the last of
     * those in is deliberate: `CLAUDE.md` §1 treats offline as the normal case rather than the error
     * case, and a held sync needs no retry because WorkManager releases it the moment the network
     * returns — telling the user to do something about it would be the nagging M4.16 already
     * removed from the sync-status card.
     *
     * `Idle` is in here for a less obvious reason: the first import has a moment before it starts,
     * and a process killed mid-setup leaves a saved mapping with nothing running. "On its way" is
     * still the true sentence — the app syncs on open.
     */
    SyncPending,

    /**
     * The last run failed, so the habits are not coming without something changing.
     *
     * ⚠ **This is the case nothing else in the app would tell the user about.** A first import that
     * fails reports into `NotionSetupViewModel`'s state, and by then the gate has already replaced
     * the wizard with the shell, so the error is written to a screen no one can see. Room stays
     * empty and Week is the only surface left.
     *
     * The reason itself is deliberately **not** carried here. `SyncStatusScreen` renders every
     * `SyncFailure` with the sentence and the retry that belong to it (`CLAUDE.md` §3.5), and a
     * second rendering on the landing screen would be two places to keep in step and two places to
     * get wrong. This says that something is wrong and where to look.
     */
    SyncFailed,
}
