package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.mapper.NotionMappingProblem

/**
 * What one run of [SyncEngine] produced.
 *
 * **A value, never an exception** — `CLAUDE.md` §3.5: "a failed sync is a *state*, not an exception
 * thrown at the user. Model it, render it, offer a retry." M4.11 turns these into the `Flow` the UI
 * observes and M4.13 renders each one as a sentence, which is why the set is closed: adding a way
 * for sync to fail should make the compiler point at the screen that has to explain it.
 *
 * Three of the four are not errors. [NotConfigured] is the state of a fresh install, and
 * [MappingBroken] is the user's database having moved on — both are things to *say*, not to retry.
 */
sealed interface SyncOutcome {
    /**
     * The run completed. Both counts are days, because a day is the unit of sync
     * (`docs/notion-schema.md` §1): five habits checked off on one day are one page update.
     *
     * @param pagesSkipped pages that could not be placed on a day — the day-key property was
     *   missing or held something that is not a date. They are counted rather than swallowed: a
     *   sync that quietly ignored half the database would otherwise look like a clean run.
     */
    data class Success(
        val daysPulled: Int,
        val daysPushed: Int,
        val pagesSkipped: Int = 0,
    ) : SyncOutcome

    /**
     * No mapping is saved, so there is nothing to sync yet. A first run, or a disconnect.
     *
     * Not a failure and not something to retry — M4.12's setup screen is the answer, and M12.9
     * routes a first launch straight to it.
     */
    data object NotConfigured : SyncOutcome

    /**
     * The saved mapping no longer describes the user's database, so **nothing was pulled and
     * nothing was pushed**.
     *
     * `CLAUDE.md` §3.4: "pause sync and surface a clear, actionable error. Never silently drop
     * data, and never overwrite a real value with a null because a lookup missed." Pushing under a
     * stale mapping is exactly how the second half of that sentence happens.
     */
    data class MappingBroken(
        val problems: List<NotionMappingProblem>,
    ) : SyncOutcome

    /**
     * A call to Notion failed, after `RetryPolicy` had already spent its attempts.
     *
     * Whatever ran before the failure stands: pages already applied stay applied, days already
     * pushed stay pushed, and everything still owed is still marked owed. The next run continues
     * from there — that is what "resumable" means here.
     */
    data class Failed(
        val error: NotionError,
    ) : SyncOutcome
}
