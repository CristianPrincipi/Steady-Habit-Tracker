package com.cristianprincipi.habits.data.repository

import com.cristianprincipi.habits.data.local.TransactionRunner
import com.cristianprincipi.habits.data.local.dao.CompletionDao
import com.cristianprincipi.habits.data.local.dao.DayDao
import com.cristianprincipi.habits.data.local.dao.HabitDao
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.local.mapper.toDomain
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [HabitRepository] over Room. The first thing in this app that actually reads the database.
 *
 * **Room only, never the network** (`CLAUDE.md` §1). Nothing here awaits Notion; the sync engine
 * (M4) reconciles in the background against the same tables, and every screen keeps working while
 * it does or does not.
 *
 * **The entities stop here.** Both queries map to `domain/model` types before returning, so no
 * `HabitEntity` or `HabitCompletionEntity` appears in any signature above this class.
 *
 * No `flowOn` and no `withContext`: Room dispatches its own queries onto its query executor for
 * both suspend functions and `Flow`s, so wrapping these calls would move the work to a second
 * thread pool to do nothing on. The only work on the collector's thread is the mapping.
 */
@Singleton
class HabitRepositoryImpl
    @Inject
    constructor(
        private val habitDao: HabitDao,
        private val dayDao: DayDao,
        private val completionDao: CompletionDao,
        private val transactionRunner: TransactionRunner,
        private val clock: Clock,
    ) : HabitRepository {
        override fun observeHabits(): Flow<List<Habit>> = habitDao.observeActive().map { it.toDomain() }

        override fun observeCompletions(
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<Completion>> =
            completionDao
                .observeBetween(from = from.toEpochDay(), to = to.toEpochDay())
                .map { it.toDomain() }

        /**
         * Writes the day and the check-off together, and tells sync the day is owed.
         *
         * Three things this has to get right, in this order:
         *
         * 1. **The day row first.** A completion's foreign key points at `days`, so on a date the
         *    app has never seen — every first check-off on a fresh day — inserting the completion
         *    first fails the constraint. The day is upserted, then the completion written.
         * 2. **One transaction.** Either both land or neither does. A day marked `PENDING_PUSH`
         *    without its completion would push a check-off that is not there.
         * 3. **`PENDING_PUSH`, unconditionally.** This is the only signal M4 has that the day owes
         *    Notion anything. It is stamped even when the write changed nothing — a second
         *    `completed = true` on an already-checked day — because the cost of a redundant push is
         *    one page update setting checkboxes to the values they already hold, and the cost of a
         *    missed one is a check-off that never reaches Notion. **M4 must not read `PENDING_PUSH`
         *    as proof that local and remote differ.**
         *
         * An existing day keeps its [DayEntity.notionPageId] and [DayEntity.remoteLastEditedAtEpochMillis]
         * through the copy: those are Notion's facts about the page, and a local edit does not
         * change them. Losing the page id would make the next push create a second page for the
         * same date.
         */
        override suspend fun setCompleted(
            habitId: HabitId,
            date: LocalDate,
            completed: Boolean,
        ) {
            val epochDay = date.toEpochDay()
            val now = clock.millis()
            transactionRunner.withTransaction {
                val existing = dayDao.find(epochDay)
                dayDao.upsert(
                    existing?.copy(
                        localUpdatedAtEpochMillis = now,
                        syncState = SyncState.PENDING_PUSH,
                    ) ?: DayEntity(
                        epochDay = epochDay,
                        notionPageId = null,
                        localUpdatedAtEpochMillis = now,
                        remoteLastEditedAtEpochMillis = null,
                        syncState = SyncState.PENDING_PUSH,
                    ),
                )
                if (completed) {
                    completionDao.insert(HabitCompletionEntity(habitId = habitId.value, epochDay = epochDay))
                } else {
                    completionDao.delete(habitId = habitId.value, epochDay = epochDay)
                }
            }
        }

        /**
         * Stores the colour by [HabitColor.name], matching what the mapper reads back.
         *
         * No day is touched and no sync state moves: the user's database has no colour property, so
         * this edit is not something Notion can ever be behind on (`docs/notion-schema.md` §3).
         */
        override suspend fun setColor(
            habitId: HabitId,
            color: HabitColor,
        ) {
            habitDao.setColor(habitId = habitId.value, color = color.name)
        }
    }
