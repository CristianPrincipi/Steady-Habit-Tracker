package com.cristianprincipi.habits.feature.settings

import app.cash.turbine.test
import com.cristianprincipi.habits.core.datetime.Elapsed
import com.cristianprincipi.habits.core.testing.FakeNotionSetupRepository
import com.cristianprincipi.habits.core.testing.FakeSyncStatusRepository
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import com.cristianprincipi.habits.domain.model.SyncFailure
import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.feature.syncstatus.SyncStatusViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * What the Settings screen is told.
 *
 * The clock is fixed, so "3 minutes ago" is a claim a test can make. The repositories are fakes:
 * how a status is arrived at is `SyncStatusRepositoryImplTest`'s subject, not this one's.
 *
 * ⚠ **These tests exist because two screens now draw one card.** `SyncStatusViewModel` computes the
 * same four facts, and nothing but a test would notice if this one started computing them
 * differently — the card would still render, just with a different answer on each screen. The
 * `sameFactsAsTheSyncScreen` case is the one that catches that.
 */
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-09-04T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle(lastSyncedAt = null))
    private val syncStatus = FakeSyncStatusRepository(status)
    private val connection = FakeNotionSetupRepository()

    private fun viewModel() = SettingsViewModel(connection, syncStatus, clock)

    @Test
    fun aConnectedAppThatSyncedThreeMinutesAgoSaysSo() =
        runTest {
            connection.saveToken("ntn_pretend")
            status.value = SyncStatus.Idle(lastSyncedAt = now.minusSeconds(180))

            viewModel().uiState.test {
                val summary = awaitItem().connection
                assertTrue(summary.isConnected)
                assertEquals(Elapsed.Minutes(3), summary.lastSynced)
            }
        }

    @Test
    fun anAppThatHasNeverSyncedHasNoElapsedTimeRatherThanZero() =
        runTest {
            connection.saveToken("ntn_pretend")

            viewModel().uiState.test {
                assertEquals(null, awaitItem().connection.lastSynced)
            }
        }

    @Test
    fun aRunInProgressIsItsOwnFactRatherThanAStaleTimestamp() =
        runTest {
            connection.saveToken("ntn_pretend")
            status.value = SyncStatus.Syncing(lastSyncedAt = now.minusSeconds(3_600))

            viewModel().uiState.test {
                val summary = awaitItem().connection
                assertTrue(summary.isSyncing)
                assertFalse(summary.isWaitingForNetwork)
                // Still carried, because the card only prefers "Syncing…" — it does not lose the time.
                assertEquals(Elapsed.Hours(1), summary.lastSynced)
            }
        }

    @Test
    fun aSyncHeldForTheNetworkIsDistinctFromOneThatIsRunning() =
        runTest {
            connection.saveToken("ntn_pretend")
            status.value = SyncStatus.WaitingForNetwork(lastSyncedAt = null)

            viewModel().uiState.test {
                val summary = awaitItem().connection
                assertTrue(summary.isWaitingForNetwork)
                assertFalse(summary.isSyncing)
            }
        }

    /**
     * ⚠ **The disconnected case is reachable, despite the setup gate.**
     *
     * `HabitsShell` routes on a stored *mapping*, not a stored token, so a revoked token leaves this
     * screen drawn with no connection behind it. The card must say so rather than keep claiming one.
     */
    @Test
    fun anAppWithNoTokenSaysItIsNotConnected() =
        runTest {
            viewModel().uiState.test {
                assertFalse(awaitItem().connection.isConnected)
            }
        }

    /**
     * ⭐ **The regression this file exists for**: one card, two screens, and no way to see them drift.
     *
     * Both ViewModels reduce the same two flows to the same four facts. If one of them ever starts
     * disagreeing — a `Failed` status reported as syncing, an elapsed time computed against a
     * different clock — the app would show two different answers to "when did this last work", each
     * of them passing its own screen's tests.
     */
    @Test
    fun sameFactsAsTheSyncScreen() =
        runTest {
            connection.saveToken("ntn_pretend")
            status.value = SyncStatus.Failed(SyncFailure.TokenRejected, lastSyncedAt = now.minusSeconds(7_200))

            val sync = SyncStatusViewModel(syncStatus, connection, clock)

            viewModel().uiState.test {
                val settings = awaitItem().connection
                sync.uiState.test {
                    assertEquals(awaitItem().connection, settings)
                }
            }
        }
}
