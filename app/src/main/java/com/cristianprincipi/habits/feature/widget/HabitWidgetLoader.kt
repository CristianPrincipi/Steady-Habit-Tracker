package com.cristianprincipi.habits.feature.widget

import android.content.Context
import com.cristianprincipi.habits.core.ui.toDayStatus
import com.cristianprincipi.habits.domain.model.HabitId
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Resolves what one widget should show, off the main thread and before any composition.
 *
 * ⚠ **`first()` rather than collecting.** A widget is a snapshot: `RemoteViews` is pushed once per
 * update and there is nothing to stay subscribed to. What re-reads this is an explicit update after
 * a check-off or a sync (M15.6c), not a `Flow` held open by a process Android will kill.
 *
 * @param habitId the id stored against this widget by the configuration activity, or `null` when
 *   there is none.
 */
internal suspend fun loadHabitWidgetState(
    context: Context,
    habitId: String?,
): HabitWidgetState {
    if (habitId.isNullOrBlank()) return HabitWidgetState.Unconfigured
    return contentFor(context, HabitId(habitId)) ?: HabitWidgetState.HabitMissing
}

/**
 * The habit's year and week, or `null` when the habit is no longer there.
 *
 * ⚠ **Separate from [loadHabitWidgetState] so that "not configured" and "configured but gone" stay
 * two different answers.** Collapsing them would let a widget the user never finished setting up
 * claim their habit had been deleted.
 */
private suspend fun contentFor(
    context: Context,
    id: HabitId,
): HabitWidgetState.Content? {
    val deps =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            HabitWidgetEntryPoint::class.java,
        )
    val year = deps.habitsForYear()().first().firstOrNull { it.period.habit.id == id } ?: return null

    // The week is looked up separately and tolerated as absent. It cannot normally be — both use
    // cases read the same habit list — but a widget that lost its subtitle should still draw its
    // grid rather than claim the habit is gone.
    val week =
        deps
            .habitsForWeek()(LocalDate.now(deps.clock()))
            .first()
            .firstOrNull { it.habit.id == id }

    return HabitWidgetState.Content(
        name = year.period.habit.name,
        emoji = year.period.habit.emoji,
        color = year.period.habit.color,
        completedThisWeek = week?.completedCount ?: 0,
        daysThisWeek = week?.cells?.size ?: DAYS_IN_WEEK,
        year = year.period.cells.map { cell -> cell.toDayStatus() },
    )
}

private const val DAYS_IN_WEEK = 7
