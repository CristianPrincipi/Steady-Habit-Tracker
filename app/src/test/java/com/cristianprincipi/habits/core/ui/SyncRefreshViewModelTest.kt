package com.cristianprincipi.habits.core.ui

import app.cash.turbine.test
import com.cristianprincipi.habits.core.testing.FakeSyncStatusRepository
import com.cristianprincipi.habits.core.testing.MainDispatcherRule
import com.cristianprincipi.habits.domain.model.SyncFailure
import com.cristianprincipi.habits.domain.model.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * M15.3 — the one boolean behind pull-to-refresh, and the one call it can make.
 *
 * ### ⚠ What is worth testing here is the *narrowness*, not the mapping
 *
 * "A sync is running" looks like it could be `status !is Idle`, and that reading is wrong in two
 * ways that both put a spinner on screen with nothing behind it. The user's ruling is that the
 * indicator **stays until the sync finishes**, so every state that is not actually running is a
 * state where it would stay for ever — until the device came back online, or until someone
 * navigated away from a failure. Hence a test per wrong reading rather than one for the right one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncRefreshViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle(lastSyncedAt = null))
    private val syncStatus = FakeSyncStatusRepository(status)

    private fun viewModel() = SyncRefreshViewModel(syncStatus)

    @Test
    fun `a sync the user asked for raises the indicator`() =
        runTest {
            viewModel().isRefreshing.test {
                assertEquals(false, awaitItem())

                status.value = SyncStatus.Syncing(lastSyncedAt = null)
                syncStatus.requestedSyncRunning.value = true
                assertEquals(true, awaitItem())

                syncStatus.requestedSyncRunning.value = false
                status.value = SyncStatus.Idle(lastSyncedAt = Instant.EPOCH)
                assertEquals(false, awaitItem())
            }
        }

    /**
     * ⚠⚠ **M14.10 — the case the old reading got wrong.** ADR-125: only a sync the user reached for
     * draws. A periodic or app-open run is `Syncing` too, and reading `Syncing` spun the indicator
     * for it; with a sync on every app open, that would have been every launch.
     */
    @Test
    fun `a sync the app started on its own does not raise the indicator`() =
        runTest {
            viewModel().isRefreshing.test {
                assertEquals(false, awaitItem())

                status.value = SyncStatus.Syncing(lastSyncedAt = null)
                expectNoEvents()
            }
        }

    /**
     * ⚠⚠ **The spinner would otherwise stay up for as long as the device is offline.**
     *
     * `WaitingForNetwork` is not a sync in flight, it is a sync that cannot start — and an
     * indicator that means "working" while nothing is working is worse than no indicator, because
     * it is a claim rather than an absence.
     */
    @Test
    fun `waiting for a network is not running`() =
        runTest {
            viewModel().isRefreshing.test {
                assertEquals(false, awaitItem())

                status.value = SyncStatus.WaitingForNetwork(lastSyncedAt = null)
                expectNoEvents()
            }
        }

    /**
     * ⚠ A failure already has a surface that says more than a spinner could, and it is the surface
     * `CLAUDE.md` §3.5 asks for. Spinning on top of it would hide the one thing worth reading.
     */
    @Test
    fun `a failed sync is not running`() =
        runTest {
            viewModel().isRefreshing.test {
                assertEquals(false, awaitItem())

                status.value = SyncStatus.Failed(lastSyncedAt = null, reason = SyncFailure.Offline)
                expectNoEvents()
            }
        }

    @Test
    fun `pulling asks for a sync`() =
        runTest {
            val viewModel = viewModel()

            viewModel.refresh()
            viewModel.refresh()

            // Two, not one: de-duplication is `SyncScheduler`'s job and it does it on a unique work
            // name. This asserts the gesture is wired, not that the scheduler is.
            assertEquals(2, syncStatus.syncRequests)
        }
}
