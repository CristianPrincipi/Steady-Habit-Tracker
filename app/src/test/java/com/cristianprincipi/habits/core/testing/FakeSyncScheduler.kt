package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [SyncScheduler] with no WorkManager behind it.
 *
 * The real one is three calls into `WorkManager.getInstance(context)`, which needs a device — so
 * everything that only cares *whether* a sync was asked for, or *whether* one is running, tests
 * against this instead. [running] is writable, because "a run started, then finished" is the
 * sequence most of those tests are actually about.
 */
class FakeSyncScheduler : SyncScheduler {
    val running = MutableStateFlow(false)

    var scheduleRequests = 0
        private set

    var syncRequests = 0
        private set

    override fun ensureScheduled() {
        scheduleRequests++
    }

    override fun syncNow() {
        syncRequests++
    }

    override fun observeRunning(): Flow<Boolean> = running
}
