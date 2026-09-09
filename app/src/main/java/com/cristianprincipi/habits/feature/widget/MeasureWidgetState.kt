package com.cristianprincipi.habits.feature.widget

import androidx.compose.ui.geometry.Offset
import com.cristianprincipi.habits.domain.model.HabitColor

/**
 * What one measure widget shows — resolved before any Glance composition runs (M15.6d).
 *
 * [HabitWidgetState]'s twin, with the same three states and for the same reason: a widget outlives
 * the screen that made it, so "never configured" and "the thing it was pointed at is gone" are both
 * sentences the user has to be told rather than an empty box.
 *
 * ⚠ **A fourth state was considered and deliberately is not here.** "Configured, present, but no
 * readings in the window" is not a broken widget — it is a user who has not weighed themselves this
 * month, which is ordinary. It lives inside [Content] as an empty [points] and a null [average],
 * because those two are the honest answer rather than a fault to report.
 */
internal sealed interface MeasureWidgetState {
    /**
     * Placed but never configured — the configuration activity was cancelled, or the widget was
     * restored from a backup that did not carry its state.
     */
    data object Unconfigured : MeasureWidgetState

    /**
     * Configured, but the number is gone: un-ticked in the mapping (ADR-081 keeps the row and marks
     * it inactive) or wiped by a disconnect.
     */
    data object MeasureMissing : MeasureWidgetState

    /**
     * @param average the mean over the days that had a reading, or **null when there were none** —
     *   never `0.0`. It moves together with [points] being empty, because both come from
     *   `MeasureSeries`, where the pairing is an invariant rather than a convention.
     * @param unit what to write after the number, or blank. The user typed it; nothing derives it.
     * @param points one per reading, `x` being how far through the window the reading falls (`0f`
     *   at its start, `1f` at today) and `y` the raw value. ⚠ **The x is already the reading's real
     *   position in time** — see `MeasureSeries.positionOf`. A drawing that spaced these evenly
     *   would turn four weigh-ins into a month of steady change.
     */
    data class Content(
        val name: String,
        val emoji: String,
        val color: HabitColor,
        val average: Double?,
        val unit: String,
        val points: List<Offset>,
    ) : MeasureWidgetState
}
