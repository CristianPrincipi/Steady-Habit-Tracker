package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [SyncScheduler] with no WorkManager behind it.
 *
 * The real one is four calls into `WorkManager.getInstance(context)`, which needs a device — so
 * everything that only cares *whether* a sync was asked for, or *whether* one is running, tests
 * against this instead. [running] is writable, because "a run started, then finished" is the
 * sequence most of those tests are actually about.
 */
class FakeSyncScheduler : SyncScheduler {
    val running = MutableStateFlow(false)

    /**
     * Whether a request is enqueued and has not run.
     *
     * **Deliberately not driven by [syncNow].** The real WorkManager decides this, and it can be
     * true without anyone having called [syncNow] this session — a request enqueued before the
     * process died is still enqueued afterwards. Making it independent lets a test set up that
     * state, and keeps [syncRequests] meaning what it says: how many times the app *asked*.
     */
    val queued = MutableStateFlow(false)

    /** A run someone asked for is running. Independent of [running] for [queued]'s reason. */
    val requestedRunning = MutableStateFlow(false)

    var scheduleRequests = 0
        private set

    var syncRequests = 0
        private set

    var appOpenRequests = 0
        private set

    var widgetCheckOffRequests = 0
        private set

    override fun ensureScheduled() {
        scheduleRequests++
    }

    override fun syncNow() {
        syncRequests++
    }

    override fun syncOnAppOpen() {
        appOpenRequests++
    }

    override suspend fun syncAfterWidgetCheckOff() {
        widgetCheckOffRequests++
    }

    override fun observeRunning(): Flow<Boolean> = running

    override fun observeRequestedRunning(): Flow<Boolean> = requestedRunning

    override fun observeQueued(): Flow<Boolean> = queued
}
