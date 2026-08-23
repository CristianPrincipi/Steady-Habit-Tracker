package com.cristianprincipi.habits.feature.syncstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.datetime.elapsedBetween
import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.domain.repository.NotionSetupRepository
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** Everything the user can do on the sync-status screen. */
sealed interface SyncStatusEvent {
    /** Ask for a sync now — the retry affordance `CLAUDE.md` §3.5 requires beside a failure. */
    data object RetryPressed : SyncStatusEvent

    /** The user has read the discarded-edit notices. */
    data object NoticesDismissed : SyncStatusEvent

    data object DisconnectPressed : SyncStatusEvent

    data object DisconnectCancelled : SyncStatusEvent

    /** Inside the confirmation: toggle "also delete the synced data". */
    data class DeleteLocalDataToggled(
        val delete: Boolean,
    ) : SyncStatusEvent

    /** Confirmed. Wipes the connection, and the local data if that was ticked. */
    data object DisconnectConfirmed : SyncStatusEvent
}

/**
 * Drives the sync-status screen.
 *
 * Three sources go in — the connection, the sync status, the notices — and one state comes out. All
 * three are already domain-shaped by M4.11 and M4.12, so this class does almost no work: the only
 * arithmetic it does is turning an `Instant` into an [com.cristianprincipi.habits.core.datetime.Elapsed],
 * and the only decision it makes is what a disconnect wipes.
 *
 * **The dialog state is local, not derived.** Whether a confirmation is open is not a fact about
 * anything stored, so it lives in a `MutableStateFlow` here and is combined in — which also means
 * closing the dialog cannot race a repository emission.
 */
@HiltViewModel
class SyncStatusViewModel
    @Inject
    constructor(
        private val syncStatus: SyncStatusRepository,
        private val connection: NotionSetupRepository,
        // Injected rather than read, so "3 minutes ago" is a testable claim (`CLAUDE.md` §6).
        private val clock: Clock,
    ) : ViewModel() {
        private val disconnectPrompt = MutableStateFlow(DisconnectPrompt())

        val uiState: StateFlow<SyncStatusUiState> =
            combine(
                connection.isConnected,
                syncStatus.observeStatus(),
                syncStatus.observeDiscardedEdits(),
                disconnectPrompt,
            ) { isConnected, status, notices, prompt ->
                SyncStatusUiState(
                    isConnected = isConnected,
                    isSyncing = status is SyncStatus.Syncing,
                    lastSynced = status.lastSyncedAt?.let { at -> elapsedBetween(at, clock.instant()) },
                    failure = (status as? SyncStatus.Failed)?.reason,
                    discardedEdits = notices,
                    isConfirmingDisconnect = prompt.isOpen,
                    willDeleteLocalData = prompt.deleteLocalData,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = SyncStatusUiState(),
            )

        fun onEvent(event: SyncStatusEvent) {
            when (event) {
                SyncStatusEvent.RetryPressed -> {
                    syncStatus.requestSync()
                }

                SyncStatusEvent.NoticesDismissed -> {
                    viewModelScope.launch { syncStatus.dismissDiscardedEdits() }
                }

                SyncStatusEvent.DisconnectPressed -> {
                    // Reopened unticked every time. CLAUDE.md §3.6 makes deleting the data a
                    // separate choice from disconnecting, and a remembered tick makes it a default.
                    disconnectPrompt.value = DisconnectPrompt(isOpen = true, deleteLocalData = false)
                }

                SyncStatusEvent.DisconnectCancelled -> {
                    disconnectPrompt.value = DisconnectPrompt()
                }

                is SyncStatusEvent.DeleteLocalDataToggled -> {
                    disconnectPrompt.value = disconnectPrompt.value.copy(deleteLocalData = event.delete)
                }

                SyncStatusEvent.DisconnectConfirmed -> {
                    disconnect()
                }
            }
        }

        private fun disconnect() {
            val deleteLocalData = disconnectPrompt.value.deleteLocalData
            // Closed first, so the dialog cannot be confirmed twice while the wipe is in flight.
            disconnectPrompt.value = DisconnectPrompt()
            viewModelScope.launch { connection.disconnect(alsoDeleteLocalData = deleteLocalData) }
        }

        private data class DisconnectPrompt(
            val isOpen: Boolean = false,
            val deleteLocalData: Boolean = false,
        )

        private companion object {
            /** `CLAUDE.md` §6's `WhileSubscribed(5_000)` — survives a rotation, stops on a real exit. */
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
