package com.cristianprincipi.habits.core.ui

import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Why a habit screen has nothing to draw, in the only two shapes it comes in.
 *
 * **Not a per-screen concept, which is why it lives here and not in a `feature/` package.** Habits
 * are global: they are the checkbox columns of one Notion database (ADR-010), so a screen is empty
 * when the *app* has no habits, never when a particular week or month has none. Week, Month and
 * Year all empty for the same reason at the same moment, and M9's Stats will too. M6 built this for
 * Week alone; M8.1 moved it out when the Month view turned out to need the identical thing, copy
 * and all.
 *
 * Both cases mean the same fact — no successful import has put habits in Room — and differ only in
 * what the user should do about it, which is the split `SyncFailure` already uses one layer down:
 * cases that need the same sentence and the same button are one case.
 *
 * **Derived from `SyncStatus`, not stored.** [observeEmptyReason] collapses that four-case status
 * into these two, which is also what stops a sync starting and finishing from re-emitting the whole
 * screen every time.
 */
enum class HabitsEmptyReason {
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
     * empty and these screens are the only surface left.
     *
     * The reason itself is deliberately **not** carried here. `SyncStatusScreen` renders every
     * `SyncFailure` with the sentence and the retry that belong to it (`CLAUDE.md` §3.5), and a
     * second rendering on a habit screen would be two places to keep in step and two places to get
     * wrong. This says that something is wrong and where to look.
     */
    SyncFailed,
}

/**
 * The reason to show, derived from the sync status and nothing else.
 *
 * Starts on [HabitsEmptyReason.SyncPending] rather than waiting: a screen that renders nothing until
 * the status arrives is indistinguishable from one that is broken, and "on its way" is true of every
 * first run.
 */
fun SyncStatusRepository.observeEmptyReason(): Flow<HabitsEmptyReason> =
    observeStatus()
        .map { status ->
            if (status is SyncStatus.Failed) HabitsEmptyReason.SyncFailed else HabitsEmptyReason.SyncPending
        }.onStart { emit(HabitsEmptyReason.SyncPending) }
        .distinctUntilChanged()
