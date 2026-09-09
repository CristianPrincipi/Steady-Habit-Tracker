package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.domain.usecase.GetMeasureSeries
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * How the measure widget reaches the app's own use cases — [HabitWidgetEntryPoint]'s twin.
 *
 * ⚠ **Its own entry point rather than two more methods on the habit one.** They are not shared
 * plumbing: a `GlanceAppWidget` reaches for exactly what it draws, and a habit widget that could
 * see `GetMeasureSeries` would be one refactor away from drawing a body weight as a dot grid — the
 * mistake `Measure`'s KDoc keeps the two models apart to prevent.
 *
 * ⭐ **No `Clock` here, where the habit one needs one.** The habit widget resolves "this week"
 * itself; the measure window belongs to [GetMeasureSeries], which reads the clock and hands the
 * dates back on the series. There is nothing left for the widget to have an opinion about.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MeasureWidgetEntryPoint {
    fun measureSeries(): GetMeasureSeries
}
