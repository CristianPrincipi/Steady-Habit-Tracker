package com.cristianprincipi.habits.data.repository

import com.cristianprincipi.habits.core.widget.WidgetRefresher
import com.cristianprincipi.habits.core.widget.refreshQuietly
import com.cristianprincipi.habits.data.local.TransactionRunner
import com.cristianprincipi.habits.data.local.dao.DayDao
import com.cristianprincipi.habits.data.local.dao.MeasureDao
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEditEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.local.mapper.toDomain
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureReading
import com.cristianprincipi.habits.domain.repository.MeasureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MeasureRepository] over Room — `HabitRepositoryImpl`'s twin (M15.4).
 *
 * ⭐ **It gained a transaction runner and a clock at M16.2, and that is the shape the old KDoc said to
 * stop and look at** — so: [setReading] writes *data* now. A typed number has to land as a day row
 * plus a reading plus an edit row, together, and the edit row carries a time for last-write-wins.
 * Exactly the reasons `HabitRepositoryImpl` has both.
 *
 * [setColor] is still app-local decoration, and the `WidgetRefresher` is there for both writes: the
 * widget draws the curve, the badge and the average, so a write that did not reach the home screen
 * would leave two answers on one device.
 *
 * **Room only, never the network** (`CLAUDE.md` §1), and the entities stop here as they do
 * everywhere: no `MeasureEntity` or `MeasurementEntity` appears above this class.
 */
@Singleton
class MeasureRepositoryImpl
    @Inject
    constructor(
        private val measureDao: MeasureDao,
        private val dayDao: DayDao,
        private val transactionRunner: TransactionRunner,
        private val clock: Clock,
        private val widgets: WidgetRefresher,
    ) : MeasureRepository {
        override fun observeMeasures(): Flow<List<Measure>> = measureDao.observeActive().map { it.toDomain() }

        override fun observeReadings(
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<MeasureReading>> =
            measureDao
                .observeBetween(from = from.toEpochDay(), to = to.toEpochDay())
                .map { it.toDomain() }

        /**
         * Writes the reading and the edit that says Notion is owed it, in one transaction.
         *
         * 1. **Nothing owed and nothing changed → nothing written.** Saving the value already held
         *    would otherwise queue a page update that changes nothing — the one place this differs
         *    from a check-off, whose redundant push costs the same and is not worth a branch there.
         * 2. **The day row first**, for the foreign keys. A day this device has never seen is
         *    created `SYNCED` with no page: its *checkboxes* owe nothing, and the push finds the day
         *    through its edit row instead (ADR-156). ⚠ An existing day is not touched at all — not
         *    its state, not its `localUpdatedAtEpochMillis`. Stamping either would make this number
         *    defend the phone's checkboxes in a conflict nobody had.
         * 3. **The base is taken once.** The first edit records what Notion held — which, with no
         *    edit owed, is exactly the reading in Room. A second edit before the push keeps that base,
         *    because Notion still holds it.
         * 4. **Clearing deletes the reading and keeps the edit**, which is how the push knows to send
         *    an empty number rather than nothing. Never a zero (`MeasurementEntity`).
         */
        override suspend fun setReading(
            measureId: MeasureId,
            date: LocalDate,
            value: Double?,
        ) {
            require(value == null || value.isFinite()) { "A reading must be a finite number, got $value" }
            val id = measureId.value
            val epochDay = date.toEpochDay()
            val wrote =
                transactionRunner.withTransaction {
                    val held = measureDao.findReading(id, epochDay)?.value
                    val owed = measureDao.findEdit(id, epochDay)
                    if (owed == null && held == value) return@withTransaction false

                    if (dayDao.find(epochDay) == null) {
                        dayDao.upsert(
                            DayEntity(
                                epochDay = epochDay,
                                notionPageId = null,
                                localUpdatedAtEpochMillis = null,
                                remoteLastEditedAtEpochMillis = null,
                                syncState = SyncState.SYNCED,
                            ),
                        )
                    }
                    measureDao.upsertEdit(
                        MeasurementEditEntity(
                            measureId = id,
                            epochDay = epochDay,
                            baseValue = if (owed != null) owed.baseValue else held,
                            editedAtEpochMillis = clock.millis(),
                        ),
                    )
                    if (value == null) {
                        measureDao.deleteReading(measureId = id, epochDay = epochDay)
                    } else {
                        measureDao.upsertReading(MeasurementEntity(measureId = id, epochDay = epochDay, value = value))
                    }
                    true
                }
            // After the transaction, for `HabitRepositoryImpl.setCompleted`'s reason: a widget must
            // be told about a write that has landed, never one that could still roll back.
            if (wrote) widgets.refreshQuietly()
        }

        override suspend fun setColor(
            measureId: MeasureId,
            color: HabitColor,
        ) {
            measureDao.setColor(measureId = measureId.value, color = color.name)
            widgets.refreshQuietly()
        }
    }
