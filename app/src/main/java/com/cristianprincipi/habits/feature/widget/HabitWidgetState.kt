package com.cristianprincipi.habits.feature.widget

import com.cristianprincipi.habits.core.designsystem.component.DayStatus
import com.cristianprincipi.habits.domain.model.DayState
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.model.HabitPeriod
import com.cristianprincipi.habits.domain.model.PeriodCell
import java.time.LocalDate

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
     * @param today the corner square (M15.11), or `null` when the period has no today to offer — in
     *   which case no square is drawn rather than one that writes to a day nobody is looking at.
     */
    data class Content(
        val name: String,
        val emoji: String,
        val color: HabitColor,
        val completedThisWeek: Int,
        val daysThisWeek: Int,
        val year: List<DayStatus>,
        val today: TodayCheck?,
    ) : HabitWidgetState
}

/**
 * The square in the widget's corner — M15.11: what it draws, and what a tap on it writes.
 *
 * ⭐ **The tap carries what the square showed, not "flip it"** — `ToggleCompletion`'s own rule. The
 * widget is a picture that can be minutes or hours old, and a flip applied to a stale picture undoes
 * the very check-off the user can see. Sending the wanted value means a tap on an empty square always
 * means *done*, whatever happened since it was drawn.
 *
 * @param date the day the square was drawn for. Carried to the tap so a square drawn before
 *   midnight cannot write to the day after it — see `WidgetCheckOff`.
 */
internal data class TodayCheck(
    val habitId: HabitId,
    val date: LocalDate,
    val completed: Boolean,
)

/**
 * Today's cell of this period as a [TodayCheck], read off the **same cells the grid draws**, so the
 * square and the grid's last dot cannot disagree about whether today is done.
 */
internal fun HabitPeriod.todayCheck(): TodayCheck? =
    (cells.getOrNull(todayIndex) as? PeriodCell.Day)?.let { day ->
        TodayCheck(habitId = habit.id, date = day.date, completed = day.state == DayState.COMPLETED)
    }
