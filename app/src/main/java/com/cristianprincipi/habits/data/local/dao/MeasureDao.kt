package com.cristianprincipi.habits.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cristianprincipi.habits.data.local.entity.MeasureEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEditEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEntity
import kotlinx.coroutines.flow.Flow

/**
 * The measure schema cache and its readings — [CompletionDao]'s twin for numbers (M15.4).
 *
 * **Every write to `measurements` needs the row for its day to exist first**, exactly as a
 * completion does: a reading has a foreign key to `days`, because the page Notion stores is the
 * day. The sync writes both inside one transaction.
 *
 * `TooManyFunctions` is suppressed: a DAO is one SQL statement per function, not a class with
 * responsibilities to split. Since M16.2 it serves three tables that only mean anything together —
 * the measures, their readings, and the readings this device owes Notion (`measurement_edits`) —
 * and a second DAO would put every caller of one in a constructor with the other.
 */
@Suppress("TooManyFunctions")
@Dao
interface MeasureDao {
    /**
     * The measures to show, in the user's order.
     *
     * **Active only, and that is the only read the UI has** — `HabitDao.observeActive`'s contract.
     * A measure whose column left the mapped set is hidden and keeps its readings (ADR-081); tick
     * it again and it comes back whole.
     */
    @Query("SELECT * FROM measures WHERE isActive = 1 ORDER BY sortOrder ASC")
    fun observeActive(): Flow<List<MeasureEntity>>

    /** Every measure, hidden ones included. The import reads this to carry forward what it must. */
    @Query("SELECT * FROM measures")
    suspend fun findAll(): List<MeasureEntity>

    /**
     * Every measure, hidden ones included, as it changes — for the discarded-edit notices only.
     *
     * `HabitDao.observeAll`'s twin, and for its reason: a notice about a number whose column has
     * since left the mapping should still be able to say which number it was.
     */
    @Query("SELECT * FROM measures")
    fun observeAll(): Flow<List<MeasureEntity>>

    /**
     * Gives a measure its colour — M15.6f, and `HabitDao.setColor`'s twin in every respect.
     *
     * ⭐ **App-local, and that is what makes it allowed here at all.** Everything else about a
     * measure is Notion's: the name, the readings, whether it exists. A colour is in neither the
     * schema nor any page, so there is nothing to push and nothing a pull can overwrite —
     * `MeasureImporter` already carries it forward on every sync for exactly that reason.
     */
    @Query("UPDATE measures SET color = :color WHERE id = :measureId")
    suspend fun setColor(
        measureId: String,
        color: String,
    )

    /**
     * Every reading in `[from, to]`, both bounds inclusive, across all measures.
     *
     * `BETWEEN` is inclusive at both ends in SQLite — [CompletionDao.observeBetween]'s contract, and
     * the reason the average and the grid above it can agree about which days they cover.
     */
    @Query("SELECT * FROM measurements WHERE epochDay BETWEEN :from AND :to")
    fun observeBetween(
        from: Long,
        to: Long,
    ): Flow<List<MeasurementEntity>>

    @Upsert
    suspend fun upsert(measures: List<MeasureEntity>)

    /**
     * Hide every measure but these. The import's half of ADR-081.
     *
     * `HabitDao.deactivateAllExcept`'s twin, and deliberately not a delete: a Notion property that
     * is deleted destroys its column and every value in it, so these rows may be the only surviving
     * copy of the readings.
     */
    @Query("UPDATE measures SET isActive = 0 WHERE id NOT IN (:ids)")
    suspend fun deactivateAllExcept(ids: List<String>)

    /**
     * Record a reading, replacing any earlier one for that measure and day.
     *
     * ⚠ **REPLACE, where a completion uses IGNORE, and the difference is what the row means.** A
     * completion is a fact with no payload — done is done, so re-inserting it must change nothing.
     * A reading has a value the user can correct in Notion, and a pull that found 84.2 where we
     * hold 84.0 must write the new one. Ignoring would pin the first number ever synced.
     */
    @Upsert
    suspend fun upsertReading(reading: MeasurementEntity)

    /**
     * Forget a reading. Deleting one that is not there is not an error.
     *
     * ⚠⚠ **This is what an emptied number in Notion means, and it must not become a zero.** A
     * day with no row had no reading; a `0.0` would drag the average down with nothing on screen
     * looking unwell — see [MeasurementEntity].
     */
    @Query("DELETE FROM measurements WHERE measureId = :measureId AND epochDay = :epochDay")
    suspend fun deleteReading(
        measureId: String,
        epochDay: Long,
    )

    /** One reading, or null for a day without one. */
    @Query("SELECT * FROM measurements WHERE measureId = :measureId AND epochDay = :epochDay")
    suspend fun findReading(
        measureId: String,
        epochDay: Long,
    ): MeasurementEntity?

    /** The edit this device owes Notion for one number on one day, or null when it owes none. */
    @Query("SELECT * FROM measurement_edits WHERE measureId = :measureId AND epochDay = :epochDay")
    suspend fun findEdit(
        measureId: String,
        epochDay: Long,
    ): MeasurementEditEntity?

    /**
     * Every number owed to Notion, oldest day first — the push's second source beside
     * `DayDao.withState(PENDING_PUSH)` (ADR-156).
     */
    @Query("SELECT * FROM measurement_edits ORDER BY epochDay ASC")
    suspend fun allEdits(): List<MeasurementEditEntity>

    /** The numbers owed for one day. A day with any is not deleted when its page goes (ADR-156). */
    @Query("SELECT * FROM measurement_edits WHERE epochDay = :epochDay")
    suspend fun editsForDay(epochDay: Long): List<MeasurementEditEntity>

    /** Records, or re-records, that a number is owed. */
    @Upsert
    suspend fun upsertEdit(edit: MeasurementEditEntity)

    /** Notion has the number now, or has a newer one that won. Deleting a missing row is not an error. */
    @Query("DELETE FROM measurement_edits WHERE measureId = :measureId AND epochDay = :epochDay")
    suspend fun deleteEdit(
        measureId: String,
        epochDay: Long,
    )

    /** Every owed number, gone. **Only for the disconnect action**, beside [deleteAllReadings]. */
    @Query("DELETE FROM measurement_edits")
    suspend fun deleteAllEdits()

    /**
     * Every reading, gone. **Only for the disconnect action.**
     *
     * Redundant with the two cascades in theory, and kept anyway for [CompletionDao.deleteAll]'s
     * reason: "the user asked to remove their data" should not depend on a pragma being enabled.
     */
    @Query("DELETE FROM measurements")
    suspend fun deleteAllReadings()

    @Query("DELETE FROM measures")
    suspend fun deleteAllMeasures()
}
