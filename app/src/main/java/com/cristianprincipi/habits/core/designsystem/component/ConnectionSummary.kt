package com.cristianprincipi.habits.core.designsystem.component

import com.cristianprincipi.habits.core.datetime.Elapsed

/**
 * The Notion connection reduced to the four facts [ConnectionStatusCard] says out loud.
 *
 * **A type rather than four parameters, because three of them are booleans.**
 * `ConnectionStatusCard(true, false, true, null)` is unreadable at a call site and one transposition
 * away from telling the user the opposite of the truth.
 *
 * **Already coarsened.** [lastSynced] is an [Elapsed] rather than an `Instant`: the arithmetic
 * against a clock happens in a ViewModel, so neither the card nor its callers ever read `now`
 * (`CLAUDE.md` §6 keeps the clock out of the layers below a ViewModel; this keeps it out of the
 * layer above too).
 */
data class ConnectionSummary(
    val isConnected: Boolean = false,
    val isSyncing: Boolean = false,
    val isWaitingForNetwork: Boolean = false,
    /** How long since the last **successful** sync, or `null` if none ever finished. */
    val lastSynced: Elapsed? = null,
)
