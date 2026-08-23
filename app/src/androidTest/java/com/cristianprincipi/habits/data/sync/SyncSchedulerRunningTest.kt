package com.cristianprincipi.habits.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That [SyncScheduler.observeRunning] gets an answer out of the **real** WorkManager.
 *
 * The JVM tests all run against `FakeSyncScheduler`, which proves what the repository does with a
 * boolean and nothing about where the boolean comes from. What can only fail on a device is the
 * part underneath: `WorkManager.getInstance` triggering on-demand initialisation through
 * `HabitsApplication`'s `Configuration.Provider`, and `getWorkInfosForUniqueWorkFlow` actually
 * emitting. ADR-049 is the reason to care — that initialisation is load-bearing and fails silently.
 *
 * **Nothing asserts `true` or `false`.** Whether a sync happens to be running on this device at this
 * instant is a race, not a property of the code — the app enqueues its periodic work on launch, and
 * that run may or may not have finished. `ConnectivityObserverTest` documents the same reasoning:
 * what matters is that a value arrives, promptly, without being prodded.
 */
@RunWith(AndroidJUnit4::class)
class SyncSchedulerRunningTest {
    private val scheduler =
        WorkManagerSyncScheduler(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun itEmitsTheCurrentStateWithoutBeingProdded() =
        runBlocking {
            val first = withTimeoutOrNull(TIMEOUT_MILLIS) { scheduler.observeRunning().first() }

            assertNotNull("observeRunning() emitted nothing within ${TIMEOUT_MILLIS}ms", first)
        }

    @Test
    fun itStillAnswersOnceTheScheduleExists() =
        runBlocking {
            // ensureScheduled() is what the app does on launch. Asking afterwards is the state the
            // sync-status screen will actually be collecting in.
            scheduler.ensureScheduled()

            val first = withTimeoutOrNull(TIMEOUT_MILLIS) { scheduler.observeRunning().first() }

            assertNotNull("observeRunning() emitted nothing within ${TIMEOUT_MILLIS}ms", first)
        }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
