package com.cristianprincipi.habits.data.local.mapper

import com.cristianprincipi.habits.data.local.entity.MeasureEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEntity
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureReading
import java.time.LocalDate

/**
 * Room entity to domain model, for measures. One direction only, exactly as `HabitMapper` is.
 *
 * There is no `Measure.toEntity()`, and here the argument is stronger than it is for a habit: a
 * habit at least has a colour the user can change, so `HabitDao.setColor` exists. Nothing above the
 * repository can change anything about a measure — the unit is edited on the mapping screen and the
 * readings come from Notion — so a reverse mapper would exist purely to be misused.
 *
 * ⭐ It reuses [splitEmojiAndName], which is the point of that function being positional rather than
 * a character-class filter: `⚖️ Weight` splits the same way `📖 Read` does, and neither has its
 * bytes touched (`docs/notion-schema.md` §2.3).
 */
fun MeasureEntity.toDomain(): Measure {
    val (emoji, name) = splitEmojiAndName(notionPropertyName)
    return Measure(
        id = MeasureId(id),
        name = name,
        emoji = emoji,
        unit = unit,
        color = color.toStoredHabitColor(),
    )
}

@JvmName("measuresToDomain")
fun List<MeasureEntity>.toDomain(): List<Measure> = map { it.toDomain() }

fun MeasurementEntity.toDomain(): MeasureReading =
    MeasureReading(
        measureId = MeasureId(measureId),
        date = LocalDate.ofEpochDay(epochDay),
        value = value,
    )

@JvmName("readingsToDomain")
fun List<MeasurementEntity>.toDomain(): List<MeasureReading> = map { it.toDomain() }
