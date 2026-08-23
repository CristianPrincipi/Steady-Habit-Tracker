package com.cristianprincipi.habits.domain.repository

import com.cristianprincipi.habits.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * What the Notion sync is doing, and the one way to ask it to run.
 *
 * **Separate from [SettingsRepository] on purpose.** That one owns durable preferences; this one
 * owns something largely ephemeral — whether a background job is running right now — and assembles
 * it from three sources that have nothing else in common. Folding them together would put a
 * `WorkManager` behind the interface that also serves the Settings screen's static values.
 *
 * The interface lives here, in `domain/`, so a ViewModel can hold it without holding a `Context`, a
 * `WorkManager` or a Ktor type (`CLAUDE.md` §6). M4.13's sync-status screen is its first consumer.
 */
interface SyncStatusRepository {
    /**
     * The current [SyncStatus], and every change to it.
     *
     * **Never a gate.** `CLAUDE.md` §1: Room is the source of truth for the UI and every user action
     * succeeds locally and immediately. A screen may render this; no screen may wait for it.
     */
    fun observeStatus(): Flow<SyncStatus>

    /**
     * Asks for a sync as soon as the network allows. Returns straight away — the run happens on
     * WorkManager, and its progress arrives through [observeStatus].
     *
     * This is the "offer a retry" half of `CLAUDE.md` §3.5, and also the app-open trigger. **Not for
     * a check-off**: §3.5 asks for writes to be batched rather than fired one per checkbox tap.
     */
    fun requestSync()
}
