package com.cristianprincipi.habits.data.sync

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo.State.BLOCKED
import androidx.work.WorkInfo.State.CANCELLED
import androidx.work.WorkInfo.State.ENQUEUED
import androidx.work.WorkInfo.State.RUNNING
import androidx.work.WorkInfo.State.SUCCEEDED
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a widget tap's sync request does to the one already queued — M15.11, ADR-152.
 *
 * ⚠⚠ **Every case is a tap whose write has already landed**, and the question is always the same:
 * will some run start after it? The first version answered `KEEP` to all of them, which is wrong
 * exactly when a run is going — and on the user's phone the next tap is what wakes the overdue run,
 * so the last tick of a row was always the one Notion did not get.
 */
class WidgetCheckOffPolicyTest {
    @Test
    fun theFirstTapAsksForARun() {
        assertEquals(ExistingWorkPolicy.REPLACE, widgetCheckOffPolicy(emptyList()))
        assertEquals(ExistingWorkPolicy.REPLACE, widgetCheckOffPolicy(listOf(SUCCEEDED, CANCELLED)))
    }

    @Test
    fun aTapWhileARunIsGoingQueuesOneMoreAfterIt() {
        // The running one may have read the day before this tap wrote it.
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, widgetCheckOffPolicy(listOf(SUCCEEDED, RUNNING)))
    }

    @Test
    fun aThirdTapRidesOnTheRunAlreadyQueued() {
        // The queued run has not started, so it reads this tap too: one more append is a wasted run.
        assertEquals(ExistingWorkPolicy.KEEP, widgetCheckOffPolicy(listOf(RUNNING, BLOCKED)))
    }

    @Test
    fun aRequestThatHasNotStartedIsReplacedByOneThatStartsNow() {
        // Waiting for the network or out a backoff, or a delayed request left by the first version:
        // all three wait on the system to wake the app, which is what the phone would not do.
        assertEquals(ExistingWorkPolicy.REPLACE, widgetCheckOffPolicy(listOf(ENQUEUED)))
        assertEquals(ExistingWorkPolicy.REPLACE, widgetCheckOffPolicy(listOf(ENQUEUED, BLOCKED)))
    }
}
