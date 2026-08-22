package com.cristianprincipi.habits.core.network

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Whether a failed request should be tried again, and how long to wait first.
 *
 * A **pure function of the outcome**, deliberately: no coroutines, no clock, no HTTP types. That
 * makes every rule below a one-line test, and it keeps `core/network` free of Ktor — the retry rules
 * are about what a status code means, not about how the request was made.
 *
 * The facts it encodes come from `docs/notion-api.md` §6, confirmed against the live docs in M4.1.
 * **Do not change them from memory.**
 */
class RetryPolicy(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val baseDelay: Duration = DEFAULT_BASE_DELAY,
    private val maxDelay: Duration = DEFAULT_MAX_DELAY,
) {
    init {
        require(maxAttempts >= 1) { "A request is attempted at least once, not $maxAttempts times" }
    }

    /**
     * How long to wait before attempt number [attempt] + 1, or `null` to give up.
     *
     * @param attempt how many attempts have already been made, starting at 1.
     * @param status the HTTP status of the failed attempt, or `null` if the request never got one
     *   — a socket timeout, a DNS failure, airplane mode. Those are retryable: they say nothing
     *   about whether Notion would have accepted the request.
     * @param retryAfter the value of the server's `Retry-After` header, if it sent one.
     */
    fun delayBefore(
        attempt: Int,
        status: Int?,
        retryAfter: Duration? = null,
    ): Duration? =
        when {
            attempt >= maxAttempts -> null

            status != null && !isRetryable(status) -> null

            // The server's own answer wins over anything we compute. CLAUDE.md §3.5: "honour the
            // server's retry delay on 429". It knows when the window reopens; our backoff is
            // guessing. Still capped — a Retry-After of an hour must not park a coroutine for one.
            retryAfter != null -> retryAfter.coerceAtMost(maxDelay)

            else -> backoffFor(attempt)
        }

    /** Exponential: base, 2×base, 4×base…, capped at [maxDelay]. */
    private fun backoffFor(attempt: Int): Duration {
        // Int, and clamped: `1 shl 32` wraps to 1 and `1 shl 31` goes negative. A negative Duration
        // reaching delay() returns immediately, turning the backoff into a hot loop against a
        // server that is already struggling. maxDelay caps the value; this caps the arithmetic.
        val multiplier = 1 shl (attempt - 1).coerceIn(0, MAX_DOUBLINGS)
        return (baseDelay * multiplier).coerceAtMost(maxDelay)
    }

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
        val DEFAULT_BASE_DELAY = 500.milliseconds
        val DEFAULT_MAX_DELAY = 60.seconds

        /** Enough doublings to pass [DEFAULT_MAX_DELAY] many times over; more would overflow. */
        const val MAX_DOUBLINGS = 20
    }
}

/**
 * The statuses worth trying again, from `docs/notion-api.md` §6 and its status-code table.
 *
 * **Everything else is a decision, not a hiccup.** A `400 validation_error` will fail identically
 * forever; a `401` means the token was revoked and needs the user, not a wait; and a **`404` means
 * "does not exist **or** is not shared with the integration"** — the very case `CLAUDE.md` §3.3
 * says must reach the user as an instruction rather than be swallowed. Retrying any of them burns
 * the rate limit to arrive at the same answer more slowly.
 */
private fun isRetryable(status: Int): Boolean =
    when (status) {
        TOO_MANY_REQUESTS -> true

        // 529 service_overload is Notion's own, and is a different condition from 503.
        SERVICE_OVERLOAD -> true

        in SERVER_ERRORS -> true

        else -> false
    }

private const val TOO_MANY_REQUESTS = 429
private const val SERVICE_OVERLOAD = 529
private const val FIRST_SERVER_ERROR = 500
private const val LAST_SERVER_ERROR = 599
private val SERVER_ERRORS = FIRST_SERVER_ERROR..LAST_SERVER_ERROR
