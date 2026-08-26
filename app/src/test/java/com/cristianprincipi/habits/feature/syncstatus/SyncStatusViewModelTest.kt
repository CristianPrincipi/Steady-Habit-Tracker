package com.cristianprincipi.habits.feature.syncstatus

import app.cash.turbine.test
import com.cristianprincipi.habits.core.datetime.Elapsed
import com.cristianprincipi.habits.core.testing.FakeNotionSetupRepository
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import com.cristianprincipi.habits.domain.model.DiscardedEditNotice
import com.cristianprincipi.habits.domain.model.EditSide
import com.cristianprincipi.habits.domain.model.SyncFailure
import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What the sync-status screen is told, and what its two buttons do.
 *
 * The clock is fixed, so "3 minutes ago" is a claim a test can make. The repositories are fakes
 * because how a status is arrived at is `SyncStatusRepositoryImplTest`'s subject, not this one's.
 */
class SyncStatusViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-08-23T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle(lastSyncedAt = null))
    private val notices = MutableStateFlow(emptyList<DiscardedEditNotice>())
    private val syncStatus = FakeSyncStatusRepository(status, notices)
    private val connection = FakeNotionSetupRepository()

    private fun viewModel() = SyncStatusViewModel(syncStatus, connection, clock)

    @Test
    fun aConnectedAppThatSyncedThreeMinutesAgoSaysSo() =
        runTest {
            connection.saveToken("ntn_pretend")
            status.value = SyncStatus.Idle(lastSyncedAt = now.minusSeconds(180))

            viewModel().uiState.test {
                val state = awaitItem()
                assertTrue(state.isConnected)
                assertEquals(Elapsed.Minutes(3), state.lastSynced)
                assertEquals(null, state.failure)
            }
        }

    @Test
    fun anAppThatHasNeverSyncedHasNoElapsedTimeRatherThanZero() =
        runTest {
            viewModel().uiState.test {
                assertEquals(null, awaitItem().lastSynced)
            }
        }

    @Test
    fun aFailureReachesTheScreenAsSomethingToSay() =
        runTest {
            connection.saveToken("ntn_pretend")
            status.value = SyncStatus.Failed(SyncFailure.DatabaseNotShared, lastSyncedAt = null)

            viewModel().uiState.test {
                assertEquals(SyncFailure.DatabaseNotShared, awaitItem().failure)
            }
        }

    @Test
    fun thereIsNothingToRetryWhileARunIsAlreadyInFlight() =
        runTest {
            connection.saveToken("ntn_pretend")
            status.value = SyncStatus.Syncing(lastSyncedAt = null)

            viewModel().uiState.test {
                val state = awaitItem()
                assertTrue(state.isSyncing)
                assertFalse(state.canRetry)
            }
        }

    @Test
    fun aHeldSyncSaysSoAndDisablesTheButtonItAnswersFor() =
        runTest {
            // ⭐ M4.16, amended by ADR-060 in M4.18. syncNow() de-duplicates on a unique name, so a
            // second tap while the request is held provably does nothing — but the button is now
            // disabled rather than removed, because removing it let the destructive button reflow
            // into its place. canRetry is the enabled flag; the card still says what happens next.
            connection.saveToken("ntn_pretend")
            status.value = SyncStatus.WaitingForNetwork(lastSyncedAt = now.minusSeconds(7200))

            viewModel().uiState.test {
                val state = awaitItem()
                assertTrue(state.isWaitingForNetwork)
                assertFalse(state.isSyncing)
                assertFalse(state.canRetry)
                // Still true, and still worth saying: waiting is not a failure.
                assertEquals(null, state.failure)
                assertEquals(Elapsed.Hours(2), state.lastSynced)
            }
        }

    @Test
    fun retryAsksTheRepositoryForASync() =
        runTest {
            // The "offer a retry" half of CLAUDE.md §3.5.
            viewModel().onEvent(SyncStatusEvent.RetryPressed)

            assertEquals(1, syncStatus.syncRequests)
        }

    @Test
    fun theNoticesReachTheScreen() =
        runTest {
            // ⭐ §3.5 permits resolution to destroy an edit and forbids doing it silently. This is
            // the screen where it stops being silent.
            notices.value = listOf(notice())

            viewModel().uiState.test {
                assertTrue(awaitItem().hasDiscardedEdits)
            }
        }

    @Test
    fun dismissingTheNoticesClearsThem() =
        runTest {
            notices.value = listOf(notice())

            viewModel().onEvent(SyncStatusEvent.NoticesDismissed)

            assertEquals(1, syncStatus.dismissals)
        }

    // --- disconnect ---------------------------------------------------------------------------

    @Test
    fun disconnectAsksBeforeItDoesAnything() =
        runTest {
            connection.saveToken("ntn_pretend")
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertFalse(awaitItem().isConfirmingDisconnect)

                viewModel.onEvent(SyncStatusEvent.DisconnectPressed)

                assertTrue(awaitItem().isConfirmingDisconnect)
                assertTrue("nothing wiped yet", connection.disconnects.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun deletingTheLocalDataStartsUntickedEveryTime() =
        runTest {
            // ⭐ CLAUDE.md §3.6 makes this a SEPARATE choice from disconnecting, so it is never a
            // remembered preference — a tick carried over from last time would be a default.
            val viewModel = viewModel()
            viewModel.onEvent(SyncStatusEvent.DisconnectPressed)
            viewModel.onEvent(SyncStatusEvent.DeleteLocalDataToggled(delete = true))
            viewModel.onEvent(SyncStatusEvent.DisconnectCancelled)

            viewModel.onEvent(SyncStatusEvent.DisconnectPressed)

            viewModel.uiState.test {
                assertFalse(awaitItem().willDeleteLocalData)
            }
        }

    @Test
    fun cancellingWipesNothing() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(SyncStatusEvent.DisconnectPressed)

            viewModel.onEvent(SyncStatusEvent.DisconnectCancelled)

            assertTrue(connection.disconnects.isEmpty())
        }

    @Test
    fun confirmingWithoutTheTickKeepsTheHistory() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(SyncStatusEvent.DisconnectPressed)

            viewModel.onEvent(SyncStatusEvent.DisconnectConfirmed)

            assertEquals(listOf(false), connection.disconnects)
        }

    @Test
    fun confirmingWithTheTickPassesItThrough() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(SyncStatusEvent.DisconnectPressed)
            viewModel.onEvent(SyncStatusEvent.DeleteLocalDataToggled(delete = true))

            viewModel.onEvent(SyncStatusEvent.DisconnectConfirmed)

            assertEquals(listOf(true), connection.disconnects)
        }

    @Test
    fun theDialogClosesBeforeTheWipeSoItCannotBeConfirmedTwice() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(SyncStatusEvent.DisconnectPressed)

            viewModel.onEvent(SyncStatusEvent.DisconnectConfirmed)

            viewModel.uiState.test {
                assertFalse(awaitItem().isConfirmingDisconnect)
            }
        }

    private fun notice() =
        DiscardedEditNotice(
            date = LocalDate.of(2026, 8, 21),
            habitNames = listOf("📖 Read"),
            habitCount = 1,
            discardedSide = EditSide.Local,
            resolvedAt = now,
        )
}

/** A [SyncStatusRepository] that answers from two flows and counts what it was asked to do. */
private class FakeSyncStatusRepository(
    private val status: MutableStateFlow<SyncStatus>,
    private val notices: MutableStateFlow<List<DiscardedEditNotice>>,
) : SyncStatusRepository {
    var syncRequests = 0
        private set

    var dismissals = 0
        private set

    override fun observeStatus(): Flow<SyncStatus> = status

    override fun requestSync() {
        syncRequests++
    }

    override fun observeDiscardedEdits(): Flow<List<DiscardedEditNotice>> = notices

    override suspend fun dismissDiscardedEdits() {
        dismissals++
        notices.value = emptyList()
    }
}
