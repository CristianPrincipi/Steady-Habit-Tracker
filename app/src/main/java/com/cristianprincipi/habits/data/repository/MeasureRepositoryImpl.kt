package com.cristianprincipi.habits.data.repository

import com.cristianprincipi.habits.data.local.dao.MeasureDao
import com.cristianprincipi.habits.data.local.mapper.toDomain
import com.cristianprincipi.habits.domain.model.Measure
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
 * **The size difference is the feature.** That class has a transaction runner and a clock because it
 * writes: a check-off has to land as a day plus a completion plus a sync state, together. This one
 * has neither, because there is nothing here that writes. Two `map`s over two queries is the whole
 * class, and if it ever grows a third dependency that is worth stopping to look at.
 *
 * **Room only, never the network** (`CLAUDE.md` §1), and the entities stop here as they do
 * everywhere: no `MeasureEntity` or `MeasurementEntity` appears above this class.
 */
@Singleton
class MeasureRepositoryImpl
    @Inject
    constructor(
        private val measureDao: MeasureDao,
    ) : MeasureRepository {
        override fun observeMeasures(): Flow<List<Measure>> = measureDao.observeActive().map { it.toDomain() }

        override fun observeReadings(
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<MeasureReading>> =
            measureDao
                .observeBetween(from = from.toEpochDay(), to = to.toEpochDay())
                .map { it.toDomain() }
    }
