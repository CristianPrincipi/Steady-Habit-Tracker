package com.cristianprincipi.habits.domain.model

import java.time.Instant

/**
 * What the Notion sync is doing, and how it last got on. **The only shape the UI ever sees.**
 *
 * `CLAUDE.md` §3.5: "a failed sync is a *state*, not an exception thrown at the user. Model it,
 * render it, offer a retry." This is that state. Nothing above the data layer sees a `NotionError`,
 * an HTTP status or a Ktor type — §6 forbids it, and a status bar that renders "429" has failed the
 * person reading it.
 *
 * **[lastSyncedAt] is on every case rather than beside them**, because it is orthogonal to what is
 * happening now: a sync that is running, and one that just failed, both still have a moment when
 * the data was last actually correct, and that is the line the user reads. It moves only on
 * success — [com.cristianprincipi.habits.domain.repository.SettingsRepository.observeLastSyncedAt]
 * explains why.
 *
 * **Observing this must never gate anything.** Room is the source of truth for every screen
 * (`CLAUDE.md` §1); this is a caption and a banner, and a check-off succeeds locally whatever it
 * says.
 */
sealed interface SyncStatus {
    /** When the last **successful** sync finished, or `null` if none ever has. */
    val lastSyncedAt: Instant?

    /**
     * Nothing is running and nothing is wrong.
     *
     * Also the state of an app that has never been connected — "not connected" is a property of the
     * connection rather than of the last run, and the screen that offers **disconnect** (M4.13)
     * has to know it independently anyway. So a fresh install reads as idle with no last sync,
     * which is the calm and accurate thing to render.
     */
    data class Idle(
        override val lastSyncedAt: Instant?,
    ) : SyncStatus

    /** A run is happening right now. */
    data class Syncing(
        override val lastSyncedAt: Instant?,
    ) : SyncStatus

    /**
     * The last run did not sync, and [reason] is what to say about it.
     *
     * Not necessarily *now* — a run worth retrying is already queued behind WorkManager's backoff
     * (`SyncWorker`), and this stays until some later run replaces it. That is the honest reading:
     * the last thing that happened, failed.
     */
    data class Failed(
        val reason: SyncFailure,
        override val lastSyncedAt: Instant?,
    ) : SyncStatus
}

/**
 * Why a sync did not happen, in terms a person can act on.
 *
 * **A deliberate re-statement of `NotionError`, not a re-export of it.** The wire errors speak HTTP
 * — 401, 403, 404, 429 — and `CLAUDE.md` §6 stops them at the data layer. More usefully, they split
 * on *what the server said*, and this splits on **what the user can do about it**: cases that need
 * the same sentence and the same button are one case here.
 *
 * The set is closed so that M4.13's `when` stops compiling when a new way to fail is added — the
 * same reason `NotionError` and `SyncOutcome` are closed.
 */
sealed interface SyncFailure {
    /**
     * No token is stored, so nothing can be reached.
     *
     * Distinct from a fresh install, which is [SyncStatus.Idle]: this is a mapping that survived
     * its token — a half-finished disconnect, or a keystore that could not decrypt. The answer is
     * to connect again.
     */
    data object NotConnected : SyncFailure

    /** Notion rejected the token. It was revoked, or it was never valid. A new one is the fix. */
    data object TokenRejected : SyncFailure

    /**
     * The token is real but the connection is not allowed to do this — a capability it lacks.
     *
     * Kept apart from [TokenRejected] because the fix is different and specific: edit the
     * connection's capabilities in Notion rather than issue a new token.
     */
    data object MissingCapability : SyncFailure

    /**
     * ⭐ The database is gone **or was never shared with the connection**, and Notion will not say
     * which (`CLAUDE.md` §3.3, `docs/notion-api.md` §3.1).
     *
     * Named for the misleading case on purpose. The instruction that fixes it far more often is:
     * open the database in Notion → ••• → Add connections → pick the connection.
     */
    data object DatabaseNotShared : SyncFailure

    /**
     * The saved mapping no longer describes the user's database, so **nothing was read and nothing
     * was written** — `CLAUDE.md` §3.4.
     *
     * [issues] is never empty, and carries every problem rather than the first: a user who renamed
     * one column and deleted another should be told both at once.
     */
    data class MappingBroken(
        val issues: List<MappingIssue>,
    ) : SyncFailure

    /** The device could not reach Notion at all. Nothing is lost; the next run picks it up. */
    data object Offline : SyncFailure

    /**
     * Notion itself could not answer — its own 5xx, or its rate limit, after the retry policy had
     * already spent its attempts waiting.
     *
     * One case rather than two because the sentence and the button are identical: wait, it will try
     * again by itself.
     */
    data object NotionUnavailable : SyncFailure

    /**
     * This build and Notion disagree about the API: a request Notion called invalid, or a `2xx`
     * whose body would not parse.
     *
     * Nothing the user can do, and retrying repeats the mistake — it is a pinned-version problem
     * (`docs/notion-api.md` §11), so it should read as one.
     */
    data object AppOutOfDate : SyncFailure
}

/**
 * One way the saved mapping has stopped matching the database.
 *
 * A domain mirror of `NotionMappingProblem`, which lives in `data/remote/notion/mapper` and is typed
 * in that layer's value classes. The ids come across as plain strings: they are opaque here, nothing
 * parses them, and M4.13 can join them against `HabitEntity.notionPropertyId` when it wants the name
 * a deleted property no longer has.
 *
 * **A rename is not in this list, and cannot be.** ADR-038: the mapping is keyed on the property id,
 * which survives a rename, so renaming a habit in Notion validates cleanly.
 */
sealed interface MappingIssue {
    /** The `Date` property that identifies a day is gone. Nothing can be read or written without it. */
    data class DayKeyMissing(
        val propertyId: String,
    ) : MappingIssue

    /** The day key is still there but is no longer a date. */
    data class DayKeyRetyped(
        val propertyId: String,
        val name: String,
        /** Notion's own type string, raw, so a type this build has never heard of still names itself. */
        val actualType: String,
    ) : MappingIssue

    /**
     * A mapped habit's checkbox property no longer exists.
     *
     * Deleting a property in Notion destroys every value in it, so the local history may now be the
     * only copy (`docs/notion-schema.md` §4.1). Sync pauses; nothing deletes anything.
     */
    data class HabitMissing(
        val propertyId: String,
    ) : MappingIssue

    /** A mapped habit is still there but is no longer a checkbox. Writing to it would overwrite real data. */
    data class HabitRetyped(
        val propertyId: String,
        val name: String,
        val actualType: String,
    ) : MappingIssue

    /**
     * The schema validated against was not the data source the mapping names.
     *
     * A bug in this app rather than anything the user did — every call takes the id from the same
     * mapping — but it is one bad argument away, and it must not render as "all your columns were
     * deleted", which is what continuing would conclude.
     */
    data object WrongDataSource : MappingIssue
}
