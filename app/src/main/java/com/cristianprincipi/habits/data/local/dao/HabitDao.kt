package com.cristianprincipi.habits.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes for the habit set.
 *
 * Anything the UI observes returns a `Flow` (`CLAUDE.md` §6). The writes are for the sync engine and
 * for the one app-local field the user owns.
 */
@Dao
interface HabitDao {
    /**
     * Habits still present in the mapped Notion set, in display order — **every screen's only
     * source**.
     *
     * The `WHERE` is what hides a habit whose property has gone (ADR-081), and no query beside it
     * returns the hidden ones as a list. [observeAll] and [findAll] do see them, one layer lower and
     * for two jobs that are not lists: naming a discarded edit, and finding the row to bring back.
     */
    @Query("SELECT * FROM habits WHERE isActive = 1 ORDER BY sortOrder ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    /**
     * Every habit including the hidden ones — **a name lookup, not a habit list**.
     *
     * The one caller is `SyncStatusRepositoryImpl.observeDiscardedEdits`, which turns stored habit
     * ids into the name each habit carries *now*. A discarded edit outlives the habit going hidden,
     * and a notice that cannot name the habit it is about is not worth showing — so this read
     * deliberately ignores `isActive`.
     *
     * ⚠ **Never wire a screen to this** (ADR-081). A habit whose property has left the mapped set is
     * invisible, and [observeActive] is the only list. This returns entities, one layer below the
     * domain, which is the fence that keeps the distinction from being an accident.
     */
    @Query("SELECT * FROM habits ORDER BY isActive DESC, sortOrder ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    /**
     * The whole habit set, once, for the sync engine.
     *
     * Not a `Flow`: the engine is not an observer, and it needs the existing rows *before* it
     * upserts — to carry [HabitEntity.color] forward, which `upsert` would otherwise overwrite, and
     * to reuse each habit's local [HabitEntity.id] so the completions pointing at it survive.
     */
    @Query("SELECT * FROM habits")
    suspend fun findAll(): List<HabitEntity>

    /**
     * Insert or update the habit set from the Notion schema.
     *
     * Upsert rather than replace, so that a habit's local history is not deleted and re-created —
     * which would take its completions with it through the cascade.
     *
     * **It does not protect [HabitEntity.color].** An upsert writes every column of the row, so an
     * entity built from the Notion schema with a default colour overwrites whatever the user chose,
     * and colour exists nowhere but this device. The caller has to read the existing row and carry
     * the colour forward. That caller is M4's pull path; there is no other, and this is the note
     * that says so.
     *
     * **It also does not enforce the unique index on [HabitEntity.notionPropertyId]** (ADR-035).
     * Room catches the constraint failure and falls back to `UPDATE ... WHERE id = ?`; for a new
     * row that key does not exist, so the update matches nothing and this returns normally having
     * written nothing. No exception and no return value say so. The pull path has to reject
     * duplicate property ids before calling this.
     */
    @Upsert
    suspend fun upsert(habits: List<HabitEntity>)

    /**
     * Mark habits inactive by id, for properties that have left the mapped Notion set.
     *
     * Never a delete: the local rows are the only surviving record of a column Notion has destroyed.
     * **Inactive means invisible** (ADR-081) — the row stops reaching every screen through
     * [observeActive] while keeping its completions, and reappears whole if the property returns.
     */
    @Query("UPDATE habits SET isActive = 0 WHERE id NOT IN (:activeIds)")
    suspend fun deactivateAllExcept(activeIds: List<String>)

    /**
     * Sets a habit's inferred start date.
     *
     * A targeted update rather than another upsert, because the start date is *derived* from the
     * completions that were just pulled — recomputing it means writing one column, and routing that
     * through `upsert` would need the whole entity rebuilt and would risk clobbering the colour
     * again for nothing.
     */
    @Query("UPDATE habits SET startDateEpochDay = :epochDay WHERE id = :habitId")
    suspend fun setStartDate(
        habitId: String,
        epochDay: Long,
    )

    /**
     * Every habit, gone. **Only for the disconnect action** (`CLAUDE.md` §3.6, M4.13).
     *
     * Nothing else in this app deletes a habit — ADR-010 removed habit deletion entirely, and
     * `deactivateAllExcept` is what a sync uses when a column leaves the mapping, precisely so the
     * history survives. This exists because a user who disconnects and asks to remove the synced
     * data has said to remove it.
     *
     * Completions go with these by cascade, and `DayDao.deleteAll` clears the other parent.
     */
    @Query("DELETE FROM habits")
    suspend fun deleteAll()

    @Query("UPDATE habits SET color = :color WHERE id = :habitId")
    suspend fun setColor(
        habitId: String,
        color: String,
    )

    /**
     * Sets one habit's position in the list — M15.5.
     *
     * ⭐ **App-local, exactly like [setColor], and that is the decision this query encodes.**
     * `sortOrder` used to be re-derived from the mapping on every sync (`sortOrder = index`), which
     * meant the user could not own it: the next pull would put it back. `HabitImporter` now carries
     * it forward the way it carries the colour, and this is how the user changes it.
     *
     * ⚠ **Callers write the whole list, not one row.** A move swaps two neighbours, and writing
     * only one of them leaves two habits claiming a position. `HabitRepositoryImpl.setHabitOrder`
     * renumbers everything from zero inside a transaction, which also repairs any gap or collision
     * an older build left behind.
     */
    @Query("UPDATE habits SET sortOrder = :sortOrder WHERE id = :habitId")
    suspend fun setSortOrder(
        habitId: String,
        sortOrder: Int,
    )

    /** The highest position in use, or null when there are no habits. New rows go after it. */
    @Query("SELECT MAX(sortOrder) FROM habits")
    suspend fun highestSortOrder(): Int?
}
