package com.cristianprincipi.habits.feature.widget

import android.content.Context
import com.cristianprincipi.habits.core.ui.toDayStatus
import com.cristianprincipi.habits.domain.model.HabitId
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * What one widget should show, as a [Flow] that keeps answering for as long as anyone collects it.
 *
 * ⚠⚠ **This used to be a one-shot `first()`, and its KDoc argued for that: *"a widget is a
 * snapshot; `RemoteViews` is pushed once per update and there is nothing to stay subscribed to."*
 * The second half of that sentence is wrong, and M15.6c is where it cost something** (ADR-130).
 * A Glance session is a live composition for the ~45 seconds it stays open, and a `Flow` collected
 * inside it does push new `RemoteViews`. What is true is only that the session is bounded — so this
 * is not a subscription held for the life of the process, it is one held for exactly as long as
 * Glance is already keeping a composition alive.
 *
 * ⭐ **Why that matters and a second `updateAll` would not have fixed it**: `provideGlance` runs
 * once per session (ADR-129 §16), so a widget told to update while its session is alive re-pushes
 * whatever it resolved at the start. Check off one habit and every widget updates; check off a
 * second within 45 seconds — which is simply what checking off a list looks like — and the sessions
 * are still open, so nothing moves. **Reacting to Room is what makes the second check-off work.**
 */
internal fun habitWidgetStateFlow(
    context: Context,
    habitId: String?,
): Flow<HabitWidgetState> {
    if (habitId.isNullOrBlank()) return flowOf(HabitWidgetState.Unconfigured)
    val id = HabitId(habitId)
    val deps =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            HabitWidgetEntryPoint::class.java,
        )
    return combine(
        deps.habitsForYear()(),
        // ⚠ The week is resolved from a date read ONCE, when the flow is built. A session lives
        // about 45 seconds, so it cannot outlive a midnight it started before by enough to matter,
        // and the alternative — a ticking clock per widget — is machinery for a case that ends
        // with the session.
        deps.habitsForWeek()(LocalDate.now(deps.clock())),
    ) { years, weeks ->
        val year = years.firstOrNull { it.period.habit.id == id }
        if (year == null) {
            // ⚠ **"Configured but gone" stays a different answer from "never configured"** — see
            // [HabitWidgetState]. Collapsing them would let a widget the user never finished
            // setting up claim their habit had been deleted.
            HabitWidgetState.HabitMissing
        } else {
            // The week is looked up separately and tolerated as absent. It cannot normally be —
            // both use cases read the same habit list — but a widget that lost its subtitle should
            // still draw its grid rather than claim the habit is gone.
            val week = weeks.firstOrNull { it.habit.id == id }
            HabitWidgetState.Content(
                name = year.period.habit.name,
                emoji = year.period.habit.emoji,
                color = year.period.habit.color,
                completedThisWeek = week?.completedCount ?: 0,
                daysThisWeek = week?.cells?.size ?: DAYS_IN_WEEK,
                year = year.period.cells.map { cell -> cell.toDayStatus() },
                today = year.period.todayCheck(),
            )
        }
    }
}

/**
 * The same answer, resolved once.
 *
 * ⭐ **Kept because the FIRST frame must not be a guess.** `provideGlance` resolves this before
 * `provideContent` so the widget's opening render is already correct; everything after it comes
 * from the flow above. Rendering empty and correcting a frame later is, on a widget, a visible
 * flash of the wrong answer.
 */
internal suspend fun loadHabitWidgetState(
    context: Context,
    habitId: String?,
): HabitWidgetState = habitWidgetStateFlow(context, habitId).first()

private const val DAYS_IN_WEEK = 7
