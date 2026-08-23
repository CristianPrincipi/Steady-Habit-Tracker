package com.cristianprincipi.habits.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import kotlinx.coroutines.flow.Flow

/**
 * The check-off table.
 *
 * **Every write here needs the row for its day to exist first.** A completion has a foreign key to
 * `days`, because the page Notion stores is the day, not the tick. The repository (M3.6) upserts the
 * day and writes the completion in one transaction; a bare insert against a day the app has never
 * seen will fail the constraint, which is the intended outcome rather than a bug to work around.
 */
@Dao
interface CompletionDao {
    /**
     * Every completion in `[from, to]`, both bounds inclusive, across all habits.
     *
     * `BETWEEN` is inclusive at both ends in SQLite, which is what the repository contract promises.
     */
    @Query("SELECT * FROM habit_completions WHERE epochDay BETWEEN :from AND :to")
    fun observeBetween(
        from: Long,
        to: Long,
    ): Flow<List<HabitCompletionEntity>>

    /** Every completion for one day. The push path reads this to build a page update. */
    @Query("SELECT * FROM habit_completions WHERE epochDay = :epochDay")
    suspend fun forDay(epochDay: Long): List<HabitCompletionEntity>

    /**
     * The first day this habit was ever checked off, or null if it never was.
     *
     * **This is how a habit gets a start date**, because Notion has none to give
     * (`docs/notion-schema.md` §3) and the data cannot supply a better answer: an old page carries
     * `false` for a habit that did not exist then and for one that was simply not done, and the API
     * returns the two identically. The first check-off is the earliest day the habit is *known* to
     * have existed — see ADR-046 for why that beats the alternatives.
     */
    @Query("SELECT MIN(epochDay) FROM habit_completions WHERE habitId = :habitId")
    suspend fun earliestDayFor(habitId: String): Long?

    /**
     * Record a completion, or do nothing if it is already recorded.
     *
     * `IGNORE` is what makes checking off idempotent at the storage layer, and it has to be: a sync
     * killed mid-run replays its writes (`CLAUDE.md` §3.5), and so does a double tap.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: HabitCompletionEntity)

    /** Take a completion back. Deleting one that is not there is not an error. */
    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun delete(
        habitId: String,
        epochDay: Long,
    )
}
