package com.cristianprincipi.habits.feature.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The `BroadcastReceiver` Android talks to for the measure widget — [HabitGridWidgetReceiver]'s
 * twin, and the second of the app's two picker entries.
 *
 * ⭐ **Two entries rather than one is the user's own call**: the picker shows a preview of what it
 * is offering, and a dot grid cannot preview a number, so the two cannot honestly share a row.
 *
 * ⚠ **Public, unlike everything else in this package**, because the manifest names it and the system
 * instantiates it by reflection.
 */
class MeasureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = MeasureWidget()
}
