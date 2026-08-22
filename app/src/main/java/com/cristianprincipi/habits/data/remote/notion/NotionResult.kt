package com.cristianprincipi.habits.data.remote.notion

import kotlin.time.Duration

/**
 * What a call to Notion produced: a value, or a [NotionError] that names what went wrong.
 *
 * **A result type rather than exceptions, because `CLAUDE.md` §3.5 requires it**: "a failed sync is
 * a *state*, not an exception thrown at the user. Model it, render it, offer a retry." An exception
 * escaping the client would have to be caught somewhere, and the somewhere that forgets is how a
 * sync failure becomes a crash. Every failure below is a value the UI can turn into a sentence.
 */
sealed interface NotionResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : NotionResult<T>

    data class Failure(
        val error: NotionError,
    ) : NotionResult<Nothing>
}

/** The value, or `null` if the call failed. */
fun <T> NotionResult<T>.getOrNull(): T? = (this as? NotionResult.Success)?.value

/** The failure, or `null` if the call succeeded. */
fun NotionResult<*>.errorOrNull(): NotionError? = (this as? NotionResult.Failure)?.error

/** Transforms a success and passes a failure through unchanged. */
inline fun <T, R> NotionResult<T>.map(transform: (T) -> R): NotionResult<R> =
    when (this) {
        is NotionResult.Success -> NotionResult.Success(transform(value))
        is NotionResult.Failure -> this
    }

/**
 * Every way a Notion call can fail, as a closed set.
 *
 * Closed on purpose: `feature/syncstatus` (M4.13) has to render each of these as something the user
 * can act on, and a `when` over a sealed interface is what makes the compiler point at the screen
 * when a new case is added. A generic `IOException` would let a case ship with no message.
 *
 * **None of these carries the token, and none may ever be made to.** `CLAUDE.md` §3.6 forbids the
 * token reaching a log or a stack trace, and an error object is a thing that gets printed.
 */
sealed interface NotionError {
    /** No token is stored. The user has not connected, or has disconnected. Not a network failure. */
    data object NotConnected : NotionError

    /**
     * `401` — the token was rejected. It was revoked in Notion, or it was never valid.
     *
     * Needs the user, not a retry: [com.cristianprincipi.habits.core.network.RetryPolicy] will not
     * try again, because waiting cannot turn a revoked token into a working one.
     */
    data object Unauthorized : NotionError

    /** `403` — the token is valid but lacks the capability for this call. */
    data object Forbidden : NotionError

    /**
     * ⭐ `404` — **the database does not exist, OR it has not been shared with the connection.**
     *
     * `CLAUDE.md` §3.3 predicted this and `docs/notion-api.md` §3.1 confirmed it: the API gives no
     * way to tell the two apart. It is named for the *misleading* case rather than the literal one
     * so that nothing downstream can render it as "not found" — which is the wrong instruction more
     * often than it is the right one, and sends the user hunting for a typo that is not there.
     *
     * M4.12 must turn this into: *go to the database in Notion → ••• → Add connections → pick your
     * connection.*
     */
    data object MissingOrNotShared : NotionError

    /**
     * `429` — rate limited, and [com.cristianprincipi.habits.core.network.RetryPolicy] has already
     * spent its attempts waiting.
     *
     * @param retryAfter the server's own `Retry-After`, in seconds, if it sent one
     *   (`docs/notion-api.md` §6).
     */
    data class RateLimited(
        val retryAfter: Duration?,
    ) : NotionError

    /**
     * `400` and the other client errors — the request itself was wrong.
     *
     * The one that will actually happen in this app is a **renamed, retyped or deleted property**:
     * `CLAUDE.md` §3.4 requires the mapping to be validated before every sync precisely so this is
     * caught as a mapping problem rather than surfacing here as a wall of JSON.
     */
    data class Invalid(
        val code: String,
        val message: String,
    ) : NotionError

    /** `5xx`, including Notion's own `529 service_overload`. Already retried; still failing. */
    data class Server(
        val status: Int,
    ) : NotionError

    /** No response at all: airplane mode, DNS, a dead socket, a timeout. Already retried. */
    data class Network(
        val cause: Throwable,
    ) : NotionError

    /**
     * A `2xx` whose body would not parse.
     *
     * Distinct from [Invalid] because the fault is ours or the wire's, not the user's — an unknown
     * *key* can never cause this (the JSON config ignores those), so reaching it means a field we
     * rely on changed shape or vanished. That is a version-pin problem, and it should look like one.
     */
    data class Malformed(
        val cause: Throwable,
    ) : NotionError
}
