package com.cristianprincipi.habits.feature.syncstatus

import com.cristianprincipi.habits.core.datetime.Elapsed
import com.cristianprincipi.habits.domain.model.DiscardedEditNotice
import com.cristianprincipi.habits.domain.model.SyncFailure

/**
 * What the sync-status screen renders.
 *
 * The screen `CLAUDE.md` §3.5 asks for: *"Failure is a clear, non-blocking, human-readable message
 * with a retry affordance"*, plus §3.6's disconnect, plus M4.9's notices. All of it is read-only
 * except two buttons, which is why this state is mostly nouns.
 *
 * **[lastSynced] is already coarsened.** The ViewModel does the arithmetic against an injected
 * clock, so the composable picks a string and never reads the time — `CLAUDE.md` §6 keeps `now` out
 * of the layers below the ViewModel, and this keeps it out of the layer above too.
 */
data class SyncStatusUiState(
    val isConnected: Boolean = false,
    val isSyncing: Boolean = false,
    /** How long since the last **successful** sync, or `null` if none ever finished. */
    val lastSynced: Elapsed? = null,
    /** Why the last run did not sync. `null` while it is running — see `SyncStatusRepositoryImpl`. */
    val failure: SyncFailure? = null,
    /** Edits last-write-wins destroyed, newest first. Empty is the normal case. */
    val discardedEdits: List<DiscardedEditNotice> = emptyList(),
    /** The disconnect confirmation is open. */
    val isConfirmingDisconnect: Boolean = false,
    /**
     * Inside the confirmation: also delete the synced habits and history.
     *
     * **Defaults to `false` every time the dialog opens**, and that is the safe direction rather
     * than a remembered preference. `CLAUDE.md` §3.6 makes wiping the data a separate choice from
     * disconnecting, and for a habit whose Notion column has since been deleted the local copy may
     * be the only one left (`docs/notion-schema.md` §4.1).
     */
    val willDeleteLocalData: Boolean = false,
) {
    /** Nothing to retry while a run is already in flight. */
    val canRetry: Boolean get() = isConnected && !isSyncing

    val hasDiscardedEdits: Boolean get() = discardedEdits.isNotEmpty()
}
