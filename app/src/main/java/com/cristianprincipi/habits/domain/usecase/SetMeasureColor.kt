package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.repository.MeasureRepository
import javax.inject.Inject

/**
 * Set a measure's colour — **the only edit this app can make to a measure**, and the user's own
 * request after seeing the widget (M15.6f).
 *
 * [SetHabitColor]'s twin, and the argument is the same one: the readings, the name and the very
 * existence of a number column are Notion's, and this app holds no code path that writes any of
 * them. A colour is not in the schema at all — the user's database has no colour property — so it
 * is stored locally, carried across every sync by `MeasureImporter`, and never pushed.
 *
 * ⚠ **Green and purple are each used twice in this app**, once by a habit and once by a measure,
 * which `Measure`'s KDoc records as the user's decision rather than a collision. Letting them choose
 * makes that theirs to arrange, and the palette is deliberately the same one: a second palette would
 * be a second design to keep in step.
 */
class SetMeasureColor
    @Inject
    constructor(
        private val measures: MeasureRepository,
    ) {
        suspend operator fun invoke(
            measureId: MeasureId,
            color: HabitColor,
        ) {
            measures.setColor(measureId = measureId, color = color)
        }
    }
