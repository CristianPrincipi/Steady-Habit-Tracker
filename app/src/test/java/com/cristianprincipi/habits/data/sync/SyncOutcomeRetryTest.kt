package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.data.remote.notion.NotionError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * The one decision `SyncWorker` makes: is this worth trying again?
 *
 * Kept as a pure property of [SyncOutcome] rather than as branching inside `doWork`, so the whole
 * rule is testable on the JVM without WorkManager, a device, or a fake worker. The worker itself is
 * then two lines and has nothing left to get wrong.
 */
class SyncOutcomeRetryTest {
    private fun failing(error: NotionError) = SyncOutcome.Failed(error)

    @Test
    fun aRunThatWorkedIsNotRetried() {
        assertFalse(SyncOutcome.Success(daysPulled = 3, daysPushed = 1).isWorthRetrying)
    }

    @Test
    fun waitingCanFixARateLimitADeadSocketOrNotionBeingDown() {
        // The three that are genuinely temporary, and the only reason the backoff exists.
        assertTrue(failing(NotionError.RateLimited(retryAfter = 30.seconds)).isWorthRetrying)
        assertTrue(failing(NotionError.Network(IOException("airplane mode"))).isWorthRetrying)
        assertTrue(failing(NotionError.Server(status = 503)).isWorthRetrying)
    }

    @Test
    fun waitingCannotFixAnythingThatNeedsAPerson() {
        // A revoked token does not come back on its own, and a database that was never shared
        // stays unshared however long we wait. Retrying spends the backoff window relearning the
        // same thing; the periodic run is along in fifteen minutes if the user fixes it.
        assertFalse(failing(NotionError.NotConnected).isWorthRetrying)
        assertFalse(failing(NotionError.Unauthorized).isWorthRetrying)
        assertFalse(failing(NotionError.Forbidden).isWorthRetrying)
        assertFalse(failing(NotionError.MissingOrNotShared).isWorthRetrying)
    }

    @Test
    fun waitingCannotFixThisBuildBeingWrongAboutTheApi() {
        // Repeating a request Notion called invalid repeats the mistake, and a response that would
        // not parse is a version-pin problem (docs/notion-api.md §11), not a flaky network.
        assertFalse(failing(NotionError.Invalid(code = "validation_error", message = "nope")).isWorthRetrying)
        assertFalse(failing(NotionError.Malformed(IllegalStateException("shape changed"))).isWorthRetrying)
    }

    @Test
    fun aStateThatNeedsTheUserIsNotAFailedRunAtAll() {
        // Neither is retried, and neither is `Result.failure()` either — see SyncWorker's KDoc:
        // failure means "give up permanently", which would stop a schedule nobody asked to stop.
        assertFalse(SyncOutcome.NotConfigured.isWorthRetrying)
        assertFalse(SyncOutcome.MappingBroken(problems = emptyList()).isWorthRetrying)
    }

    @Test
    fun onlyAPullCanChangeWhatAWidgetDraws() {
        // M15.6c. A push-only success sent local state TO Notion, so every widget is already
        // drawing it; nothing else touched Room at all. `updateAll` starts a Glance session per
        // placed widget, so "refresh on every sync" would be that cost on every run that could not
        // move a pixel.
        assertTrue(SyncOutcome.Success(daysPulled = 3, daysPushed = 0).changedLocalData)
        assertFalse(SyncOutcome.Success(daysPulled = 0, daysPushed = 5).changedLocalData)
        assertFalse(SyncOutcome.Success(daysPulled = 0, daysPushed = 0).changedLocalData)
        assertFalse(SyncOutcome.NotConfigured.changedLocalData)
        assertFalse(failing(NotionError.Unauthorized).changedLocalData)
    }
}
