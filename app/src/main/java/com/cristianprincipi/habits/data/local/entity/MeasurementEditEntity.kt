package com.cristianprincipi.habits.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A number typed on this device that Notion has not been sent yet — M16.2, ADR-156.
 *
 * ### ⭐⭐ Its own sync record, and deliberately not the day's
 *
 * [DayEntity.syncState] says whether the day's **checkboxes** are owed, and `ConflictResolver`
 * decides a whole day by last-write-wins. Folding a number into that would let a weight typed on the
 * phone make the phone's stale checkboxes "win" against a habit ticked later in Notion — a habit lost
 * that nobody edited on the phone. So a number edit never touches the day's state, and this row is
 * what says it is owed instead: one row, one number, one day.
 *
 * ### [baseValue] is what makes the pull a three-way merge
 *
 * It is the value Notion held when the edit was made, so the pull can tell *"Notion did not touch
 * this number"* (remote == base: keep ours) from *"both sides changed it"* (last-write-wins). Without
 * it, every remote value that differs from ours would look like a conflict.
 *
 * ⚠ **The value itself is not here.** `measurements` stays the one table every screen reads, so a
 * typed number shows on Week, Stats and the widget at once. A **cleared** number is a missing
 * `measurements` row with this row still present — which is how "send `null`" is told apart from
 * "nothing owed".
 *
 * ### Both foreign keys cascade, for [MeasurementEntity]'s reasons
 *
 * Measures are deleted only on disconnect. A day is deleted only when its page is gone in Notion
 * **and** nothing on it is owed, which `SyncEngine.reconcileGonePage` checks against this table.
 */
@Entity(
    tableName = "measurement_edits",
    primaryKeys = ["measureId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = MeasureEntity::class,
            parentColumns = ["id"],
            childColumns = ["measureId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DayEntity::class,
            parentColumns = ["epochDay"],
            childColumns = ["epochDay"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["epochDay"])],
)
data class MeasurementEditEntity(
    val measureId: String,
    val epochDay: Long,
    /** What Notion held when the edit was made; `null` when it held nothing. */
    val baseValue: Double?,
    /**
     * When the edit was made, on this device's clock — the local side of last-write-wins, compared
     * with the page's `last_edited_time` exactly as `ConflictResolver` compares a day's.
     */
    val editedAtEpochMillis: Long,
)
