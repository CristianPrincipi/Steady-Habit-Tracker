package com.cristianprincipi.habits.feature.widget

import androidx.compose.ui.geometry.Offset
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.MeasureId

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
     * ⚠⚠ **There is no emoji here, and its absence is load-bearing (M15.6g).** [HabitWidgetState]
     * carries one because a habit's Notion property is named with a leading emoji; a number's is
     * named plainly — `Weight`, `Sleep` — so this badge drew an empty circle. The user's ruling was
     * to put the [average] in it instead and take the figure off the line below, which makes the
     * badge the widget's **only** printing of the number. A measure that later acquired an emoji in
     * Notion must therefore still not draw one here: doing so would not add an icon, it would delete
     * the value. The field is gone rather than ignored so that cannot come back as a plausible
     * one-line change.
     *
     * @param average the mean over the days that had a reading, or **null when there were none** —
     *   never `0.0`. It moves together with [points] being empty, because both come from
     *   `MeasureSeries`, where the pairing is an invariant rather than a convention.
     * @param unit what to write after the number, or blank. The user typed it; nothing derives it.
     * @param points one per reading, `x` being how far through the window the reading falls (`0f`
     *   at its start, `1f` at today) and `y` the raw value. ⚠ **The x is already the reading's real
     *   position in time** — see `MeasureSeries.positionOf`. A drawing that spaced these evenly
     *   would turn four weigh-ins into a month of steady change.
     * @param id what the corner square's dialog writes to (M16.3).
     * @param today today's reading, or null when there is none yet — which is what draws the corner
     *   square as an outline with a "+" rather than filled. Read off the same series as [points], so
     *   the square and the line's last point cannot disagree about today.
     */
    data class Content(
        val name: String,
        val color: HabitColor,
        val average: Double?,
        val unit: String,
        val points: List<Offset>,
        val id: MeasureId,
        val today: Double?,
    ) : MeasureWidgetState
}
