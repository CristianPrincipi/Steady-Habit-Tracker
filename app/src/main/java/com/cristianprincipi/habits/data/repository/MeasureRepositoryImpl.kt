package com.cristianprincipi.habits.data.repository

import com.cristianprincipi.habits.core.widget.WidgetRefresher
import com.cristianprincipi.habits.core.widget.refreshQuietly
import com.cristianprincipi.habits.data.local.dao.MeasureDao
import com.cristianprincipi.habits.data.local.mapper.toDomain
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureReading
import com.cristianprincipi.habits.domain.repository.MeasureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MeasureRepository] over Room — `HabitRepositoryImpl`'s twin, and much smaller (M15.4).
 *
 * **The size difference is still the feature.** That class has a transaction runner and a clock
 * because it writes *data*: a check-off has to land as a day plus a completion plus a sync state,
 * together. This one has neither, because nothing here writes data.
 *
 * ⚠ **It gained a second dependency at M15.6f and the old KDoc said to stop and look at that** — so:
 * [setColor] is app-local decoration, not a reading, and the `WidgetRefresher` is there for the
 * reason `HabitRepositoryImpl.setColor` has one. The widget draws its curve and its badge in this
 * colour, so a recolour that did not reach the home screen would leave two answers on one device.
 * ⭐ There is still no transaction runner and no clock, which is the shape that matters: a colour is
 * one row and one column, with nothing to keep consistent with anything else.
 *
 * **Room only, never the network** (`CLAUDE.md` §1), and the entities stop here as they do
 * everywhere: no `MeasureEntity` or `MeasurementEntity` appears above this class.
 */
@Singleton
class MeasureRepositoryImpl
    @Inject
    constructor(
        private val measureDao: MeasureDao,
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

        override suspend fun setColor(
            measureId: MeasureId,
            color: HabitColor,
        ) {
            measureDao.setColor(measureId = measureId.value, color = color.name)
            widgets.refreshQuietly()
        }
    }
