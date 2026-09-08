package com.cristianprincipi.habits.feature.widget

import android.util.Log
import com.cristianprincipi.habits.BuildConfig

/**
 * ⚠⚠ **TEMPORARY. Delete this file and its five call sites the moment M15.6b's 40–50s is
 * diagnosed.** It exists because the delay has now survived one wrong fix (ADR-129 §13) and the
 * evidence that disproved that fix could not distinguish the remaining candidates.
 *
 * ⭐ **What it adds that the previous capture could not have**: a timestamp *before* Glance. Every
 * line in §13's log came from inside a `SessionWorker` that was already running, so a delay between
 * the broadcast arriving and the worker starting was invisible — and that is the largest unmeasured
 * gap in the chain. [receiver] is logged from the `BroadcastReceiver` itself.
 *
 * The chain this covers, in order, so a capture reads as a sequence of gaps:
 *
 * ```
 * receiver   ACTION_APPWIDGET_UPDATE / OPTIONS_CHANGED arrives   <- the missing datum
 *   ...      WorkManager enqueue -> SessionWorker starts         <- gap A: is the worker queued?
 * provide    provideGlance entry
 * state      getAppWidgetState returned, with the habitId read   <- gap B: DataStore read-back race
 * loaded     loadHabitWidgetState returned, with elapsed ms      <- gap C: GetHabitsForYear hangs
 * grid       one line per Grid composition, with size + bitmap   <- gap D: recomposition loop
 * ```
 *
 * ⚠ **Debug builds only** — `BuildConfig.DEBUG` guards every call, so a release build carries the
 * string constants and nothing else. Nothing here logs a token or any Notion value; the only user
 * data that reaches it is a habit id, which is a Notion property id, not a secret.
 */
internal object WidgetProbe {
    const val TAG: String = "HabitWidgetProbe"

    fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
