package com.cristianprincipi.habits.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The `BroadcastReceiver` Android actually talks to. One per picker entry.
 *
 * ⚠ **Public, unlike everything else in this package**, because the manifest names it and the
 * system instantiates it by reflection. Its `android:label` is the row the user sees when they long
 * press their home screen.
 *
 * ⚠ **A new [HabitGridWidget] per call is correct, not wasteful.** The widget object holds no state
 * — everything it shows is read inside `provideGlance` from the widget's own `Preferences` and from
 * Room — so there is nothing for a shared instance to keep, and a shared one would be a field
 * living for as long as the process on the off chance a broadcast arrives.
 */
class HabitGridWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = HabitGridWidget()

    // ⚠⚠ TEMPORARY — M15.6b's 40–50s probe; see WidgetProbe. Both overrides call super and do
    // nothing else, which is what GlanceAppWidgetReceiver's KDoc requires of a subclass: "you must
    // call the super implementation, and you must not call goAsync". These two lines are the only
    // timestamps in the whole chain that come from BEFORE Glance's WorkManager hop.
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetProbe.log("receiver onUpdate ids=${appWidgetIds.toList()}")
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // The min/max pair is what says whether the launcher is sending the SAME size repeatedly or
        // a changing one — the difference between a recomposition loop we cause and one it drives.
        val w = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val h = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        WidgetProbe.log("receiver onOptionsChanged id=$appWidgetId w=$w..$maxW h=$h..$maxH")
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }
}
