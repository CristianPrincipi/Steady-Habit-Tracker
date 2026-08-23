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

    /**
     * The oldest day the app knows about, or null on a first run.
     *
     * The fallback for inferring a habit's start date: a habit with no completions at all has no
     * first check-off to date it from, and the earliest page in the database is the earliest moment
     * it could plausibly have existed (`docs/notion-schema.md` §3 — Notion holds no start date).
     */
    @Query("SELECT MIN(epochDay) FROM days")
    suspend fun earliestDay(): Long?

    /**
     * Forgets a day whose page is gone from Notion, taking its completions with it by cascade.
     *
     * **The one place anything local is deleted, and it is not a local delete** — ADR-036's rule
     * stands. This is the *remote* delete `CLAUDE.md` §3.5 distinguishes: Notion no longer has the
     * page, so keeping the day would be inventing data. The engine calls it only for a day with no
     * unpushed local edit; a day that has one becomes a conflict instead.
     */
    @Query("DELETE FROM days WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    /**
     * Every day, gone. **Only for the disconnect action** (M4.13).
     *
     * This also drops the pull watermark, which is the point: a later reconnect should import from
     * scratch rather than resume from a `MAX(remoteLastEditedAt)` describing data that is no longer
     * here (ADR-047).
     */
    @Query("DELETE FROM days")
    suspend fun deleteAll()
}
