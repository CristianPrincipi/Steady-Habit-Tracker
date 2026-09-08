package com.cristianprincipi.habits.feature.widget

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
}
