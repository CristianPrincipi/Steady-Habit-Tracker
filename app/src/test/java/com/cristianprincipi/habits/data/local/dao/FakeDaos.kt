package com.cristianprincipi.habits.data.local.dao

import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-ins for the three DAOs, so the repository can be tested on the JVM.
 *
 * Room's real DAOs need a device (ADR-017), which puts a several-minute emulator round trip between
 * writing a line of repository logic and finding out whether it works. These fakes make that loop a
 * second long. They are not a substitute for M3.11, which tests the real SQL against real SQLite —
 * they test the repository's decisions, not the queries'.
 *
 * Two behaviours are reproduced deliberately because the repository depends on them:
 * [FakeCompletionDao] enforces the foreign key to `days`, and its insert ignores duplicates.
 */
class FakeHabitDao(
    initial: List<HabitEntity> = emptyList(),
) : HabitDao {
    private val rows = MutableStateFlow(initial.associateBy { it.id })

    /** Direct read for assertions, without collecting a flow. */
    val current: List<HabitEntity> get() = rows.value.values.toList()

    override fun observeActive(): Flow<List<HabitEntity>> =
        rows.map { byId ->
            byId.values.filter { it.isActive }.sortedBy { it.sortOrder }
        }

    override fun observeAll(): Flow<List<HabitEntity>> =
        rows.map { byId ->
            byId.values.sortedWith(compareByDescending<HabitEntity> { it.isActive }.thenBy { it.sortOrder })
        }

    override suspend fun findAll(): List<HabitEntity> = current

    override suspend fun upsert(habits: List<HabitEntity>) {
        rows.value = rows.value + habits.associateBy { it.id }
    }

    override suspend fun setStartDate(
        habitId: String,
        epochDay: Long,
    ) {
        val habit = rows.value[habitId] ?: return
        rows.value = rows.value + (habitId to habit.copy(startDateEpochDay = epochDay))
    }

    override suspend fun deactivateAllExcept(activeIds: List<String>) {
        rows.value =
            rows.value.mapValues { (id, habit) ->
                if (id in activeIds) habit else habit.copy(isActive = false)
            }
    }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }

    override suspend fun setColor(
        habitId: String,
        color: String,
    ) {
        val habit = rows.value[habitId] ?: return
        rows.value = rows.value + (habitId to habit.copy(color = color))
    }
}

class FakeDayDao(
    initial: List<DayEntity> = emptyList(),
) : DayDao {
    private val rows = LinkedHashMap<Long, DayEntity>().apply { putAll(initial.associateBy { it.epochDay }) }

    /** Direct read for assertions, and for wiring [FakeCompletionDao]'s foreign key check. */
    val current: Map<Long, DayEntity> get() = rows

    /**
     * Mirrors `ON DELETE CASCADE` from `habit_completions` to `days`.
     *
     * A `var` rather than a constructor parameter because the two fakes point at each other — the
     * completion dao checks this one for its foreign key, and this one has to reach back into it.
     * Set it after both exist; a test that forgets simply gets no cascade, which the real database
     * would never do.
     */
    var onDelete: (Long) -> Unit = {}

    override suspend fun find(epochDay: Long): DayEntity? = rows[epochDay]

    override suspend fun upsert(day: DayEntity) {
        rows[day.epochDay] = day
    }

    override suspend fun withState(state: SyncState): List<DayEntity> =
        rows.values.filter { it.syncState == state }.sortedBy { it.epochDay }

    override suspend fun latestRemoteEditMillis(): Long? =
        rows.values
            .mapNotNull {
                it.remoteLastEditedAtEpochMillis
            }.maxOrNull()

    override suspend fun earliestDay(): Long? = rows.keys.minOrNull()

    override suspend fun deleteAll() {
        rows.clear()
    }

    override suspend fun delete(epochDay: Long) {
        rows.remove(epochDay)
        onDelete(epochDay)
    }
}

class FakeCompletionDao(
    /**
     * Mirrors `habit_completions`' foreign key to `days`.
     *
     * Not decoration. The repository has to upsert the day *before* the completion, and on the JVM
     * nothing else would notice if it stopped doing so — the failure would first appear on a device,
     * on the first check-off of a date the app had never seen.
     */
    private val dayExists: (Long) -> Boolean = { true },
) : CompletionDao {
    private val rows = MutableStateFlow(emptySet<HabitCompletionEntity>())

    val current: Set<HabitCompletionEntity> get() = rows.value

    override fun observeBetween(
        from: Long,
        to: Long,
    ): Flow<List<HabitCompletionEntity>> =
        rows.map { all ->
            all.filter { it.epochDay in from..to }.sortedWith(compareBy({ it.epochDay }, { it.habitId }))
        }

    override suspend fun forDay(epochDay: Long): List<HabitCompletionEntity> =
        rows.value.filter {
            it.epochDay ==
                epochDay
        }

    override suspend fun earliestDayFor(habitId: String): Long? =
        rows.value.filter { it.habitId == habitId }.minOfOrNull { it.epochDay }

    /** What the foreign key's cascade does when a day is deleted. Wire it to [FakeDayDao.onDelete]. */
    fun dropDay(epochDay: Long) {
        rows.value = rows.value.filterNot { it.epochDay == epochDay }.toSet()
    }

    override suspend fun insert(completion: HabitCompletionEntity) {
        check(dayExists(completion.epochDay)) {
            "FOREIGN KEY constraint failed: no row in `days` for epochDay ${completion.epochDay}"
        }
        // The real DAO inserts with OnConflictStrategy.IGNORE; a Set gives the same outcome.
        rows.value = rows.value + completion
    }

    override suspend fun deleteAll() {
        rows.value = emptySet()
    }

    override suspend fun delete(
        habitId: String,
        epochDay: Long,
    ) {
        rows.value = rows.value.filterNot { it.habitId == habitId && it.epochDay == epochDay }.toSet()
    }
}
