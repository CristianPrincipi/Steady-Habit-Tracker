package com.cristianprincipi.habits.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.SyncState

/**
 * The sync-facing half of local storage: one row per day, one page per day.
 *
 * Nothing here returns a `Flow`. No screen shows a day as such — the UI reads habits and completions
 * — so these are one-shot reads for the sync engine (M4), which runs on its own schedule and is not
 * an observer of anything.
 */
@Dao
interface DayDao {
    @Query("SELECT * FROM days WHERE epochDay = :epochDay")
    suspend fun find(epochDay: Long): DayEntity?

    @Upsert
    suspend fun upsert(day: DayEntity)

    /**
     * Days owing Notion a write, oldest first.
     *
     * The state is a parameter rather than a literal in the SQL on purpose: `WHERE syncState =
     * 'PENDING_PUSH'` would keep compiling after somebody renamed the constant, and would then match
     * nothing for as long as it took anyone to notice a sync that silently pushed no changes.
     */
    @Query("SELECT * FROM days WHERE syncState = :state ORDER BY epochDay ASC")
    suspend fun withState(state: SyncState): List<DayEntity>

    /** The most recent day pulled from Notion, for the incremental pull filter (`CLAUDE.md` §3.5). */
    @Query("SELECT MAX(remoteLastEditedAtEpochMillis) FROM days")
    suspend fun latestRemoteEditMillis(): Long?
}
