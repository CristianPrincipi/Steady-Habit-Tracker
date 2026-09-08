package com.cristianprincipi.habits.data.local.mapper

import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import java.time.LocalDate

/**
 * Room entity to domain model. One direction only.
 *
 * There is no `Habit.toEntity()` and there should not be. Nothing in the app turns a domain habit
 * back into a row: the habit set arrives from the Notion schema (M4), and the single field the user
 * can change is colour, which `HabitDao.setColor` updates by id. A reverse mapper would exist purely
 * to be misused — the first caller to round-trip a `Habit` through it would silently overwrite
 * `notionPropertyId` and `sortOrder` with whatever the domain happened to be carrying.
 */
fun HabitEntity.toDomain(): Habit {
    val (emoji, name) = splitEmojiAndName(notionPropertyName)
    return Habit(
        id = HabitId(id),
        name = name,
        emoji = emoji,
        color = color.toStoredHabitColor(),
        startDate = LocalDate.ofEpochDay(startDateEpochDay),
    )
}

fun List<HabitEntity>.toDomain(): List<Habit> = map { it.toDomain() }

/**
 * Reads a stored [HabitColor] name, falling back rather than throwing.
 *
 * `internal` rather than private since M15.4: `MeasureMapper` reads the same column from its own
 * table, and two copies of "what does an unrecognised colour name mean" is two answers waiting to
 * disagree.
 *
 * The column holds a name this app wrote (`HabitColor.name`), so an unknown value means the enum
 * changed under existing data. Crashing every screen over a colour would be a poor trade for a
 * field that is decoration; the habit and its whole history still render, in blue.
 */
internal fun String.toStoredHabitColor(): HabitColor =
    HabitColor.entries.firstOrNull { it.name == this } ?: HabitColor.BLUE

/**
 * Splits a Notion property name into its leading emoji and the rest.
 *
 * The user's habits are properties called things like `"<emoji> Read"`, and the design shows those
 * two parts in different places: the emoji on a tinted circle, the name beside it.
 *
 * **The split is positional, never a character-class filter.** `docs/notion-schema.md` §2.3 is
 * emphatic about this: one of the five real property names carries a `U+FE0F` variation selector and
 * the others do not, so anything that "strips non-alphanumerics" or normalises would change bytes
 * that every API call for that property depends on. This takes the text before the first space, and
 * touches nothing else.
 *
 * A name with no leading emoji — a plain `"Read"`, or a two-word `"Read more"` — keeps all of itself
 * and gets no emoji. The test for a leading emoji is that the first word contains no letter or
 * digit, which is true of every emoji sequence and false of every word.
 *
 * @return the emoji (empty when there is none) and the display name.
 */
internal fun splitEmojiAndName(propertyName: String): Pair<String, String> {
    val firstSpace = propertyName.indexOf(' ')
    if (firstSpace <= 0) return "" to propertyName

    val head = propertyName.substring(0, firstSpace)
    val tail = propertyName.substring(firstSpace + 1).trim()

    val headIsAWord = head.any { it.isLetterOrDigit() }
    return if (headIsAWord || tail.isEmpty()) "" to propertyName else head to tail
}
