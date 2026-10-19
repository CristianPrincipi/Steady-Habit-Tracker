package com.cristianprincipi.habits.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.cristianprincipi.habits.core.widget.refreshQuietly
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes

/*
 * Every widget repainted when the day changes — M15.11.
 *
 * ⭐⭐ **The corner square is what made this necessary.** Before it, a widget that missed midnight
 * drew yesterday's grid until something else repainted it — stale, but only by one dot nobody was
 * looking for. A square that means *today* and still shows yesterday's tick at 07:00 says the habit
 * is done when it is not, which is exactly what the user would act on. Nothing in M15.6c's update
 * path fires at midnight: it reacts to writes, syncs and app starts, and a night has none of those.
 * The user's ruling (2026-10-15) was to add this.
 *
 * ### ⚠⚠ Inexact and non-waking, on purpose
 *
 * - **Not an exact alarm.** `CLAUDE.md` §4 keeps `setExactAndAllowWhileIdle` for reminders, and
 *   Android 12+ wants a permission for it. A repaint at 00:07 is as good as one at 00:00.
 * - **`RTC`, not `RTC_WAKEUP`.** A sleeping phone has nobody looking at its home screen. A
 *   non-waking alarm whose time has passed fires as the phone next wakes, which is the moment the
 *   picture starts to matter, and it costs the battery nothing overnight.
 *
 * ### ⚠ Armed from the widget, not from the app
 *
 * [armMidnightRepaint] runs every time a habit or number widget's session starts, so the alarm exists
 * exactly when a widget with a "today" square does: after placing one, after a reboot (the system
 * re-sends `APPWIDGET_UPDATE`), after an app update, and after every repaint — including the one this
 * alarm causes, which is how it re-arms for the next night. One `PendingIntent` throughout, so arming
 * twice replaces rather than stacks. A phone with neither widget has no alarm. (The number widget's
 * square joined at M16.3: it fills once today has a value, so it has a midnight too.)
 */

/**
 * Sets the repaint for the coming local midnight, replacing any already set.
 *
 * ⚠ **Late is safe; wrong is not.** If this fires late — Doze, a battery saver, a time zone that
 * moved under it — the square shows yesterday for longer, and a tap on it is refused rather than
 * written (`WidgetCheckOff`). So nothing here needs to be exact, and nothing here is.
 */
internal fun armMidnightRepaint(context: Context) {
    val clock =
        EntryPointAccessors
            .fromApplication(context.applicationContext, HabitWidgetEntryPoint::class.java)
            .clock()
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return
    alarms.setWindow(
        AlarmManager.RTC,
        nextMidnightMillis(clock),
        MIDNIGHT_WINDOW.inWholeMilliseconds,
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, MidnightRepaintReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    )
}

/**
 * The first instant of tomorrow, in the zone the clock is in **now**.
 *
 * `atStartOfDay` rather than `atTime(MIDNIGHT)`: in a zone whose daylight-saving change happens at
 * midnight, that midnight does not exist, and the day starts at 01:00 instead.
 */
internal fun nextMidnightMillis(clock: Clock): Long =
    LocalDate
        .now(clock)
        .plusDays(1)
        .atStartOfDay(clock.zone)
        .toInstant()
        .toEpochMilli()

/**
 * Where the midnight alarm lands. Repaints every widget, then re-arms for the following night if a
 * habit widget is still placed.
 *
 * ⚠ **Public**, for [HabitGridWidgetReceiver]'s reason: the manifest names it. **Not exported** —
 * only this app's own `PendingIntent` may reach it.
 *
 * ⚠ Re-armed here as well as in `provideGlance`, because a repaint only starts a new Glance
 * session when none is open. If one happens to be open at midnight it is updated in place, the
 * session start that would have re-armed never happens, and without this the chain would stop.
 */
class MidnightRepaintReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        val app = context.applicationContext
        // Its own scope, ended by `finish()`: `goAsync` hands the work ten seconds, and a repaint is
        // a few `updateAll` calls inside that.
        CoroutineScope(Dispatchers.Default).launch {
            try {
                EntryPointAccessors
                    .fromApplication(app, HabitWidgetEntryPoint::class.java)
                    .widgets()
                    .refreshQuietly()
                val manager = GlanceAppWidgetManager(app)
                val anySquare =
                    manager.getGlanceIds(HabitGridWidget::class.java).isNotEmpty() ||
                        manager.getGlanceIds(MeasureWidget::class.java).isNotEmpty()
                if (anySquare) armMidnightRepaint(app)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * How late after midnight the system may deliver the repaint. Ten minutes is the shortest window
 * Android 12+ honours for an inexact alarm, so asking for less buys nothing.
 */
private val MIDNIGHT_WINDOW = 10.minutes
