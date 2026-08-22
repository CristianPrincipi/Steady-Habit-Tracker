package com.cristianprincipi.habits.data.remote.notion.auth

/**
 * The user's Notion secret.
 *
 * **A type, not a `String`, for one reason: [toString] is overridden to redact it.** `CLAUDE.md`
 * §3.6 says never log the token — "not at debug level, not in a stack trace, not through an HTTP
 * logging interceptor" — and the ways a bare `String` leaks are all implicit. A data class holding
 * one prints it from its generated `toString`. So does string interpolation in an exception message,
 * a Compose recomposition trace, and any `Log.d("state=$state")` written months from now by somebody
 * who never read §3.6. Wrapping it means those all print `NotionToken(***)` instead.
 *
 * It is `@JvmInline`, so at runtime this is still just the `String` — the protection costs nothing.
 *
 * **This does not make the token safe to log deliberately.** [value] still returns it. The type
 * removes the accidents, not the intent.
 */
@JvmInline
value class NotionToken(
    val value: String,
) {
    override fun toString(): String = REDACTED

    /**
     * A cheap sanity check on a pasted string, **not** a validation.
     *
     * `docs/notion-api.md` §3 records that a personal access token carries the `ntn_` prefix. This
     * catches the paste that went wrong — a page URL, a database id, an empty clipboard — without
     * spending an API call to find out. **Only the API can say whether a token actually works**, so
     * M4.12 must still verify against the real endpoint; a prefix that changes upstream must never
     * be able to lock the user out of connecting.
     */
    fun looksLikeAToken(): Boolean = value.startsWith(PREFIX) && value.length > PREFIX.length

    companion object {
        private const val REDACTED = "NotionToken(***)"

        /** From `docs/notion-api.md` §3, confirmed against the live docs in M4.1. */
        const val PREFIX = "ntn_"
    }
}
