package com.cristianprincipi.habits.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Which failures are worth another attempt, and how long to wait.
 *
 * Every rule here traces to `docs/notion-api.md` §6 and its status table, confirmed against the live
 * docs in M4.1. The two that matter most are the ones about *not* retrying: burning the rate limit
 * to re-ask a question already answered is worse than failing, because it delays the moment the user
 * is told what is actually wrong.
 */
class RetryPolicyTest {
    private val policy =
        RetryPolicy(
            maxAttempts = 4,
            baseDelay = 100.milliseconds,
            maxDelay = 10.seconds,
        )

    // ─── What gets retried ─────────────────────────────────────────────────────────────────────

    @Test
    fun aRateLimitIsRetried() {
        assertEquals(100.milliseconds, policy.delayBefore(attempt = 1, status = 429))
    }

    @Test
    fun notionsOwnOverloadStatusIsRetried() {
        // 529 service_overload is Notion's, and is a different condition from 503.
        assertEquals(100.milliseconds, policy.delayBefore(attempt = 1, status = 529))
    }

    @Test
    fun serverErrorsAreRetried() {
        listOf(500, 502, 503, 504).forEach { status ->
            assertEquals(
                "$status should be retried",
                100.milliseconds,
                policy.delayBefore(attempt = 1, status = status),
            )
        }
    }

    @Test
    fun aRequestThatNeverGotAStatusIsRetried() {
        // A socket timeout, a DNS failure, airplane mode. None of them say anything about whether
        // Notion would have accepted the request.
        assertEquals(100.milliseconds, policy.delayBefore(attempt = 1, status = null))
    }

    // ─── What does not ─────────────────────────────────────────────────────────────────────────

    @Test
    fun aNotFoundIsNotRetried() {
        // The important one. docs/notion-api.md §3.1: 404 means "does not exist OR is not shared
        // with the integration", and the API gives no way to tell them apart. Both need the USER —
        // CLAUDE.md §3.3 wants it surfaced as an instruction, not swallowed by a retry loop.
        assertNull(policy.delayBefore(attempt = 1, status = 404))
    }

    @Test
    fun anUnauthorizedRequestIsNotRetried() {
        // The token was revoked. Waiting changes nothing; the user has to paste a new one.
        assertNull(policy.delayBefore(attempt = 1, status = 401))
    }

    @Test
    fun aValidationErrorIsNotRetried() {
        // It will fail identically forever. Retrying just arrives at the same answer more slowly.
        assertNull(policy.delayBefore(attempt = 1, status = 400))
    }

    @Test
    fun aRestrictedResourceIsNotRetried() {
        assertNull(policy.delayBefore(attempt = 1, status = 403))
    }

    @Test
    fun successIsNotRetried() {
        assertNull(policy.delayBefore(attempt = 1, status = 200))
    }

    // ─── Retry-After wins ──────────────────────────────────────────────────────────────────────

    @Test
    fun theServersRetryAfterBeatsOurBackoff() {
        // CLAUDE.md §3.5: "honour the server's retry delay on 429". It knows when the window
        // reopens; our exponential backoff is guessing.
        val delay = policy.delayBefore(attempt = 1, status = 429, retryAfter = 7.seconds)

        assertEquals(7.seconds, delay)
    }

    @Test
    fun aShortRetryAfterIsHonouredEvenWhenOurBackoffWouldBeLonger() {
        // It wins in BOTH directions — this is not a "take the maximum".
        val delay = policy.delayBefore(attempt = 3, status = 429, retryAfter = 50.milliseconds)

        assertEquals(50.milliseconds, delay)
    }

    @Test
    fun anAbsurdRetryAfterIsStillCapped() {
        // A misconfigured proxy sending Retry-After: 3600 must not park a coroutine for an hour.
        val delay = policy.delayBefore(attempt = 1, status = 429, retryAfter = 1.minutes * 60)

        assertEquals(10.seconds, delay)
    }

    @Test
    fun retryAfterDoesNotResurrectANonRetryableStatus() {
        // Belt and braces: a Retry-After on a 400 is meaningless and must not override the rule.
        assertNull(policy.delayBefore(attempt = 1, status = 400, retryAfter = 5.seconds))
    }

    @Test
    fun retryAfterDoesNotOutlastTheAttemptBudget() {
        assertNull(policy.delayBefore(attempt = 4, status = 429, retryAfter = 1.seconds))
    }

    // ─── The backoff curve ─────────────────────────────────────────────────────────────────────

    @Test
    fun theBackoffDoubles() {
        assertEquals(100.milliseconds, policy.delayBefore(attempt = 1, status = 500))
        assertEquals(200.milliseconds, policy.delayBefore(attempt = 2, status = 500))
        assertEquals(400.milliseconds, policy.delayBefore(attempt = 3, status = 500))
    }

    @Test
    fun theBackoffIsCapped() {
        val slowPolicy = RetryPolicy(maxAttempts = 30, baseDelay = 1.seconds, maxDelay = 5.seconds)

        assertEquals(5.seconds, slowPolicy.delayBefore(attempt = 10, status = 500))
    }

    @Test
    fun aVeryHighAttemptNumberDoesNotOverflowIntoANegativeDelay() {
        // 1L shl 64 wraps. The doubling count is clamped for exactly this reason, and the failure
        // it prevents is a negative Duration reaching delay(), which returns immediately and turns
        // the backoff into a hot loop against a struggling server.
        val slowPolicy = RetryPolicy(maxAttempts = 200, baseDelay = 1.seconds, maxDelay = 60.seconds)

        assertEquals(60.seconds, slowPolicy.delayBefore(attempt = 100, status = 503))
    }

    // ─── Giving up ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun theAttemptBudgetIsSpentEventually() {
        // attempt 3 is the last one that gets a delay: 100 × 2² = 400ms, then the budget is out.
        assertEquals(400.milliseconds, policy.delayBefore(attempt = 3, status = 500))
        assertNull("The fourth attempt is the last", policy.delayBefore(attempt = 4, status = 500))
    }

    @Test
    fun aPolicyMustAllowAtLeastOneAttempt() {
        val thrown = runCatching { RetryPolicy(maxAttempts = 0) }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class.java, thrown?.javaClass)
    }
}
