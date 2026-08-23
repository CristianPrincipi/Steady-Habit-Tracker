package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.data.local.TransactionRunner
import com.cristianprincipi.habits.data.local.dao.CompletionDao
import com.cristianprincipi.habits.data.local.dao.DayDao
import com.cristianprincipi.habits.data.local.dao.HabitDao
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPageDto
import com.cristianprincipi.habits.data.remote.notion.mapper.MappedProperty
import com.cristianprincipi.habits.data.remote.notion.mapper.NotionPropertyValue
import com.cristianprincipi.habits.data.remote.notion.mapper.property
import com.cristianprincipi.habits.domain.model.HabitColor
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The mapped habits, paired with the local row each one turned into.
 *
 * Both halves are needed everywhere in a sync and neither is much use alone: Notion speaks in
 * **property ids** and the local tables speak in **habit row ids**, and this is the only place the
 * two are held together. Passing them separately is how a function ends up looking a habit up in the
 * wrong dictionary.
 */
class MappedHabits(
    /** In the mapping's order, carrying each property's current name. */
    val properties: List<MappedProperty>,
    private val localIdByPropertyId: Map<String, String>,
) {
    val localIds: Collection<String> get() = localIdByPropertyId.values

    fun localIdOf(property: MappedProperty): String? = localIdByPropertyId[property.id.value]

    /**
     * The habits this page records as done, as local ids.
     *
     * Read by **property id** through [property], never by the page's map key — that key is the
     * user-editable name, and depending on it is what ADR-038 chose the id to avoid.
     */
    fun checkedOn(page: NotionPageDto): Set<String> =
        properties
            .filter { (page.property(it.id.value) as? NotionPropertyValue.Checkbox)?.checked == true }
            .mapNotNull(::localIdOf)
            .toSet()
}

/**
 * Keeps the local habit set equal to the mapped part of the Notion schema.
 *
 * Separate from [SyncEngine] because it answers a different question. The engine reconciles *days*
 * against *pages*; this owns the rule that **a habit is a checkbox column** — which is a schema
 * fact, refreshed wholesale on every sync and never pushed back, because ADR-010 leaves this app no
 * code path that can write a schema.
 */
@Singleton
class HabitImporter
    @Inject
    constructor(
        private val habitDao: HabitDao,
        private val dayDao: DayDao,
        private val completionDao: CompletionDao,
        private val transactionRunner: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Brings the habit table in line with the mapped schema.
         *
         * **Three things are carried forward from an existing row, and each has a reason.** The
         * local [HabitEntity.id], because every completion in the database points at it. The
         * [HabitEntity.color], because it is app-local and exists nowhere else — `HabitDao.upsert`
         * writes every column, so a fresh entity would silently repaint the user's habits
         * (`docs/notion-schema.md` §3). The start date, because [refreshStartDates] owns it.
         *
         * A **renamed** property arrives as a new [MappedProperty.name] on an unchanged id and is
         * simply written down (ADR-038). A habit the mapping no longer names goes inactive rather
         * than being deleted: deleting a Notion property destroys its column and every value in it,
         * so these rows may be the only surviving copy (`docs/notion-schema.md` §4.1).
         */
        suspend fun import(habits: List<MappedProperty>): MappedHabits {
            val existing = habitDao.findAll().associateBy(HabitEntity::notionPropertyId)
            val today = LocalDate.now(clock).toEpochDay()

            val entities =
                habits.mapIndexed { index, mapped ->
                    val previous = existing[mapped.id.value]
                    HabitEntity(
                        id = previous?.id ?: UUID.randomUUID().toString(),
                        notionPropertyId = mapped.id.value,
                        notionPropertyName = mapped.name,
                        color = previous?.color ?: defaultColorAt(index),
                        // Provisional, and corrected by refreshStartDates once days exist. Today
                        // rather than the epoch: an uncorrected zero would render as 1970 and turn
                        // every completion rate into a rounding error.
                        startDateEpochDay = previous?.startDateEpochDay ?: today,
                        isActive = true,
                        sortOrder = index,
                    )
                }

            transactionRunner.withTransaction {
                habitDao.upsert(entities)
                habitDao.deactivateAllExcept(entities.map(HabitEntity::id))
            }
            return MappedHabits(
                properties = habits,
                localIdByPropertyId = entities.associate { it.notionPropertyId to it.id },
            )
        }

        /**
         * Dates every habit from its **first check-off**, falling back to the oldest day on record.
         *
         * Notion holds no start date (`docs/notion-schema.md` §3) and the data cannot supply one: an
         * old page carries `false` for a habit that did not exist yet and for one that simply was
         * not done, identically. The first completion is the earliest day a habit is *known* to have
         * existed — ADR-046 records why that beats dating every habit from the oldest page, which
         * would make a habit added last week look like it had missed a year.
         *
         * Called **after** the pull: a habit imported moments ago has nothing to infer from yet.
         */
        suspend fun refreshStartDates(habits: MappedHabits) {
            val oldestDay = dayDao.earliestDay()
            habits.localIds.forEach { habitId ->
                val inferred = completionDao.earliestDayFor(habitId) ?: oldestDay
                if (inferred != null) habitDao.setStartDate(habitId = habitId, epochDay = inferred)
            }
        }

        /**
         * The palette, cycled, for a habit seen for the first time.
         *
         * Four colours and usually five habits, so two will match — which is the design's own
         * behaviour and is the user's to change (M7.5). The alternative, everything in blue, looks
         * like a bug.
         */
        private fun defaultColorAt(index: Int): String = HabitColor.entries[index % HabitColor.entries.size].name
    }
