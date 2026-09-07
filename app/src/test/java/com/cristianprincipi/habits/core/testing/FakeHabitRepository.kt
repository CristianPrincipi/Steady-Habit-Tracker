package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * A [HabitRepository] backed by two lists, for testing the use cases above it.
 *
 * It reproduces the behaviour the real one promises and the use cases lean on: `observeCompletions`
 * is **inclusive at both ends**. An off-by-one there would let a use case pass its own boundary
 * test wrongly.
 *
 * ⚠ **It cannot express *why* a habit is hidden, and does not need to** (ADR-081). Hiding is a
 * `WHERE` in `HabitDao.observeActive`, one layer below this; `HabitRepositoryImplTest` and
 * `SyncEngineTest` are where the rule itself is tested. What it can express, through [setHabits],
 * is the only part of hiding anything above the repository can see: **the set changed**.
 */
class FakeHabitRepository(
    habits: List<Habit> = emptyList(),
    completions: List<Completion> = emptyList(),
) : HabitRepository {
    private val habitRows = MutableStateFlow(habits)
    private val completionRows = MutableStateFlow(completions.toSet())

    /** Every `setCompleted` this received, in order, for asserting what a use case wrote. */
    val writes = mutableListOf<Write>()

    /** Every `setColor` this received, in order. */
    val colorWrites = mutableListOf<Pair<HabitId, HabitColor>>()

    /**
     * Every window `observeCompletions` was asked for, in order — one entry per **call**.
     *
     * M9.7's counter. The invariant it exists to hold is ADR-094 §1's: one range query answers the
     * whole Stats screen, and a use case that reached for a second one — a per-habit query, a
     * separate count for the rate — would be two chances for two sections to disagree about a day.
     * Nothing else in the project can see that happening.
     *
     * Recorded when the flow is **built**, not when it is collected, because that is where a stray
     * extra query would be written.
     */
    val completionQueries = mutableListOf<ClosedRange<LocalDate>>()

    data class Write(
        val habitId: HabitId,
        val date: LocalDate,
        val completed: Boolean,
    )

    override fun observeHabits(): Flow<List<Habit>> = habitRows

    /**
     * Replace the habit set, as a sync does when a habit leaves the mapped set or returns.
     *
     * The whole of hiding, from above the repository: a habit is simply not in the list any more
     * (ADR-081). `StatsViewModelTest` uses it for the one screen that holds a *chosen* habit and so
     * has to survive that habit disappearing.
     */
    fun setHabits(habits: List<Habit>) {
        habitRows.value = habits
    }

    override fun observeCompletions(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Completion>> {
        completionQueries += from..to
        return completionRows.map { rows ->
            rows.filter { it.date >= from && it.date <= to }.sortedBy { it.date }
        }
    }

    /**
     * ⭐ **A device that will not write — M13.12.**
     *
     * `SQLiteFullException` rather than a bare `RuntimeException`, because it is the one this app
     * will actually meet: a phone with no space left, on the write that `CLAUDE.md` §1 promises is
     * "never lost". Nothing in the ViewModel inspects the type, so the choice is documentation —
     * but a fake that threw something impossible would let a test pass for the wrong reason.
     *
     * Set on [setCompleted] and [setColor] alike; the writes are still recorded, because a failed
     * write **was attempted** and a test about retrying needs to count the attempts.
     */
    var failWritesWith: Exception? = null

    override suspend fun setCompleted(
        habitId: HabitId,
        date: LocalDate,
        completed: Boolean,
    ) {
        writes += Write(habitId, date, completed)
        failWritesWith?.let { throw it }
        val row = Completion(habitId, date)
        completionRows.value = if (completed) completionRows.value + row else completionRows.value - row
    }

    override suspend fun setColor(
        habitId: HabitId,
        color: HabitColor,
    ) {
        colorWrites += habitId to color
        failWritesWith?.let { throw it }
        habitRows.value = habitRows.value.map { if (it.id == habitId) it.copy(color = color) else it }
    }
}

/**
 * A habit with sensible defaults, so a test names only what it is about.
 *
 * No `emoji` parameter: nothing above the mapper cares about it, and the split itself is already
 * covered by `HabitMapperTest`. A test that needs one can `.copy(emoji = ...)`.
 */
fun testHabit(
    id: String = "read",
    name: String = "Read",
    color: HabitColor = HabitColor.BLUE,
    startDate: LocalDate = LocalDate.of(2020, 1, 1),
): Habit =
    Habit(
        id = HabitId(id),
        name = name,
        emoji = "",
        color = color,
        startDate = startDate,
    )
