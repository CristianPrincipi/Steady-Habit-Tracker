package com.cristianprincipi.habits.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.cristianprincipi.habits.core.widget.WidgetRefresher
import com.cristianprincipi.habits.core.widget.refreshQuietly
import com.cristianprincipi.habits.domain.model.HabitId
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import com.cristianprincipi.habits.domain.usecase.GetHabits
import com.cristianprincipi.habits.domain.usecase.ToggleCompletion
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * A check-off made on the home screen — M15.11, the widget's corner square.
 *
 * ⭐⭐ **The goal is to track a habit without opening the app or Notion, and that rules two things in.**
 * The tap writes through the same [ToggleCompletion] the Week pills use — Room first, every widget
 * repainted by the repository — and then asks for the sync that would otherwise wait for an app-open
 * that, by design, is not coming.
 *
 * ### ⚠⚠ It writes only what the user could see, and refuses the rest
 *
 * A widget is a picture that can be hours old, and a tap is aimed at the picture. Two pictures are
 * not worth writing from:
 *
 * - **A square drawn for a day that is over.** The midnight repaint makes this rare rather than
 *   impossible — an alarm can be late, and a battery saver can hold it. Writing the new day would
 *   apply a decision made about yesterday's square; writing yesterday is back-fill nobody asked for.
 *   So nothing is written, and the repaint shows today, which is what the user was looking for.
 * - **A habit that is not in the mapping.** A widget can outlive its habit (ADR-081), and the
 *   picker's sample square names no habit at all (`sampleTodayCheck`). A completion for either would
 *   be a row nothing draws and a push no property can carry.
 *
 * Either way **the widget is repainted**, so a refused tap still answers by showing the truth.
 *
 * ### ⚠ A failure is swallowed, and that is the only option a widget has
 *
 * There is no screen to put `ADR-120`'s sentence on, and an exception escaping a Glance action
 * callback would take the process down with it. Nothing is half-written either way: the repository
 * writes in one transaction. The repaint shows the square as it really is, which on a failed write
 * is unchanged — the tap visibly did nothing, and a second tap retries.
 */
internal class WidgetCheckOff
    @Inject
    constructor(
        private val habits: GetHabits,
        private val toggleCompletion: ToggleCompletion,
        private val syncStatus: SyncStatusRepository,
        private val widgets: WidgetRefresher,
        private val clock: Clock,
    ) {
        /**
         * @param drawnFor the day the tapped square showed.
         * @param completed the value wanted — the opposite of what the square showed, worked out by
         *   the widget when it drew the square, never by flipping what Room holds now.
         */
        suspend operator fun invoke(
            habitId: HabitId,
            drawnFor: LocalDate,
            completed: Boolean,
        ) {
            val written =
                try {
                    write(habitId, drawnFor, completed)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") throwable: Throwable,
                ) {
                    // See the class KDoc: there is nowhere to report this, and the repaint below says
                    // what is true.
                    false
                }
            if (written) {
                // The write already repainted every widget — `HabitRepositoryImpl.setCompleted`.
                syncStatus.requestWidgetCheckOffSync()
            } else {
                widgets.refreshQuietly()
            }
        }

        /** In this order, and each refusal stops the rest — see the class KDoc for both. */
        private suspend fun write(
            habitId: HabitId,
            drawnFor: LocalDate,
            completed: Boolean,
        ): Boolean =
            drawnFor == LocalDate.now(clock) &&
                habits().first().any { habit -> habit.id == habitId } &&
                toggleCompletion(habitId = habitId, date = drawnFor, completed = completed)
    }

/**
 * What a tap on [check]'s square sends: its habit, its day, and **the opposite of what it showed**.
 */
internal fun checkOffTodayAction(check: TodayCheck): Action =
    actionRunCallback<CheckOffTodayAction>(
        actionParametersOf(
            CheckOffTodayAction.HABIT_ID to check.habitId.value,
            CheckOffTodayAction.EPOCH_DAY to check.date.toEpochDay(),
            CheckOffTodayAction.COMPLETED to !check.completed,
        ),
    )

/**
 * Glance's way in for [WidgetCheckOff]: it runs in a broadcast, with the app closed.
 *
 * ⚠⚠ **Glance builds this class by reflection, from its name**, so it needs a no-argument
 * constructor and it must survive R8. Glance's own consumer rule keeps
 * `public class * extends ActionCallback` — and Kotlin's `internal` is `public` to the JVM, so this
 * is kept. ⚠ Making it `private` would compile, pass every debug test, and break only the minified
 * release, which is the M15.10 trap (ADR-148) in a different coat.
 */
internal class CheckOffTodayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val habitId = parameters[HABIT_ID]
        val epochDay = parameters[EPOCH_DAY]
        val completed = parameters[COMPLETED]
        // Only a tap built by checkOffTodayAction carries all three; anything else writes nothing.
        if (habitId == null || epochDay == null || completed == null) return
        EntryPointAccessors
            .fromApplication(context.applicationContext, HabitWidgetEntryPoint::class.java)
            .checkOff()(HabitId(habitId), LocalDate.ofEpochDay(epochDay), completed)
    }

    internal companion object {
        val HABIT_ID = ActionParameters.Key<String>("habit_id")
        val EPOCH_DAY = ActionParameters.Key<Long>("epoch_day")
        val COMPLETED = ActionParameters.Key<Boolean>("completed")
    }
}
