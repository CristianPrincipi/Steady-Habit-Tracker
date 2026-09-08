package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.data.local.dao.MeasureDao
import com.cristianprincipi.habits.data.local.entity.MeasureEntity
import com.cristianprincipi.habits.data.remote.notion.mapper.MappedMeasure
import com.cristianprincipi.habits.domain.model.HabitColor
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The mapped measures, paired with the local row each one turned into.
 *
 * [MappedHabits]' twin, and separate for the reason the tables are separate: Notion speaks in
 * **property ids** and the local tables speak in **row ids**, and holding a measure's pair in the
 * habit dictionary is how a lookup ends up returning a habit for a number.
 */
class MappedMeasures(
    /** In the mapping's order, carrying each property's current name and the user's unit. */
    val properties: List<MappedMeasure>,
    private val localIdByPropertyId: Map<String, String>,
) {
    val localIds: Collection<String> get() = localIdByPropertyId.values

    fun localIdOf(measure: MappedMeasure): String? = localIdByPropertyId[measure.id.value]
}

/**
 * Keeps the local measure set equal to the mapped number columns of the Notion schema (M15.4).
 *
 * [HabitImporter]'s twin, and a separate class rather than two more methods on it, because the rule
 * each one owns is different: that one owns *a habit is a checkbox column*, this owns *a measure is
 * a number column the user asked to average*. Both are schema facts, refreshed wholesale on every
 * sync and never pushed back — ADR-010 leaves this app no code path that can write a schema, and
 * M15.4 leaves it none that can write a number either.
 */
@Singleton
class MeasureImporter
    @Inject
    constructor(
        private val measureDao: MeasureDao,
    ) {
        /**
         * Brings the measure table in line with the mapped schema.
         *
         * **Three things are carried forward from an existing row**, and the list differs from
         * [HabitImporter.import]'s by one. The local [MeasureEntity.id], because every reading in
         * the database points at it. The [MeasureEntity.color], because it is app-local and exists
         * nowhere else. And — new here — the [MeasureEntity.unit], for very nearly the same reason:
         * the user typed it, nothing can derive it (`NotionPropertySchemaDto` carries no number
         * format, and `CLAUDE.md` §3.4 forbids reading "kg" off a column *named* `Weight`), and
         * `upsert` writes every column, so a fresh entity would silently blank it.
         *
         * ⚠ **The unit still follows the mapping when the mapping has one**, which is the
         * difference from the colour. A colour is only ever chosen in the app, so Notion has no
         * opinion and `previous` always wins; a unit is typed on the mapping screen, so a user who
         * has just changed `kg` to `lb` there must see `lb`. `previous` is the fallback, not the
         * winner.
         *
         * A **renamed** property arrives as a new name on an unchanged id and is written down
         * (ADR-038). A measure the mapping no longer names goes inactive rather than being deleted:
         * deleting a Notion property destroys its column and every value in it, so these rows may
         * be the only surviving copy of the readings.
         */
        suspend fun import(measures: List<MappedMeasure>): MappedMeasures {
            val existing = measureDao.findAll().associateBy(MeasureEntity::notionPropertyId)

            val entities =
                measures.mapIndexed { index, mapped ->
                    val previous = existing[mapped.id.value]
                    MeasureEntity(
                        id = previous?.id ?: UUID.randomUUID().toString(),
                        notionPropertyId = mapped.id.value,
                        notionPropertyName = mapped.name,
                        unit = mapped.unit.ifBlank { previous?.unit.orEmpty() },
                        color = previous?.color ?: defaultColorAt(index),
                        isActive = true,
                        sortOrder = index,
                    )
                }

            measureDao.upsert(entities)
            measureDao.deactivateAllExcept(entities.map(MeasureEntity::id))
            return MappedMeasures(
                properties = measures,
                localIdByPropertyId = entities.associate { it.notionPropertyId to it.id },
            )
        }

        /**
         * The palette, cycled, for a measure seen for the first time.
         *
         * ⭐ **Green then purple, which is the user's own assignment** (M15.2, ADR-121 §9): they
         * asked for `Weight` green and `Sleep` purple, and those are their first and second number
         * columns. Reproduced **positionally**, never by name — `CLAUDE.md` §3.4 forbids a codebase
         * that knows a column called `Weight` should be green.
         *
         * ⚠ **These two are each used twice in the app**, once by a habit and once by a measure,
         * and that is deliberate rather than a collision to fix: a measure never appears in the same
         * list, grid or widget as a habit. Recorded here so a future session does not "correct" it.
         *
         * The shared [HabitColor] enum rather than a parallel one, so there is a single palette to
         * keep accessible and a single set of contrast tests over it.
         */
        private fun defaultColorAt(index: Int): String = MEASURE_PALETTE[index % MEASURE_PALETTE.size].name

        private companion object {
            val MEASURE_PALETTE = listOf(HabitColor.GREEN, HabitColor.PURPLE)
        }
    }
