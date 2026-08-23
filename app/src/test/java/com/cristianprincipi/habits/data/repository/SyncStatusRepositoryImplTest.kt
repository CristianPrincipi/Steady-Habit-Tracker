package com.cristianprincipi.habits.data.repository

import app.cash.turbine.test
import com.cristianprincipi.habits.core.testing.FakeSyncScheduler
import com.cristianprincipi.habits.core.testing.InMemoryDataStore
import com.cristianprincipi.habits.data.preferences.SettingsDataStore
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.sync.LastSyncFailureStore
import com.cristianprincipi.habits.data.sync.SyncOutcome
import com.cristianprincipi.habits.domain.model.SyncFailure
import com.cristianprincipi.habits.domain.model.SyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * The one shape the UI sees, assembled from three sources that each know a third of the answer.
 *
 * Everything here is about the **combination**, because that is all this class is: which source
 * wins when two of them have something to say, and what a screen is told while nothing is happening.
 */
class SyncStatusRepositoryImplTest {
    private val syncedAt = Instant.parse("2026-08-23T11:45:00Z")

    private val dataStore = InMemoryDataStore()
    private val settings = SettingsRepositoryImpl(SettingsDataStore(dataStore))
    private val lastFailure = LastSyncFailureStore(dataStore)
    private val scheduler = FakeSyncScheduler()

    private val repository = SyncStatusRepositoryImpl(settings, lastFailure, scheduler)

    @Test
    fun aFreshInstallIsIdleAndHasNeverSynced() =
        runTest {
            // Not "failed" and not "not connected": an app nobody has connected has not gone wrong.
            repository.observeStatus().test {
                assertEquals(SyncStatus.Idle(lastSyncedAt = null), awaitItem())
            }
        }

    @Test
    fun aRunThatWorkedLeavesTheTimeItFinished() =
        runTest {
            settings.setLastSyncedAt(syncedAt)
            lastFailure.record(SyncOutcome.Success(daysPulled = 1, daysPushed = 0))

            repository.observeStatus().test {
                assertEquals(SyncStatus.Idle(syncedAt), awaitItem())
            }
        }

    @Test
    fun aRunInProgressIsSyncing() =
        runTest {
            scheduler.running.value = true

            repository.observeStatus().test {
                assertEquals(SyncStatus.Syncing(lastSyncedAt = null), awaitItem())
            }
        }

    @Test
    fun theLastFailureIsWhatIdleTurnsInto() =
        runTest {
            lastFailure.record(SyncOutcome.Failed(NotionError.MissingOrNotShared))

            repository.observeStatus().test {
                assertEquals(SyncStatus.Failed(SyncFailure.DatabaseNotShared, lastSyncedAt = null), awaitItem())
            }
        }

    @Test
    fun aFailedRunStillReportsWhenTheDataWasLastCorrect() =
        runTest {
            // The line the user actually reads. A sync failing does not make yesterday's data stop
            // having arrived yesterday — which is why lastSyncedAt is on every case, not beside them.
            settings.setLastSyncedAt(syncedAt)
            lastFailure.record(SyncOutcome.Failed(NotionError.Unauthorized))

            repository.observeStatus().test {
                assertEquals(SyncStatus.Failed(SyncFailure.TokenRejected, syncedAt), awaitItem())
            }
        }

    @Test
    fun aRetryInProgressHidesTheErrorItIsRetrying() =
        runTest {
            // The one ordering decision this class makes. The stored failure is still on disk during
            // a retry — it is only replaced when the new run finishes — and showing it over a live
            // attempt would say "broken" at the moment it is being fixed.
            lastFailure.record(SyncOutcome.Failed(NotionError.Server(status = 503)))
            scheduler.running.value = true

            repository.observeStatus().test {
                assertEquals(SyncStatus.Syncing(lastSyncedAt = null), awaitItem())
            }
        }

    @Test
    fun aRunThatStartsAndSucceedsGoesSyncingThenIdle() =
        runTest {
            lastFailure.record(SyncOutcome.Failed(NotionError.Network(IOException())))

            repository.observeStatus().test {
                assertEquals(SyncStatus.Failed(SyncFailure.Offline, lastSyncedAt = null), awaitItem())

                scheduler.running.value = true
                assertEquals(SyncStatus.Syncing(lastSyncedAt = null), awaitItem())

                // The engine's own bookkeeping — it clears the failure and stamps the time before
                // the worker returns, so both land while WorkManager still calls the run RUNNING.
                // The caption updates a moment before the spinner stops, which is the truth.
                lastFailure.record(SyncOutcome.Success(daysPulled = 2, daysPushed = 1))
                settings.setLastSyncedAt(syncedAt)
                assertEquals(SyncStatus.Syncing(syncedAt), awaitItem())

                scheduler.running.value = false
                assertEquals(SyncStatus.Idle(syncedAt), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aSourceThatMovesWithoutChangingTheAnswerEmitsNothing() =
        runTest {
            // WorkManager re-emits a work info on every transition, most of which are between two
            // kinds of not-running. Without distinctUntilChanged a status bar recomposes on each.
            repository.observeStatus().test {
                assertEquals(SyncStatus.Idle(lastSyncedAt = null), awaitItem())

                scheduler.running.value = false
                lastFailure.record(SyncOutcome.NotConfigured)

                expectNoEvents()
            }
        }

    @Test
    fun askingForASyncGoesToTheScheduler() =
        runTest {
            // The "offer a retry" half of CLAUDE.md §3.5, and the only write this interface has.
            repository.requestSync()

            assertEquals(1, scheduler.syncRequests)
        }
}
