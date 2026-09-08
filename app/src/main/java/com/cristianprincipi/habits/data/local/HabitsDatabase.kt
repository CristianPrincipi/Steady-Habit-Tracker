package com.cristianprincipi.habits.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cristianprincipi.habits.data.local.dao.CompletionDao
import com.cristianprincipi.habits.data.local.dao.DayDao
import com.cristianprincipi.habits.data.local.dao.HabitDao
import com.cristianprincipi.habits.data.local.dao.MeasureDao
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.data.local.entity.MeasureEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEntity

/**
 * The local source of truth. Every screen reads from here and from nowhere else (`CLAUDE.md` §1).
 *
 * Five tables, in the shape the user's Notion database actually has: habits are a cached schema,
 * days are pages, completions are the checkboxes on them, and — since M15.4 — measures are a cached
 * schema too, with a reading per measure per day. See [DayEntity] for why the day is what carries
 * sync state.
 *
 * ⭐⭐ **Measures are two tables of their own rather than a flag on the habit tables, and that is
 * the whole exclusion mechanism** — see [MeasureEntity]. Every screen in this app reads habits; a
 * measure sharing that table would have to be filtered out of each of them by a `where` somebody
 * has to remember.
 *
 * `exportSchema = true`, and the JSON under `app/schemas/` is committed. That file is the input to
 * every future migration test; a schema change that does not update it is a schema change nobody can
 * write a migration against. `fallbackToDestructiveMigration` appears nowhere in this project and
 * must not — it deletes the user history that ADR-010 goes to some length to protect.
 */
@Database(
    entities = [
        HabitEntity::class,
        DayEntity::class,
        HabitCompletionEntity::class,
        MeasureEntity::class,
        MeasurementEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HabitsDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    abstract fun dayDao(): DayDao

    abstract fun completionDao(): CompletionDao

    abstract fun measureDao(): MeasureDao

    companion object {
        /** Room needs the file name in two places: the Hilt module (M3.6) and the migration tests. */
        const val NAME = "habits.db"
    }
}
