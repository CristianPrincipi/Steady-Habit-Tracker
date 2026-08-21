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
     * Insert or update the habit set from the Notion schema.
     *
     * Upsert rather than replace, so that [HabitEntity.color] — which exists nowhere but this
     * device — is not wiped by a sync. Only the mapper decides which columns a pull may overwrite.
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

    @Query("UPDATE habits SET color = :color WHERE id = :habitId")
    suspend fun setColor(
        habitId: String,
        color: String,
    )
}
