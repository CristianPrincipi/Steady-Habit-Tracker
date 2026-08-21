package com.cristianprincipi.habits.data.local.mapper

import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.HabitId
import java.time.LocalDate

/**
 * Epoch day to [LocalDate] and back. That is the whole of it, and it is deliberately the only place
 * the conversion happens.
 *
 * `CLAUDE.md` §6 stores a completion as a day rather than a timestamp, so no time zone is involved
 * in either direction: [LocalDate.ofEpochDay] and [LocalDate.toEpochDay] are pure arithmetic. Any
 * code that reaches for a `ZoneId` to turn one of these into the other has misunderstood the model
 * and will move somebody's check-off across midnight.
 */
fun HabitCompletionEntity.toDomain(): Completion =
    Completion(
        habitId = HabitId(habitId),
        date = LocalDate.ofEpochDay(epochDay),
    )

fun List<HabitCompletionEntity>.toDomain(): List<Completion> = map { it.toDomain() }

fun Completion.toEntity(): HabitCompletionEntity =
    HabitCompletionEntity(
        habitId = habitId.value,
        epochDay = date.toEpochDay(),
    )
