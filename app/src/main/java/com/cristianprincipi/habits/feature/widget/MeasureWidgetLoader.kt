package com.cristianprincipi.habits.feature.widget

import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.cristianprincipi.habits.domain.model.MeasureId
import com.cristianprincipi.habits.domain.model.MeasureSeries
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * What one measure widget should show, as a [Flow] that keeps answering while anyone collects it.
 *
 * `habitWidgetStateFlow`'s twin, and a flow for the same reason M15.6c made that one a flow: a
 * Glance session is a live composition for the ~45 seconds it stays open, so a widget with a session
 * open is only reached by reacting to Room. ⚠ **The `WidgetRefresher` half is what covers the rest
 * of the time, and it is not free here** — see `AppWidgetRefresher`, which had to widen before this
 * widget could be woken at all.
 *
 * ⭐ **A measure changes on a pull, a colour change, and — since M16.2 — a typed number**, from a Week
 * cell or from this widget's own square (M16.3). Every one lands in Room, which this flow observes,
 * and `MeasureRepository` repaints the widgets after each local write; a pull is covered by
 * `SyncOutcome.changedLocalData`. (This used to say a pull was the only writer, which was true until
 * numbers became writable.)
 */
internal fun measureWidgetStateFlow(
    context: Context,
    measureId: String?,
): Flow<MeasureWidgetState> {
    if (measureId.isNullOrBlank()) return flowOf(MeasureWidgetState.Unconfigured)
    val id = MeasureId(measureId)
    val deps =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            MeasureWidgetEntryPoint::class.java,
        )
    return deps.measureSeries()().map { all ->
        // ⚠ "Configured but gone" stays a different answer from "never configured" — see
        // [MeasureWidgetState]. Collapsing them would let a widget the user never finished setting
        // up claim their number had been deleted.
        all.firstOrNull { it.measure.id == id }?.toContent() ?: MeasureWidgetState.MeasureMissing
    }
}

/**
 * The same answer, resolved once.
 *
 * ⭐ **Kept because the FIRST frame must not be a guess**, exactly as `loadHabitWidgetState` is:
 * `provideGlance` resolves this before `provideContent`, so the widget's opening render is already
 * correct rather than a placeholder corrected a frame later.
 */
internal suspend fun loadMeasureWidgetState(
    context: Context,
    measureId: String?,
): MeasureWidgetState = measureWidgetStateFlow(context, measureId).first()

/**
 * The domain's series as the widget draws it.
 *
 * ⭐ **`positionOf` is asked rather than re-derived.** The series carries the window it was queried
 * over, so the x of every point is the day the reading actually happened measured against the span
 * the data actually came from. A loader that recomputed "days since thirty days ago" from its own
 * clock could disagree with the query by a day at midnight and nobody would see it.
 */
internal fun MeasureSeries.toContent(): MeasureWidgetState.Content =
    MeasureWidgetState.Content(
        name = measure.name,
        color = measure.color,
        average = average.value,
        unit = measure.unit,
        points = readings.map { reading -> Offset(positionOf(reading.date), reading.value.toFloat()) },
        id = measure.id,
        // The window ends today (`GetMeasureSeries`), so today is its last day — asked of the series,
        // not of a clock here, for `positionOf`'s reason above.
        today = readings.lastOrNull { reading -> reading.date == to }?.value,
    )
