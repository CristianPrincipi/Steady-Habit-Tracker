package com.cristianprincipi.habits.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One day, and everything sync needs to know about it.
 *
 * **A day is the unit of sync in this app**, because a day is what Notion stores: one page per date,
 * with each habit a checkbox property on it (`docs/notion-schema.md` §1). Five habits checked off on
 * one day are five properties of one page, so five local check-offs owe Notion **one** page update,
 * not five. `CLAUDE.md` §3.5 asks for writes to be batched and coalesced rather than fired one per
 * checkbox tap; here that is not an optimisation, it falls out of the shape of the data.
 *
 * A row exists for every day the app has synced or touched, including days with nothing checked: a
 * day the user cleared still owes Notion a page update setting those checkboxes to false.
 */
@Entity(tableName = "days")
data class DayEntity(
    /** The date, as an epoch day. The whole key — a page is identified by its `Date` property. */
    @PrimaryKey val epochDay: Long,
    /**
     * The Notion page for this day, or null if no page exists yet.
     *
     * Null is the normal first state of a day checked off offline, not an error. Creating the page
     * is part of the write path (`docs/notion-schema.md` §2.5) and has to be idempotent — two rapid
     * check-offs on a fresh day must not create two pages for the same date.
     */
    val notionPageId: String?,
    /** When this day was last edited on this device, or null if it never has been. */
    val localUpdatedAtEpochMillis: Long?,
    /** The `last_edited_time` Notion reports for the page, as of the last pull. */
    val remoteLastEditedAtEpochMillis: Long?,
    val syncState: SyncState,
)
