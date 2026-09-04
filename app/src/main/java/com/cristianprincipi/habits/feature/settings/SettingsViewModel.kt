package com.cristianprincipi.habits.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.core.datetime.elapsedBetween
import com.cristianprincipi.habits.core.designsystem.component.ConnectionSummary
import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.domain.repository.NotionSetupRepository
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

/**
 * Drives the Settings screen.
 *
 * Two flows in, one summary out. It is a smaller `SyncStatusViewModel` on purpose: this screen shows
 * the connection and offers no way to change it, so it holds no dialog state and needs no
 * repository call.
 *
 * ⚠ **There is no `SettingsEvent`, and that is not an omission.** `CLAUDE.md` §6 asks for one event
 * type per screen *that has events*; every control here is navigation, which is hoisted to
 * `HabitsNavHost` as a lambda exactly as `ManageHabits` and `Stats` hoist theirs. An event type
 * whose only member was "the user tapped a row" would route a navigation decision through a
 * ViewModel that has no opinion about it.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        connection: NotionSetupRepository,
        syncStatus: SyncStatusRepository,
        // Injected rather than read, so "3 minutes ago" is a testable claim (`CLAUDE.md` §6).
        clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            combine(
                connection.isConnected,
                syncStatus.observeStatus(),
            ) { isConnected, status ->
                SettingsUiState(
                    connection =
                        ConnectionSummary(
                            isConnected = isConnected,
                            isSyncing = status is SyncStatus.Syncing,
                            isWaitingForNetwork = status is SyncStatus.WaitingForNetwork,
                            lastSynced = status.lastSyncedAt?.let { at -> elapsedBetween(at, clock.instant()) },
                        ),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = SettingsUiState(),
            )

        private companion object {
            /** `CLAUDE.md` §6's `WhileSubscribed(5_000)` — survives a rotation, stops on a real exit. */
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
