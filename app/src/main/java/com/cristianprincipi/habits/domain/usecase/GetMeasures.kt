package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.repository.MeasureRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The tracked numbers, plain — no readings, no window, no average.
 *
 * [GetHabits]' twin, with the same one caller and the same reason for existing: the manage screen
 * lists them and changes a colour, and a pass-through keeps it from holding a repository that can
 * also read every reading in the database.
 *
 * ⚠ **Only the mapped ones**, exactly as [GetHabits] returns only mapped habits (ADR-081). A number
 * column the user has unticked keeps its row and its readings and is hidden until it comes back —
 * so it is not listed here, and cannot be given a colour it would not show.
 */
class GetMeasures
    @Inject
    constructor(
        private val measures: MeasureRepository,
    ) {
        operator fun invoke(): Flow<List<Measure>> = measures.observeMeasures()
    }
