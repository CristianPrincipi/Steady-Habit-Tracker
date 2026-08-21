package com.cristianprincipi.habits.data.local.entity

/**
 * Where a record stands with Notion.
 *
 * The four states `CLAUDE.md` §3.5 requires, kept in the data layer because they describe how a row
 * is stored rather than what a habit is. Nothing above the repository sees them.
 *
 * Only [DayEntity] carries one. A day is what Notion holds as a page, so a day is what can be out of
 * step with it; habits are read from the database schema and never pushed, and a completion is a
 * checkbox inside the page for its day rather than a record of its own.
 */
enum class SyncState {
    /** Local and Notion agree, as of the last sync. */
    SYNCED,

    /** Edited locally since the last sync, and owed to Notion. */
    PENDING_PUSH,

    /**
     * Deleted locally and owed as a delete to Notion.
     *
     * **Nothing produces this state yet, and it may turn out that nothing ever can.** This app has
     * no delete: ADR-010 removed habit deletion, and un-checking a habit sets a checkbox to false
     * rather than deleting the page for that day. `CLAUDE.md` §3.5 requires delete tombstones, so
     * the state stays modelled until that question is put to the user in M3.13 rather than being
     * quietly dropped here.
     */
    PENDING_DELETE,

    /** Both sides changed since the last sync. Resolution is last-write-wins (`CLAUDE.md` §3.5). */
    CONFLICT,
}
