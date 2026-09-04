package com.cristianprincipi.habits.feature.syncstatus

import com.cristianprincipi.habits.core.datetime.Elapsed
import com.cristianprincipi.habits.core.designsystem.component.ConnectionSummary
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
    /**
     * ⭐ A sync was asked for and is being held until the device has a network (M4.16).
     *
     * Distinct from [isSyncing] on purpose: nothing is happening, and nothing will until the
     * network returns. The card says so; see `SyncStatusScreen.secondLine`.
     */
    val isWaitingForNetwork: Boolean = false,
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
    /**
     * Nothing to retry while a run is already in flight — **or already owed**.
     *
     * ⚠ **This disables the button; it does not hide it** (ADR-060, the user's decision in M4.18).
     * `SyncScheduler.syncNow()` de-duplicates on a unique name, so a second tap while a request is
     * held provably does nothing — but M4.16 answered that by removing the control, and removing it
     * let "Disconnect from Notion" reflow up into the exact pixels "Sync now" had occupied. A second
     * tap then landed on the destructive button. A disabled control is not the silent no-op
     * `CLAUDE.md` §3.5 forbids: it says "queued, nothing more to do" out loud, and it holds the
     * layout still. Seen on the device in M4.18 — the two screenshots differ by one button.
     */
    val canRetry: Boolean get() = isConnected && !isSyncing && !isWaitingForNetwork

    /**
     * The four fields the shared status card draws, gathered for it.
     *
     * ⭐ **Derived rather than stored, which is why M11.1 cost this file one property and no test.**
     * Moving the four into a nested field would have rewritten every construction site in
     * `SyncStatusRenderTest` and `SyncStatusViewModelTest` to prove nothing; the card only needs
     * them handed over together.
     */
    val connection: ConnectionSummary
        get() =
            ConnectionSummary(
                isConnected = isConnected,
                isSyncing = isSyncing,
                isWaitingForNetwork = isWaitingForNetwork,
                lastSynced = lastSynced,
            )

    val hasDiscardedEdits: Boolean get() = discardedEdits.isNotEmpty()

    /**
     * ⭐ **The one failure a retry cannot fix, so the one that needs its own way out** (M7.8).
     *
     * A mapped column has been deleted or retyped in Notion, so every run halts before reading or
     * writing anything (`CLAUDE.md` §3.4). Until M7.8 this screen answered that with a retry that
     * would fail identically and a disconnect that would cost the user their token — ADR-079's
     * *"the diagnosis is good and the recovery is missing"*. The recovery is editing the mapping.
     */
    val isMappingBroken: Boolean get() = failure is SyncFailure.MappingBroken

    /**
     * ⭐ **The other failure a retry cannot fix** (M12.7), and it is [isMappingBroken]'s twin.
     *
     * The token is gone or Notion has stopped accepting it — a rotation or a revocation, which is an
     * ordinary thing for a person to do to a credential. Both of those failures say so in words that
     * ask for a new token, and until M12.7 the app had **nowhere to type one**: the setup wizard is a
     * gate that never reappears once a mapping is stored (ADR-032), so the only way through was
     * disconnect, which throws away a database choice and a mapping that were never wrong.
     *
     * ⚠ **`MissingCapability` is deliberately not in here.** That token is accepted and simply not
     * allowed to do this; the remedy is in Notion's permissions, which is what its own sentence says.
     * Offering a new token there would send a user to fetch a credential that changes nothing.
     */
    val isTokenBroken: Boolean
        get() = failure == SyncFailure.NotConnected || failure == SyncFailure.TokenRejected
}
