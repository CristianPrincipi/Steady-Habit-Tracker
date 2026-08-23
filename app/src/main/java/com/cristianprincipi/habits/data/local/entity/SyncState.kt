package com.cristianprincipi.habits.data.local.entity

/**
 * Where a day stands with Notion.
 *
 * Kept in the data layer because it describes how a row is stored rather than what a habit is.
 * Nothing above the repository sees it.
 *
 * Only [DayEntity] carries one. A day is what Notion holds as a page, so a day is what can be out of
 * step with it; habits are read from the database schema and never pushed (ADR-010), and a
 * completion is a checkbox inside the page for its day rather than a record of its own (ADR-028).
 *
 * **Three states, not the four `CLAUDE.md` §3.5 originally listed.** `PENDING_DELETE` was removed in
 * M3.13 — see ADR-036. This app has no delete to represent: habit deletion is a Notion *schema*
 * change that ADR-010 removed, and un-checking a habit sets a checkbox to false rather than deleting
 * the page for that day. A state nothing can produce is a state every `when` has to handle and every
 * reader has to wonder about. It comes back the day something can reach it.
 */
enum class SyncState {
    /** Local and Notion agree, as of the last sync. */
    SYNCED,

    /**
     * Edited locally since the last sync, and owed to Notion.
     *
     * **Not proof that the two sides differ.** `HabitRepositoryImpl.setCompleted` stamps this on
     * every local edit, including one that changed nothing — the cost of a redundant push is a page
     * update writing checkboxes the values they already hold, and the cost of a missed one is a
     * check-off that never leaves the device.
     */
    PENDING_PUSH,

    /**
     * Both sides changed since the last sync.
     *
     * **Nothing writes this any more, as of M4.9.** Resolution is last-write-wins and needs no
     * input from the user, so `SyncEngine` decides a conflict in the transaction that finds it —
     * the page is in hand at that moment — and the day comes out either [SYNCED] or
     * [PENDING_PUSH]. A persisted "both changed" state would only ever mean *not yet resolved*,
     * and a day sitting in it is a day the push skips: stuck, for no benefit.
     *
     * It is kept for two reasons. `CLAUDE.md` §3.5 lists it among the three states, and removing it
     * is a §3 change to ask about rather than to make. And `SyncEngine.planFor` still *reads* it, as
     * the way out for a row an earlier build could have written — treating it as unpushed and
     * resolving it like any other disagreement.
     *
     * What the resolution destroyed is recorded as a
     * [com.cristianprincipi.habits.data.sync.DiscardedEdit], because §3.5 permits resolution to
     * discard an edit but never to do it silently.
     */
    CONFLICT,
}
