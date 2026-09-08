package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.core.designsystem.component.DayStatus
import com.cristianprincipi.habits.domain.model.HabitColor

/**
 * What one home-screen widget shows — resolved before any Glance composition runs.
 *
 * ⭐ **A sealed interface rather than a nullable data class, because two of the three states are
 * things the user has to be told.** A widget is the one surface in this app that outlives the
 * screen that made it: it sits on a home screen for months while the habit behind it can be
 * un-ticked in the mapping, or the app disconnected from Notion entirely. A widget that answered
 * either of those with an empty grid would look like a habit with no history rather than like a
 * widget with no habit.
 */
internal sealed interface HabitWidgetState {
    /**
     * Placed but never configured — the configuration activity was cancelled, or the widget was
     * restored from a backup that did not carry its state.
     */
    data object Unconfigured : HabitWidgetState

    /**
     * Configured, but the habit is gone: un-ticked in the mapping (ADR-081 keeps the row and marks
     * it inactive) or wiped by a disconnect.
     */
    data object HabitMissing : HabitWidgetState

    /**
     * @param year the trailing year, column-major and Monday-aligned, exactly as `YearDotGrid`
     *   receives it. The widget shows the **last** N columns of it; see `lastWeeks`.
     * @param completedThisWeek and [daysThisWeek] are the user's chosen subtitle — *"N of 7 this
     *   week"* — and come from `HabitPeriod.completedCount`, the same count the Week card prints.
     */
    data class Content(
        val name: String,
        val emoji: String,
        val color: HabitColor,
        val completedThisWeek: Int,
        val daysThisWeek: Int,
        val year: List<DayStatus>,
    ) : HabitWidgetState
}
