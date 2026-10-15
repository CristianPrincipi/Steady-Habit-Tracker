# R8 rules for the release build. Filled in properly in M14.3, where Room, Hilt and
# kotlinx.serialization are the usual casualties and the release build is actually tested.

# Glance identifies a widget KIND by its GlanceAppWidget class: updateAll() and getGlanceIds() look
# placed widgets up by `javaClass`, and the receiver-to-widget map it persists stores the class name.
# R8's horizontal class merging folded MeasureWidget into HabitGridWidget (one class, told apart by a
# synthetic $r8$classId), so HabitGridWidget().updateAll() also matched every measure widget and
# repainted it with habit code: "Remove this widget and add it again to pick a habit" on a number
# trend, after every sync and every app start (M15.10, ADR-148). Keeping the class stops the merge,
# and keeping its name keeps that persisted map valid across updates.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget

# Glance builds an ActionCallback by reflection: getDeclaredConstructor().newInstance() on the class
# name stored in the widget's click. Glance's own consumer rule keeps the CLASS, and under R8 full
# mode keeping a class no longer keeps its constructor - so R8 removed CheckOffTodayAction.<init>
# (usage.txt said so) and every tap on the habit widget's square failed with
# NoSuchMethodException, on the phone only: debug builds do not shrink (M15.11, ADR-151 §6).
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
