package com.cristianprincipi.habits.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Spaces outgoing requests so the app stays under Notion's rate limit.
 *
 * `CLAUDE.md` §3.5: "Respect the rate limit with a client-side throttle… Never call the API in a
 * bare loop." This is that throttle. It is the *floor* under [RetryPolicy], not a replacement for
 * it — a 429 can still happen (another client on the same token, a burst we did not model), and
 * when it does the server's `Retry-After` is the authority.
 *
 * **Why a fixed interval rather than a token bucket.** `docs/notion-api.md` §6 records what the
 * live docs actually say: "an average of three requests per second, with some bursts beyond the
 * average allowed" — and **the burst size is not published**. A bucket would mean inventing a
 * capacity, and an invented capacity that is too generous is worse than none, because it produces
 * 429s the throttle was added to prevent. Spacing requests evenly needs no number Notion has not
 * given us.
 *
 * This app's traffic makes that cheap. A day owes Notion exactly one page update however many
 * habits changed on it (ADR-028), so a full day's check-offs are a single request.
 *
 * @param nowMillis a **monotonic** millisecond source. Injected so tests can drive it from the
 *   coroutine scheduler's virtual clock — see `RateLimiterTest`. Never `currentTimeMillis`: a
 *   clock that steps backwards over an NTP correction would hang the limiter.
 */
class RateLimiter(
    private val minInterval: Duration = DEFAULT_MIN_INTERVAL,
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    private val mutex = Mutex()
    private var nextAllowedAtMillis: Long? = null

    /**
     * Waits until the next request is due, then runs [block].
     *
     * The wait happens **inside** the lock and the block **outside** it. That ordering is the whole
     * design: holding the lock across the request would serialise every call to Notion behind the
     * slowest one, turning a rate limiter into a queue of one. Holding it only across the bookkeeping
     * means concurrent callers are spaced apart but still overlap in flight.
     */
    suspend fun <T> withPermit(block: suspend () -> T): T {
        mutex.withLock {
            val now = nowMillis()
            val due = nextAllowedAtMillis
            if (due != null && due > now) {
                delay(due - now)
            }
            // Measured from when the permit was granted, not from `now` — otherwise a caller that
            // waited would immediately be due again, and a burst would slip through after any pause.
            nextAllowedAtMillis = maxOf(now, due ?: now) + minInterval.inWholeMilliseconds
        }
        return block()
    }

    companion object {
        /**
         * Three requests per second, spaced evenly — 334ms rather than 333ms.
         *
         * Rounded **up** deliberately. 333ms is 3.003 requests per second, which is over the limit
         * on the only side that matters.
         */
        val DEFAULT_MIN_INTERVAL = 334.milliseconds

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
