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
    /** Habits still present in the Notion schema, in display order. */
    @Query("SELECT * FROM habits WHERE isActive = 1 ORDER BY sortOrder ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    /**
     * Every habit, active first.
     *
     * For M7.5, which lists habits whose Notion property has since disappeared: their history is
     * still here and may be the only copy left, since deleting a property destroys the column
     * upstream.
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
     * Mark habits inactive by id, for properties that have left the Notion schema.
     *
     * Never a delete: the local rows are the only surviving record of a column Notion has destroyed.
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

    @Query("UPDATE habits SET color = :color WHERE id = :habitId")
    suspend fun setColor(
        habitId: String,
        color: String,
    )
}
